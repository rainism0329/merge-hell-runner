package com.bigphil.mergehell;

import org.junit.jupiter.api.Test;

import com.bigphil.mergehell.model.EntityType;
import com.bigphil.mergehell.model.Platform;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevelManagerTest {

    @Test
    void darkMissionRouteDoesNotEndBeforeItsTimedBossGate() {
        assertTrue(new LevelManager(0).getCameraMaxX() > 500_000);
        assertEquals(8_600, new LevelManager(1).getCameraMaxX(), 0.001);
    }

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

    @Test
    void completedBattleZoneDoesNotRestartWhilePlayerRemainsInsideIt() {
        LevelManager level = new LevelManager(1);
        LevelManager.BattleZone battle = level.getBattleAt(900);

        level.enterBattle(battle);
        int ticks = 0;
        while (level.isInBattle() && ticks++ < 500) {
            if (level.needsWaveSpawn(0)) {
                level.popWave();
                level.startNextWaveTimer();
            }
        }

        assertFalse(level.isInBattle());
        assertNull(level.getBattleAt(900));
        level.enterBattle(battle);
        assertFalse(level.isInBattle());
    }

    @Test
    void labNextShortcutAdvancesAcrossLegacyRouteEncounters() {
        LevelManager level = new LevelManager(1);

        assertEquals(900, level.advanceToNextEncounterForTesting(100));
        LevelManager.BattleZone firstBattle = level.getBattleAt(900);
        level.enterBattle(firstBattle);

        assertEquals(1_501, level.advanceToNextEncounterForTesting(900));
        assertFalse(level.isInBattle());
        assertNull(level.getBattleAt(900));
        assertEquals(2_300, level.advanceToNextEncounterForTesting(1_501));
    }

    @Test
    void labBossShortcutClearsArenasAndEarlierSpawnTriggers() {
        LevelManager level = new LevelManager(2);

        level.advanceToBossGateForTesting();

        assertNull(level.getBattleAt(900));
        assertNull(level.getBattleAt(2_300));
        assertTrue(level.getPendingTriggers(level.getBossGateX()).isEmpty());
        assertTrue(level.shouldSpawnBoss(level.getBossGateX()));
    }

    @Test
    void laterLevelsHaveDistinctEnemyAndTerrainEcosystems() {
        List<LevelManager> levels = List.of(
                new LevelManager(1), new LevelManager(2),
                new LevelManager(3), new LevelManager(4));

        assertTrue(levels.get(0).getEnemyRoster().contains(EntityType.LEAK));
        assertTrue(levels.get(1).getEnemyRoster().contains(EntityType.SENTINEL));
        assertTrue(levels.get(2).getEnemyRoster().contains(EntityType.INTERRUPT));
        assertTrue(levels.get(3).getEnemyRoster().contains(EntityType.MIRROR));

        Set<Set<EntityType>> rosters = new HashSet<>();
        Set<Set<Platform.Style>> terrainStyles = new HashSet<>();
        for (LevelManager level : levels) {
            rosters.add(level.getEnemyRoster());
            terrainStyles.add(level.getPlatforms().stream().map(platform -> platform.style)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet()));
        }
        assertEquals(4, rosters.size());
        assertEquals(4, terrainStyles.size());
    }
}
