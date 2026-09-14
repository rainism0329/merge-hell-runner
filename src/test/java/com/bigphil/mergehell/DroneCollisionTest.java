package com.bigphil.mergehell;

import com.bigphil.mergehell.combat.*;
import com.bigphil.mergehell.model.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DroneCollisionTest {
    @Test void dronePiercesExactlyTwoEnemiesAndReportsItsOwnSourceWithoutDuplicateRewards() {
        List<CombatEvent> events = new ArrayList<>(); CollisionSystem collision = new CollisionSystem(events::add);
        var ctx = new CollisionSystem.Context(); var enemies = new ObstacleManager();
        for (int i = 0; i < 3; i++) enemies.getEnemies().add(new ObstacleManager.Enemy(150 + i * 55, 180, EntityType.BUG, i + 1L));
        var shots = new ArrayList<>(List.of(Projectile.drone(110, 190,
                new ProjectileSpec(WeaponId.FIREWALL, 100, 80, 0, false, 1, 0, 0))));
        for (int tick = 0; tick < 5; tick++) collision.process(ctx, shots, enemies, null, new Player(850, 450),
                GameState.RUNNING, 960, 600, 0, new ArrayList<>(), new ArrayList<>(), ignored -> { });
        var kills = events.stream().filter(CombatEvent.EnemyKilled.class::isInstance).map(CombatEvent.EnemyKilled.class::cast).toList();
        assertEquals(2, kills.size()); assertEquals(2, ctx.combo);
        assertTrue(kills.stream().allMatch(event -> event.cause() == CombatEvent.DamageKind.DRONE && event.weapon() == WeaponId.FIREWALL));
        assertEquals(kills.get(0).rootEventId(), kills.get(1).rootEventId()); assertTrue(kills.get(0).rootEventId() > 0);
        assertEquals(1, enemies.getEnemies().stream().filter(e -> e.getType().isHostile() && !e.isDead()).count());
    }

    @Test void existingBossDamageEventRetainsDroneAttribution() {
        List<CombatEvent> events = new ArrayList<>(); CollisionSystem collision = new CollisionSystem(events::add);
        Boss boss = new Boss("test", 2400, "!", 400, 0); boss.activate();
        var bounds = boss.getBounds();
        var shots = new ArrayList<>(List.of(Projectile.drone(bounds.getCenterX(), bounds.getCenterY(),
                new ProjectileSpec(WeaponId.COMMIT_CANNON, 10, 1, 0, false, 0, 0, 0))));
        collision.process(new CollisionSystem.Context(), shots, new ObstacleManager(), boss, new Player(100, 450),
                GameState.BOSS_FIGHT, 960, 600, 0, new ArrayList<>(), new ArrayList<>(), ignored -> { });
        var damage = events.stream().filter(CombatEvent.DamageDealt.class::isInstance).map(CombatEvent.DamageDealt.class::cast).toList();
        assertEquals(1, damage.size()); assertEquals(CombatEvent.DamageKind.DRONE, damage.get(0).kind());
    }
}
