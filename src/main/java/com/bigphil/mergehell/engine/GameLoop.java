package com.bigphil.mergehell.engine;

import com.intellij.openapi.Disposable;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

public final class GameLoop implements Disposable {
    public static final long SIXTY_HZ_STEP_NANOS = 1_000_000_000L / 60;
    /** Temporary compatibility cadence until all legacy motion and timers are migrated. */
    public static final long LEGACY_STEP_NANOS = 16_000_000L;
    private static final long POLL_MILLIS = 8;
    private static final int MAX_CATCH_UP_TICKS = 5;

    private final TickScheduler scheduler;
    private final Runnable tick;
    private final Consumer<Throwable> errorHandler;
    private final LongSupplier clock;
    private final FixedStepAccumulator accumulator;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean paused = new AtomicBoolean();
    private final AtomicBoolean disposed = new AtomicBoolean();
    private final AtomicBoolean errorReported = new AtomicBoolean();
    private final AtomicReference<Long> requestedReset = new AtomicReference<>();

    public GameLoop(TickScheduler scheduler, Runnable tick, Consumer<Throwable> errorHandler) {
        this(scheduler, tick, errorHandler, System::nanoTime, SIXTY_HZ_STEP_NANOS);
    }

    public GameLoop(TickScheduler scheduler, Runnable tick, Consumer<Throwable> errorHandler,
                    LongSupplier clock, long stepNanos) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.tick = Objects.requireNonNull(tick, "tick");
        this.errorHandler = Objects.requireNonNull(errorHandler, "errorHandler");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.accumulator = new FixedStepAccumulator(stepNanos, MAX_CATCH_UP_TICKS);
    }

    public void start() {
        if (disposed.get() || !started.compareAndSet(false, true)) return;
        resetClock();
        scheduler.scheduleAtFixedRate(this::runFrame, POLL_MILLIS);
    }

    private void runFrame() {
        if (disposed.get() || paused.get()) return;
        try {
            Long resetAt = requestedReset.getAndSet(null);
            if (resetAt != null) accumulator.reset(resetAt);
            accumulator.consume(clock.getAsLong(), () -> {
                // A tick may pause, reset or dispose the loop during a catch-up batch.
                if (!disposed.get() && !paused.get() && requestedReset.get() == null) tick.run();
            });
        } catch (Throwable error) {
            pause();
            if (errorReported.compareAndSet(false, true)) errorHandler.accept(error);
        }
    }

    public void pause() {
        paused.set(true);
        resetClock();
    }

    public void resume() {
        if (disposed.get() || !paused.get()) return;
        resetClock();
        paused.set(false);
    }

    /** Discards time before a world-state boundary without stopping menu input polling. */
    public void resetClock() { requestedReset.set(clock.getAsLong()); }
    public boolean isPaused() { return paused.get(); }

    @Override
    public void dispose() {
        if (disposed.compareAndSet(false, true)) scheduler.dispose();
    }
}
