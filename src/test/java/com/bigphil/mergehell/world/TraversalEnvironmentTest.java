package com.bigphil.mergehell.world;

import com.bigphil.mergehell.model.Platform;
import com.bigphil.mergehell.model.Player;
import com.bigphil.mergehell.model.Projectile;
import com.bigphil.mergehell.model.ProjectileType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.bigphil.mergehell.world.TraversalEnvironment.*;
import static org.junit.jupiter.api.Assertions.*;

class TraversalEnvironmentTest {
    private static final int GROUND = 480;

    @Test void finalWorldSharedStairsLeaveTheAuthoredEchoesAndSwitchesClear() {
        for (int seed = 0; seed < 12; seed++) {
            var environment = environment(4, seed);
            var finalWorld = SingularityEdgeController.standard(seed);
            for (int x : new int[]{900, 3000, 5000}) {
                double camera = x - 260;
                environment.prepare(camera, 960, false);
                finalWorld.update(new SingularityEdgeController.Input(
                        new SingularityEdgeController.Bounds(x - 15, 450, 30, 30), camera, 960, GROUND, true, false, 1));
                var anchor = finalWorld.snapshot().anchors().stream().filter(node -> node.bounds().centerX() == x).findFirst().orElseThrow();
                assertTrue(environment.snapshot().props().stream().noneMatch(prop -> prop.bounds().intersects(anchor.bounds().rectangle())));
                assertTrue(environment.platforms().stream().noneMatch(platform -> platform.x < x + 400
                        && platform.x + platform.width > x - 80));
            }
        }
    }

    @Test void sharedStairsAndPropsDoNotCoverKernelSwitchesOrBridgeTheirLiveRails() {
        for (int seed = 0; seed < 12; seed++) {
            var environment = environment(3, seed);
            var kernel = KernelCoreController.standard(seed);
            for (int x : new int[]{720, 2760, 4780}) {
                double camera = x - 260;
                environment.prepare(camera, 960, false);
                kernel.update(new KernelCoreController.Input(new KernelCoreController.Bounds(x - 15, 450, 30, 30),
                        camera, 960, GROUND, true, false, null, 1, false));
                var station = kernel.snapshot().stations().stream().filter(node -> node.bounds().centerX() == x).findFirst().orElseThrow();
                assertTrue(environment.snapshot().props().stream().noneMatch(prop -> prop.bounds().intersects(station.bounds().rectangle())));
                var rail = kernel.snapshot().rails().stream().filter(track -> track.stationId() == station.id()).findFirst().orElseThrow();
                assertTrue(environment.platforms().stream().noneMatch(platform -> platform.x < rail.bounds().x() + rail.bounds().width()
                        && platform.x + platform.width > rail.bounds().x()));
            }
        }
    }

    @Test void routeAndWaterEffectsAreDeterministicAndDoNotShareRandomState() {
        var first = environment(0, 6174);
        var second = environment(0, 6174);
        var unrelated = environment(3, 123);
        for (int i = 0; i < 300; i++) {
            Step input = step(i % 2 == 0 ? 200 : 900, GROUND,
                    i % 2 == 0 ? 900 : 200, GROUND, true, true, true, false, 0);
            first.update(input);
            unrelated.update(input);
            second.update(input);
            assertEquals(first.snapshot().water(), second.snapshot().water());
            assertEquals(first.snapshot().props(), second.snapshot().props());
            assertEquals(platformGeometry(first), platformGeometry(second));
            assertEquals(first.snapshot().ripples(), second.snapshot().ripples());
            assertEquals(first.snapshot().droplets(), second.snapshot().droplets());
            assertEquals(first.snapshot().tick(), second.snapshot().tick());
        }
        var otherSeed = environment(0, 111);
        otherSeed.prepare(0, 960, false);
        assertNotEquals(first.snapshot().water(), otherSeed.snapshot().water());
    }

