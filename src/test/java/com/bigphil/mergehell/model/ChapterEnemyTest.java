package com.bigphil.mergehell.model;

import com.bigphil.mergehell.engine.ProjectileBuffer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.bigphil.mergehell.model.ObstacleManager.Enemy.Mode.*;
import static org.junit.jupiter.api.Assertions.*;

class ChapterEnemyTest {
    private static final int GROUND = 480;
    private static final EntityType[] SPECIALISTS = {EntityType.SENTINEL, EntityType.WARDEN,
            EntityType.RIGGER, EntityType.INTERRUPT, EntityType.DRILLER, EntityType.SLAG_SPITTER,
            EntityType.MIRROR, EntityType.SPORE_POD, EntityType.LURKER};

    @Test void laterChaptersHaveNineExclusiveSpeciesAndGroundedSpawnFeet() {
        Set<EntityType> seen = new HashSet<>();
        for (int chapter = 2; chapter <= 4; chapter++) {
            var family = Arrays.asList(ObstacleManager.ambientRoster(chapter));
            assertEquals(3, new HashSet<>(family).size());
            for (EntityType type : family) {
                assertTrue(seen.add(type), "Repeated species " + type);
                assertTrue(type.isChapterSpecialist());
                int y = ObstacleManager.specialistSpawnY(type, GROUND, 30);
                boolean flies = type == EntityType.RIGGER || type == EntityType.INTERRUPT || type == EntityType.SPORE_POD;
                if (flies) assertTrue(y + type.height < GROUND - 80);
                else assertEquals(GROUND, y + type.height);
            }
        }
        assertEquals(9, seen.size());
    }

    @Test void sniperLocksItsOriginAndAimForFullSeventyFiveTicks() {
        var sniper = enemy(EntityType.SENTINEL, 600);
        List<Projectile> shots = new ArrayList<>();
        reachWarning(sniper, shots, 180, 400);
        assertEquals(75, sniper.getTelegraphTicks());
        var lock = sniper.getTactics();
        double x = sniper.getX(), y = sniper.getY();
        for (int i = 0; i < 74; i++) {
            tick(sniper, 700, 120, shots);
            assertTrue(shots.isEmpty());
            assertEquals(lock.targetX(), sniper.getTactics().targetX());
            assertEquals(lock.targetY(), sniper.getTactics().targetY());
            assertEquals(x, sniper.getX()); assertEquals(y, sniper.getY());
        }
        tick(sniper, 700, 120, shots);
        assertEquals(1, shots.size());
        assertEquals(lock.originX(), shots.get(0).getX());
        assertEquals(lock.velocityX(), shots.get(0).getVx());
        assertEquals(lock.velocityY(), shots.get(0).getVy());
        assertEquals(RECOVER, sniper.getTactics().mode());
    }

    @Test void losingAnOffscreenWarningRequiresTheEntireWarningAgain() {
        var sniper = enemy(EntityType.SENTINEL, 600);
        List<Projectile> shots = new ArrayList<>();
        reachWarning(sniper, shots, 180, 400);
        for (int i = 0; i < 65; i++) tick(sniper, 180, 400, shots);
        sniper.update(1, 180, 400, 700, 1660, GROUND);
        sniper.maybeShoot(400, shots);
        assertEquals(0, sniper.getTelegraphTicks());
        assertTrue(shots.isEmpty());
        reachWarning(sniper, shots, 180, 400);
        assertEquals(75, sniper.getTelegraphTicks());
        for (int i = 0; i < 74; i++) tick(sniper, 180, 400, shots);
        assertTrue(shots.isEmpty());
    }

    @Test void everySpecialistWaitsOffscreenInsteadOfSpringingAnUnseenAttack() {
        for (EntityType type : SPECIALISTS) {
            var enemy = enemy(type, 1800);
            List<Projectile> shots = new ArrayList<>();
            for (int tick = 0; tick < 80; tick++) {
                enemy.update(1, 180, 400, 0, 960, GROUND);
                enemy.maybeShoot(400, shots);
                assertTrue(shots.isEmpty(), type.name());
                assertEquals(0, enemy.getTelegraphTicks(), type.name());
                assertNotEquals(ATTACK, enemy.getTactics().mode(), type.name());
            }
        }
    }

    @Test void wardenShieldReducesOnlyFrontalDamageAndAlwaysCanBeBroken() {
        var front = enemy(EntityType.WARDEN, 450);
        front.takeHit(4, 20, 1);
        assertEquals(EntityType.WARDEN.maxHp - 2, front.getHp());
        var behind = enemy(EntityType.WARDEN, 450);
        behind.takeHit(4, 20, -1);
        assertEquals(EntityType.WARDEN.maxHp - 4, behind.getHp());
        front.takeHit(0, 0, 1);
        assertEquals(EntityType.WARDEN.maxHp - 2, front.getHp());
        for (int i = 0; i < EntityType.WARDEN.maxHp - 2; i++) front.takeHit(1, 0, 1);
        assertTrue(front.isDead());
    }

