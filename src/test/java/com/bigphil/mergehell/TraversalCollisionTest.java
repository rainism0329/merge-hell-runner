package com.bigphil.mergehell;

import com.bigphil.mergehell.combat.CombatEvent;
import com.bigphil.mergehell.combat.WeaponId;
import com.bigphil.mergehell.model.EntityType;
import com.bigphil.mergehell.model.FloatingText;
import com.bigphil.mergehell.model.ObstacleManager;
import com.bigphil.mergehell.model.Particle;
import com.bigphil.mergehell.model.Player;
import com.bigphil.mergehell.model.Projectile;
import com.bigphil.mergehell.model.ProjectileType;
import com.bigphil.mergehell.world.TraversalEnvironment;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class TraversalCollisionTest {
    @Test void ordinaryShotCannotBreakAPropThroughANearerEnemy() {
        var fixture = new Fixture();
        var prop = fixture.environment.snapshot().props().get(0);
        var near = new ObstacleManager.Enemy(prop.x() - 100, 440, EntityType.BUG, 51L);
        fixture.enemies.getEnemies().add(near);
        fixture.shots.add(new Projectile(prop.x() - 200, 450, 400, 0, ProjectileType.COMMIT));
        fixture.process();
        assertTrue(near.isDead());
        assertTrue(fixture.worldEvents.isEmpty());
        assertTrue(fixture.environment.snapshot().props().contains(prop));
        assertEquals(100, fixture.context.score);
        fixture.process();
        assertTrue(fixture.worldEvents.isEmpty(), "A spent projectile cannot hit the crate on the following tick");
    }

    @Test void nearerPropConsumesTheShotBeforeAnEnemyBehindIt() {
        var fixture = new Fixture();
        var prop = fixture.environment.snapshot().props().get(0);
        var far = new ObstacleManager.Enemy(prop.x() + 100, 440, EntityType.BUG, 53L);
        fixture.enemies.getEnemies().add(far);
        Projectile shot = new Projectile(prop.x() - 200, 450, 400, 0, ProjectileType.COMMIT);
        fixture.shots.add(shot);
        fixture.process();
        assertTrue(shot.isDead());
        assertFalse(far.isDead());
        assertEquals(1, fixture.worldEvents.size());
        assertEquals(prop.id(), fixture.worldEvents.get(0).id());
        assertEquals(0, fixture.context.score, "Claiming a prop defers the blast until projectile iteration has ended");
        fixture.process();
        assertEquals(1, fixture.worldEvents.size());
        assertTrue(fixture.shots.isEmpty());
    }

    @Test void supplyIsClaimedOnceEvenWhenTwoProjectilesReachItTogether() {
        var fixture = new Fixture();
        var supply = fixture.environment.snapshot().props().stream()
                .filter(prop -> prop.kind() == TraversalEnvironment.PropKind.SUPPLY).findFirst().orElseThrow();
        fixture.camera = 1000;
        fixture.shots.add(new Projectile(supply.x() - 100, 456, 200, 0, ProjectileType.COMMIT));
        fixture.shots.add(new Projectile(supply.x() - 100, 456, 200, 0, ProjectileType.COMMIT));
        fixture.process();
        assertEquals(1, fixture.worldEvents.size());
        assertEquals(TraversalEnvironment.PropKind.SUPPLY, fixture.worldEvents.get(0).kind());
        assertTrue(fixture.environment.snapshot().props().stream().noneMatch(prop -> prop.id() == supply.id()));
    }

    @Test void capacitorDischargeUsesNormalKillScoreXpEventsAndDropsWithoutHurtingPlayer() throws Exception {
        var fixture = new Fixture();
        var enemy = new ObstacleManager.Enemy(710, 430, EntityType.TECHDEBT, 7L);
        fixture.enemies.getEnemies().add(enemy);
        fixture.player.setX(590);
        fixture.player.setY(450);
        int hp = fixture.player.getHp();
        // Force the normal ten-percent drop branch deterministically, without replacing the production drop path.
        var field = CollisionSystem.class.getDeclaredField("dropRandom"); field.setAccessible(true);
        Random random = (Random) field.get(fixture.collision);
        for (long seed = 0; seed < 100_000; seed++) {
            if (new Random(seed).nextDouble() <= 0.10) { random.setSeed(seed); break; }
        }
        fixture.collision.discharge(fixture.context, fixture.enemies, WeaponId.COMMIT_CANNON,
                675, 460, 150, fixture.particles, fixture.texts);
        assertTrue(enemy.isDead());
        assertEquals(500, fixture.context.score);
        assertEquals(1, fixture.context.combo);
        assertEquals(1, fixture.context.newKills);
        assertEquals(hp, fixture.player.getHp());
        var death = fixture.events.stream().filter(CombatEvent.EnemyKilled.class::isInstance)
                .map(CombatEvent.EnemyKilled.class::cast).findFirst().orElseThrow();
        assertEquals(CombatEvent.DamageKind.ENVIRONMENT, death.cause());
        var damage = fixture.events.stream().filter(CombatEvent.DamageDealt.class::isInstance)
                .map(CombatEvent.DamageDealt.class::cast).findFirst().orElseThrow();
        assertEquals(50, damage.damage(), "Overkill is not counted as extra damage");
        assertEquals(death.rootEventId(), damage.rootEventId());
        assertTrue(fixture.enemies.getEnemies().stream().anyMatch(candidate -> candidate.getType().isPowerup()));
        fixture.collision.discharge(fixture.context, fixture.enemies, WeaponId.COMMIT_CANNON,
                675, 460, 150, fixture.particles, fixture.texts);
        assertEquals(500, fixture.context.score, "Dead actors cannot pay a second reward");
        assertEquals(1, fixture.events.stream().filter(CombatEvent.EnemyKilled.class::isInstance).count());
    }

    @Test void capacitorAffectsAtMostSixteenHostilesAndDoesNotDamageOutsideRadiusOrPickups() {
        var fixture = new Fixture();
        for (int i = 0; i < 24; i++)
            fixture.enemies.getEnemies().add(new ObstacleManager.Enemy(500 + i, 430, EntityType.BUG, (long) i));
        var outside = new ObstacleManager.Enemy(800, 430, EntityType.BUG, 27L);
        var pickup = new ObstacleManager.Enemy(530, 445, EntityType.HEALTH, 28L);
        fixture.enemies.getEnemies().add(outside); fixture.enemies.getEnemies().add(pickup);
        fixture.collision.discharge(fixture.context, fixture.enemies, WeaponId.COMMIT_CANNON,
                530, 460, 150, fixture.particles, fixture.texts);
        assertEquals(16, fixture.enemies.getEnemies().stream().filter(ObstacleManager.Enemy::isDead).count());
        assertEquals(16, fixture.context.newKills);
        assertFalse(outside.isDead()); assertFalse(pickup.isDead());
        assertEquals(16, fixture.events.stream().filter(CombatEvent.EnemyKilled.class::isInstance).count());
    }

    private static final class Fixture {
        final TraversalEnvironment environment = new TraversalEnvironment(0, 6174, 480);
        final List<CombatEvent> events = new ArrayList<>();
        final List<TraversalEnvironment.BreakEvent> worldEvents = new ArrayList<>();
        final CollisionSystem collision = new CollisionSystem(events::add, () -> true);
        final CollisionSystem.Context context = new CollisionSystem.Context();
        final ObstacleManager enemies = new ObstacleManager();
        final List<Projectile> shots = new ArrayList<>();
        final List<Particle> particles = new ArrayList<>();
        final List<FloatingText> texts = new ArrayList<>();
        final Player player = new Player(100, 450);
        double camera;

        Fixture() {
            environment.update(new TraversalEnvironment.Step(100, 480, 100, 480,
                    30, 0, 960, true, false, true, false));
            collision.setWorldImpactHandler((shot, before) -> {
                var hit = environment.hit(shot, before);
                if (hit.isEmpty()) return false;
                worldEvents.add(hit.get()); shot.setDead(true); return true;
            });
        }

        void process() {
            collision.process(context, shots, enemies, null, player, GameState.RUNNING,
                    960, 600, camera, particles, texts, ignored -> { });
        }
    }
}
