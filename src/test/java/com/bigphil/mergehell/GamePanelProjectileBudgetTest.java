package com.bigphil.mergehell;

import com.bigphil.mergehell.boss.BossAction;
import com.bigphil.mergehell.boss.LegacyBossController;
import com.bigphil.mergehell.combat.CombatEvent;
import com.bigphil.mergehell.combat.DroneController;
import com.bigphil.mergehell.combat.ProjectileEffects;
import com.bigphil.mergehell.combat.ProjectileSpec;
import com.bigphil.mergehell.combat.WeaponId;
import com.bigphil.mergehell.engine.EntityLimits;
import com.bigphil.mergehell.engine.ProjectileBuffer;
import com.bigphil.mergehell.model.Projectile;
import com.bigphil.mergehell.model.EntityType;
import com.bigphil.mergehell.model.ObstacleManager;
import com.bigphil.mergehell.model.ProjectileType;
import com.bigphil.mergehell.model.WeaponType;
import com.bigphil.mergehell.persistence.MergeHellState;
import com.bigphil.mergehell.persistence.MergeHellStateService;
import com.bigphil.mergehell.progression.UpgradeCatalog;
import com.bigphil.mergehell.progression.UpgradeId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Real panel input/update/collision coverage; saturated shots are placed away from actors. */
class GamePanelProjectileBudgetTest {
    private final List<HeapGameHarness> games = new ArrayList<>();
    private MergeHellState original;

    @BeforeEach void isolate() {
        original = MergeHellStateService.getInstance().getState();
        MergeHellStateService.getInstance().loadState(new MergeHellState());
    }

    @AfterEach void restore() {
        games.forEach(HeapGameHarness::close);
        MergeHellStateService.getInstance().loadState(original);
    }

    @Test void fullFieldRejectsRealPlayerFireWithoutLosingOldShotsAmmoOrFiringFeedback() throws Exception {
        var h = world(1, WeaponId.COMMIT_CANNON);
        h.player().giveWeapon(WeaponType.SPREAD, 3);
        fillFriendly(h, EntityLimits.MAX_PROJECTILES);
        List<Projectile> old = List.copyOf(h.shots());
        long sequence = h.player().getShotSequence();
        h.key("SHOOT"); h.ticks(15);
        assertEquals(GameState.RUNNING, h.state());
        assertEquals(old, h.shots());
        assertEquals(3, h.player().getWeaponAmmo());
        assertEquals(sequence, h.player().getShotSequence());
        assertTrue(buffer(h).rejectedProjectiles() >= 5);
        assertTrue(h.panel.getSession().metrics().rejectedProjectiles() >= 5);

        h.shots().subList(0, 5).clear(); h.tick();
        assertEquals(2, h.player().getWeaponAmmo());
        assertEquals(sequence + 1, h.player().getShotSequence());
        assertEquals(EntityLimits.MAX_PROJECTILES, h.shots().size());
        assertEquals(old.subList(5, old.size()), h.shots().subList(0, old.size() - 5));
        assertEquals(WeaponId.FORCE_PUSH, h.player().getLastFiredWeaponId());
    }

    @Test void fullFieldDefersReflectionWithoutSpendingItsChargeThenUsesTheFirstFreeSlot() throws Exception {
        var h = world(1, WeaponId.COMMIT_CANNON);
        fillFriendly(h, EntityLimits.MAX_PROJECTILES);
        var incoming = new Projectile(h.player().getX() + 55, h.player().getY(),
                0, 0, ProjectileType.ENEMY);
        h.enemies().getEnemyBullets().add(incoming);
        h.key("MELEE"); h.tick();
        assertTrue(h.enemies().getEnemyBullets().contains(incoming));
        assertTrue(h.player().canReflectProjectile());
        assertEquals(EntityLimits.MAX_PROJECTILES, h.shots().size());
        assertEquals(1, buffer(h).rejectedProjectiles());
        assertTrue(h.shots().stream().noneMatch(shot -> shot.getDamageKind() == CombatEvent.DamageKind.REFLECTED));

        h.shots().remove(0); h.tick();
        assertFalse(h.enemies().getEnemyBullets().contains(incoming));
        assertEquals(EntityLimits.MAX_PROJECTILES, h.shots().size());
        assertEquals(1, h.shots().stream().filter(shot -> shot.getDamageKind() == CombatEvent.DamageKind.REFLECTED).count());
        assertTrue(h.player().canReflectProjectile(), "Only one of the three reflection charges was consumed");
        assertEquals(0, h.player().getShotSequence(), "Reflection cannot impersonate primary weapon fire");
    }

