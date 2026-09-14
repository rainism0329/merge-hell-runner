package com.bigphil.mergehell.engine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InputCommandBufferTest {
    @Test
    void suppressesAutoRepeatButAcceptsAnotherPhysicalPress() {
        InputCommandBuffer input = new InputCommandBuffer();
        AtomicInteger jumps = new AtomicInteger();
        input.press(32, jumps::incrementAndGet);
        input.press(32, jumps::incrementAndGet);
        assertEquals(0, jumps.get());
        input.drain();
        input.press(32, jumps::incrementAndGet);
        input.drain();
        assertEquals(1, jumps.get());
        input.release(32, () -> { });
        input.press(32, jumps::incrementAndGet);
        input.drain();
        assertEquals(2, jumps.get());
    }

    @Test
    void preservesHeldPressReleaseAndSingleActionOrderAcrossCatchUp() {
        InputCommandBuffer input = new InputCommandBuffer();
        List<String> events = new ArrayList<>();
        input.press(39, () -> events.add("right:on"));
        input.press(16, () -> events.add("dash"));
        input.release(39, () -> events.add("right:off"));
        input.submit(() -> events.add("upgrade:2"));
        for (int i = 0; i < 5; i++) input.drain();
        assertEquals(List.of("right:on", "dash", "right:off", "upgrade:2"), events);
    }

    @Test
    void focusLossCancelsQueuedActionsAndClearsPhysicalKeys() {
        InputCommandBuffer input = new InputCommandBuffer();
        List<String> events = new ArrayList<>();
        input.press(66, () -> events.add("stale bomb"));
        input.clearAndSubmit(() -> events.add("clear held and pause"));
        input.press(66, () -> events.add("fresh bomb"));
        input.drain();
        assertEquals(List.of("clear held and pause", "fresh bomb"), events);
    }

    @Test
    void commandsSubmittedDuringDrainWaitUntilNextSimulationBoundary() {
        InputCommandBuffer input = new InputCommandBuffer();
        List<Integer> events = new ArrayList<>();
        input.submit(() -> {
            events.add(1);
            input.submit(() -> events.add(2));
        });
        input.drain();
        assertEquals(List.of(1), events);
        input.drain();
        assertEquals(List.of(1, 2), events);
    }

    @Test
    void focusLossDuringDrainCancelsRemainingCommandsAndAppliesBarrierBeforeReturning() {
        InputCommandBuffer input = new InputCommandBuffer();
        List<String> events = new ArrayList<>();
        input.submit(() -> {
            events.add("admitted action finishes");
            input.clearAndSubmit(() -> events.add("clear held and pause"));
            input.submit(() -> events.add("fresh action"));
        });
        input.press(66, () -> events.add("stale bomb"));

        input.drain();

        assertEquals(List.of("admitted action finishes", "clear held and pause"), events);
        input.drain();
        assertEquals(List.of("admitted action finishes", "clear held and pause", "fresh action"), events);
    }

    @Test
    void hostCanCancelWhileSimulationCallbackRunsWithoutWaitingForItsLock() throws Exception {
        InputCommandBuffer input = new InputCommandBuffer();
        List<String> events = new ArrayList<>();
        CountDownLatch callbackStarted = new CountDownLatch(1);
        CountDownLatch allowCallbackToFinish = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        input.submit(() -> {
            callbackStarted.countDown();
            try {
                assertTrue(allowCallbackToFinish.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            }
            events.add("in-flight action finishes");
        });
        input.submit(() -> events.add("stale bomb"));
        Thread simulation = new Thread(() -> {
            try { input.drain(); } catch (Throwable exception) { failure.set(exception); }
        }, "InputCommandBufferTest-simulation");
        simulation.start();
        try {
            assertTrue(callbackStarted.await(5, TimeUnit.SECONDS));
            input.clearAndSubmit(() -> events.add("clear held and pause"));
        } finally {
            allowCallbackToFinish.countDown();
            simulation.join(5_000);
        }

        assertFalse(simulation.isAlive());
        assertNull(failure.get());
        assertEquals(List.of("in-flight action finishes", "clear held and pause"), events);
    }

    @Test
    void barrierSubmittedWhileApplyingABarrierRunsNowButOrdinaryInputWaits() {
        InputCommandBuffer input = new InputCommandBuffer();
        List<String> events = new ArrayList<>();
        input.clearAndSubmit(() -> {
            events.add("first barrier");
            input.clearAndSubmit(() -> {
                events.add("latest barrier");
                input.submit(() -> {
                    events.add("ordinary action");
                    input.submit(() -> events.add("next ordinary action"));
                });
            });
        });

        input.drain();
        assertEquals(List.of("first barrier", "latest barrier"), events);
        input.drain();
        assertEquals(List.of("first barrier", "latest barrier", "ordinary action"), events);
        input.drain();
        assertEquals(List.of("first barrier", "latest barrier", "ordinary action", "next ordinary action"), events);
    }
}