    @Test void wardenDropsShieldThroughoutWarningRamAndLongRecovery() {
        var warden = enemy(EntityType.WARDEN, 460);
        List<Projectile> shots = new ArrayList<>();
        reachMode(warden, AIM, 280, shots);
        assertEquals(48, warden.getTelegraphTicks());
        assertFalse(warden.getTactics().shielded());
        double lockedDestination = warden.getTactics().targetX();
        for (int i = 0; i < 48; i++) tick(warden, 700, 400, shots);
        assertEquals(ATTACK, warden.getTactics().mode());
        for (int i = 0; i < 16; i++) tick(warden, 700, 400, shots);
        assertEquals(lockedDestination, warden.getX(), 1e-8);
        assertEquals(RECOVER, warden.getTactics().mode());
        int hp = warden.getHp();
        warden.takeHit(4, 0, 1);
        assertEquals(hp - 4, warden.getHp());
        for (int i = 0; i < 75; i++) { tick(warden, 700, 400, shots); assertFalse(warden.getTactics().shielded()); }
        tick(warden, 700, 400, shots);
        assertTrue(warden.getTactics().shielded());
    }

    @Test void drillerIsSafeToTouchUntilItsWholeSurfaceWarningFinishes() {
        var driller = enemy(EntityType.DRILLER, 470);
        List<Projectile> shots = new ArrayList<>();
        for (int i = 0; i < 65; i++) {
            assertFalse(driller.isContactDangerous());
            tick(driller, 280, 400, shots);
        }
        assertEquals(EMERGE, driller.getTactics().mode());
        assertEquals(58, driller.getTelegraphTicks());
        double x = driller.getX();
        for (int i = 0; i < 57; i++) {
            tick(driller, 800, 100, shots);
            assertFalse(driller.isContactDangerous());
            assertEquals(x, driller.getX());
        }
        tick(driller, 800, 100, shots);
        assertTrue(driller.isContactDangerous());
        assertEquals(ATTACK, driller.getTactics().mode());
        for (int i = 0; i < 30; i++) tick(driller, 800, 100, shots);
        assertEquals(RECOVER, driller.getTactics().mode());
        assertEquals(GROUND, driller.getY() + EntityType.DRILLER.height);
        assertEquals(94, driller.getTactics().ticks());
    }

    @Test void lurkerHasALockedArcingPounceAndCannotHomeAfterTakeoff() {
        var lurker = enemy(EntityType.LURKER, 490);
        List<Projectile> shots = new ArrayList<>();
        reachMode(lurker, AIM, 280, shots);
        assertEquals(48, lurker.getTelegraphTicks());
        double target = lurker.getTactics().targetX();
        for (int i = 0; i < 48; i++) tick(lurker, 800, 100, shots);
        for (int i = 0; i < 18; i++) tick(lurker, 800, 100, shots);
        assertTrue(lurker.getY() + EntityType.LURKER.height < GROUND - 100);
        for (int i = 0; i < 18; i++) tick(lurker, 800, 100, shots);
        assertEquals(target, lurker.getX(), 1e-9);
        assertEquals(GROUND, lurker.getY() + EntityType.LURKER.height, 1e-9);
        assertEquals(RECOVER, lurker.getTactics().mode());
        assertEquals(68, lurker.getTactics().ticks());
    }

    @Test void interruptDivesFromHoverThenClimbsBackDuringRecovery() {
        var drone = enemy(EntityType.INTERRUPT, 490);
        List<Projectile> shots = new ArrayList<>();
        reachMode(drone, AIM, 280, shots);
        assertEquals(50, drone.getTelegraphTicks());
        double start = drone.getY(), target = drone.getTactics().targetX();
        for (int i = 0; i < 50 + 28; i++) tick(drone, 800, 400, shots);
        assertEquals(target, drone.getX(), 1e-8);
        assertEquals(GROUND - 5, drone.getY() + EntityType.INTERRUPT.height, 1e-8);
        assertEquals(RECOVER, drone.getTactics().mode());
        for (int i = 0; i < 60; i++) tick(drone, 800, 400, shots);
        assertTrue(drone.getY() < start + 25);
        assertEquals(RECOVER, drone.getTactics().mode());
    }

