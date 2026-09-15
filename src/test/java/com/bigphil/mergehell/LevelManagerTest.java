package com.bigphil.mergehell;

import org.junit.jupiter.api.Test;

import com.bigphil.mergehell.model.EntityType;
import com.bigphil.mergehell.model.Platform;
import com.bigphil.mergehell.world.ChapterRouteController;

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
        assertEquals(10_900, new LevelManager(1).getCameraMaxX(), 0.001);
    }

    @Test
    void bossGate_isAfterAllAuthoredBattleZones() {
        LevelManager level = new LevelManager(0);

        assertFalse(level.shouldSpawnBoss(9_899));
        assertTrue(level.shouldSpawnBoss(9_900));
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

    @Test
    void laterChapterFamiliesNeverRecycleTheEarlierChaptersEnemies() {
        assertEquals(Set.of(EntityType.SENTINEL, EntityType.WARDEN, EntityType.RIGGER),
                new LevelManager(2).getEnemyRoster());
        assertEquals(Set.of(EntityType.INTERRUPT, EntityType.DRILLER, EntityType.SLAG_SPITTER),
                new LevelManager(3).getEnemyRoster());
        assertEquals(Set.of(EntityType.MIRROR, EntityType.SPORE_POD, EntityType.LURKER),
                new LevelManager(4).getEnemyRoster());
    }

    @Test
    void laterChaptersUseSparseEncountersOutsideDifferentlyPlacedArenas() {
        int[][] starts = {{1250, 5200}, {1700, 4200, 6350}, {1850, 5900}};
        int[][] ends = {{2050, 6000}, {2400, 4850, 7000}, {2550, 6500}};
        for (int chapter = 2; chapter <= 4; chapter++) {
            LevelManager level = new LevelManager(chapter, 27);
            var triggers = level.getPendingTriggers(level.getBossGateX());
            var hostiles = triggers.stream().filter(t -> t.type.isHostile()).toList();
            assertTrue(hostiles.size() >= 21 && hostiles.size() <= 28);
            for (var trigger : hostiles) {
                assertTrue(trigger.count >= 1 && trigger.count <= (trigger.worldX < 7600 ? 1 : 2));
                assertEquals(0, trigger.fromLeft, "Early movement never spawns an unintroduced rear ambush");
                assertNull(level.getBattleAt(trigger.worldX));
            }
            for (int i = 0; i < starts[chapter - 2].length; i++) {
                int start = starts[chapter - 2][i], end = ends[chapter - 2][i];
                var battle = level.getBattleAt(start);
                assertEquals(start, battle.start);
                assertEquals(end, battle.end);
                assertTrue(battle.waves.length >= 2 && battle.waves.length <= 3);
                for (var wave : battle.waves) {
                    int actors = wave.count * (wave.fromDir == 2 ? 2 : 1);
                    assertTrue(actors <= 2, "Each specialist gets readable attack space");
                }
                assertNull(level.getBattleAt(end));
            }
            assertEquals(9900, level.getBossGateX());
            assertEquals(10900, level.getCameraMaxX());
        }
    }

    @Test
    void laterCoinsUseTheSameStaticPhysicalLedgesAndNeverAStaleMovingLift() {
        for (int chapter = 2; chapter <= 4; chapter++) {
            LevelManager level = new LevelManager(chapter, 27);
            var route = new ChapterRouteController(chapter, 480);
            var all = route.snapshot();
            assertEquals(all.platforms().size() - all.lifts().size(), level.getPlatforms().size());
            for (Platform p : level.getPlatforms()) {
                assertTrue(all.platforms().stream().anyMatch(actual -> actual.x == p.x && actual.y == p.y
                        && actual.width == p.width && actual.style == p.style));
                assertFalse(all.lifts().stream().anyMatch(lift -> lift.platform().x == p.x && lift.platform().y == p.y));
            }
            assertFalse(level.getCoins().isEmpty());
            for (var coin : level.getCoins()) {
                assertTrue(level.getPlatforms().stream().anyMatch(p -> coin.x >= p.x + 25
                        && coin.x <= p.x + p.width - 25 && coin.y == p.y - 30));
                assertTrue(coin.x < level.getBossGateX());
            }
        }
    }
}
