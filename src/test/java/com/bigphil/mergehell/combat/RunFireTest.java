package com.bigphil.mergehell.combat;

import com.bigphil.mergehell.model.*;
import com.bigphil.mergehell.progression.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RunFireTest {
    @Test void commitRunningPreservesEveryPellet() { verifyRunningPellets(WeaponId.COMMIT_CANNON); }
    @Test void forceRunningPreservesEveryPellet() { verifyRunningPellets(WeaponId.FORCE_PUSH); }
    @Test void rapidRunningPreservesEveryPellet() { verifyRunningPellets(WeaponId.RAPID_CI); }
    @Test void garbageCollectorRunningPreservesEveryPellet() { verifyRunningPellets(WeaponId.GARBAGE_COLLECTOR); }
    @Test void firewallRunningPreservesEveryPellet() { verifyRunningPellets(WeaponId.FIREWALL); }
    @Test void refactorBeamRunningPreservesEveryPellet() { verifyRunningPellets(WeaponId.REFACTOR_BEAM); }

    private void verifyRunningPellets(WeaponId id) {
        for (int direction : new int[]{-1, 1}) {
            List<Projectile> standing = fire(player(id, direction, false), direction, false);
            List<Projectile> running = fire(player(id, direction, false), direction, true);
            List<Projectile> upgraded = fire(player(id, direction, true), direction, true);
            assertEquals(standing.size(), running.size());
            for (int i = 0; i < standing.size(); i++) {
                Projectile a = standing.get(i), b = running.get(i), c = upgraded.get(i);
                assertEquals(a.getVx(), b.getVx() - direction * 5, 1e-9);
                assertEquals(a.getVy(), b.getVy(), 1e-9);
                assertEquals(a.getDamage(), b.getDamage()); assertEquals(a.isCritical(), b.isCritical());
                assertEquals(a.getSpec().effects(), b.getSpec().effects());
                assertEquals(b.getVx(), c.getVx());
                int ttl = a.getSpec().effects().lifetimeTicks();
                assertEquals(a.getVx() * ttl, (b.getVx() - direction * 5) * ttl, 1e-7);
            }
        }
    }

    @Test void firewallRetainsIts196PixelNominalRelativeRangeWhileRunning() {
        var shots = fire(player(WeaponId.FIREWALL, 1, false), 1, true);
        var center = shots.get(shots.size() / 2);
        assertEquals(12, center.getVx());
        assertEquals(196, (center.getVx() - 5) * center.getSpec().effects().lifetimeTicks());
    }

    @Test void stoppingOrTurningDoesNotChangeAnAlreadyEmittedShot() {
        Player player = player(WeaponId.GARBAGE_COLLECTOR, 1, false);
        Projectile shot = fire(player, 1, true).get(0);
        double start = shot.getX();
        for (int step = 0; step < 20; step++) {
            player.update(step > 9, false, false, false, 480, 20_000, new ArrayList<>(), List.of());
            shot.update(); assertEquals(12, shot.getVx());
        }
        assertEquals(start + 240, shot.getX());
    }

    @Test void actualWorldClampAndCancelledInputsAddOnlyActualDisplacement() {
        for (int direction : new int[]{-1, 1}) {
            Player wall = player(WeaponId.COMMIT_CANNON, direction, false);
            wall.setX(direction > 0 ? 19_970 : 0);
            assertEquals(direction * 11, fire(wall, direction, true).get(0).getVx());
            Player partial = player(WeaponId.COMMIT_CANNON, direction, false);
            partial.setX(direction > 0 ? 19_968 : 2);
            assertEquals(direction * 13, fire(partial, direction, true).get(0).getVx());
        }
        Player both = player(WeaponId.COMMIT_CANNON, 1, false);
        List<Projectile> shots = new ArrayList<>();
        both.update(true, true, false, true, 480, 20_000, shots, List.of());
        assertEquals(11, shots.get(0).getVx());
    }

    @Test void jumpingDoesNotTiltTheShotAndTeleportingDoesNotBecomeLaunchMomentum() {
        Player p = player(WeaponId.COMMIT_CANNON, 1, false); p.setX(10_000); p.requestJump();
        var shots = fire(p, 1, true);
        assertEquals(16, shots.get(0).getVx()); assertEquals(0, shots.get(0).getVy());
        assertTrue(p.getVerticalVelocity() < 0);
    }

    @Test void viewLimitsClampBeforeFiringAndCorrectionIsNotMomentum() {
        for (int direction : new int[]{-1, 1}) {
            Player p = player(WeaponId.COMMIT_CANNON, direction, false);
            p.setX(direction > 0 ? 9000 : 0); // Initial correction is deliberately much larger than running.
            List<Projectile> shots = new ArrayList<>();
            p.update(direction < 0, direction > 0, false, true, 480, 20_000, shots, List.of(), 400, 1300);
            assertEquals(direction > 0 ? 1300 : 400, p.getX());
            assertEquals(direction * 11, shots.get(0).getVx());
        }
        Player moving = player(WeaponId.COMMIT_CANNON, 1, false); moving.setX(0);
        List<Projectile> shots = new ArrayList<>();
        moving.update(false, true, false, true, 480, 20_000, shots, List.of(), 400, 1300);
        assertEquals(405, moving.getX()); assertEquals(16, shots.get(0).getVx());
    }

    @Test void suppliedMovementBoundsIntersectWorldAndConstrainDash() {
        Player right = player(WeaponId.COMMIT_CANNON, 1, false); right.setX(19_966); right.dash(1);
        right.update(false, true, false, true, 480, 20_000, new ArrayList<>(), List.of(), 19_000, 21_000);
        assertEquals(19_970, right.getX());
        Player left = player(WeaponId.COMMIT_CANNON, -1, false); left.setX(3); left.dash(-1);
        left.update(true, false, false, true, 480, 20_000, new ArrayList<>(), List.of(), -800, 500);
        assertEquals(0, left.getX());
        Player arena = player(WeaponId.COMMIT_CANNON, 1, false); arena.setX(1296); arena.dash(1);
        arena.update(false, true, false, true, 480, 20_000, new ArrayList<>(), List.of(), 400, 1300);
        assertEquals(1300, arena.getX());
    }

    @Test void commitDashDoesNotFireOrLeakSpeed() { verifyDashDoesNotFireOrLeakSpeed(WeaponId.COMMIT_CANNON); }
    @Test void forceDashDoesNotFireOrLeakSpeed() { verifyDashDoesNotFireOrLeakSpeed(WeaponId.FORCE_PUSH); }
    @Test void rapidDashDoesNotFireOrLeakSpeed() { verifyDashDoesNotFireOrLeakSpeed(WeaponId.RAPID_CI); }
    @Test void garbageCollectorDashDoesNotFireOrLeakSpeed() { verifyDashDoesNotFireOrLeakSpeed(WeaponId.GARBAGE_COLLECTOR); }
    @Test void firewallDashDoesNotFireOrLeakSpeed() { verifyDashDoesNotFireOrLeakSpeed(WeaponId.FIREWALL); }
    @Test void refactorBeamDashDoesNotFireOrLeakSpeed() { verifyDashDoesNotFireOrLeakSpeed(WeaponId.REFACTOR_BEAM); }

    private void verifyDashDoesNotFireOrLeakSpeed(WeaponId id) {
        Player p = player(id, 1, true); p.dash(1);
        List<Projectile> shots = new ArrayList<>();
        for (int step = 0; step < 8; step++) {
            p.update(false, true, false, true, 480, 20_000, shots, List.of());
            assertTrue(shots.isEmpty());
        }
        shots = fire(p, 1, false);
        double intrinsic = WeaponCatalog.definition(id).baseStats().speed();
        assertEquals(intrinsic, shots.get(shots.size() / 2).getVx(), 1e-9);
        assertTrue(shots.stream().allMatch(Projectile::isCritical));
    }

    @Test void commitBackwardMomentumCannotReverseTheShot() { verifyBackwardMomentum(WeaponId.COMMIT_CANNON); }
    @Test void forceBackwardMomentumCannotReverseTheShot() { verifyBackwardMomentum(WeaponId.FORCE_PUSH); }
    @Test void rapidBackwardMomentumCannotReverseTheShot() { verifyBackwardMomentum(WeaponId.RAPID_CI); }
    @Test void garbageCollectorBackwardMomentumCannotReverseTheShot() { verifyBackwardMomentum(WeaponId.GARBAGE_COLLECTOR); }
    @Test void firewallBackwardMomentumCannotReverseTheShot() { verifyBackwardMomentum(WeaponId.FIREWALL); }
    @Test void refactorBeamBackwardMomentumCannotReverseTheShot() { verifyBackwardMomentum(WeaponId.REFACTOR_BEAM); }

    private void verifyBackwardMomentum(WeaponId id) {
        var stats = WeaponCatalog.definition(id).baseStats();
        for (int direction : new int[]{-1, 1}) {
            var request = new FireRequest(id, 0, 0, direction, stats, false, new Random(3), false, -5 * direction);
            for (var shot : new WeaponFireController().fire(request)) assertTrue(shot.getVx() * direction > 0);
        }
    }

    @Test void oldFireRequestAndEnemyProjectileContractsRemainStationary() {
        var request = new FireRequest(WeaponId.COMMIT_CANNON, 5, 7, 1,
                WeaponCatalog.definition(WeaponId.COMMIT_CANNON).baseStats(), false, new Random(1));
        assertEquals(11, new WeaponFireController().fire(request).get(0).getVx());
        Projectile enemy = new Projectile(100, 80, -8, 2, ProjectileType.ENEMY); enemy.update();
        assertEquals(92, enemy.getX()); assertEquals(82, enemy.getY());
        assertEquals(14, enemy.getBounds().width);
    }

    private static Player player(WeaponId id, int direction, boolean upgraded) {
        Player p = new Player(4_000, 450); RunBuild build = new RunBuild(id);
        if (upgraded) for (int i = 0; i < 3; i++) build.apply(UpgradeCatalog.definition(UpgradeId.DASH_CACHE));
        p.setRunBuild(build); p.setCombatSeed(29);
        p.update(direction < 0, direction > 0, false, false, 480, 20_000, new ArrayList<>(), List.of());
        return p;
    }

    private static List<Projectile> fire(Player p, int direction, boolean move) {
        List<Projectile> shots = new ArrayList<>();
        for (int step = 0; step < 12 && shots.isEmpty(); step++)
            p.update(move && direction < 0, move && direction > 0, false, true, 480, 20_000, shots, List.of());
        assertFalse(shots.isEmpty()); return shots;
    }
}