    @Test void riggersTwinCableLanesLeaveARealCentralDodgeCorridor() {
        var rigger = enemy(EntityType.RIGGER, 570);
        List<Projectile> shots = new ArrayList<>();
        reachWarning(rigger, shots, 280, 360);
        var lock = rigger.getTactics();
        assertEquals(48, rigger.getTelegraphTicks());
        for (int i = 0; i < 48; i++) tick(rigger, 700, 100, shots);
        assertEquals(2, shots.size());
        assertEquals(66, shots.get(1).getY() - shots.get(0).getY());
        assertEquals(shots.get(0).getVx(), shots.get(1).getVx());
        assertEquals(shots.get(0).getVy(), shots.get(1).getVy());
        assertEquals(lock.originY(), (shots.get(0).getY() + shots.get(1).getY()) / 2);
    }

    @Test void aStillHeroActuallyFitsBetweenRiggersTelegraphedLanes() {
        var rigger = enemy(EntityType.RIGGER, 570);
        List<Projectile> shots = new ArrayList<>();
        reachWarning(rigger, shots, 280, 450);
        for (int i = 0; i < 48; i++) tick(rigger, 280, 450, shots);
        var hero = new java.awt.Rectangle(280, 450, 30, 30);
        for (int i = 0; i < 100; i++) for (Projectile shot : shots) {
            shot.update();
            assertFalse(shot.hits(hero), "The center corridor must fit the real player collision body");
        }
    }

    @Test void slagAndSporeTrajectoriesLandAtTheirLockedAimAndUseRealGravity() {
        for (EntityType type : new EntityType[]{EntityType.SLAG_SPITTER, EntityType.SPORE_POD}) {
            var enemy = enemy(type, 570);
            List<Projectile> shots = new ArrayList<>();
            reachWarning(enemy, shots, 280, 420);
            var lock = enemy.getTactics();
            for (int i = 0; i < lock.ticks(); i++) tick(enemy, 800, 100, shots);
            assertEquals(type == EntityType.SPORE_POD ? 3 : 1, shots.size());
            Projectile center = shots.get(type == EntityType.SPORE_POD ? 1 : 0);
            int flight = type == EntityType.SPORE_POD ? 68 : 55;
            double initialVy = center.getVy();
            for (int i = 0; i < flight; i++) center.update();
            assertEquals(lock.targetX(), center.getX(), 1e-7);
            assertEquals(lock.targetY(), center.getY(), 1e-7);
            assertTrue(center.getVy() > initialVy + 6);
        }
    }

    @Test void saturatedSporeFieldPreservesLockedWarningAndDoesNotCreatePartialFan() {
        var pod = enemy(EntityType.SPORE_POD, 570);
        ProjectileBuffer shots = new ProjectileBuffer(3);
        Projectile blocker = new Projectile(0, 0, 1, 0, ProjectileType.ENEMY);
        shots.add(blocker);
        for (int i = 0; i < 250; i++) tick(pod, 280, 400, shots);
        assertEquals(List.of(blocker), shots);
        assertEquals(1, pod.getTelegraphTicks());
        var aim = pod.getTactics();
        shots.clear();
        tick(pod, 800, 100, shots);
        assertEquals(3, shots.size());
        assertEquals(aim.velocityX(), shots.get(1).getVx());
        assertEquals(aim.velocityY(), shots.get(1).getVy());
        assertEquals(RECOVER, pod.getTactics().mode());
    }

    @Test void deterministicSpecialistReplayIncludesModesTargetsArcsAndWholeVolleys() {
        for (EntityType type : SPECIALISTS) {
            var first = enemy(type, 570); var second = enemy(type, 570);
            List<Projectile> a = new ArrayList<>(), b = new ArrayList<>();
            for (int i = 0; i < 700; i++) {
                double playerX = 200 + i % 100, playerY = 280 + i % 70;
                tick(first, playerX, playerY, a); tick(second, playerX, playerY, b);
                assertEquals(first.getX(), second.getX()); assertEquals(first.getY(), second.getY());
                assertEquals(first.getTactics(), second.getTactics());
                assertEquals(a.size(), b.size());
                for (int j = 0; j < a.size(); j++) {
                    assertEquals(a.get(j).getVx(), b.get(j).getVx());
                    assertEquals(a.get(j).getVy(), b.get(j).getVy());
                }
                a.clear(); b.clear();
            }
        }
    }

