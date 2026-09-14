package com.bigphil.mergehell;

import com.bigphil.mergehell.combat.*;
import com.bigphil.mergehell.model.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WeaponEffectsCollisionTest {
    private final List<CombatEvent> events = new ArrayList<>();
    private final CollisionSystem collision = new CollisionSystem(events::add);
    private final CollisionSystem.Context context = new CollisionSystem.Context();
    private final ObstacleManager enemies = new ObstacleManager();
    private final Player player = new Player(700, 450);
    private final List<Projectile> shots = new ArrayList<>();
    private final List<Particle> particles = new ArrayList<>();
    private final List<FloatingText> texts = new ArrayList<>();

    @Test
    void burnKeepsItsWeaponAndRootAttributionAndAwardsDeathOnlyOnce() {
        var target = enemy(110, EntityType.TECHDEBT);
        shots.add(shot(WeaponId.FIREWALL, 1, new ProjectileEffects(10, 72, 0, 0, 0, 0, 0, 0, 28)));
        tick(GameState.RUNNING);
        long root = shots.get(0).getRootEventId();
        for (int i = 0; i < 80; i++) tick(GameState.RUNNING);
        assertTrue(target.isDead());
        var kills = kills();
        assertEquals(1, kills.size());
        assertEquals(WeaponId.FIREWALL, kills.get(0).weapon());
        assertEquals(root, kills.get(0).rootEventId());
        assertEquals(CombatEvent.DamageKind.BURN, kills.get(0).cause());
        assertEquals(500, context.score);
        assertEquals(50, events.stream().filter(CombatEvent.DamageDealt.class::isInstance)
                .map(CombatEvent.DamageDealt.class::cast).mapToInt(CombatEvent.DamageDealt::damage).sum());
    }

    @Test
    void pauseDoesNotAdvanceBurnAndResetRemovesOldRunEffects() {
        var target = enemy(110, EntityType.TECHDEBT);
        shots.add(shot(WeaponId.FIREWALL, 1, new ProjectileEffects(10, 72, 0, 0, 0, 0, 0, 0, 28)));
        tick(GameState.RUNNING);
        for (int i = 0; i < 100; i++) tick(GameState.PAUSED);
        assertEquals(49, target.getHp());
        for (int i = 0; i < 12; i++) tick(GameState.RUNNING);
        assertEquals(39, target.getHp());
        collision.resetEffects(context);
        for (int i = 0; i < 72; i++) tick(GameState.RUNNING);
        assertEquals(39, target.getHp());
    }

    @Test
    void fullGcChainReachesAnotherClusterWithoutRecursiveDuplicateRewards() {
        enemy(110, EntityType.BUG);
        enemy(175, EntityType.BUG);
        var far = enemy(245, EntityType.BUG);
        shots.add(shot(WeaponId.GARBAGE_COLLECTOR, 80,
                ProjectileEffects.forWeapon(WeaponId.GARBAGE_COLLECTOR, true)));
        tick(GameState.RUNNING);
        assertTrue(far.isDead());
        assertEquals(3, kills().size());
        assertEquals(1, kills().stream().map(CombatEvent.EnemyKilled::rootEventId).distinct().count());
        assertTrue(events.stream().filter(CombatEvent.DamageDealt.class::isInstance)
                .map(CombatEvent.DamageDealt.class::cast).anyMatch(event -> event.depth() == 2));
        tick(GameState.RUNNING);
        assertEquals(3, kills().size());
    }

    @Test
    void fullGcHasAHardLimitOfSixteenAreaTargetsPerImpact() {
        enemy(110, EntityType.BUG);
        for (int i = 0; i < 20; i++) enemy(155, EntityType.BUG);
        shots.add(shot(WeaponId.GARBAGE_COLLECTOR, 80,
                ProjectileEffects.forWeapon(WeaponId.GARBAGE_COLLECTOR, true)));
        tick(GameState.RUNNING);
        assertEquals(17, kills().size(), "One direct target plus at most sixteen area targets");
        assertEquals(4, enemies.getEnemies().stream().filter(enemy -> enemy.getType().isHostile() && !enemy.isDead()).count());
        assertTrue(events.stream().filter(CombatEvent.DamageDealt.class::isInstance)
                .map(CombatEvent.DamageDealt.class::cast).allMatch(event -> event.depth() <= 3));
    }

    @Test
    void zeroTrustStopsThreeNormalBulletsButCannotEraseCriticalThreats() {
        Projectile firewall = shot(WeaponId.FIREWALL, 12,
                ProjectileEffects.forWeapon(WeaponId.FIREWALL, true));
        shots.add(firewall);
        for (int i = 0; i < 3; i++) enemies.getEnemyBullets().add(new Projectile(110, 200, 0, 0, ProjectileType.ENEMY));
        Projectile critical = new Projectile(110, 200, 0, 0, ProjectileType.CRITICAL);
        enemies.getEnemyBullets().add(critical);
        tick(GameState.RUNNING);
        assertEquals(List.of(critical), enemies.getEnemyBullets());
        assertTrue(firewall.isDead());
        assertEquals(0, firewall.getRemainingInterceptions());
        assertTrue(kills().isEmpty());
    }

    @Test
    void evolvedFirewallLeavesAVisibleBoundedDamagePatch() {
        enemy(110, EntityType.BUG);
        var bystander = enemy(145, EntityType.TECHDEBT);
        shots.add(shot(WeaponId.FIREWALL, 12,
                ProjectileEffects.forWeapon(WeaponId.FIREWALL, true)));
        tick(GameState.RUNNING);
        assertEquals(1, collision.effectZones(context).size());
        assertEquals(50, bystander.getHp());
        for (int i = 0; i < 12; i++) tick(GameState.RUNNING);
        assertEquals(46, bystander.getHp());
        assertTrue(events.stream().filter(CombatEvent.DamageDealt.class::isInstance)
                .map(CombatEvent.DamageDealt.class::cast).anyMatch(event -> event.kind() == CombatEvent.DamageKind.RESIDUE));
        for (int i = 0; i < 100; i++) tick(GameState.RUNNING);
        assertTrue(collision.effectZones(context).isEmpty());
    }

    @Test
    void cherryPickMarkAmplifiesTheNextIndependentHitAndExpires() {
        var target = enemy(110, EntityType.TECHDEBT);
        shots.add(shot(WeaponId.COMMIT_CANNON, 10,
                ProjectileEffects.forWeapon(WeaponId.COMMIT_CANNON, true)));
        tick(GameState.RUNNING);
        assertTrue(collision.isMarked(context, target));
        shots.add(shot(WeaponId.COMMIT_CANNON, 10, ProjectileEffects.NONE));
        tick(GameState.RUNNING);
        assertEquals(27, target.getHp());
        for (int i = 0; i < 120; i++) tick(GameState.RUNNING);
        assertFalse(collision.isMarked(context, target));
    }

    private ObstacleManager.Enemy enemy(int x, EntityType type) {
        var enemy = new ObstacleManager.Enemy(x, 195, type);
        enemies.getEnemies().add(enemy);
        return enemy;
    }

    private Projectile shot(WeaponId weapon, int damage, ProjectileEffects effects) {
        return new Projectile(100, 200, new ProjectileSpec(weapon, damage, 0, 0, false, 0, 0, 0, effects));
    }

    private void tick(GameState state) {
        collision.process(context, shots, enemies, null, player, state, 960, 600, 0, particles, texts, text -> { });
    }

    private List<CombatEvent.EnemyKilled> kills() {
        return events.stream().filter(CombatEvent.EnemyKilled.class::isInstance)
                .map(CombatEvent.EnemyKilled.class::cast).toList();
    }
}
