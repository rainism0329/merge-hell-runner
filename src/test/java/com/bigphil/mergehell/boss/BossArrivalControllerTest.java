package com.bigphil.mergehell.boss;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BossArrivalControllerTest {
    @Test void fullPreparationMustElapseBeforeTheSingleCombatTransition() {
        BossArrivalController arrival = new BossArrivalController();
        assertFalse(arrival.active());
        assertFalse(arrival.tick(true));
        arrival.begin("Memory Leak Daemon", BossArrivalController.Direction.RIGHT);
        assertEquals(3, arrival.snapshot().secondsRemaining());
        for (int tick = 1; tick < BossArrivalController.DURATION_TICKS; tick++) {
            assertFalse(arrival.tick(true), "An early tick must not activate attacks");
            assertTrue(arrival.active());
            assertTrue(arrival.snapshot().secondsRemaining() >= 1);
        }
        assertTrue(arrival.tick(true));
        assertFalse(arrival.active());
        assertEquals(0, arrival.remainingTicks());
        assertEquals(0, arrival.snapshot().secondsRemaining());
        assertEquals(1, arrival.snapshot().progress());
        for (int tick = 0; tick < 30; tick++) assertFalse(arrival.tick(true));
    }

    @Test void pauseFreezesCountdownAndPublishedSnapshotsDoNotChange() {
        BossArrivalController arrival = new BossArrivalController();
        arrival.begin("Legacy Code Monstrosity", BossArrivalController.Direction.RIGHT);
        var first = arrival.snapshot();
        for (int tick = 0; tick < 63; tick++) arrival.tick(true);
        var paused = arrival.snapshot();
        assertEquals(2, paused.secondsRemaining());
        for (int tick = 0; tick < 500; tick++) assertFalse(arrival.tick(false));
        assertEquals(paused, arrival.snapshot());
        assertEquals(BossArrivalController.DURATION_TICKS, first.remainingTicks());
        assertEquals(3, first.secondsRemaining());
        for (int tick = 0; tick < 63; tick++) arrival.tick(true);
        assertEquals(1, arrival.snapshot().secondsRemaining());
    }

    @Test void duplicateSpawnSignalsCannotRestartPreparationButResetStartsTheNextBoss() {
        BossArrivalController arrival = new BossArrivalController();
        arrival.begin("The Architect", BossArrivalController.Direction.LEFT);
        for (int tick = 0; tick < 50; tick++) arrival.tick(true);
        var before = arrival.snapshot();
        assertFalse(arrival.begin("Kernel Panic Overlord", BossArrivalController.Direction.RIGHT));
        assertEquals(before, arrival.snapshot());
        arrival.reset();
        assertFalse(arrival.active());
        assertEquals("", arrival.snapshot().bossName());
        assertTrue(arrival.begin("Singularity Engine", BossArrivalController.Direction.RIGHT));
        assertEquals(BossArrivalController.DURATION_TICKS, arrival.remainingTicks());
        assertEquals("Singularity Engine", arrival.snapshot().bossName());
    }

    @Test void invalidArrivalCannotLeaveTheControllerHalfInitialized() {
        BossArrivalController arrival = new BossArrivalController();
        assertThrows(NullPointerException.class, () -> arrival.begin(null, BossArrivalController.Direction.RIGHT));
        assertThrows(NullPointerException.class, () -> arrival.begin("Boss", null));
        assertThrows(IllegalArgumentException.class, () -> arrival.begin(" ", BossArrivalController.Direction.RIGHT));
        assertFalse(arrival.active());
        assertThrows(IllegalArgumentException.class, () -> new BossArrivalController.Snapshot("Boss",
                BossArrivalController.Direction.RIGHT, 189, 188));
    }
}
