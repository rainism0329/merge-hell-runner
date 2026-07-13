package com.bigphil.mergehell.engine;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameLoopTest {
    @Test
    void pauseResumeAndDisposeOwnScheduledTicks() {
        FakeScheduler scheduler = new FakeScheduler();
        AtomicInteger ticks = new AtomicInteger();
        GameLoop loop = new GameLoop(scheduler, ticks::incrementAndGet, error -> { throw new AssertionError(error); });
        loop.start();
        scheduler.fire();
        assertEquals(1, ticks.get());
        loop.pause();
        scheduler.fire();
        assertEquals(1, ticks.get());
        loop.resume();
        scheduler.fire();
        assertEquals(2, ticks.get());
        loop.dispose();
        assertTrue(scheduler.disposed);
    }

    @Test
    void tickFailurePausesAndReportsOnlyOnce() {
        FakeScheduler scheduler = new FakeScheduler();
        AtomicInteger errors = new AtomicInteger();
        GameLoop loop = new GameLoop(scheduler, () -> { throw new IllegalStateException("boom"); },
                error -> errors.incrementAndGet());
        loop.start();
        scheduler.fire();
        scheduler.fire();
        assertTrue(loop.isPaused());
        assertEquals(1, errors.get());
    }

    private static final class FakeScheduler implements TickScheduler {
        Runnable task;
        boolean disposed;
        @Override public void scheduleAtFixedRate(Runnable task, long periodMillis) { this.task = task; }
        void fire() { if (!disposed && task != null) task.run(); }
        @Override public void dispose() { disposed = true; }
    }
}