    @Test void pauseAndUpgradeKeepInFlightShotsWhileContinueClearsOnlyAtTheSavedEntrance() throws Exception {
        var h = world(1, WeaponId.COMMIT_CANNON);
        h.panel.getSession().runBuild().apply(UpgradeCatalog.definition(UpgradeId.COMMIT_RICOCHET));
        h.player().bindRunBuild(h.panel.getSession().runBuild());
        h.invoke("advanceLevel"); h.tick();
        var savedBuild = h.panel.getSession().runBuild().checkpoint();
        fillFriendly(h, EntityLimits.MAX_PROJECTILES);
        h.key("SHOOT"); h.tick(); h.key("SHOOT_R"); h.tick();
        List<Projectile> old = List.copyOf(h.shots());
        assertTrue(buffer(h).rejectedProjectiles() > 0);
        h.key("PAUSE_P"); h.tick(); h.ticks(15);
        assertEquals(GameState.PAUSED, h.state());
        assertEquals(old, h.shots());
        h.key("PAUSE_P"); h.tick();
        h.panel.getSession().awardBuildXp(h.panel.getSession().buildProgress().nextThreshold() - 3);
        // Collect a real coin during the simulation so the draft opens at the normal event boundary.
        @SuppressWarnings("unchecked") var coins = (List<LevelManager.Coin>) h.get("coins");
        coins.add(new LevelManager.Coin(h.player().getX() + 15, h.player().getY() + 15));
        h.tick(); assertEquals(GameState.UPGRADE_SELECTION, h.state());
        h.ticks(15); assertEquals(old, h.shots());
        h.key("UPGRADE_1"); h.tick();
        assertEquals(GameState.RUNNING, h.state());
        assertEquals(old, h.shots(), "Installing an upgrade must not reset the projectile buffer");

        h.key("PAUSE_P"); h.tick(); h.key("MENU"); h.tick();
        h.key("UPGRADE_REROLL"); h.tick();
        assertEquals(GameState.RUNNING, h.state());
        assertEquals(1, h.get("level"));
        assertEquals(savedBuild, h.panel.getSession().runBuild().checkpoint());
        assertTrue(h.shots().isEmpty());
        assertTrue(h.enemies().getEnemyBullets().isEmpty());
        assertEquals(0, buffer(h).rejectedProjectiles());
        assertEquals(0, h.enemies().getRejectedProjectiles());
    }

    @Test void legacyVolleyAndShockwaveRejectWholePatternsWithoutReplacingExistingDanger() throws Exception {
        var h = world(0, WeaponId.COMMIT_CANNON); h.bossPractice();
        assertInstanceOf(LegacyBossController.class, h.get("legacyBoss"));
        fillEnemy(h, EntityLimits.MAX_ENEMY_PROJECTILES - 2);
        List<Projectile> old = List.copyOf(h.enemies().getEnemyBullets());
        var volley = new BossAction.Volley(35, 100, 450, 5, 7);
        var wave = new BossAction.Shockwave(35, 3, 8.5);
        invoke(h, "spawnLegacyVolley", new Class<?>[]{BossAction.Volley.class}, volley);
        invoke(h, "spawnLegacyShockwave", new Class<?>[]{BossAction.Shockwave.class, int.class}, wave, 480);
        assertEquals(old, h.enemies().getEnemyBullets());
        assertEquals(8, h.enemies().getRejectedProjectiles());
        assertEquals(2, h.enemies().getRejectedProjectileVolleys());

        h.enemies().getEnemyBullets().clear();
        invoke(h, "spawnLegacyVolley", new Class<?>[]{BossAction.Volley.class}, volley);
        invoke(h, "spawnLegacyShockwave", new Class<?>[]{BossAction.Shockwave.class, int.class}, wave, 480);
        assertEquals(8, h.enemies().getEnemyBullets().size());
        var core = ((LegacyBossController) h.get("legacyBoss")).coreBounds();
        for (int i = 0; i < 5; i++) {
            Projectile shot = h.enemies().getEnemyBullets().get(i);
            assertEquals(Math.cos(volley.angleFrom(core.x + 10, core.y + 92, i)) * 7, shot.getVx(), 1e-9);
        }
        assertTrue(h.enemies().getEnemyBullets().subList(5, 8).stream()
                .allMatch(shot -> shot.getType() == ProjectileType.CRITICAL && shot.getY() == 463));
    }

