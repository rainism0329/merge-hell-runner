package com.bigphil.mergehell.audio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.bigphil.mergehell.audio.CombatFeedbackEvent.Cue.*;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(5)
class AudioServiceTest {
    @Test
    void pauseWhileDeviceBecomesWritableRejectsThePreviouslyCapturedGeneration() throws Exception {
        FakeOutput output = new FakeOutput(false);
        try (AudioService service = new AudioService(() -> output, System::nanoTime)) {
            service.setMuted(false); output.allowBlocks(1); output.awaitBlocks(1);
            output.availableGate = new CountDownLatch(1);
            assertTrue(service.emit(CombatFeedbackEvent.of(FORCE_SHOT)));
            assertTrue(output.availableEntered.await(2, TimeUnit.SECONDS));
            service.setPaused(true); output.allowBlocks(4); output.availableGate.countDown();
            output.awaitBlocks(4);
            assertFalse(hasNonZero(output.allSamples()), "A queued shot cannot start after focus/pause wins the race");
        } finally {
            if (output.availableGate != null) output.availableGate.countDown();
        }
    }

    @Test
    void defaultMuteDoesNotEvenConstructAnOutputAndOpeningInAPausedMenuStaysQuiet() throws Exception {
        AtomicInteger factories = new AtomicInteger();
        FakeOutput output = new FakeOutput(false);
        try (AudioService service = new AudioService(() -> { factories.incrementAndGet(); return output; }, System::nanoTime)) {
            assertTrue(service.isMuted());
            service.setBackground(AmbienceScene.BOSS, 100);
            assertFalse(service.emit(CombatFeedbackEvent.of(BOSS_PHASE)));
            assertFalse(output.openEntered.await(100, TimeUnit.MILLISECONDS));
            service.setPaused(true);
            service.setMuted(false);
            assertFalse(output.openEntered.await(100, TimeUnit.MILLISECONDS));
            assertEquals(0, factories.get());
            service.setPaused(false);
            assertTrue(output.openEntered.await(2, TimeUnit.SECONDS));
            output.allowBlocks(1); output.awaitBlocks(1);
            assertTrue(hasNonZero(output.lastBlock()));
        }
        assertTrue(output.closed.await(2, TimeUnit.SECONDS));
    }

    @Test
    void backgroundFadesWithMuteAndCannotLeakIntoAnEffectsOnlyResume() throws Exception {
        FakeOutput output = new FakeOutput(false);
        try (AudioService service = new AudioService(() -> output, System::nanoTime)) {
            service.setBackground(AmbienceScene.HEAP, 80);
            service.setMuted(false);
            output.allowBlocks(2); output.awaitBlocks(2);
            assertTrue(hasNonZero(output.lastBlock()));
            service.setMuted(true);
            output.allowBlocks(4); output.awaitBlocks(4);
            assertFalse(hasNonZero(Arrays.copyOfRange(output.lastBlock(), 256, 512)));
            service.setBackground(AmbienceScene.BOSS, 0);
            service.setMuted(false);
            output.allowBlocks(1); output.awaitBlocks(1);
            assertFalse(hasNonZero(output.lastBlock()));
            assertTrue(service.emit(CombatFeedbackEvent.of(BOSS_HIT)));
            output.allowBlocks(1); output.awaitBlocks(1);
            assertTrue(hasNonZero(output.lastBlock()));
        }
    }

    @Test
    void blockedDeviceOpenDoesNotBlockCommandsOrCloseAndPendingEventsAreBounded() throws Exception {
        FakeOutput output = new FakeOutput(true);
        AudioService service = new AudioService(() -> output, System::nanoTime);
        try {
            service.setMuted(false);
            assertTrue(output.openEntered.await(2, TimeUnit.SECONDS));
            for (int i = 0; i < 64; i++) assertTrue(service.emit(CombatFeedbackEvent.of(COMMIT_SHOT)));
            assertFalse(service.emit(CombatFeedbackEvent.of(COMMIT_SHOT)));
            assertEquals(64, service.pendingEventCount());
            service.setPaused(true);
            service.setMuted(true);
            service.setVolumePercent(12);
            assertFalse(service.emit(CombatFeedbackEvent.of(BOSS_PHASE)));
            assertTrue(service.isPaused());
            assertTrue(service.isMuted());
            assertEquals(12, service.volumePercent());
            service.close();
            service.close();
            assertEquals(AudioService.Status.CLOSED, service.status());
            assertFalse(service.emit(CombatFeedbackEvent.of(COMMIT_SHOT)));
            assertTrue(output.closed.await(2, TimeUnit.SECONDS), "Interruptible device open releases in the worker");
            assertEquals(1, output.closeCalls.get());
            assertEquals(0, service.pendingEventCount());
            assertEquals(1, output.deviceThreads.size());
            assertFalse(output.deviceThreads.contains(Thread.currentThread().getId()));
        } finally {
            output.allowOpen.countDown();
            service.close();
        }
    }