    @Test void ballisticFactoryCannotChangeLegacyLinearShots() {
        Projectile linear = new Projectile(30, 40, -6, -2, ProjectileType.ENEMY);
        Projectile ballistic = Projectile.ballistic(30, 40, -6, -2, ProjectileType.ENEMY, .16);
        for (int i = 0; i < 20; i++) { linear.update(); ballistic.update(); }
        assertEquals(-90, linear.getX()); assertEquals(0, linear.getY());
        assertEquals(-2, linear.getVy()); assertEquals(-90, ballistic.getX());
        assertEquals(.16 * 20 * 19 / 2, ballistic.getY(), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> Projectile.ballistic(0, 0, 1, 0, ProjectileType.COMMIT, .1));
        assertThrows(IllegalArgumentException.class, () -> Projectile.ballistic(0, 0, 1, 0, ProjectileType.ENEMY, Double.NaN));
    }

    @Test void publishedShotLanesAreImmutableAndExactlyDescribeEmittedProjectiles() {
        for (EntityType type : new EntityType[]{EntityType.SENTINEL, EntityType.RIGGER,
                EntityType.SLAG_SPITTER, EntityType.MIRROR, EntityType.SPORE_POD}) {
            var enemy = enemy(type, 550);
            List<Projectile> shots = new ArrayList<>();
            reachWarning(enemy, shots, 250, 400);
            var tactics = enemy.getTactics();
            assertThrows(UnsupportedOperationException.class, () -> tactics.lanes().clear());
            for (int i = 0; i < tactics.ticks(); i++) tick(enemy, 780, 100, shots);
            assertEquals(tactics.lanes().size(), shots.size());
            for (int i = 0; i < shots.size(); i++) {
                var lane = tactics.lanes().get(i); var shot = shots.get(i);
                assertEquals(lane.x(), shot.getX()); assertEquals(lane.y(), shot.getY());
                assertEquals(lane.velocityX(), shot.getVx()); assertEquals(lane.velocityY(), shot.getVy());
                assertEquals(lane.gravity(), shot.getVerticalAcceleration());
                assertEquals(lane.projectileType(), shot.getType());
            }
        }
    }

    @Test void wardenWarningAndRamBothStopAtTheActualShaftEdge() {
        var warden = enemy(EntityType.WARDEN, 510);
        warden.setMovementBounds(450, 690);
        List<Projectile> shots = new ArrayList<>();
        reachMode(warden, AIM, 800, shots);
        var lock = warden.getTactics();
        assertEquals(690 - EntityType.WARDEN.width, lock.targetX());
        assertTrue(lock.targetX() + EntityType.WARDEN.width <= 690);
        for (int i = 0; i < lock.ticks() + 16; i++) {
            tick(warden, 800, 450, shots);
            assertTrue(warden.getX() >= 450);
            assertTrue(warden.getX() + EntityType.WARDEN.width <= 690 + 1e-8);
        }
        assertEquals(lock.targetX(), warden.getX(), 1e-8);
        assertEquals(RECOVER, warden.getTactics().mode());
        warden.takeHit(1, 24, 1);
        assertEquals(lock.targetX(), warden.getX(), 1e-8);
    }

    @Test void flyingRiggersCanCrossShaftsButInvalidMovementBoundsAreRejected() {
        var rigger = enemy(EntityType.RIGGER, 510);
        rigger.setMovementBounds(450, 570);
        List<Projectile> shots = new ArrayList<>();
        for (int i = 0; i < 30; i++) tick(rigger, 900, 450, shots);
        assertTrue(rigger.getX() + EntityType.RIGGER.width > 570);
        assertThrows(IllegalArgumentException.class, () -> rigger.setMovementBounds(100, 90));
        assertThrows(IllegalArgumentException.class, () -> rigger.setMovementBounds(0, Double.POSITIVE_INFINITY));
    }

    private static ObstacleManager.Enemy enemy(EntityType type, int x) {
        return new ObstacleManager.Enemy(x, ObstacleManager.specialistSpawnY(type, GROUND, 15), type, 90210L);
    }

    private static void tick(ObstacleManager.Enemy enemy, double playerX, double playerY, List<Projectile> shots) {
        enemy.update(1, playerX, playerY, 0, 960, GROUND);
        enemy.maybeShoot(playerY, shots);
    }

    private static void reachWarning(ObstacleManager.Enemy enemy, List<Projectile> shots, double playerX, double playerY) {
        for (int i = 0; i < 250 && enemy.getTelegraphTicks() == 0; i++) tick(enemy, playerX, playerY, shots);
        assertTrue(enemy.getTelegraphTicks() > 0, enemy.getType().name());
        assertTrue(shots.isEmpty());
    }

    private static void reachMode(ObstacleManager.Enemy enemy, ObstacleManager.Enemy.Mode mode,
                                  double playerX, List<Projectile> shots) {
        for (int i = 0; i < 300 && enemy.getTactics().mode() != mode; i++) tick(enemy, playerX, 400, shots);
        assertEquals(mode, enemy.getTactics().mode());
    }
}