    @Test void firstRouteLeavesSpawnDryAndProvidesPhysicalLowAndUpperPlatforms() {
        var environment = environment(0, 10);
        environment.prepare(0, 960, false);
        assertEquals(330, environment.snapshot().water().get(0).x());
        assertTrue(environment.snapshot().water().stream().allMatch(water -> water.x() > 300));
        assertTrue(environment.platforms().stream().allMatch(platform -> platform.x >= 800));
        assertTrue(environment.platforms().stream().anyMatch(platform -> platform.y == GROUND - 24));
        assertTrue(environment.platforms().stream().anyMatch(platform -> platform.y == GROUND - 48));
        Platform high = environment.platforms().stream().filter(platform -> platform.y == GROUND - 80)
                .findFirst().orElseThrow();
        var player = new Player((int) high.x + 30, (int) high.y - 120);
        for (int tick = 0; tick < 90; tick++)
            player.update(false, false, false, false, GROUND, 10_000, new ArrayList<>(), environment.platforms());
        assertTrue(player.isGrounded());
        assertEquals(high.y, player.getY() + 30, 0.001, "The rendered upper route is also real landing geometry");
        var floorPlayer = new Player(100, GROUND - 30);
        for (int tick = 0; tick < 350; tick++)
            floorPlayer.update(false, true, false, false, GROUND, 10_000, new ArrayList<>(), environment.platforms());
        assertTrue(floorPlayer.getX() > 1700, "The continuous lower route is traversable without jumping");
        assertTrue(floorPlayer.getY() + 30 <= GROUND);
    }

    @Test void heapRoutesPreserveGroundAccessToEveryAuthoredGcStation() {
        var environment = environment(1, 10);
        environment.prepare(0, 6000, false);
        for (int stationX : new int[]{760, 2600, 4650})
            assertTrue(environment.platforms().stream().noneMatch(platform ->
                            platform.x < stationX + 120 && platform.x + platform.width > stationX - 120),
                    "Station " + stationX + " must retain its ground approach");
    }

    @Test void longRunsAndBacktrackingKeepGeometryBoundedWithoutRegrowingRewards() {
        var environment = environment(0, 8);
        environment.update(standing(100));
        PropView target = environment.snapshot().props().get(0);
        assertTrue(environment.hit(shotAt(target)).isPresent());
        for (int index = 0; index < 1000; index++) {
            environment.prepare(index * 3000.0, 8192, false);
            assertTrue(environment.chunkCount() <= MAX_CHUNKS);
            assertTrue(environment.snapshot().water().size() <= 2 * MAX_CHUNKS);
            assertTrue(environment.platforms().size() <= 5 * MAX_CHUNKS);
            assertTrue(environment.snapshot().props().size() <= 2 * MAX_CHUNKS);
        }
        environment.prepare(0, 960, false);
        assertEquals(330, environment.snapshot().water().get(0).x(), "Backtracking still restores scenery");
        assertFalse(environment.platforms().isEmpty());
        assertTrue(environment.snapshot().props().isEmpty(), "Retired route rewards cannot be farmed by backtracking");
        assertTrue(environment.hit(shotAt(target)).isEmpty());
    }

    @Test void pausedUpdatesFreezeSnapshotAndRefuseInteractiveHits() {
        var environment = environment(0, 2);
        environment.update(standing(350));
        assertFalse(environment.snapshot().ripples().isEmpty());
        Snapshot frozen = environment.snapshot();
        var shot = shotAt(frozen.props().get(0));
        for (int tick = 0; tick < 500; tick++) {
            environment.update(step(350, GROUND, 950_000, GROUND, true, true, false, true, 949_000));
            assertSame(frozen, environment.snapshot());
            assertTrue(environment.hit(shot).isEmpty());
        }
        environment.update(standing(350));
        assertEquals(frozen.tick() + 1, environment.snapshot().tick());
        assertTrue(environment.hit(shot).isPresent());
    }

    @Test void footstepsNeedActualMovementAndLandingMakesAStrongerSplash() {
        var walking = environment(0, 5);
        walking.update(standing(350));
        assertEquals(1, walking.snapshot().ripples().size());
        for (int i = 0; i < 8; i++) walking.update(standing(350));
        assertEquals(1, walking.snapshot().ripples().size(), "Standing in water does not emit footsteps");
        for (int i = 0; i < 7; i++)
            walking.update(step(350 + i * 5, GROUND, 355 + i * 5, GROUND, true, false, true, false, 0));
        assertEquals(2, walking.snapshot().ripples().size());
        var landing = environment(0, 5);
        landing.update(step(350, 440, 350, GROUND, true, false, true, false, 0));
        assertEquals(1, landing.snapshot().ripples().size());
        assertTrue(landing.snapshot().ripples().get(0).strength() > walking.snapshot().ripples().get(0).strength());
        assertTrue(landing.snapshot().droplets().size() > walking.snapshot().droplets().size() / 2);
    }

