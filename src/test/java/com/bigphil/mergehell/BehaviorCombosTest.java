package com.bigphil.mergehell;

import com.bigphil.mergehell.combat.*;
import com.bigphil.mergehell.engine.GameSession;
import com.bigphil.mergehell.model.*;
import com.bigphil.mergehell.progression.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BehaviorCombosTest {
    private final List<CombatEvent> events = new ArrayList<>();
    private final CollisionSystem collision = new CollisionSystem(events::add, () -> true, () -> 30);
    private final CollisionSystem.Context context = new CollisionSystem.Context();
    private final ObstacleManager enemies = new ObstacleManager();
    private final Player player = new Player(700, 450);
    private final List<Projectile> shots = new ArrayList<>();

    @Test
    void dashCacheChangesOnlyTheNextActualVolleyAndExpiresOrClearsAtEntry() {
        Player hero = dashPlayer(WeaponId.COMMIT_CANNON);
        completeDash(hero);
        assertEquals(90, hero.getDashCacheTicks());
        update(hero, true);
        assertTrue(shots.get(0).isCritical());
        assertEquals(hero.getRunBuild().effectiveStats().pierces() + 1, shots.get(0).getRemainingPierces());
        assertEquals(0, hero.getDashCacheTicks());
        shots.clear();
        for (int i = 0; i < 18; i++) update(hero, true);
        assertEquals(hero.getRunBuild().effectiveStats().pierces(), shots.get(0).getRemainingPierces());

        Player expired = dashPlayer(WeaponId.COMMIT_CANNON);
        completeDash(expired);
        for (int i = 0; i < 90; i++) update(expired, false);
        assertEquals(0, expired.getDashCacheTicks());
        Player entry = dashPlayer(WeaponId.COMMIT_CANNON);
        completeDash(entry);
        assertThrows(IllegalStateException.class, entry::checkpoint);
        entry.beginNextLevel(100, 450);
        assertEquals(0, entry.getDashCacheTicks());
        assertDoesNotThrow(entry::checkpoint);
    }

    @Test
    void beamChargingDoesNotConsumeDashCacheBeforeEmission() {
        Player hero = dashPlayer(WeaponId.REFACTOR_BEAM);
        completeDash(hero);
        for (int i = 0; i < 9; i++) update(hero, true);
        assertTrue(shots.isEmpty());
        assertEquals(81, hero.getDashCacheTicks());
        update(hero, true);
        assertEquals(1, shots.size());
        assertTrue(shots.get(0).isCritical());
        assertEquals(0, hero.getDashCacheTicks());
    }

    @Test
    void aPlainDashDoesNotGrantAnUninstalledCache() {
        Player hero = new Player(100, 450);
        completeDash(hero);
        assertEquals(0, hero.getDashCacheTicks());
    }

    @Test
    void forceKnockbackStopsAtAWallAndExplodesOnceWithOriginalAttribution() {
        var target = enemy(110, 195, EntityType.TECHDEBT);
        var neighbor = enemy(160, 225, EntityType.BUG);
        shots.add(shot(100, 200, WeaponId.FORCE_PUSH, 1, false, 0, 24, ProjectileEffects.NONE));
        List<Platform> walls = List.of(new Platform(175, 190, 20, 100));
        tick(walls);
        assertEquals(115, target.getX());
        assertEquals(37, target.getHp());
        assertTrue(neighbor.isDead());
        var blast = damage(CombatEvent.DamageKind.WALL_IMPACT);
        assertEquals(2, blast.size());
        long root = blast.get(0).rootEventId();
        assertTrue(blast.stream().allMatch(event -> event.weapon() == WeaponId.FORCE_PUSH
                && event.rootEventId() == root && event.depth() == 1));
        shots.add(shot(110, 200, WeaponId.FORCE_PUSH, 1, false, 0, 24, ProjectileEffects.NONE));
        tick(walls);
        assertEquals(36, target.getHp(), "The next pellet cannot repeat the same wall explosion");
        assertEquals(2, damage(CombatEvent.DamageKind.WALL_IMPACT).size());
        assertEquals(1, events.stream().filter(CombatEvent.EnemyKilled.class::isInstance).count());
    }

    @Test
    void distantPlatformsDoNotTurnOrdinaryHitsIntoWallBlasts() {
        var target = enemy(110, 195, EntityType.TECHDEBT);
        shots.add(shot(100, 200, WeaponId.FORCE_PUSH, 1, false, 0, 24, ProjectileEffects.NONE));
        tick(List.of(new Platform(175, 300, 20, 40)));
        assertEquals(134, target.getX());
        assertEquals(49, target.getHp());
        assertTrue(damage(CombatEvent.DamageKind.WALL_IMPACT).isEmpty());
    }

    @Test
    void wallBlastHasOneLayerAndAtMostSixteenTargetsIncludingTheImpactEnemy() {
        enemy(110, 195, EntityType.TECHDEBT);
        for (int i = 0; i < 20; i++) enemy(155, 220, EntityType.BUG);
        shots.add(shot(100, 200, WeaponId.FORCE_PUSH, 1, false, 0, 24, ProjectileEffects.NONE));
        tick(List.of(new Platform(175, 190, 20, 100)));
        assertEquals(16, damage(CombatEvent.DamageKind.WALL_IMPACT).size());
        assertEquals(15, events.stream().filter(CombatEvent.EnemyKilled.class::isInstance).count());
    }

    @Test
    void aMarkAttractsDronesOverANearerTargetUntilItExpires() {
        var marked = enemy(110, 195, EntityType.TECHDEBT);
        var nearer = enemy(500, 450, EntityType.TECHDEBT);
        shots.add(shot(100, 200, WeaponId.COMMIT_CANNON, 1, false, 0, 0,
                ProjectileEffects.forWeapon(WeaponId.COMMIT_CANNON, true)));
        tick(List.of());
        assertSame(marked, collision.selectDroneTarget(context, enemies.getEnemies(), player.getX(), player.getY()));
        for (int i = 0; i < 120; i++) tick(List.of());
        assertSame(nearer, collision.selectDroneTarget(context, enemies.getEnemies(), player.getX(), player.getY()));
        nearer.setDead(true);
        assertSame(marked, collision.selectDroneTarget(context, enemies.getEnemies(), player.getX(), player.getY()));
    }

    @Test
    void meleeReflectsAtMostThreeNormalsPerSwingAndKeepsCriticalThreats() {
        player.setX(100); player.setY(200); player.melee();
        for (int i = 0; i < 5; i++) enemies.getEnemyBullets().add(new Projectile(145, 200, 0, 0, ProjectileType.ENEMY));
        Projectile critical = new Projectile(145, 200, 0, 0, ProjectileType.CRITICAL);
        enemies.getEnemyBullets().add(critical);
        tick(List.of());
        var reflections = events.stream().filter(CombatEvent.ProjectileReflected.class::isInstance)
                .map(CombatEvent.ProjectileReflected.class::cast).toList();
        assertEquals(3, reflections.size());
        assertEquals(36, reflections.stream().mapToInt(CombatEvent.ProjectileReflected::charge).sum());
        assertEquals(3, reflections.stream().map(CombatEvent.ProjectileReflected::rootEventId).distinct().count());
        assertTrue(enemies.getEnemyBullets().contains(critical));
        tick(List.of());
        assertEquals(3, events.stream().filter(CombatEvent.ProjectileReflected.class::isInstance).count());
    }

    @Test
    void reflectedProjectileHasAPlayerSourceAndCannotRepeatKillRewards() {
        player.setX(100); player.setY(200); player.melee();
        enemies.getEnemyBullets().add(new Projectile(145, 200, 0, 0, ProjectileType.ENEMY));
        tick(List.of());
        var reflection = (CombatEvent.ProjectileReflected) events.get(0);
        player.beginNextLevel(100, 200); // End the melee window; keep the already-reflected projectile.
        var target = enemy(180, 195, EntityType.BUG);
        tick(List.of());
        tick(List.of());
        assertTrue(target.isDead());
        var kills = events.stream().filter(CombatEvent.EnemyKilled.class::isInstance)
                .map(CombatEvent.EnemyKilled.class::cast).toList();
        assertEquals(1, kills.size());
        assertEquals(CombatEvent.DamageKind.REFLECTED, kills.get(0).cause());
        assertEquals(WeaponId.COMMIT_CANNON, kills.get(0).weapon());
        assertEquals(reflection.rootEventId(), kills.get(0).rootEventId());
    }

    @Test
    void reflectionEventsFillTheRealSudoMeterWithoutOverflowOrPauseRewards() {
        GameSession session = new GameSession(42);
        CollisionSystem linked = new CollisionSystem(session);
        for (int swing = 0; swing < 4; swing++) {
            Player hero = new Player(100, 200);
            hero.melee();
            shots.clear(); enemies.getEnemyBullets().clear();
            for (int i = 0; i < 3; i++) enemies.getEnemyBullets().add(new Projectile(145, 200, 0, 0, ProjectileType.ENEMY));
            int before = session.overclockCharge();
            linked.process(context, shots, enemies, null, hero, GameState.PAUSED,
                    960, 600, 0, new ArrayList<>(), new ArrayList<>(), text -> { });
            assertEquals(before, session.overclockCharge());
            linked.process(context, shots, enemies, null, hero, GameState.RUNNING,
                    960, 600, 0, new ArrayList<>(), new ArrayList<>(), text -> { });
            assertEquals(Math.min(100, (swing + 1) * 36), session.overclockCharge());
        }
        assertTrue(session.isOverclocked());
        assertEquals(480, session.overclockActiveTicks(), "Further reflections cannot stack active SUDO duration");
    }

    @Test
    void criticalPiercingExtendsAnExistingComboOncePerProjectileAndHasACeiling() {
        context.combo = 1; context.comboTimer = 130;
        enemy(110, 195, EntityType.TECHDEBT);
        enemy(120, 195, EntityType.TECHDEBT);
        shots.add(shot(100, 200, WeaponId.COMMIT_CANNON, 1, true, 4, 0, ProjectileEffects.NONE));
        tick(List.of());
        assertEquals(145, context.comboTimer, "Two pierced targets still consume one critical extension");
        tick(List.of());
        assertEquals(145, context.comboTimer);
        for (int i = 0; i < 10; i++) {
            shots.add(shot(100, 200, WeaponId.COMMIT_CANNON, 1, true, 0, 0, ProjectileEffects.NONE));
            tick(List.of());
        }
        assertEquals(190, context.comboTimer, "At most sixty ticks beyond the upgraded base window");
    }

    private Player dashPlayer(WeaponId weapon) {
        Player hero = new Player(100, 450);
        RunBuild build = new RunBuild(weapon);
        build.apply(UpgradeCatalog.definition(UpgradeId.DASH_CACHE));
        hero.setRunBuild(build);
        return hero;
    }

    private void completeDash(Player hero) {
        hero.dash();
        for (int i = 0; i < 8; i++) update(hero, false);
    }

    private void update(Player hero, boolean shoot) { hero.update(false, false, false, shoot, 480, 960, shots, List.of()); }

    private ObstacleManager.Enemy enemy(int x, int y, EntityType type) {
        var enemy = new ObstacleManager.Enemy(x, y, type);
        enemies.getEnemies().add(enemy);
        return enemy;
    }

    private Projectile shot(int x, int y, WeaponId weapon, int damage, boolean critical,
                            int pierces, double knockback, ProjectileEffects effects) {
        return new Projectile(x, y, new ProjectileSpec(weapon, damage, 0, 0, critical, pierces, knockback, 0, effects));
    }

    private void tick(List<Platform> walls) {
        collision.process(context, shots, enemies, null, player, GameState.RUNNING, 960, 600,
                0, new ArrayList<>(), new ArrayList<>(), text -> { }, walls);
    }

    private List<CombatEvent.DamageDealt> damage(CombatEvent.DamageKind kind) {
        return events.stream().filter(CombatEvent.DamageDealt.class::isInstance)
                .map(CombatEvent.DamageDealt.class::cast).filter(event -> event.kind() == kind).toList();
    }
}
