package com.bigphil.mergehell.model;

import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SeededEncounterTest {
    private record EnemyState(EntityType type, double x, double y, int hp, int telegraph, boolean protectedSpawn) { }
    private record Shot(double x, double y, double vx, double vy, ProjectileType type) { }
    private record Frame(List<EnemyState> enemies, List<Shot> shots, int rejected) { }

    @Test void seededEnemiesReplayMovementAndShootingDespiteInterleavedOtherEnemies() {
        for (EntityType type : EntityType.values()) {
            var first = new ObstacleManager.Enemy(700, 300, type, 1, 42L);
            var restored = new ObstacleManager.Enemy(700, 300, type, 1, 42L);
            var unrelated = new ObstacleManager.Enemy(100, 180, EntityType.LOCK, -1, 7L);
            for (int tick = 0; tick < 400; tick++) {
                first.update(1.2, 500);
                Shot expected = shot(first.maybeShoot(330));
                unrelated.update(1.5, 500);
                unrelated.maybeShoot(100 + tick % 200);
                restored.update(1.2, 500);
                Shot actual = shot(restored.maybeShoot(330));
                assertEquals(state(first), state(restored), type + " tick " + tick);
                assertEquals(expected, actual, type + " shot " + tick);
                assertEquals(first.getTelegraphTicks(), restored.getTelegraphTicks());
            }
        }
    }

    @Test void movementUsesSimulationUpdatesRatherThanTimeBetweenConstructionOrPaints() throws Exception {
        var first = new ObstacleManager.Enemy(500, 250, EntityType.LOCK, 41L);
        for (int i = 0; i < 80; i++) first.update(1, 200);
        EnemyState paused = state(first);
        Thread.sleep(35);
        Graphics2D graphics = new BufferedImage(960, 600, BufferedImage.TYPE_INT_ARGB).createGraphics();
        try { for (int i = 0; i < 5; i++) first.draw(graphics); }
        finally { graphics.dispose(); }
        assertEquals(paused, state(first));
        var restoredLater = new ObstacleManager.Enemy(500, 250, EntityType.LOCK, 41L);
        for (int i = 0; i < 80; i++) restoredLater.update(1, 200);
        assertEquals(paused, state(restoredLater));
        first.update(1, 200); restoredLater.update(1, 200);
        assertEquals(state(first), state(restoredLater));
    }

    @Test void managerSpawnStreamsAreIndependentOfOtherInstancesAndEnemyActivity() {
        ObstacleManager first = new ObstacleManager(8128L);
        ObstacleManager restored = new ObstacleManager(8128L);
        ObstacleManager unrelated = new ObstacleManager(99L);
        for (int tick = 0; tick < 320; tick++) {
            Frame expected = step(first, tick);
            step(unrelated, tick * 2);
            step(unrelated, tick * 2 + 1);
            assertEquals(expected, step(restored, tick), "manager tick " + tick);
        }
    }

    @Test void explicitLevelResetReplaysEntranceAndClearsOldBulletsAndLimits() {
        ObstacleManager manager = new ObstacleManager(517L);
        List<Frame> original = new ArrayList<>();
        for (int tick = 0; tick < 200; tick++) original.add(step(manager, tick));
        manager.getEnemyBullets().add(new Projectile(10, 20, 2, 0, ProjectileType.ENEMY));
        for (int i = 0; i < 200; i++) manager.spawnEnemy(300, 400, EntityType.BUG);
        assertTrue(manager.getRejectedHostiles() > 0);
        manager.reset(517L);
        assertTrue(manager.getEnemies().isEmpty());
        assertTrue(manager.getEnemyBullets().isEmpty());
        assertEquals(0, manager.getRejectedHostiles());
        for (int tick = 0; tick < 200; tick++) assertEquals(original.get(tick), step(manager, tick));
    }

    @Test void differentSeedsProduceDifferentEncounterSequences() {
        ObstacleManager first = new ObstacleManager(1L);
        ObstacleManager second = new ObstacleManager(2L);
        first.spawnFromLeft(480, 0, EntityType.LOCK);
        second.spawnFromLeft(480, 0, EntityType.LOCK);
        assertNotEquals(first.getEnemies().stream().map(SeededEncounterTest::state).toList(),
                second.getEnemies().stream().map(SeededEncounterTest::state).toList());
    }

    @Test void publicBossSeedReplaysAllFourBossesIncludingSummonsAndProjectiles() {
        for (int level = 1; level <= 4; level++) {
            Boss first = new Boss("Boss", 2_400, "!", 960, level, 1_001L);
            Boss restored = new Boss("Boss", 2_400, "!", 960, level, 1_001L);
            Boss unrelated = new Boss("Noise", 2_400, "!", 960, level, 91L);
            first.activate(); restored.activate(); unrelated.activate();
            ObstacleManager firstEnemies = new ObstacleManager(1_002L);
            ObstacleManager restoredEnemies = new ObstacleManager(1_002L);
            ObstacleManager noiseEnemies = new ObstacleManager(92L);
            List<Projectile> firstShots = new ArrayList<>(), restoredShots = new ArrayList<>(), noiseShots = new ArrayList<>();
            for (int tick = 0; tick < 900; tick++) {
                if (tick == 300 || tick == 600) { first.takeDamage(430); restored.takeDamage(430); }
                first.update(firstEnemies, 480, 260 + tick % 150, 330, firstShots);
                unrelated.update(noiseEnemies, 480, tick % 900, 150, noiseShots);
                restored.update(restoredEnemies, 480, 260 + tick % 150, 330, restoredShots);
                assertEquals(first.getX(), restored.getX());
                assertEquals(first.getY(), restored.getY());
                assertEquals(first.getEncounterStatus(), restored.getEncounterStatus());
                assertEquals(first.isDashing(), restored.isDashing());
                assertEquals(firstShots.stream().map(SeededEncounterTest::shot).toList(),
                        restoredShots.stream().map(SeededEncounterTest::shot).toList());
                assertEquals(firstEnemies.getEnemies().stream().map(SeededEncounterTest::state).toList(),
                        restoredEnemies.getEnemies().stream().map(SeededEncounterTest::state).toList());
                firstShots.clear(); restoredShots.clear(); noiseShots.clear();
            }
            assertFalse(first.movesSeenForTesting().isEmpty());
        }
    }

    private static Frame step(ObstacleManager manager, int tick) {
        if (tick % 40 == 0) manager.clearHostiles();
        if (tick % 29 == 0) manager.spawnFromLeft(480, 0);
        if (tick % 43 == 0) manager.spawnFormation(900, 480);
        if (tick % 17 == 0) manager.spawnEnemy(700, 280, EntityType.MIRROR, -1);
        manager.spawnRandom(960, 480, 6, 0, 4);
        List<Shot> shots = new ArrayList<>();
        for (var enemy : manager.getEnemies()) {
            enemy.update(1.2, 400 + tick % 100);
            Projectile projectile = enemy.maybeShoot(320);
            if (projectile != null) shots.add(shot(projectile));
        }
        manager.update(-400, 2_000);
        return new Frame(manager.getEnemies().stream().map(SeededEncounterTest::state).toList(),
                List.copyOf(shots), manager.getRejectedHostiles());
    }

    private static EnemyState state(ObstacleManager.Enemy enemy) {
        return new EnemyState(enemy.getType(), enemy.getX(), enemy.getY(), enemy.getHp(),
                enemy.getTelegraphTicks(), enemy.isCollisionProtected());
    }
    private static Shot shot(Projectile projectile) {
        return projectile == null ? null : new Shot(projectile.getX(), projectile.getY(),
                projectile.getVx(), projectile.getVy(), projectile.getType());
    }
}
