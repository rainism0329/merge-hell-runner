package com.bigphil.mergehell.model;

import com.bigphil.mergehell.engine.EntityLimits;
import com.bigphil.mergehell.engine.ProjectileBudget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class EnemyProjectileBudgetTest {
    @Test void fullEnemyFieldHoldsWarningAndRetainsExistingShotsUntilARoomOpens() {
        for (EntityType type : new EntityType[]{EntityType.CONFLICT, EntityType.LOCK,
                EntityType.TECHDEBT, EntityType.SENTINEL, EntityType.MIRROR,
                EntityType.RIGGER, EntityType.SLAG_SPITTER, EntityType.SPORE_POD}) {
            ObstacleManager manager = fullManager();
            var enemy = new ObstacleManager.Enemy(700, 300, type, 7263L);
            var control = new ObstacleManager.Enemy(700, 300, type, 7263L);
            List<Projectile> originals = List.copyOf(manager.getEnemyBullets());
            for (int tick = 0; tick < 200; tick++) assertFalse(enemy.maybeShoot(450, manager.getEnemyBullets()));
            assertEquals(originals, manager.getEnemyBullets());
            assertEquals(1, enemy.getTelegraphTicks());
            assertTrue(manager.getRejectedProjectiles() > 0);
            List<Projectile> expected = new ArrayList<>();
            for (int tick = 0; tick < 200 && expected.isEmpty(); tick++) control.maybeShoot(450, expected);
            assertFalse(expected.isEmpty());
            // A fan waits for the entire authored volley, never emitting a partial attack.
            for (int i = 0; i < expected.size() - 1; i++) {
                manager.getEnemyBullets().remove(0);
                assertFalse(enemy.maybeShoot(450, manager.getEnemyBullets()));
            }
            manager.getEnemyBullets().remove(0);
            assertTrue(enemy.maybeShoot(450, manager.getEnemyBullets()));
            assertEquals(EntityLimits.MAX_ENEMY_PROJECTILES, manager.getEnemyBullets().size());
            for (int i = 0; i < expected.size(); i++) {
                Projectile actual = manager.getEnemyBullets().get(EntityLimits.MAX_ENEMY_PROJECTILES - expected.size() + i);
                assertEquals(expected.get(i).getType(), actual.getType());
                assertEquals(expected.get(i).getVy(), actual.getVy());
                assertEquals(expected.get(i).getVx(), actual.getVx());
                assertEquals(expected.get(i).getVerticalAcceleration(), actual.getVerticalAcceleration());
            }
            assertEquals(0, enemy.getTelegraphTicks());
        }
    }

    @Test void clearingDangerPreservesRejectionsWhileEitherResetStartsFresh() {
        ObstacleManager manager = fullManager();
        assertFalse(ProjectileBudget.emitOne(manager.getEnemyBullets(), EnemyProjectileBudgetTest::shot));
        manager.clearHostiles();
        assertTrue(manager.getEnemyBullets().isEmpty());
        assertEquals(1, manager.getRejectedProjectiles());
        assertEquals(1, manager.getRejectedProjectileVolleys());
        manager.reset();
        assertEquals(0, manager.getRejectedProjectiles());
        for (int i = 0; i < EntityLimits.MAX_ENEMY_PROJECTILES; i++) manager.getEnemyBullets().add(shot());
        assertFalse(ProjectileBudget.emitOne(manager.getEnemyBullets(), EnemyProjectileBudgetTest::shot));
        manager.reset(827);
        assertTrue(manager.getEnemyBullets().isEmpty());
        assertEquals(0, manager.getRejectedProjectiles());
    }

    private static ObstacleManager fullManager() {
        ObstacleManager manager = new ObstacleManager(18);
        for (int i = 0; i < EntityLimits.MAX_ENEMY_PROJECTILES; i++) manager.getEnemyBullets().add(shot());
        return manager;
    }

    private static Projectile shot() { return new Projectile(700, 300, -6, 0, ProjectileType.ENEMY); }
}