    @Test void airborneAndPlatformFootprintsCannotTouchWaterBelowThem() {
        var airborne = environment(0, 5);
        airborne.update(step(200, 420, 900, 420, false, true, true, false, 0));
        assertTrue(airborne.snapshot().ripples().isEmpty());
        assertTrue(airborne.snapshot().droplets().isEmpty());
        airborne.update(step(350, 456, 420, 456, true, false, true, false, 0));
        assertTrue(airborne.snapshot().ripples().isEmpty(), "Grounded on an elevated surface is not grounded in water");
        airborne.update(step(350, GROUND, 370, GROUND, false, true, true, false, 0));
        assertTrue(airborne.snapshot().ripples().isEmpty(), "Jump initiation must not be mistaken for a landing");
    }

    @Test void aFastGroundDashCannotSkipAWholeWaterStripBetweenFrames() {
        var environment = environment(0, 40);
        environment.update(step(200, GROUND, 900, GROUND, true, true, true, false, 0));
        assertEquals(1, environment.snapshot().ripples().size());
        var water = environment.snapshot().water().get(0);
        var ripple = environment.snapshot().ripples().get(0);
        assertTrue(ripple.x() >= water.x() && ripple.x() <= water.x() + water.width());
        assertTrue(ripple.strength() > 1.5);
        assertFalse(environment.snapshot().droplets().isEmpty());
    }

    @Test void waterEffectsHaveHardBudgetsAndFullyExpireWithoutWallClockWork() {
        var environment = environment(0, 45);
        for (int tick = 0; tick < 1500; tick++) {
            environment.update(step(tick % 2 == 0 ? 200 : 900, GROUND,
                    tick % 2 == 0 ? 900 : 200, GROUND, true, true, true, false, 0));
            assertTrue(environment.snapshot().ripples().size() <= MAX_RIPPLES);
            assertTrue(environment.snapshot().droplets().size() <= MAX_DROPLETS);
            environment.snapshot().droplets().forEach(droplet -> {
                assertTrue(Double.isFinite(droplet.x()) && Double.isFinite(droplet.y()));
                assertTrue(droplet.alpha() > 0 && droplet.alpha() <= 1);
            });
        }
        for (int tick = 0; tick < 100; tick++) environment.update(standing(100));
        assertTrue(environment.snapshot().ripples().isEmpty());
        assertTrue(environment.snapshot().droplets().isEmpty());
    }

    @Test void propsUseSweptHitsOnceAndCallerRetainsProjectileOwnership() {
        var environment = environment(0, 9);
        environment.update(standing(100));
        var target = environment.snapshot().props().get(0);
        int initialCount = environment.snapshot().props().size();
        var shot = new Projectile(target.x() - 140, target.y() + 10, 350, 0, ProjectileType.COMMIT);
        shot.update();
        assertFalse(shot.getBounds().intersects(target.bounds()), "This is a real tunneling regression");
        var event = environment.hit(shot).orElseThrow();
        assertEquals(target.id(), event.id());
        assertEquals(PropKind.CAPACITOR, event.kind());
        assertEquals(150, event.radius());
        assertFalse(shot.isDead(), "Only the caller removes or consumes a projectile");
        shot.setDead(true);
        assertEquals(initialCount - 1, environment.snapshot().props().size());
        assertTrue(environment.hit(shot).isEmpty());
        assertTrue(environment.hit(shotAt(target)).isEmpty(), "A second shot cannot receive the same prop event");
    }

