package com.bigphil.mergehell.combat;

import com.bigphil.mergehell.model.Projectile;
import com.bigphil.mergehell.model.ProjectileType;
import com.bigphil.mergehell.model.Player;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DroneControllerTest {
    private static final DroneController.Bounds SCREEN = new DroneController.Bounds(0, 0, 960, 480);

    @Test void ranksCreateOneThenTwoRealCompanionsAndNeverThree() {
        DroneController controller = new DroneController();
        controller.update(input(0, List.of(), 10)); assertTrue(controller.snapshot().drones().isEmpty());
        controller.update(input(1, List.of(), 10)); assertEquals(1, controller.snapshot().drones().size());
        controller.update(input(2, List.of(), 10)); assertEquals(2, controller.snapshot().drones().size());
        var drones = controller.snapshot().drones();
        assertTrue(Math.hypot(drones.get(0).x() - drones.get(1).x(), drones.get(0).y() - drones.get(1).y()) > 50);
        controller.update(input(3, List.of(), 10)); assertEquals(2, controller.snapshot().drones().size());
        assertTrue(controller.snapshot().drones().stream().allMatch(pose -> pose.rank() == 3));
        assertThrows(IllegalArgumentException.class, () -> DroneController.profile(4));
    }

    @Test void companionsFollowIndependentlyAndKeepTheirBodiesInsideTheVisibleWorld() {
        DroneController controller = new DroneController(); controller.update(input(2, List.of(), 0));
        var first = controller.snapshot();
        controller.update(at(520, 420, -1, 2, List.of(), 0, SCREEN, true));
        for (int slot = 0; slot < 2; slot++) {
            var before = first.drones().get(slot); var after = controller.snapshot().drones().get(slot);
            assertTrue(Math.hypot(before.x() - after.x(), before.y() - after.y()) <= 12.000001);
            assertNotEquals(before.x(), after.x());
        }
        DroneController.Bounds arena = new DroneController.Bounds(1000, 30, 1400, 350);
        for (double px : new double[]{990, 1410}) for (double py : new double[]{0, 380}) {
            controller.update(at(px, py, 1, 2, List.of(), 0, arena, true));
            for (var drone : controller.snapshot().drones()) {
                assertTrue(drone.x() >= 1020 && drone.x() <= 1380);
                assertTrue(drone.y() >= 50 && drone.y() <= 330);
            }
        }
    }

    @Test void actualRunningTurnsJumpsDashesAndScreenEdgesNeverOverlapTheTwoHulls() {
        for (int rank : new int[]{2, 3}) {
            Player player = new Player(400, 450);
            DroneController controller = new DroneController();
            for (int tick = 0; tick < 600; tick++) {
                // Alternating full-width runs hit both locked screen edges; short turns cross the
                // original failing transition while jumping/dashing exercises unequal follow lag.
                boolean right = tick < 120 || tick >= 280 && tick < 450;
                if (tick >= 200 && tick < 240) right = (tick / 4) % 2 == 0;
                if (tick % 100 == 10 || tick % 100 == 24) player.requestJump();
                if (tick % 100 == 60) player.dash(right ? 1 : -1);
                player.update(!right, right, false, false, 480, 20_000, new ArrayList<>(), List.of(), 0, 930);
                controller.update(at(player.getBounds().getCenterX(), player.getBounds().getCenterY(),
                        player.getFacingDir(), rank, List.of(target(1, 600)), 10, SCREEN, true));
                assertSeparatedAndVisible(controller.snapshot(), SCREEN, "rank=" + rank + " tick=" + tick);
            }
            // A visible window moving past the formation and an upper-bound jump are both legal
            // inputs: clamping two craft to the same corner must not collapse them into one body.
            DroneController.Bounds cropped = new DroneController.Bounds(500, 240, 1460, 480);
            for (int tick = 0; tick < 60; tick++) {
                controller.update(at(tick < 30 ? 515 : 1445, 250, tick % 2 == 0 ? 1 : -1,
                        rank, List.of(), 0, cropped, true));
                assertSeparatedAndVisible(controller.snapshot(), cropped, "corner tick=" + tick);
            }
        }
    }

    private static void assertSeparatedAndVisible(DroneController.Snapshot snapshot,
                                                  DroneController.Bounds bounds, String context) {
        var left = snapshot.drones().get(0); var right = snapshot.drones().get(1);
        assertTrue(right.x() - left.x() >= 38 - 1e-9, context);
        assertTrue(Math.hypot(right.x() - left.x(), right.y() - left.y()) >= 38 - 1e-9, context);
        for (var drone : snapshot.drones()) {
            assertTrue(drone.x() >= bounds.minX() + 20 && drone.x() <= bounds.maxX() - 20, context);
            assertTrue(drone.y() >= bounds.minY() + 20 && drone.y() <= bounds.maxY() - 20, context);
            assertEquals(18, Math.hypot(drone.muzzleX() - drone.x(), drone.muzzleY() - drone.y()), 1e-9);
        }
    }

    @Test void noTargetsProtectedDeadOffscreenAndDistantActorsNeverCauseBlindFire() {
        DroneController controller = new DroneController();
        var targets = List.of(target(1, 500, 0, false, true), target(2, 510, 100, false, false),
                target(3, -1, 100, false, true), target(4, 959, 100, false, true));
        for (int step = 0; step < 200; step++) assertTrue(controller.update(input(1, targets, 10)).isEmpty());
        assertEquals(0, controller.snapshot().drones().get(0).targetId());
    }

    @Test void locksRemainStableButNewMarksAndTargetDeathReassignImmediately() {
        DroneController controller = new DroneController();
        controller.update(input(1, List.of(target(1, 600), target(2, 650)), 10));
        assertEquals(1, controller.snapshot().drones().get(0).targetId());
        controller.update(input(1, List.of(target(2, 500), target(1, 620)), 10));
        assertEquals(1, controller.snapshot().drones().get(0).targetId(), "Nearest jitter must not steal a valid lock");
        controller.update(input(1, List.of(target(2, 500, 100, true, true), target(1, 620)), 10));
        assertEquals(2, controller.snapshot().drones().get(0).targetId());
        controller.update(input(1, List.of(target(2, 500, 0, true, true), target(1, 620)), 10));
        assertEquals(1, controller.snapshot().drones().get(0).targetId());
    }

    @Test void twoCompanionsChooseDifferentEnemiesAndTheSecondWaitsOnOneWeakEnemy() {
        DroneController controller = new DroneController();
        controller.update(input(2, List.of(target(1, 600, 5, false, true), target(2, 650)), 10));
        assertEquals(List.of(1L, 2L), controller.snapshot().drones().stream().map(DroneController.Pose::targetId).toList());
        controller.reset();
        List<Projectile> shots = controller.update(input(2, List.of(target(1, 600, 10, false, true)), 10));
        assertEquals(1, shots.size());
        for (int tick = 0; tick < 36; tick++) controller.update(input(2, List.of(target(1, 600, 10, false, true)), 10));
        assertEquals(0, controller.snapshot().drones().get(1).targetId());
        assertEquals(0, controller.snapshot().drones().get(1).recoil());
    }

    @Test void healthySingleBossCanBeSharedButShotsStayStaggeredAfterAnIdlePeriod() {
        DroneController controller = new DroneController();
        for (int tick = 0; tick < 120; tick++) controller.update(input(2, List.of(), 10));
        List<Integer> firedAt = new ArrayList<>(); List<Integer> slots = new ArrayList<>();
        for (int tick = 0; tick < 220; tick++) {
            var shots = controller.update(input(2, List.of(target(5, 620, 1000, false, true)), 10));
            assertTrue(shots.size() <= 1);
            if (!shots.isEmpty()) {
                firedAt.add(tick);
                slots.add(controller.snapshot().drones().stream().filter(pose -> pose.recoil() == 1).findFirst().orElseThrow().slot());
            }
        }
        assertEquals(List.of(0, 36, 72, 108, 144, 180, 216), firedAt);
        assertEquals(List.of(0, 1, 0, 1, 0, 1, 0), slots);
    }

    @Test void rankThreeImprovesBothUnitsCadenceDamageAndExactlyOnePierce() {
        DroneController controller = new DroneController(); List<Integer> firedAt = new ArrayList<>();
        for (int tick = 0; tick < 100; tick++) {
            var shots = controller.update(input(3, List.of(target(1, 620, 1000, false, true)), 10));
            if (!shots.isEmpty()) {
                firedAt.add(tick); assertEquals(14, shots.get(0).getDamage());
                assertEquals(1, shots.get(0).getRemainingPierces());
            }
        }
        assertEquals(List.of(0, 24, 48, 72, 96), firedAt);
        assertEquals(10, DroneController.profile(1).damage(25));
        assertEquals(2, DroneController.profile(3).count());
        assertEquals(44, DroneController.profile(3).damage(80));
        assertEquals(55, DroneController.profile(3).damage(100));
        assertEquals(61, DroneController.profile(3).damage(110));
    }

    @Test void capacityDenialDoesNotConsumeAShotOrCreateRecoilAndNeverBurstsLater() {
        DroneController controller = new DroneController(); var targets = List.of(target(1, 600), target(2, 650));
        for (int tick = 0; tick < 120; tick++) assertTrue(controller.update(input(2, targets, 0)).isEmpty());
        assertTrue(controller.snapshot().drones().stream().allMatch(pose -> pose.recoil() == 0));
        assertTrue(controller.rejectedProjectiles() >= 120);
        long refused = controller.rejectedProjectiles();
        controller.update(at(400, 450, 1, 2, targets, 0, SCREEN, false));
        assertEquals(refused, controller.rejectedProjectiles());
        assertEquals(1, controller.update(input(2, targets, 1)).size());
        assertTrue(controller.update(input(2, targets, 1)).isEmpty());
        controller.reset();
        assertEquals(0, controller.rejectedProjectiles());
    }

    @Test void pausedInputFreezesPositionTargetRecoilAndCooldownUntilTheNextGameplayTick() {
        DroneController controller = new DroneController(); var active = input(2, List.of(target(1, 600)), 10);
        controller.update(active); var frozen = controller.snapshot();
        for (int i = 0; i < 200; i++)
            assertTrue(controller.update(at(50, 50, -1, 3, List.of(), 10, SCREEN, false)).isEmpty());
        assertSame(frozen, controller.snapshot());
        assertTrue(controller.update(active).isEmpty());
        assertEquals(frozen.tick() + 1, controller.snapshot().tick());
    }

    @Test void resetRebuildsFromRankWithoutSavingOrReusingPositionsTargetsAndPrediction() {
        DroneController restored = new DroneController();
        for (int tick = 0; tick < 100; tick++) restored.update(at(800, 300, -1, 3, List.of(target(1, 620)), 10, SCREEN, true));
        restored.reset(); assertTrue(restored.snapshot().drones().isEmpty()); assertEquals(0, restored.snapshot().tick());
        DroneController fresh = new DroneController(); var input = input(3, List.of(target(2, 650)), 10);
        var expected = fresh.update(input); var actual = restored.update(input);
        assertEquals(fresh.snapshot(), restored.snapshot()); assertEquals(expected.size(), actual.size());
        assertEquals(expected.get(0).getVx(), actual.get(0).getVx());
    }

    @Test void frameAndSnapshotListsCannotBeMutatedByTheCaller() {
        List<DroneController.Target> external = new ArrayList<>(List.of(target(1, 600)));
        var input = input(1, external, 10); external.clear();
        DroneController controller = new DroneController(); assertEquals(1, controller.update(input).size());
        var snapshot = controller.snapshot();
        assertThrows(UnsupportedOperationException.class, () -> snapshot.drones().clear());
        controller.update(at(600, 300, 1, 1, List.of(), 0, SCREEN, true));
        assertEquals(1, snapshot.tick()); assertEquals(1, snapshot.drones().get(0).targetId());
    }

    @Test void everyWeaponSourceProducesTheSameSmallDroneBodyWithoutWeaponEvolutionEffects() {
        for (WeaponId weapon : WeaponId.values()) {
            DroneController controller = new DroneController();
            var input = new DroneController.Input(400, 450, 1, 3, weapon, 80, SCREEN, List.of(target(1, 600)), 10, true);
            Projectile shot = controller.update(input).get(0); var pose = controller.snapshot().drones().get(0);
            assertTrue(shot.isDrone(), weapon.name()); assertEquals(ProjectileType.DRONE, shot.getType());
            assertEquals(CombatEvent.DamageKind.DRONE, shot.getDamageKind()); assertEquals(weapon, shot.getWeapon());
            assertEquals(12, shot.getBounds().width); assertEquals(6, shot.getBounds().height);
            assertEquals(pose.muzzleX(), shot.getX() + 6, 1e-9); assertEquals(pose.muzzleY(), shot.getY() + 3, 1e-9);
            assertEquals(18, Math.hypot(pose.muzzleX() - pose.x(), pose.muzzleY() - pose.y()), 1e-9);
            assertEquals(14, Math.hypot(shot.getVx(), shot.getVy()), 1e-9);
            assertFalse(shot.isCritical()); assertEquals(0, shot.getKnockback()); assertEquals(0, shot.getRemainingRicochets());
            assertEquals(new ProjectileEffects(0, 0, 0, 0, 0, 0, 0, 0, 60), shot.getSpec().effects());
        }
    }

    @Test void aMovingEnemyIsLedAtLaunchAndOldShotsDoNotHomingFollowLaterMotion() {
        DroneController controller = new DroneController();
        controller.update(at(400, 300, 1, 1, List.of(new DroneController.Target(1, 650, 147, 100, false, true)), 0, SCREEN, true));
        Projectile shot = controller.update(at(400, 300, 1, 1,
                List.of(new DroneController.Target(1, 650, 150, 100, false, true)), 1, SCREEN, true)).get(0);
        double vx = shot.getVx(), vy = shot.getVy(); boolean hit = false;
        for (int tick = 1; tick <= 35; tick++) {
            shot.update();
            hit |= shot.hits(new Rectangle(644, 144 + tick * 3, 12, 12));
            controller.update(at(200, 420, -1, 1, List.of(target(1, 300)), 10, SCREEN, true));
            assertEquals(vx, shot.getVx()); assertEquals(vy, shot.getVy());
        }
        assertTrue(hit, "Finite prediction should intersect an enemy moving 3px/tick across the shot path");
    }

    @Test void targetTeleportsDoNotCreateUnboundedPredictionAndDroneShotsHaveAFiniteLife() {
        DroneController controller = new DroneController();
        controller.update(input(1, List.of(target(1, 500)), 0));
        Projectile shot = controller.update(input(1, List.of(target(1, 800)), 1)).get(0);
        var pose = controller.snapshot().drones().get(0);
        assertEquals(Math.atan2(420 - pose.y(), 800 - pose.x()), pose.aimRadians(), 1e-9);
        for (int tick = 0; tick < 59; tick++) { shot.update(); assertFalse(shot.isDead()); }
        shot.update(); assertTrue(shot.isDead());
    }

    private static DroneController.Target target(long id, double x) { return target(id, x, 100, false, true); }
    private static DroneController.Target target(long id, double x, int hp, boolean marked, boolean attackable) {
        return new DroneController.Target(id, x, 420, hp, marked, attackable);
    }
    private static DroneController.Input input(int rank, List<DroneController.Target> targets, int capacity) {
        return at(400, 450, 1, rank, targets, capacity, SCREEN, true);
    }
    private static DroneController.Input at(double x, double y, int facing, int rank,
                                             List<DroneController.Target> targets, int capacity,
                                             DroneController.Bounds bounds, boolean active) {
        return new DroneController.Input(x, y, facing, rank, WeaponId.COMMIT_CANNON, 25, bounds, targets, capacity, active);
    }
}