    @Test
    void pauseCancelsQueuedAudioAndResumeDoesNotReplayIt() throws Exception {
        FakeOutput output = new FakeOutput(false);
        try (AudioService service = new AudioService(() -> output, System::nanoTime)) {
            service.setMuted(false);
            assertTrue(output.openEntered.await(2, TimeUnit.SECONDS));
            output.allowBlocks(1);
            output.awaitBlocks(1); // Establish active gain before requesting its fade.
            assertTrue(service.emit(CombatFeedbackEvent.of(FORCE_SHOT)));
            service.setPaused(true);
            assertFalse(service.emit(CombatFeedbackEvent.of(COMMIT_SHOT)));
            output.allowBlocks(4);
            output.awaitBlocks(4);
            assertFalse(hasNonZero(output.allSamples()));

            service.setPaused(false);
            output.allowBlocks(1);
            output.awaitBlocks(1);
            assertFalse(hasNonZero(output.lastBlock()), "Old queued shot is invalid after the pause boundary");
            assertTrue(service.emit(CombatFeedbackEvent.of(UPGRADE)));
            output.allowBlocks(1);
            output.awaitBlocks(1);
            assertTrue(hasNonZero(output.lastBlock()));
        }
        assertTrue(output.closed.await(2, TimeUnit.SECONDS));
    }

    @Test
    void muteAndZeroVolumeRejectNewEventsAndFadeExistingTailsToSilence() throws Exception {
        FakeOutput output = new FakeOutput(false);
        try (AudioService service = new AudioService(() -> output, System::nanoTime)) {
            service.setMuted(false);
            assertTrue(output.openEntered.await(2, TimeUnit.SECONDS));
            assertTrue(service.emit(CombatFeedbackEvent.of(SUDO)));
            output.allowBlocks(1);
            output.awaitBlocks(1);
            assertTrue(hasNonZero(output.lastBlock()));
            service.setMuted(true);
            assertFalse(service.emit(CombatFeedbackEvent.of(BOSS_PHASE)));
            output.allowBlocks(4);
            output.awaitBlocks(4);
            byte[] faded = output.lastBlock();
            assertFalse(hasNonZero(Arrays.copyOfRange(faded, 256, faded.length)));

            service.setMuted(false);
            output.allowBlocks(1);
            output.awaitBlocks(1);
            assertFalse(hasNonZero(output.lastBlock()), "Unmuting cannot restart the previous tail");
            service.setVolumePercent(0);
            assertFalse(service.emit(CombatFeedbackEvent.of(COMMIT_SHOT)));
        }
        assertTrue(output.closed.await(2, TimeUnit.SECONDS));
    }

    @Test
    void oldEventsExpireWhileOutputIsNotWritable() throws Exception {
        FakeOutput output = new FakeOutput(false);
        AtomicLong clock = new AtomicLong();
        try (AudioService service = new AudioService(() -> output, clock::get)) {
            service.setMuted(false);
            assertTrue(output.openEntered.await(2, TimeUnit.SECONDS));
            assertTrue(service.emit(CombatFeedbackEvent.of(COMMIT_SHOT)));
            clock.set(200_000_000L);
            output.allowBlocks(1);
            output.awaitBlocks(1);
            assertFalse(hasNonZero(output.lastBlock()));
            assertTrue(service.emit(CombatFeedbackEvent.of(COMMIT_SHOT)));
            output.allowBlocks(1);
            output.awaitBlocks(1);
            assertTrue(hasNonZero(output.lastBlock()));
        }
        assertTrue(output.closed.await(2, TimeUnit.SECONDS));
    }

