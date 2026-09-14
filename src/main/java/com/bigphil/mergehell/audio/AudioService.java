package com.bigphil.mergehell.audio;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Best-effort local audio. Commands never wait for device I/O. The daemon stays dormant
 * until explicitly unmuted and unpaused; quiet startup does not open an audio device.
 * A device failure silently disables audio until a new service is created.
 */
public final class AudioService implements AutoCloseable {
    public enum Status { STARTING, AVAILABLE, UNAVAILABLE, CLOSED }
    private record Controls(int volumePercent, boolean muted, boolean paused, long generation) {
        boolean silent() { return volumePercent == 0 || muted || paused; }
    }
    private record QueuedEvent(CombatFeedbackEvent event, long generation, long submittedNanos) { }
    private record Background(AmbienceScene scene, int volumePercent) { }

    private static final int MAX_PENDING_EVENTS = 64;
    private static final int MAX_EVENTS_PER_BLOCK = 16;
    private static final int MAX_VOICES = 8;
    private static final int BLOCK_FRAMES = 256;
    private static final int BLOCK_BYTES = BLOCK_FRAMES * 2;
    private static final int DEVICE_BUFFER_BYTES = BLOCK_BYTES * 4;
    private static final int FADE_FRAMES = SoundBank.SAMPLE_RATE * 40 / 1_000;
    private static final long MAX_EVENT_AGE_NANOS = 150_000_000L;

    private final ArrayBlockingQueue<QueuedEvent> pending = new ArrayBlockingQueue<>(MAX_PENDING_EVENTS);
    private final AtomicReference<Controls> controls = new AtomicReference<>(new Controls(35, true, false, 0));
    private final AtomicReference<Background> background = new AtomicReference<>(new Background(AmbienceScene.NONE, 20));
    private final AtomicReference<Status> status = new AtomicReference<>(Status.STARTING);
    private final AtomicBoolean closed = new AtomicBoolean();
    private final LongSupplier clock;
    private final Thread worker;
    private volatile String failureDescription = "";

    public AudioService() { this(JavaSoundOutput::new, System::nanoTime); }

    /** Injectable output and monotonic clock permit validation without opening a real audio device. */
    public AudioService(Supplier<? extends PcmOutput> outputFactory, LongSupplier clock) {
        Objects.requireNonNull(outputFactory, "outputFactory");
        this.clock = Objects.requireNonNull(clock, "clock");
        worker = new Thread(() -> run(outputFactory), "MergeHell-Audio");
        worker.setDaemon(true);
        worker.start();
    }

    /** True means queued, not necessarily audible: rate limits, staleness and voice budgets still apply. */
    public boolean emit(CombatFeedbackEvent event) {
        Objects.requireNonNull(event, "event");
        Controls current = controls.get();
        if (closed.get() || current.silent() || status.get() == Status.UNAVAILABLE || event.intensity() == 0) return false;
        boolean accepted = pending.offer(new QueuedEvent(event, current.generation(), clock.getAsLong()));
        if (accepted) LockSupport.unpark(worker);
        return accepted;
    }

    public void setVolumePercent(int volumePercent) {
        if (volumePercent < 0 || volumePercent > 100) throw new IllegalArgumentException("Volume must be from 0 to 100");
        updateControls(old -> new Controls(volumePercent, old.muted(), old.paused(), old.generation()));
    }

    public void setMuted(boolean muted) {
        updateControls(old -> new Controls(old.volumePercent(), muted, old.paused(), old.generation()));
    }

    /** Use for game pause, hidden tool windows and focus loss; resume does not replay old sounds. */
    public void setPaused(boolean paused) {
        updateControls(old -> new Controls(old.volumePercent(), old.muted(), paused, old.generation()));
    }

    public void setBackground(AmbienceScene scene, int volumePercent) {
        Objects.requireNonNull(scene);
        if (volumePercent < 0 || volumePercent > 100) throw new IllegalArgumentException();
        Background next = new Background(scene, volumePercent);
        if (!next.equals(background.getAndSet(next))) LockSupport.unpark(worker);
    }

    public AmbienceScene backgroundScene() { return background.get().scene(); }

    private void updateControls(UnaryOperator<Controls> update) {
        Controls before = controls.getAndUpdate(old -> {
            Controls next = update.apply(old);
            if (next.muted() != old.muted() || next.paused() != old.paused() || next.silent() != old.silent()) {
                return new Controls(next.volumePercent(), next.muted(), next.paused(), old.generation() + 1);
            }
            return next.equals(old) ? old : next;
        });
        if (controls.get() != before) LockSupport.unpark(worker);
    }

