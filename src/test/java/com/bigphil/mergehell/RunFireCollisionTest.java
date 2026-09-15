package com.bigphil.mergehell;

import com.bigphil.mergehell.combat.*;
import com.bigphil.mergehell.model.*;
import org.junit.jupiter.api.Test;
import java.awt.Rectangle;
import java.util.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RunFireCollisionTest {
    @Test void runningEvolvedBeamHitsAnEnemyCornerThatDiscreteEndpointMisses() {
        var stats = WeaponCatalog.definition(WeaponId.REFACTOR_BEAM).baseStats().withPelletsAndSpread(3, 0.10);
        Projectile shot = new WeaponFireController().fire(new FireRequest(WeaponId.REFACTOR_BEAM,
                100, 100.95, 1, stats, false, new Random(3), true, 5)).get(2);
        // Stable legacy body: the right edge at x=96 is crossed between samples.
        var target = new ObstacleManager.Enemy(56, 107, EntityType.BUG, 3);
        assertFalse(shot.getBounds().intersects(target.getBounds()));
        shot.update();
        assertFalse(shot.getBounds().intersects(target.getBounds()), "Endpoint has passed the enemy's right corner");
        assertTrue(shot.hits(target.getBounds()));
        assertTrue(shot.hitFraction(target.getBounds()) > 0 && shot.hitFraction(target.getBounds()) < 1);

        Projectile sameShot = new WeaponFireController().fire(new FireRequest(WeaponId.REFACTOR_BEAM,
                100, 100.95, 1, stats, false, new Random(3), true, 5)).get(2);
        var manager = new ObstacleManager(); manager.getEnemies().add(target);
        process(new ArrayList<>(List.of(sameShot)), manager);
        assertTrue(target.isDead(), "Production friendly collision must use the swept contact");
    }

    @Test void sweepDoesNotTreatTheEntireDiagonalBoundingBoxAsDamage() {
        Projectile shot = new Projectile(10, 10, 100, 100, ProjectileType.COMMIT); shot.update();
        assertFalse(shot.hits(new Rectangle(15, 100, 20, 20)));
        assertTrue(shot.hits(new Rectangle(65, 65, 20, 20)));
        assertFalse(shot.hits(new Rectangle(110, 120, 10, 10)), "Edge-only contact is not overlap");
    }

    @Test void nonPiercingShotHitsTheNearestContactEvenWhenEnemyInsertionOrderIsReversed() {
        Projectile shot = new Projectile(100, 180, new ProjectileSpec(WeaponId.GARBAGE_COLLECTOR,
                100, 200, 0, false, 0, 0, 0));
        var manager = new ObstacleManager();
        var far = new ObstacleManager.Enemy(240, 175, EntityType.BUG, 1);
        var near = new ObstacleManager.Enemy(160, 175, EntityType.BUG, 2);
        manager.getEnemies().add(far); manager.getEnemies().add(near);
        var shots = new ArrayList<>(List.of(shot)); var ctx = process(shots, manager);
        assertTrue(near.isDead()); assertFalse(far.isDead()); assertEquals(1, ctx.combo);
        process(shots, manager); assertFalse(far.isDead());
    }

    @Test void highSpeedPiercingContactsRewardEachEnemyOnceAcrossSteps() {
        Projectile shot = new Projectile(100, 180, new ProjectileSpec(WeaponId.GARBAGE_COLLECTOR,
                100, 100, 0, false, 2, 0, 0));
        var manager = new ObstacleManager();
        manager.getEnemies().add(new ObstacleManager.Enemy(170, 175, EntityType.BUG, 1));
        manager.getEnemies().add(new ObstacleManager.Enemy(220, 175, EntityType.BUG, 2));
        var shots = new ArrayList<>(List.of(shot));
        var collision = new CollisionSystem(); var ctx = new CollisionSystem.Context();
        for (int step = 0; step < 3; step++) collision.process(ctx, shots, manager, null,
                new Player(800, 450), GameState.RUNNING, 960, 600, 0, new ArrayList<>(), new ArrayList<>(), ignored -> {});
        assertTrue(manager.getEnemies().stream().filter(en -> en.getType().isHostile()).allMatch(ObstacleManager.Enemy::isDead));
        assertEquals(2, ctx.combo);
    }

    private static CollisionSystem.Context process(List<Projectile> shots, ObstacleManager manager) {
        var ctx = new CollisionSystem.Context();
        new CollisionSystem().process(ctx, shots, manager, null, new Player(800, 450), GameState.RUNNING,
                960, 600, 0, new ArrayList<>(), new ArrayList<>(), ignored -> {});
        return ctx;
    }
}
