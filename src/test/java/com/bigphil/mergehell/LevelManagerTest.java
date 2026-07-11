package com.bigphil.mergehell;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevelManagerTest {

    @Test
    void bossGate_isAfterAllAuthoredBattleZones() {
        LevelManager level = new LevelManager(0);

        assertFalse(level.shouldSpawnBoss(7_599));
        assertTrue(level.shouldSpawnBoss(7_600));
        assertTrue(level.getBossGateX() > 7_000);
    }

    @Test
    void progress_isClampedToTheBossGate() {
        LevelManager level = new LevelManager(0);

        assertEquals(0.0, level.getProgress(-10));
        assertEquals(0.5, level.getProgress(level.getBossGateX() / 2), 0.0001);
        assertEquals(1.0, level.getProgress(level.getBossGateX() + 500));
    }
}