    private void run(Supplier<? extends PcmOutput> outputFactory) {
        PcmOutput output = null;
        try {
            while (!closed.get() && controls.get().silent()) LockSupport.parkNanos(100_000_000L);
            if (closed.get()) return;
            PcmMixer mixer = new PcmMixer(SoundBank.synthesize(), AmbienceBank.synthesized(), MAX_VOICES);
            // The user may have muted or left the game while the cache was being prepared.
            while (!closed.get() && controls.get().silent()) LockSupport.parkNanos(100_000_000L);
            if (closed.get()) return;
            output = Objects.requireNonNull(outputFactory.get(), "audio output");
            output.open(SoundBank.SAMPLE_RATE, DEVICE_BUFFER_BYTES);
            if (closed.get()) return;
            status.compareAndSet(Status.STARTING, Status.AVAILABLE);
            byte[] buffer = new byte[BLOCK_BYTES];
            Controls previous = controls.get();
            mixer.setGain(previous.silent() ? 0 : previous.volumePercent() / 100f, 0);
            boolean silentOutputFlushed = false;
            Background previousBackground = null;

            while (!closed.get()) {
                Controls current = controls.get();
                Background currentBackground = background.get();
                if (!currentBackground.equals(previousBackground)) {
                    mixer.setAmbience(currentBackground.scene(), currentBackground.volumePercent(),
                            SoundBank.SAMPLE_RATE * 600 / 1_000);
                    previousBackground = currentBackground;
                }
                if (current.generation() != previous.generation()) {
                    pending.removeIf(event -> event.generation() != current.generation());
                    output.flush();
                    if (!current.silent()) {
                        mixer.clear();
                        mixer.setGain(0, 0);
                    }
                    mixer.setGain(current.silent() ? 0 : current.volumePercent() / 100f, FADE_FRAMES);
                } else if (current.volumePercent() != previous.volumePercent() && !current.silent()) {
                    mixer.setGain(current.volumePercent() / 100f, FADE_FRAMES);
                }
                previous = current;

                if (current.silent() && mixer.gain() == 0) {
                    if (!silentOutputFlushed) {
                        mixer.clear();
                        output.flush();
                        silentOutputFlushed = true;
                    }
                    LockSupport.parkNanos(20_000_000L);
                    continue;
                }
                silentOutputFlushed = false;
                if (output.availableBytes() < BLOCK_BYTES) {
                    LockSupport.parkNanos(2_000_000L);
                    continue;
                }
                // The device can become writable while the user is leaving the game. Do not
                // admit a queued shot using the control snapshot from before that boundary.
                if (controls.get().generation() != current.generation()) continue;
                for (int count = 0; count < MAX_EVENTS_PER_BLOCK; count++) {
                    QueuedEvent queued = pending.poll();
                    if (queued == null) break;
                    long age = clock.getAsLong() - queued.submittedNanos();
                    if (!current.silent() && queued.generation() == current.generation()
                            && age >= 0 && age <= MAX_EVENT_AGE_NANOS) mixer.trigger(queued.event());
                }
                mixer.render(buffer, BLOCK_FRAMES);
                if (controls.get().generation() != current.generation()) {
                    mixer.clear();
                    continue;
                }
                output.write(buffer, BLOCK_BYTES);
            }
        } catch (Exception | LinkageError failure) {
            if (!closed.get()) {
                String detail = failure.getMessage();
                failureDescription = failure.getClass().getSimpleName()
                        + (detail == null || detail.isBlank() ? "" : ": " + detail);
                status.compareAndSet(Status.STARTING, Status.UNAVAILABLE);
                status.compareAndSet(Status.AVAILABLE, Status.UNAVAILABLE);
            }
        } finally {
            pending.clear();
            if (output != null) {
                try { output.close(); } catch (Exception | LinkageError ignored) { }
            }
            if (closed.get()) status.set(Status.CLOSED);
        }
    }

    public Status status() { return status.get(); }
    public String failureDescription() { return failureDescription; }
    public int pendingEventCount() { return pending.size(); }
    public boolean isMuted() { return controls.get().muted(); }
    public boolean isPaused() { return controls.get().paused(); }
    public int volumePercent() { return controls.get().volumePercent(); }

    /** Requests release without joining or touching the device on the caller's thread. */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            status.set(Status.CLOSED);
            pending.clear();
            worker.interrupt();
            LockSupport.unpark(worker);
        }
    }
}
