package com.bigphil.mergehell;

import com.bigphil.mergehell.engine.GameLoop;
import com.bigphil.mergehell.engine.InputCommandBuffer;
import com.bigphil.mergehell.engine.TickScheduler;
import com.bigphil.mergehell.model.Boss;
import com.bigphil.mergehell.persistence.MergeHellState;
import com.bigphil.mergehell.persistence.MergeHellStateService;
import com.bigphil.mergehell.render.FrameMailbox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.event.ActionEvent;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class GamePanelLifecycleTest {
    private final MergeHellStateService storage = MergeHellStateService.getInstance();
    private final List<Harness> windows = new ArrayList<>();
    private MergeHellState original;

    @BeforeEach void isolate() {
        original = storage.getState();
        storage.loadState(new MergeHellState());
    }
    @AfterEach void cleanup() {
        windows.forEach(h -> h.panel.dispose());
        storage.loadState(original);
    }

    @Test void closingDuringASettlingTickPreservesTheNextEntrance() throws Exception {
        verifyCloseAtSettlement(1);
    }

    @Test void closingDuringTheFinalSettlingTickPreservesRewardsAndClearsTheRun() throws Exception {
        verifyCloseAtSettlement(4);
    }

    private void verifyCloseAtSettlement(int level) throws Exception {
        Harness h = window(); h.startAt(level);
        BlockingLevelManager blocker = new BlockingLevelManager(level);
        h.panel.setLevelManager(blocker);
        Boss boss = new Boss("closing fixture", 100, "#", 700, level, 11L);
        boss.activate(); boss.takeDamage(100);
        set(h.panel, "boss", boss);
        set(h.panel, "bossDeathTimer", 1);
        set(h.panel, "state", GameState.BOSS_FIGHT);
        h.panel.getSession().setGameplayState(GameState.BOSS_FIGHT);

        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread tick = thread("settling-tick", h::scheduledTick, failure);
        CountDownLatch closeAttempted = new CountDownLatch(1);
        Thread close = thread("closing-panel", () -> {
            closeAttempted.countDown();
            h.panel.dispose();
        }, failure);
        tick.start();
        try {
            assertTrue(blocker.entered.await(3, TimeUnit.SECONDS), "The real tick must reach the pre-settlement latch");
            close.start();
            assertTrue(closeAttempted.await(3, TimeUnit.SECONDS));
            awaitBlocked(close);
            assertEquals(level, storage.readCheckpoint().orElseThrow().mission);
            assertTrue(storage.ownedByAnotherWindow("probe-other-window"));
        } finally {
            blocker.proceed.countDown();
            join(tick);
            if (close.getState() != Thread.State.NEW) join(close);
        }
        assertNull(failure.get());
        assertEquals(GameState.MISSION_COMPLETE, get(h.panel, "state"));
        assertEquals(Set.of(level), storage.getState().completedMissions);
        assertEquals(250, storage.getState().refactorPoints);
        assertFalse(storage.ownedByAnotherWindow("probe-other-window"));
        assertTrue(((FrameMailbox) get(h.panel, "frames")).isClosed());
        assertTrue(h.scheduler.closed);
        if (level < 4) {
            var restored = storage.claimCheckpoint("reopened-window").orElseThrow();
            assertEquals(level + 1, restored.mission);
            storage.releaseRun("reopened-window");
        } else {
            assertTrue(storage.readCheckpoint().isEmpty());
            assertEquals(1, storage.getState().topScores.size());
        }
    }

    @Test void anAlreadyQueuedTickAndLateLoopErrorCannotMutateAClosedPanel() throws Exception {
        Harness h = window(); h.startAt(0);
        var sessionBefore = h.panel.getSession();
        var savedBefore = storage.readCheckpoint().orElseThrow();
        h.queue("NEW_RANKED_RUN");
        CountDownLatch tickAttempted = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        // Represents a callback already admitted by GameLoop, before it acquires the panel monitor.
        Thread oldTick = thread("queued-old-tick", () -> {
            tickAttempted.countDown();
            h.panel.actionPerformed(null);
        }, failure);
        synchronized (h.panel) {
            oldTick.start();
            assertTrue(tickAttempted.await(3, TimeUnit.SECONDS));
            awaitBlocked(oldTick);
            h.panel.dispose();
        }
        join(oldTick);
        assertNull(failure.get());
        assertSame(sessionBefore, h.panel.getSession());
        assertEquals(savedBefore.runId, storage.readCheckpoint().orElseThrow().runId);
        assertFalse(storage.ownedByAnotherWindow("probe-other-window"));
        h.scheduledTick(); // Even an external stale scheduler callback is harmless.
        var errorHandler = GamePanel.class.getDeclaredMethod("handleLoopError", Throwable.class);
        errorHandler.setAccessible(true);
        errorHandler.invoke(h.panel, new IllegalStateException("late shutdown error"));
        assertSame(sessionBefore, h.panel.getSession());
        assertEquals(GameState.RUNNING, get(h.panel, "state"));
        assertTrue(((FrameMailbox) get(h.panel, "frames")).isClosed());
    }

    private Harness window() throws Exception {
        Harness h = new Harness(); windows.add(h); return h;
    }
    private static Thread thread(String name, Runnable action, AtomicReference<Throwable> failure) {
        Thread thread = new Thread(() -> {
            try { action.run(); } catch (Throwable error) { failure.compareAndSet(null, error); }
        }, name);
        thread.setDaemon(true);
        return thread;
    }
    private static void awaitBlocked(Thread thread) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (thread.getState() != Thread.State.BLOCKED && thread.isAlive() && System.nanoTime() < deadline) {
            Thread.sleep(1);
        }
        assertEquals(Thread.State.BLOCKED, thread.getState(), "The callback must be waiting for the panel monitor");
    }
    private static void join(Thread thread) throws InterruptedException {
        thread.join(3_000);
        assertFalse(thread.isAlive(), "Lifecycle operations must finish without a worker join deadlock");
    }
    private static Object get(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    private static final class BlockingLevelManager extends LevelManager {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch proceed = new CountDownLatch(1);
        BlockingLevelManager(int level) { super(level, 11L); }
        @Override public double getCameraMaxX() {
            entered.countDown();
            try {
                if (!proceed.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Settlement latch timed out");
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(error);
            }
            return super.getCameraMaxX();
        }
    }
    private static final class Scheduler implements TickScheduler {
        Runnable callback;
        volatile boolean closed;
        public void scheduleAtFixedRate(Runnable tick, long period) { callback = tick; }
        public void dispose() { closed = true; }
    }
    private static final class Harness {
        final Scheduler scheduler = new Scheduler();
        final AtomicLong clock = new AtomicLong();
        GamePanel panel;
        Harness() throws Exception {
            SwingUtilities.invokeAndWait(() -> panel = new GamePanel(scheduler, clock::get));
        }
        void queue(String name) throws Exception {
            SwingUtilities.invokeAndWait(() -> {
                panel.getActionMap().get(name).actionPerformed(new ActionEvent(panel, 0, name));
                panel.getActionMap().get(name + "_RELEASE").actionPerformed(new ActionEvent(panel, 0, name));
            });
        }
        void startAt(int level) throws Exception {
            queue("START");
            ((InputCommandBuffer) get(panel, "inputCommands")).drain();
            if (level > 0) {
                set(panel, "level", level);
                var advance = GamePanel.class.getDeclaredMethod("advanceLevel");
                advance.setAccessible(true); advance.invoke(panel);
            }
        }
        void scheduledTick() {
            clock.addAndGet(GameLoop.LEGACY_STEP_NANOS);
            scheduler.callback.run();
        }
    }
}