    @Test
    void openFailureSilentlyDisablesAudioAndClosesPartialDevice() throws Exception {
        FakeOutput output = new FakeOutput(false);
        output.failOpen = true;
        try (AudioService service = new AudioService(() -> output, System::nanoTime)) {
            service.setMuted(false);
            assertTrue(output.openEntered.await(2, TimeUnit.SECONDS));
            assertTrue(output.closed.await(2, TimeUnit.SECONDS));
            assertEquals(AudioService.Status.UNAVAILABLE, service.status());
            assertFalse(service.emit(CombatFeedbackEvent.of(COMMIT_SHOT)));
            assertTrue(service.failureDescription().contains("open failure"));
            assertEquals(1, output.closeCalls.get());
        }
    }

    @Test
    void writeFailureDoesNotEscapeToGameAndReleasesDevice() throws Exception {
        FakeOutput output = new FakeOutput(false);
        output.failWrite = true;
        try (AudioService service = new AudioService(() -> output, System::nanoTime)) {
            service.setMuted(false);
            assertTrue(output.openEntered.await(2, TimeUnit.SECONDS));
            output.allowBlocks(1);
            assertTrue(output.closed.await(2, TimeUnit.SECONDS));
            assertEquals(AudioService.Status.UNAVAILABLE, service.status());
            assertFalse(service.emit(CombatFeedbackEvent.of(HIT)));
            assertTrue(service.failureDescription().contains("write failure"));
        }
        assertEquals(1, output.closeCalls.get());
    }

    private static boolean hasNonZero(byte[] values) {
        for (byte value : values) if (value != 0) return true;
        return false;
    }

    private static final class FakeOutput implements PcmOutput {
        final CountDownLatch openEntered = new CountDownLatch(1);
        final CountDownLatch allowOpen;
        final CountDownLatch closed = new CountDownLatch(1);
        final AtomicInteger closeCalls = new AtomicInteger();
        final Set<Long> deviceThreads = ConcurrentHashMap.newKeySet();
        final AtomicInteger writableBlocks = new AtomicInteger();
        final Semaphore completedBlocks = new Semaphore(0);
        final List<byte[]> blocks = new ArrayList<>();
        volatile boolean failOpen;
        volatile boolean failWrite;
        volatile CountDownLatch availableGate;
        final CountDownLatch availableEntered = new CountDownLatch(1);

        FakeOutput(boolean blockOpen) { allowOpen = new CountDownLatch(blockOpen ? 1 : 0); }
        private void recordThread() { deviceThreads.add(Thread.currentThread().getId()); }

        @Override public void open(int sampleRate, int bufferBytes) throws Exception {
            recordThread();
            assertEquals(SoundBank.SAMPLE_RATE, sampleRate);
            assertTrue(bufferBytes > 0);
            openEntered.countDown();
            allowOpen.await();
            if (failOpen) throw new IOException("open failure");
        }
        @Override public int availableBytes() throws InterruptedException {
            recordThread();
            CountDownLatch gate = availableGate;
            if (gate != null) { availableEntered.countDown(); gate.await(); }
            return writableBlocks.get() > 0 ? 512 : 0;
        }
        @Override public void write(byte[] pcm, int byteCount) throws Exception {
            recordThread();
            if (failWrite) throw new IOException("write failure");
            writableBlocks.decrementAndGet();
            synchronized (blocks) { blocks.add(Arrays.copyOf(pcm, byteCount)); }
            completedBlocks.release();
        }
        @Override public void flush() { recordThread(); }
        @Override public void close() {
            recordThread();
            closeCalls.incrementAndGet();
            closed.countDown();
        }
        void allowBlocks(int count) { writableBlocks.addAndGet(count); }
        void awaitBlocks(int count) throws InterruptedException {
            assertTrue(completedBlocks.tryAcquire(count, 2, TimeUnit.SECONDS), "Audio worker did not produce requested blocks");
        }
        byte[] lastBlock() { synchronized (blocks) { return blocks.get(blocks.size() - 1); } }
        byte[] allSamples() {
            synchronized (blocks) {
                byte[] all = new byte[blocks.stream().mapToInt(block -> block.length).sum()];
                int offset = 0;
                for (byte[] block : blocks) { System.arraycopy(block, 0, all, offset, block.length); offset += block.length; }
                return all;
            }
        }
    }
}
