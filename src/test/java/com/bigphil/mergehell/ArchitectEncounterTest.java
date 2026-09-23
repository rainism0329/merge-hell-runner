package com.bigphil.mergehell;

import com.bigphil.mergehell.model.EntityType;
import com.bigphil.mergehell.model.ObstacleManager;
import com.bigphil.mergehell.progression.GameDifficulty;
import com.bigphil.mergehell.world.ChapterRouteController;
import com.bigphil.mergehell.world.ExplorationRoute;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ArchitectEncounterTest {
    @Test void bothAuthoredArenasMixRolesAndCompleteThroughActualEnemyAdmission() {
        for (int start : new int[]{1250, 5200}) {
            LevelManager level = new LevelManager(2, 42);
            var arena = level.getBattleAt(start);
            var route = new ChapterRouteController(2, 480);
            var exploration = new ExplorationRoute(2);
            var manager = new ObstacleManager(42);
            manager.configureCombat(2, GameDifficulty.STANDARD);
            manager.setSolids(exploration.solids());
            level.enterBattle(arena);
            int[] emitted = {0};
            int expected = 0, maximumAlive = 0;
            Set<EntityType> overlap = new HashSet<>();
            for (int tick = 0; tick < 1800 && level.isInBattle(); tick++) {
                int alive = (int) manager.getEnemies().stream().filter(e -> !e.isDead()).count();
                if (level.needsWaveSpawn(alive)) {
                    var wave = level.popWave();
                    assertTrue(wave.scripted());
                    assertEquals(3, wave.maxConcurrent);
                    assertEquals(Set.of(EntityType.SENTINEL, EntityType.WARDEN, EntityType.RIGGER),
                            wave.beats.stream().map(b -> b.type()).collect(java.util.stream.Collectors.toSet()));
                    expected += wave.beats.size();
                    level.startNextWaveTimer();
                }
                level.advanceEncounter(alive, 480, exploration.solids(), manager.getEnemies().stream()
                                .filter(e -> !e.isDead()).map(ObstacleManager.Enemy::getBounds).toList(),
                        new Rectangle(start + 30, 450, 30, 30), b -> route.groundFor(b.x, b.width) == 480,
                        spawn -> {
                            int previous = manager.getEnemies().size();
                            manager.spawnEnemy(spawn.x(), spawn.y(), spawn.type(), spawn.moveDir());
                            assertEquals(previous + 1, manager.getEnemies().size());
                            var actual = manager.getEnemies().get(previous);
                            assertEquals(spawn.bounds(), actual.getBounds(), "The factory must not need to eject a spawn from a wall");
                            assertTrue(exploration.solids().stream().noneMatch(actual.getBounds()::intersects));
                            emitted[0]++;
                            return true;
                        });
                List<ObstacleManager.Enemy> living = manager.getEnemies().stream().filter(e -> !e.isDead()).toList();
                maximumAlive = Math.max(maximumAlive, living.size());
                assertTrue(living.size() <= 3, "The cap includes all already living threats");
                if (living.size() == 3) living.forEach(e -> overlap.add(e.getType()));
                // Keep enemies alive long enough for the mixed formation, then clear one slot at a time.
                if (tick > 180 && tick % 45 == 0 && !living.isEmpty()) living.get(0).setDead(true);
            }
            assertFalse(level.isInBattle(), "All delayed reinforcements must eventually settle");
            assertEquals(expected, emitted[0]);
            assertEquals(start == 1250 ? 7 : 9, emitted[0]);
            assertEquals(3, maximumAlive);
            assertEquals(Set.of(EntityType.SENTINEL, EntityType.WARDEN, EntityType.RIGGER), overlap);
            assertNull(level.getBattleAt(start), "A cleared encounter must not restart");
        }
    }

    @Test void anEmptyArenaDoesNotCompleteWhileDelayedReinforcementsRemain() {
        LevelManager level = new LevelManager(2, 42);
        level.enterBattle(level.getBattleAt(1250));
        for (int i = 0; i < 31; i++) level.needsWaveSpawn(0);
        assertTrue(level.popWave().scripted());
        level.startNextWaveTimer();
        for (int i = 0; i < 300; i++) assertFalse(level.needsWaveSpawn(0));
        assertEquals(1, level.getCurrentWave());
        assertTrue(level.hasPendingReinforcements());
        assertTrue(level.isInBattle());
    }

    @Test void labSkippingAnEncounterOrJumpingToBossCancelsItsUnspawnedBeats() {
        for (boolean boss : new boolean[]{false, true}) {
            LevelManager level = new LevelManager(2, 42);
            level.enterBattle(level.getBattleAt(1250));
            level.popWave();
            assertTrue(level.hasPendingReinforcements());
            if (boss) level.advanceToBossGateForTesting(); else level.advanceToNextEncounterForTesting(1250);
            assertFalse(level.hasPendingReinforcements());
            assertEquals(0, level.advanceEncounter(0, 480, List.of(), List.of(), new Rectangle(100, 450, 30, 30),
                    b -> true, s -> { fail("Skipped reinforcements must not follow the player"); return true; }));
        }
    }

    @Test void callersCanStillConstructTheExistingLegacyWaveContract() {
        var wave = new LevelManager.WaveDef(EntityType.BUG, 2, 2, LevelManager.WaveType.RUSH);
        assertFalse(wave.scripted());
        assertEquals(EntityType.BUG, wave.type);
        assertEquals(2, wave.count);
        assertEquals(2, wave.fromDir);
        assertEquals(LevelManager.WaveType.RUSH, wave.waveType);
        assertEquals(4, wave.maxConcurrent);
        assertTrue(wave.beats.isEmpty());
    }
}
