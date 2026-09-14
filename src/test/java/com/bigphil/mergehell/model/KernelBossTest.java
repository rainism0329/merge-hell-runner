package com.bigphil.mergehell.model;

import com.bigphil.mergehell.boss.MagmaMortarProjectile;
import com.bigphil.mergehell.engine.ProjectileBuffer;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class KernelBossTest {
    private static final class Fight {
        final Boss boss = new Boss("Siege", 12000, "!", 960, 3, 781L);
        final ObstacleManager enemies = new ObstacleManager();
        final ArrayList<Projectile> bullets = new ArrayList<>();
        Fight() { boss.previewArrival(1, 480); boss.activate(); }
        void tick(double px) { boss.update(enemies, 480, px, 450, bullets); }
        void until(String action) {
            for (int i = 0; i < 1800 && !boss.getEncounterAction().equals(action); i++) tick(200);
            assertEquals(action, boss.getEncounterAction());
        }
        Boss.PartView part(String id) { return boss.getParts().stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow(); }
    }
    @Test void drillRunLocksItsFullWarningAndNeverFollowsTheNewPlayerPosition() {
        var f = new Fight(); f.until("DRILL_CHARGE");
        assertEquals(80, f.boss.getWarningTicks()); var warning = f.boss.getAttackTelegraphs();
        double x = f.boss.getX(), direction = f.boss.getDashDirection();
        for (int i = 0; i < 79; i++) {
            f.tick(1400); assertEquals(x, f.boss.getX()); assertFalse(f.boss.isDashing());
            assertFalse(f.boss.isContactDangerous());
            assertEquals(warning.get(0).bounds(), f.boss.getAttackTelegraphs().get(0).bounds());
        }
        f.tick(1400); assertTrue(f.boss.isDashing()); assertEquals(direction, f.boss.getDashDirection());
        for (int i = 0; i < 12; i++) {
            f.tick(1400);
            for (var contact : f.boss.getContactBounds())
                assertTrue(warning.get(0).bounds().rectangle().contains(contact.rectangle()));
        }
        assertTrue(f.boss.getX() < x); assertTrue(f.bullets.isEmpty());
    }
    @Test void breakingArmorRevealsRearVentAndSustainedVentFireCausesOverheat() {
        var f = new Fight(); f.tick(200); var plate = f.part("armor-plate");
        assertEquals((int) Math.ceil(plate.hp() * .35), f.boss.damageAt(plate.bounds().rectangle(), plate.hp()));
        assertTrue(f.part("armor-plate").destroyed()); assertTrue(f.part("heat-vent").weak());
        assertFalse(f.boss.isVulnerable());
        f.boss.damageAt(f.part("heat-vent").bounds().rectangle(), plate.maxHp());
        assertEquals(100, f.boss.getHeat()); assertEquals("OVERHEATED", f.boss.getEncounterAction());
        assertEquals(190, f.boss.getVulnerabilityTicks()); assertFalse(f.boss.isContactDangerous());
        assertTrue(f.part("core").weak()); assertTrue(f.boss.getAttackTelegraphs().isEmpty());
        double x = f.boss.getX();
        for (int i = 0; i < 189; i++) { f.tick(200); assertEquals(x, f.boss.getX()); assertTrue(f.boss.isVulnerable()); }
        f.tick(200); assertFalse(f.boss.isVulnerable());
        assertTrue(f.part("armor-plate").destroyed(), "Armor damage is permanent");
    }
    @Test void mortarHasLockedLandingsAndActualParabolicShells() {
        var f = new Fight(); f.until("MAGMA_MORTAR");
        var landings = f.boss.getAttackTelegraphs(); assertEquals(3, landings.size());
        assertEquals(100, f.boss.getWarningTicks()); assertTrue(f.bullets.isEmpty());
        for (int i = 0; i < 99; i++) { f.tick(1300); assertTrue(f.bullets.isEmpty()); }
        f.tick(1300); assertEquals(3, f.bullets.size());
        for (int i = 0; i < 3; i++) {
            var shell = assertInstanceOf(MagmaMortarProjectile.class, f.bullets.get(i));
            double launchY = shell.getY();
            for (int t = 0; t < 16; t++) shell.update();
            assertTrue(shell.getY() < launchY - 60);
            for (int t = 16; t < MagmaMortarProjectile.FLIGHT_TICKS; t++) shell.update();
            assertTrue(landings.get(i).bounds().rectangle().contains(shell.getBounds()));
            assertFalse(shell.isDead()); shell.update(); shell.update(); assertTrue(shell.isDead());
        }
        assertTrue(f.boss.getActiveHazards().isEmpty(), "A cleared shell cannot leave an invisible explosion");
    }
    @Test void naturalCoolingWorksWithoutBreakingArmorAndOldConsoleHookCannotSkipMechanics() {
        var f = new Fight(); f.until("COOLING");
        assertFalse(f.part("armor-plate").destroyed()); assertTrue(f.part("heat-vent").weak());
        f.boss.interruptKernel(180); assertFalse(f.boss.isVulnerable());
        f.boss.damageAt(f.part("heat-vent").bounds().rectangle(), 100); assertTrue(f.boss.getHeat() > 0);
        f.boss.clearVulnerability(); assertEquals(0, f.boss.getHeat()); assertEquals(90, f.boss.getKernelRebootTicks());
        assertFalse(f.boss.isContactDangerous()); assertTrue(f.boss.getAttackTelegraphs().isEmpty());
    }
    @Test void phaseChangePreservesPositionAndClearsContactAndPendingAttacks() {
        var f = new Fight(); f.until("DRILL_CHARGE");
        for (int i = 0; i < 88; i++) f.tick(200);
        double x = f.boss.getX(); f.boss.damage(2200); f.tick(200);
        assertEquals(2, f.boss.getCombatStage()); assertEquals(90, f.boss.getKernelRebootTicks());
        assertEquals(x, f.boss.getX()); assertFalse(f.boss.isContactDangerous());
        assertTrue(f.boss.getAttackTelegraphs().isEmpty());
        for (int i = 0; i < 90; i++) f.tick(200);
        assertEquals(x, f.boss.getX()); assertTrue(f.bullets.isEmpty());
    }
    @Test void everyBossKeepsExistingBulletsWhenWholeVolleyIsDenied() {
        for (int level : List.of(1, 2, 3, 4)) {
            var boss = new Boss("Boss", 12000, "!", 960, level, 781L);
            boss.previewArrival(1, 480); boss.activate(); var bullets = new ProjectileBuffer(3);
            var original = new ArrayList<Projectile>();
            for (int i = 0; i < 3; i++) original.add(new Projectile(100, 100, -2, 0, ProjectileType.ENEMY));
            bullets.addAll(original); var manager = new ObstacleManager();
            for (int i = 0; i < 1600; i++) boss.update(manager, 480, 200, 450, bullets);
            assertEquals(original, bullets); assertTrue(bullets.rejectedProjectiles() > 0, "Level " + level);
        }
    }
}