    @Test void droneResetsRetainTheDiagnosticTotalAndNextLevelStartsANewInterval() throws Exception {
        var h = world(1, WeaponId.COMMIT_CANNON);
        h.panel.getSession().runBuild().apply(UpgradeCatalog.definition(UpgradeId.DRONE_COPILOT));
        h.player().bindRunBuild(h.panel.getSession().runBuild());
        h.enemies().getEnemies().add(new ObstacleManager.Enemy(450, 310, EntityType.LOCK, 719L));
        fillFriendly(h, EntityLimits.MAX_PROJECTILES);
        h.key("SHOOT"); h.ticks(3); h.key("SHOOT_R"); h.tick();
        var drones = (DroneController) h.get("drones");
        assertTrue(drones.rejectedProjectiles() > 0);
        int total = h.panel.getSession().metrics().rejectedProjectiles();
        assertTrue(total >= buffer(h).rejectedProjectiles() + drones.rejectedProjectiles());
        h.invoke("resetDrones"); h.invoke("enforceEntityLimits");
        assertEquals(0, drones.rejectedProjectiles());
        assertEquals(total, h.panel.getSession().metrics().rejectedProjectiles());
        h.set("level", 2); h.invoke("advanceLevel"); h.tick();
        assertEquals(0, buffer(h).rejectedProjectiles());
        assertEquals(0, h.enemies().getRejectedProjectiles());
        assertEquals(0, h.panel.getSession().metrics().rejectedProjectiles());
    }

    @Test void allFiveBossesWithAllSixEvolvedWeaponsSurviveSustainedSaturationAndRecoverFiring() throws Exception {
        for (int world = 0; world < 5; world++) for (WeaponId weapon : WeaponId.values()) {
            var h = world(world, weapon);
            try {
                h.bossPractice();
                var build = h.panel.getSession().runBuild();
                for (var upgrade : UpgradeCatalog.all()) if (upgrade.isWeaponRelevant(weapon))
                    for (int rank = 0; rank < upgrade.maxRank(); rank++) build.apply(upgrade);
                assertTrue(build.tryEvolve());
                h.player().bindRunBuild(build);
                h.key("SHOOT");
                long sequence = h.player().getShotSequence();
                String context = "world=" + world + " weapon=" + weapon;
                fillFriendly(h, EntityLimits.MAX_PROJECTILES);
                // The crane leads with physical strikes; include its complete four-attack cycle.
                for (int tick = 0; tick < (world >= 2 ? 1100 : 400); tick++) {
                    h.enemies().getEnemies().clear();
                    fillFriendly(h, EntityLimits.MAX_PROJECTILES);
                    fillEnemy(h, EntityLimits.MAX_ENEMY_PROJECTILES);
                    // Exercise the production simulation for this combat matrix. Repainting 12,000
                    // identical saturated frames belongs to the separate render/performance probes.
                    h.invoke("advanceSimulation");
                    assertEquals(GameState.BOSS_FIGHT, h.state(), context + " tick=" + tick);
                    assertTrue(h.shots().size() <= EntityLimits.MAX_PROJECTILES, context);
                    assertTrue(h.enemies().getEnemyBullets().size() <= EntityLimits.MAX_ENEMY_PROJECTILES, context);
                }
                assertEquals(sequence, h.player().getShotSequence(), context);
                assertTrue(buffer(h).rejectedProjectiles() > 0, context);
                assertTrue(h.enemies().getRejectedProjectiles() > 0, context);
                int volleySize = build.effectiveStats().pellets();
                h.shots().subList(0, volleySize).clear();
                h.tick();
                assertEquals(sequence + 1, h.player().getShotSequence(), context);
                assertEquals(weapon, h.player().getLastFiredWeaponId(), context);
                assertTrue(h.shots().size() <= EntityLimits.MAX_PROJECTILES, context);
            } finally {
                h.close(); games.remove(h);
            }
        }
    }

    private HeapGameHarness world(int world, WeaponId weapon) throws Exception {
        var h = new HeapGameHarness(); games.add(h);
        h.set("selectedStartingWeapon", weapon); h.invoke("startGame"); h.tick();
        if (world != 0) { h.set("level", world); h.invoke("advanceLevel"); h.tick(); }
        return h;
    }

    private static ProjectileBuffer buffer(HeapGameHarness h) throws Exception { return (ProjectileBuffer) h.shots(); }

    private static void fillFriendly(HeapGameHarness h, int targetSize) throws Exception {
        double camera = (double) h.get("cameraX");
        while (h.shots().size() < targetSize) h.shots().add(new Projectile(camera + 40, 20,
                new ProjectileSpec(WeaponId.COMMIT_CANNON, 1, 0, 0, false, 0, 0, 0,
                        new ProjectileEffects(0, 0, 0, 0, 0, 0, 0, 0, 600))));
    }

    private static void fillEnemy(HeapGameHarness h, int targetSize) throws Exception {
        double camera = (double) h.get("cameraX");
        while (h.enemies().getEnemyBullets().size() < targetSize)
            h.enemies().getEnemyBullets().add(new Projectile(camera + 40, 65, 0, 0, ProjectileType.CRITICAL));
    }

    private static void invoke(HeapGameHarness h, String name, Class<?>[] types, Object... arguments) throws Exception {
        var method = GamePanel.class.getDeclaredMethod(name, types);
        method.setAccessible(true); method.invoke(h.panel, arguments);
    }
}