    @Test void nearerEnemyEntryPreventsShootingAPropThroughAnEnemy() {
        var environment = environment(0, 9);
        environment.update(standing(100));
        var target = environment.snapshot().props().get(0);
        var shot = new Projectile(target.x() - 300, target.y() + 8, 600, 0, ProjectileType.COMMIT);
        shot.update();
        double entry = shot.hitFraction(target.bounds());
        assertTrue(Double.isFinite(entry));
        var before = environment.snapshot();
        assertTrue(environment.hit(shot, entry - 0.01).isEmpty());
        assertSame(before, environment.snapshot());
        assertEquals(target.id(), environment.hit(shot, entry).orElseThrow().id());
    }

    @Test void oneSweepClaimsOnlyItsNearestPropAndSupplyIsADistinctEvent() {
        var environment = environment(0, 9);
        environment.update(standing(100));
        var before = environment.snapshot().props();
        var shot = new Projectile(100, GROUND - 20, 1850, 0, ProjectileType.COMMIT);
        shot.update();
        assertEquals(before.get(0).id(), environment.hit(shot).orElseThrow().id());
        assertTrue(environment.snapshot().props().contains(before.get(1)), "A single shot does not collect every crossed prop");
        shot.setDead(true);
        var supply = environment.hit(shotAt(before.get(1))).orElseThrow();
        assertEquals(PropKind.SUPPLY, supply.kind());
        assertEquals(0, supply.radius());
    }

    @Test void hostileShotsAndBossArenaCannotTriggerRouteRewards() {
        var environment = environment(0, 9);
        environment.update(standing(350));
        var target = environment.snapshot().props().get(0);
        var hostile = new Projectile(target.x(), target.y(), 0, 0, ProjectileType.ENEMY);
        assertTrue(environment.hit(hostile).isEmpty());
        assertFalse(environment.snapshot().ripples().isEmpty());
        environment.update(step(350, GROUND, 350, GROUND, true, false, true, true, 0));
        assertTrue(environment.snapshot().bossArena());
        assertTrue(environment.platforms().isEmpty());
        assertTrue(environment.snapshot().props().isEmpty());
        assertTrue(environment.snapshot().ripples().isEmpty());
        assertTrue(environment.snapshot().droplets().isEmpty());
        assertFalse(environment.snapshot().water().isEmpty(), "Water remains part of the arena's dressing");
        assertTrue(environment.hit(shotAt(target)).isEmpty());
        environment.update(standing(100));
        assertFalse(environment.platforms().isEmpty());
        assertTrue(environment.hit(shotAt(target)).isPresent());
    }

    @Test void snapshotsCannotBeMutatedByTheRendererOrRetroactivelyChangedByHits() {
        var environment = environment(0, 9);
        environment.update(standing(350));
        Snapshot before = environment.snapshot();
        assertThrows(UnsupportedOperationException.class, () -> before.props().clear());
        assertThrows(UnsupportedOperationException.class, () -> before.platforms().clear());
        assertThrows(UnsupportedOperationException.class, () -> before.water().clear());
        assertThrows(UnsupportedOperationException.class, () -> before.ripples().clear());
        assertThrows(UnsupportedOperationException.class, () -> before.droplets().clear());
        assertTrue(environment.hit(shotAt(before.props().get(0))).isPresent());
        assertEquals(before.props().size() - 1, environment.snapshot().props().size());
        assertTrue(before.props().contains(before.props().get(0)));
    }

    private static TraversalEnvironment environment(int level, long seed) {
        return new TraversalEnvironment(level, seed, GROUND);
    }

    private static Step standing(double x) {
        return step(x, GROUND, x, GROUND, true, false, true, false, 0);
    }

    private static Step step(double previousX, double previousBottom, double x, double bottom,
                             boolean onGround, boolean dashing, boolean active, boolean boss, double cameraX) {
        return new Step(previousX, previousBottom, x, bottom, 30, cameraX, 960, onGround, dashing, active, boss);
    }

    private static Projectile shotAt(PropView target) {
        return new Projectile(target.x(), target.y() + 8, 0, 0, ProjectileType.COMMIT);
    }

    private static List<String> platformGeometry(TraversalEnvironment environment) {
        return environment.platforms().stream().map(platform -> platform.x + ":" + platform.y + ":"
                + platform.width + ":" + platform.height + ":" + platform.style).toList();
    }
}
