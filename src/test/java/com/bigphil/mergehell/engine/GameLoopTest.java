package com.bigphil.mergehell.engine;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class GameLoopTest {
    @Test
    void pauseResumeAndDisposeOwnScheduledTicks() {
        FakeScheduler scheduler = new FakeScheduler();
        AtomicLong clock = new AtomicLong();
        AtomicInteger ticks = new AtomicInteger();
        GameLoop loop = loop(scheduler, clock, ticks::incrementAndGet);
        loop.start();
        loop.start();
        assertEquals(1, scheduler.schedules);
        clock.addAndGet(GameLoop.SIXTY_HZ_STEP_NANOS);
        scheduler.fire();
        assertEquals(1, ticks.get());
        loop.pause();
        clock.addAndGet(60_000_000_000L);
        scheduler.fire();
        assertEquals(1, ticks.get());
        loop.resume();
        scheduler.fire();
        assertEquals(1, ticks.get(), "Resume must not replay hidden/paused time");
        clock.addAndGet(GameLoop.SIXTY_HZ_STEP_NANOS);
        scheduler.fire();
        assertEquals(2, ticks.get());
        loop.dispose();
        loop.resume();
        clock.addAndGet(GameLoop.SIXTY_HZ_STEP_NANOS);
        scheduler.fire();
        assertEquals(2, ticks.get());
        assertTrue(scheduler.disposed);
    }

    @Test
    void tickFailurePausesAndReportsOnlyOnce() {
        FakeScheduler scheduler = new FakeScheduler();
        AtomicLong clock = new AtomicLong();
        AtomicInteger errors = new AtomicInteger();
        GameLoop loop = new GameLoop(scheduler, () -> { throw new IllegalStateException("boom"); },
                error -> errors.incrementAndGet(), clock::get, GameLoop.SIXTY_HZ_STEP_NANOS);
        loop.start();
        clock.set(1_000_000_000L);
        scheduler.fire();
        scheduler.fire();
        assertTrue(loop.isPaused());
        assertEquals(1, errors.get());
        loop.resume();
        clock.addAndGet(GameLoop.SIXTY_HZ_STEP_NANOS);
        scheduler.fire();
        assertTrue(loop.isPaused());
        assertEquals(1, errors.get());
    }

    @Test
    void sixtyHzFollowsElapsedTimeDespiteSchedulerJitter() {
        FakeScheduler scheduler = new FakeScheduler();
        AtomicLong clock = new AtomicLong();
        AtomicInteger ticks = new AtomicInteger();
        GameLoop loop = loop(scheduler, clock, ticks::incrementAndGet);
        loop.start();
        scheduler.fire();
        assertEquals(0, ticks.get(), "Scheduling a callback does not create simulation time");
        long[] jitter = {7, 19, 3, 28, 11, 8, 24};
        int i = 0;
        while (clock.get() < 1_000_000_000L) {
            clock.set(Math.min(1_000_000_000L, clock.get() + jitter[i++ % jitter.length] * 1_000_000L));
            scheduler.fire();
        }
        assertEquals(60, ticks.get());
        scheduler.fire();
        assertEquals(60, ticks.get(), "Duplicate wakeups do not run extra ticks");
    }

    @Test
    void legacyCompatibilityPreservesRealTimeTickCount() {
        FakeScheduler scheduler = new FakeScheduler();
        AtomicLong clock = new AtomicLong();
        AtomicInteger ticks = new AtomicInteger();
        GameLoop loop = new GameLoop(scheduler, ticks::incrementAndGet,
                error -> fail(error), clock::get, GameLoop.LEGACY_STEP_NANOS);
        loop.start();
        for (int i = 0; i < 250; i++) {
            clock.addAndGet(8_000_000L);
            scheduler.fire();
        }
        assertEquals(125, ticks.get(), "Two seconds retain the existing 62.5 Hz behavior");
    }

    @Test
    void discardsExcessBacklogAfterFiveCatchUpTicks() {
        FakeScheduler scheduler = new FakeScheduler();
        AtomicLong clock = new AtomicLong();
        AtomicInteger ticks = new AtomicInteger();
        GameLoop loop = loop(scheduler, clock, ticks::incrementAndGet);
        loop.start();
        clock.set(10_000_000_000L);
        scheduler.fire();
        assertEquals(5, ticks.get());
        scheduler.fire();
        assertEquals(5, ticks.get());
        clock.addAndGet(GameLoop.SIXTY_HZ_STEP_NANOS);
        scheduler.fire();
        assertEquals(6, ticks.get());
    }

    @Test
    void resettingFromAnInputBoundaryStopsCurrentCatchUpBatch() {
        FakeScheduler scheduler = new FakeScheduler();
        AtomicLong clock = new AtomicLong();
        AtomicInteger ticks = new AtomicInteger();
        AtomicReference<GameLoop> reference = new AtomicReference<>();
        GameLoop loop = loop(scheduler, clock, () -> {
            ticks.incrementAndGet();
            reference.get().resetClock();
        });
        reference.set(loop);
        loop.start();
        clock.set(1_000_000_000L);
        scheduler.fire();
        assertEquals(1, ticks.get());
        scheduler.fire();
        assertEquals(1, ticks.get());
        clock.addAndGet(GameLoop.SIXTY_HZ_STEP_NANOS);
        scheduler.fire();
        assertEquals(2, ticks.get());
    }

    private static GameLoop loop(FakeScheduler scheduler, AtomicLong clock, Runnable tick) {
        return new GameLoop(scheduler, tick, error -> fail(error), clock::get,
                GameLoop.SIXTY_HZ_STEP_NANOS);
    }

    private static final class FakeScheduler implements TickScheduler {
        Runnable task;
        boolean disposed;
        int schedules;
        @Override public void scheduleAtFixedRate(Runnable task, long periodMillis) {
            this.task = task;
            schedules++;
        }
        void fire() { if (!disposed && task != null) task.run(); }
        @Override public void dispose() { disposed = true; }
    }
}
