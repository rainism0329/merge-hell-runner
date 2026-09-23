package com.bigphil.mergehell.model;

import com.bigphil.mergehell.world.ExplorationRoute;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EnemyTerrainNavigationTest {
    private static final int GROUND = 480;

    @Test void aPlayerBesideTheSameWallFaceDoesNotSendTheEnemyToItsOppositeSide() {
        Rectangle wall = new Rectangle(400, 320, 170, 160);
        for (EntityType type : new EntityType[]{EntityType.SENTINEL, EntityType.DRILLER}) {
            for (boolean onLeft : new boolean[]{true, false}) {
                var manager = new ObstacleManager(60);
                manager.setSolids(List.of(wall));
                manager.spawnEnemy(onLeft ? 250 : 650, GROUND - type.height, type);
                var enemy = manager.getEnemies().get(0);
                double playerX = onLeft ? wall.x - 30 : wall.getMaxX();
                for (int tick = 0; tick < 350; tick++) {
                    advance(manager, enemy, playerX, new ArrayList<>());
                    assertEquals(0, enemy.getTerrainClimbDirection(),
                            type + " must not climb a wall behind the player, side=" + onLeft);
                    assertTrue(onLeft ? enemy.getX() + type.width <= wall.x : enemy.getX() >= wall.getMaxX());
                    assertFalse(enemy.getBounds().intersects(wall));
                }
            }
        }
    }

    @Test void aPlayerOnTheActualOppositeSideStillMakesTroopsCrossEitherWallFace() {
        Rectangle wall = new Rectangle(400, 320, 170, 160);
        for (boolean onLeft : new boolean[]{true, false}) {
            var manager = new ObstacleManager(61);
            manager.setSolids(List.of(wall));
            manager.spawnEnemy(onLeft ? 250 : 650, GROUND - EntityType.SENTINEL.height, EntityType.SENTINEL);
            var enemy = manager.getEnemies().get(0);
            boolean crossed = false;
            for (int tick = 0; tick < 600; tick++) {
                advance(manager, enemy, onLeft ? 750 : 160, new ArrayList<>());
                assertFalse(enemy.getBounds().intersects(wall));
                if ((onLeft ? enemy.getX() > wall.getMaxX() : enemy.getX() + EntityType.SENTINEL.width < wall.x)
                        && Math.abs(enemy.getY() + EntityType.SENTINEL.height - GROUND) < 2) {
                    crossed = true;
                    break;
                }
            }
            assertTrue(crossed, "A real wall between the actors still requires navigation, side=" + onLeft);
        }
    }

    @Test void repeatedEquivalentGeometryDoesNotResetNavigationOrAttackTiming() {
        Rectangle wall=new Rectangle(400,320,170,160);
        var unchanged=new ObstacleManager(59);var republished=new ObstacleManager(59);
        unchanged.setSolids(List.of(wall));republished.setSolids(List.of(new Rectangle(wall)));
        unchanged.spawnEnemy(650,GROUND-EntityType.WARDEN.height,EntityType.WARDEN);
        republished.spawnEnemy(650,GROUND-EntityType.WARDEN.height,EntityType.WARDEN);
        var a=unchanged.getEnemies().get(0);var b=republished.getEnemies().get(0);
        var shotsA=new ArrayList<Projectile>();var shotsB=new ArrayList<Projectile>();
        boolean crossed=false;
        for(int tick=0;tick<550;tick++) {
            republished.setSolids(List.of(new Rectangle(wall)));
            advance(unchanged,a,160,shotsA);advance(republished,b,160,shotsB);
            assertEquals(a.getX(),b.getX(),1e-9);assertEquals(a.getY(),b.getY(),1e-9);
            assertEquals(a.getTelegraphTicks(),b.getTelegraphTicks());
            assertEquals(a.getTactics().mode(),b.getTactics().mode());assertEquals(shotsA.size(),shotsB.size());
            crossed|=b.getX()+EntityType.WARDEN.width<wall.x;
        }
        assertTrue(crossed,"ordinary per-tick terrain publication must not keep restarting a climb");
    }

    @Test void everyAuthoredWallCanBeClimbedByItsChapterTroopsWithoutCrossingSolidPixels() {
        EntityType[][] troops = {
                {EntityType.BUG, EntityType.CONFLICT, EntityType.TECHDEBT},
                {EntityType.BUG, EntityType.LEAK, EntityType.TECHDEBT},
                {EntityType.SENTINEL, EntityType.WARDEN, EntityType.RIGGER},
                {EntityType.SLAG_SPITTER, EntityType.INTERRUPT},
                {EntityType.MIRROR, EntityType.LURKER, EntityType.SPORE_POD}
        };
        for (int chapter = 0; chapter < 5; chapter++) for (Rectangle wall : new ExplorationRoute(chapter).solids())
            for (EntityType type : troops[chapter]) {
                var manager = new ObstacleManager(42);
                manager.setSolids(List.of(wall));
                manager.spawnEnemy(wall.x + wall.width + 65, GROUND - type.height, type);
                var enemy = manager.getEnemies().get(0);
                boolean crossed = false;
                for (int tick = 0; tick < 600; tick++) {
                    advance(manager, enemy, wall.x - 240, new ArrayList<>());
                    assertFalse(enemy.getBounds().intersects(wall), chapter + ": " + type + " at " + tick);
                    if (enemy.getX() + type.width < wall.x - 10 && Math.abs(enemy.getY() + type.height - GROUND) < 2) {
                        crossed = true; break;
                    }
                }
                assertTrue(crossed, "Still trapped: chapter " + chapter + ", " + type + ", " + wall + " at " + enemy.getX() + "," + enemy.getY());
            }
    }

    @Test void aFlierUsesTheShorterPassageBeneathAnOverhangAtBoundedSpeed() {
        Rectangle canopy = new Rectangle(400, 150, 170, 170);
        var manager = new ObstacleManager(43); manager.setSolids(List.of(canopy));
        manager.spawnEnemy(590, 310, EntityType.RIGGER);
        var enemy = manager.getEnemies().get(0);
        double lowestTop = enemy.getY(); boolean crossed = false;
        for (int tick = 0; tick < 300; tick++) {
            double beforeX = enemy.getX(), beforeY = enemy.getY();
            advance(manager, enemy, 150, new ArrayList<>());
            assertFalse(enemy.getBounds().intersects(canopy));
            assertTrue(Math.hypot(enemy.getX() - beforeX, enemy.getY() - beforeY) < 6);
            lowestTop = Math.max(lowestTop, enemy.getY());
            if (enemy.getX() + EntityType.RIGGER.width < canopy.x) { crossed = true; break; }
        }
        assertTrue(crossed);
        assertTrue(lowestTop >= canopy.getMaxY(), "Flier should use the open underside instead of climbing over a tall roof");
    }

    @Test void tunnellerCrossesTheFoundationButNeverEmergesInsideItEvenIfThePlayerStandsAbove() {
        Rectangle wall = new Rectangle(400, 300, 200, 180);
        var manager = new ObstacleManager(44); manager.setSolids(List.of(wall));
        manager.spawnEnemy(650, GROUND - EntityType.DRILLER.height, EntityType.DRILLER);
        var enemy = manager.getEnemies().get(0);
        boolean undergroundInside = false, emergedBeyond = false;
        for (int tick = 0; tick < 900; tick++) {
            double before = enemy.getX();
            advance(manager, enemy, 450, new ArrayList<>());
            assertTrue(Math.abs(enemy.getX() - before) <= 2.01);
            Rectangle wholeBody = new Rectangle((int)enemy.getX(), (int)enemy.getY(), EntityType.DRILLER.width, EntityType.DRILLER.height);
            if (wholeBody.intersects(wall)) {
                assertEquals(ObstacleManager.Enemy.Mode.BURROWED, enemy.getTactics().mode());
                assertFalse(enemy.isContactDangerous()); undergroundInside = true;
            }
            if (undergroundInside && enemy.getTactics().mode() == ObstacleManager.Enemy.Mode.EMERGE) {
                assertFalse(wholeBody.intersects(wall)); emergedBeyond = true; break;
            }
        }
        assertTrue(undergroundInside); assertTrue(emergedBeyond);
    }

    @Test void navigationDoesNotCrossALandSegmentBoundaryAndRestartsWarningsAfterTheClimb() {
        Rectangle wall = new Rectangle(430, 320, 120, 160);
        var manager = new ObstacleManager(45); manager.setSolids(List.of(wall));
        manager.spawnEnemy(650, GROUND - EntityType.SENTINEL.height, EntityType.SENTINEL);
        var enemy = manager.getEnemies().get(0);
        enemy.setMovementBounds(560, 950);
        for (int i = 0; i < 200; i++) { advance(manager, enemy, 200, new ArrayList<>()); assertTrue(enemy.getX() >= 560); }
        enemy.setMovementBounds(0, 950);
        List<Projectile> shots = new ArrayList<>(); boolean climbed = false, resumed = false;
        for (int i = 0; i < 600; i++) {
            advance(manager, enemy, 200, shots);
            if (enemy.getY() < GROUND - EntityType.SENTINEL.height - 4) {
                climbed = true;
                assertEquals(0, enemy.getTelegraphTicks());
                assertTrue(shots.isEmpty(), "No shot can use a stale origin while the body is climbing");
            }
            if (climbed && enemy.getX() + EntityType.SENTINEL.width < wall.x && !shots.isEmpty()) { resumed = true; break; }
            if (!climbed) shots.clear();
        }
        assertTrue(climbed); assertTrue(resumed);
    }

    @Test void adjacentWallsHaveAClearLandingAndAFastChargeCannotTunnelThroughThem() {
        List<Rectangle> walls = List.of(new Rectangle(390, 355, 95, 125), new Rectangle(490, 305, 100, 175));
        var manager = new ObstacleManager(46); manager.setSolids(walls);
        manager.spawnEnemy(670, GROUND - EntityType.BUG.height, EntityType.BUG);
        var enemy = manager.getEnemies().get(0);
        double before = enemy.getX();
        enemy.update(70, 130, 440, 0, 960, GROUND);
        manager.resolveSolidMotion(enemy, before);
        assertTrue(enemy.getX() >= 590, "A large step must collide with the first wall before navigation begins");
        boolean crossed = false;
        for (int tick = 0; tick < 600; tick++) {
            before = enemy.getX(); double beforeY = enemy.getY();
            advance(manager, enemy, 130, new ArrayList<>());
            for (Rectangle wall : walls) assertFalse(enemy.getBounds().intersects(wall));
            assertTrue(Math.hypot(enemy.getX() - before, enemy.getY() - beforeY) <= 4.1);
            if (enemy.getX() + EntityType.BUG.width < 380 && Math.abs(enemy.getY() + EntityType.BUG.height - GROUND) < 2) {
                crossed = true; break;
            }
        }
        assertTrue(crossed);
    }

    @Test void repeatedKnockbackCannotEmbedATroopInTheWallItWillClimb() {
        Rectangle wall = new Rectangle(400, 320, 140, 160);
        var manager = new ObstacleManager(47); manager.setSolids(List.of(wall));
        manager.spawnEnemy(550, GROUND - EntityType.TECHDEBT.height, EntityType.TECHDEBT);
        var enemy = manager.getEnemies().get(0);
        for (int i = 0; i < 10; i++) {
            enemy.takeHit(1, 24, -1);
            assertFalse(enemy.getBounds().intersects(wall));
        }
        boolean crossed = false;
        for (int i = 0; i < 400; i++) {
            advance(manager, enemy, 160, new ArrayList<>());
            assertFalse(enemy.getBounds().intersects(wall));
            if (enemy.getX() + EntityType.TECHDEBT.width < wall.x) { crossed = true; break; }
        }
        assertTrue(crossed);
    }

    @Test void heavyTroopsApproachThenClimbBesideTheWallInsteadOfFloatingDiagonallyFromFarAway() {
        Rectangle wall = new Rectangle(400, 280, 210, 162);
        var manager = new ObstacleManager(48); manager.setSolids(List.of(wall));
        manager.spawnEnemy(760, GROUND - EntityType.TECHDEBT.height, EntityType.TECHDEBT);
        var enemy = manager.getEnemies().get(0);
        boolean climbedUp = false, climbedDown = false, crossed = false;
        for (int i = 0; i < 600; i++) {
            double previousX = enemy.getX(), previousY = enemy.getY();
            advance(manager, enemy, 180, new ArrayList<>());
            if (enemy.getTerrainClimbDirection() != 0) {
                assertEquals(previousX, enemy.getX(), .001, "Climbing is vertical beside a physical wall face");
                boolean nearRight = Math.abs(enemy.getX() - wall.getMaxX() - 3) < .01;
                boolean nearLeft = Math.abs(enemy.getX() + EntityType.TECHDEBT.width + 3 - wall.x) < .01;
                assertTrue(nearRight || nearLeft, "No ascent in open air away from a wall");
                assertEquals(nearRight ? -1 : 1, enemy.getTerrainClimbFacing());
                climbedUp |= enemy.getY() < previousY;
                climbedDown |= enemy.getY() > previousY;
            }
            if (enemy.getX() + EntityType.TECHDEBT.width < wall.x - 10 && Math.abs(enemy.getY() + EntityType.TECHDEBT.height - GROUND) < 2) {
                crossed = true; break;
            }
        }
        assertTrue(climbedUp); assertTrue(climbedDown); assertTrue(crossed);
    }

    private static void advance(ObstacleManager manager, ObstacleManager.Enemy enemy, double playerX, List<Projectile> shots) {
        double x = enemy.getX();
        enemy.update(1, playerX, 440, (int)Math.min(playerX - 150, x - 100), (int)Math.max(playerX + 750, x + 200), GROUND);
        manager.resolveSolidMotion(enemy, x);
        enemy.maybeShoot(440, shots);
    }
}
