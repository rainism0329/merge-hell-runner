package com.bigphil.mergehell.world;

import com.bigphil.mergehell.model.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.bigphil.mergehell.world.KernelCoreController.*;
import static org.junit.jupiter.api.Assertions.*;

class KernelCoreControllerTest {
    @Test void equalSeedsProduceEqualCyclesAndEventsWithoutSharedRandomState() {
        var first = standard(42); var second = standard(42); var unrelated = standard(81);
        for (int step = 0; step < 2400; step++) {
            Input input = step < 1200 ? route(880, 0) : boss(280, 0, 730, false);
            first.update(input); unrelated.update(route(2900, 2400)); second.update(input);
            if (step == 300) {
                first.update(route(720, 0)); second.update(route(720, 0));
                assertEquals(first.interact(), second.interact());
            }
            assertEquals(first.snapshot().rails(), second.snapshot().rails());
            assertEquals(stationValues(first), stationValues(second));
            assertEquals(first.snapshot().story(), second.snapshot().story());
            assertEquals(first.snapshot().objective(), second.snapshot().objective());
            assertEquals(first.drainEvents(), second.drainEvents());
        }
    }

    @Test void railPublishesTheWholeWarningBeforeItsFirstDamageAndLimitsRepeatedDamage() {
        var controller = standard(3);
        awaitWarning(controller, route(900, 0));
        RailView warning = controller.snapshot().rails().get(0);
        assertEquals(RAIL_WARNING_TICKS, warning.ticksRemaining());
        assertEquals(new Bounds(802, 464, 300, 16), warning.bounds());
        controller.drainEvents();
        advance(controller, route(900, 0), RAIL_WARNING_TICKS - 1);
        assertEquals(RailPhase.WARNING, controller.snapshot().rails().get(0).phase());
        assertTrue(controller.drainEvents().stream().noneMatch(DamagePlayer.class::isInstance));
        controller.update(route(900, 0));
        assertEquals(RailPhase.ACTIVE, controller.snapshot().rails().get(0).phase());
        var damage = controller.drainEvents().stream().filter(DamagePlayer.class::isInstance)
                .map(DamagePlayer.class::cast).toList();
        assertEquals(List.of(new DamagePlayer(8, warning.id())), damage);
        advance(controller, route(900, 0), 59);
        assertTrue(controller.drainEvents().stream().noneMatch(DamagePlayer.class::isInstance));
        controller.update(route(900, 0));
        assertEquals(1, controller.drainEvents().stream().filter(DamagePlayer.class::isInstance).count());
        advance(controller, route(900, 0), RAIL_ACTIVE_TICKS - 60);
        assertEquals(RailPhase.SAFE, controller.snapshot().rails().get(0).phase());
    }

    @Test void entireGroundSwitchReachIsSafeAndAirbornePlayersCannotOperateIt() {
        for (int offset : new int[] {-58, 58}) {
            var controller = standard(4);
            awaitWarning(controller, route(720 + offset, 0)); controller.drainEvents();
            advance(controller, route(720 + offset, 0), RAIL_WARNING_TICKS + RAIL_ACTIVE_TICKS);
            assertTrue(controller.drainEvents().stream().noneMatch(DamagePlayer.class::isInstance));
            assertTrue(controller.interact());
            assertEquals(NodeState.DISABLED, station(controller, 1).state());
        }
        var airborne = standard(4);
        airborne.update(new Input(new Bounds(705, 350, 30, 30), 0, 960, 480, true, false, null, 0, false));
        assertFalse(airborne.interact());
        airborne.update(route(779, 0)); assertFalse(airborne.interact());
    }

    @Test void actualPlayerCanJumpOntoTheSafePlatformAndRemainAboveAnActiveRail() {
        var controller = standard(5);
        controller.update(route(720, 0));
        var deck = station(controller, 1).safetyPlatform();
        var player = new Player(780, 450);
        move(player, controller, false, false);
        for (int step = 0; step < 65; step++) {
            move(player, controller, step < 27, step == 0);
            controller.update(playerInput(player));
        }
        assertTrue(player.isGrounded());
        assertEquals(deck.y, player.getY() + 30, 0.001);
        controller.drainEvents();
        boolean activeSeen = false;
        for (int step = 0; step < 650; step++) {
            move(player, controller, false, false); controller.update(playerInput(player));
            activeSeen |= controller.snapshot().rails().get(0).phase() == RailPhase.ACTIVE;
            assertEquals(deck.y, player.getY() + 30, 0.001);
            assertTrue(controller.drainEvents().stream().noneMatch(DamagePlayer.class::isInstance));
        }
        assertTrue(activeSeen);
        for (int step = 0; step < 95; step++) move(player, controller, true, false);
        assertTrue(player.isGrounded()); assertEquals(480, player.getY() + 30, 0.001);
    }

    @Test void EDisablesImmediatelyForItsFullDurationAndCannotFarmRouteRewards() {
        var controller = standard(6);
        awaitWarning(controller, route(720, 0));
        advance(controller, route(720, 0), RAIL_WARNING_TICKS);
        assertEquals(RailPhase.ACTIVE, controller.snapshot().rails().get(0).phase());
        controller.drainEvents();
        assertTrue(controller.interact()); assertFalse(controller.interact());
        assertEquals(STATION_DISABLED_TICKS, station(controller, 1).ticksRemaining());
        assertEquals(RailPhase.SAFE, controller.snapshot().rails().get(0).phase());
        assertEquals(List.of(new StationDisabled(1, 15, 250)), controller.drainEvents().stream()
                .filter(StationDisabled.class::isInstance).toList());
        advance(controller, route(900, 0), STATION_DISABLED_TICKS - 1);
        assertEquals(NodeState.DISABLED, station(controller, 1).state());
        assertTrue(controller.drainEvents().stream().noneMatch(DamagePlayer.class::isInstance));
        controller.update(route(720, 0));
        assertEquals(NodeState.READY, station(controller, 1).state());
        assertTrue(controller.interact());
        assertTrue(controller.drainEvents().stream().noneMatch(StationDisabled.class::isInstance));
        assertEquals(3, controller.platforms().size(), "Safety platforms are fixed, including during a power cut");
    }

    @Test void leavingAndReenteringAVisibleRailNeverResumesAnUnseenActivePhase() {
        var controller = standard(8);
        awaitWarning(controller, route(900, 0));
        advance(controller, route(900, 0), RAIL_WARNING_TICKS);
        assertEquals(RailPhase.ACTIVE, controller.snapshot().rails().get(0).phase());
        controller.update(route(3000, 2400));
        controller.update(route(900, 0)); controller.drainEvents();
        assertEquals(RailPhase.SAFE, controller.snapshot().rails().get(0).phase());
        awaitWarning(controller, route(900, 0));
        assertEquals(RAIL_WARNING_TICKS, controller.snapshot().rails().get(0).ticksRemaining());
        assertTrue(controller.drainEvents().stream().noneMatch(DamagePlayer.class::isInstance));
    }

    @Test void bossFaultRequiresArmingAndAnActualDashRatherThanNormalContact() {
        var controller = standard(9);
        controller.update(boss(280, 0, 260, true));
        assertTrue(controller.drainEvents().stream().noneMatch(Discharge.class::isInstance));
        controller.update(boss(280, 0, 260, false)); assertTrue(controller.interact());
        advance(controller, boss(280, 0, 260, false), 100);
        assertEquals(NodeState.ARMED, station(controller, 101).state());
        assertTrue(controller.drainEvents().stream().noneMatch(Discharge.class::isInstance));
        controller.update(boss(280, 0, 260, true));
        var event = controller.drainEvents().stream().filter(Discharge.class::isInstance)
                .map(Discharge.class::cast).findFirst().orElseThrow();
        assertEquals(101, event.id()); assertEquals(BOSS_EXPOSE_TICKS, event.bossExposeTicks());
        assertEquals(new Bounds(0, 0, 960, 480), event.clearedArea());
        assertEquals(NodeState.COOLDOWN, station(controller, 101).state());
        assertEquals(NODE_COOLDOWN_TICKS, station(controller, 101).ticksRemaining());
        assertEquals(BOSS_EXPOSE_TICKS, controller.snapshot().bossExposeTicks());
        assertTrue(controller.snapshot().rails().stream().allMatch(rail -> rail.phase() == RailPhase.SAFE));
    }

    @Test void fastDashSweepsThinNodesAndChoosesTheFirstCollisionAlongItsPath() {
        var controller = standard(10);
        controller.update(boss(280, 0, 800, false)); assertTrue(controller.interact());
        controller.update(boss(580, 0, 800, false)); assertTrue(controller.interact());
        controller.update(boss(580, 0, 800, true)); controller.drainEvents();
        controller.update(boss(580, 0, 60, true));
        var events = controller.drainEvents().stream().filter(Discharge.class::isInstance).map(Discharge.class::cast).toList();
        assertEquals(1, events.size()); assertEquals(102, events.get(0).id());
        assertEquals(NodeState.ARMED, station(controller, 101).state());
        assertEquals(NodeState.COOLDOWN, station(controller, 102).state());
        controller.update(boss(580, 0, 300, true));
        assertTrue(controller.drainEvents().stream().noneMatch(Discharge.class::isInstance), "A live core window cannot stack additional discharges");
    }

    @Test void dashAboveNodesAndOrdinaryRepositionDoNotCreateFalseDischarges() {
        var controller = standard(11);
        controller.update(boss(280, 0, 800, false)); controller.interact();
        controller.update(new Input(new Bounds(265, 450, 30, 30), 0, 960, 480, true, true,
                new Bounds(800, 160, 120, 150), 1, false));
        controller.update(new Input(new Bounds(265, 450, 30, 30), 0, 960, 480, true, true,
                new Bounds(0, 160, 120, 150), 1, true));
        controller.update(boss(280, 0, 800, false));
        controller.update(boss(280, 0, 0, false));
        controller.update(boss(280, 0, 0, true));
        assertTrue(controller.drainEvents().stream().noneMatch(Discharge.class::isInstance));
        assertEquals(NodeState.ARMED, station(controller, 101).state());
    }

    @Test void unusedArmingExpiresAndSpentNodesRearmAfterExactCooldownWithoutRewards() {
        var controller = standard(12);
        controller.update(boss(280, 0, 730, false)); assertTrue(controller.interact());
        assertFalse(controller.interact());
        advance(controller, boss(280, 0, 730, false), NODE_ARMED_TICKS - 1);
        assertEquals(NodeState.ARMED, station(controller, 101).state());
        controller.update(boss(280, 0, 730, false));
        assertEquals(NodeState.READY, station(controller, 101).state());
        assertTrue(controller.interact());
        controller.update(boss(280, 0, 100, true));
        assertEquals(NodeState.COOLDOWN, station(controller, 101).state());
        advance(controller, boss(280, 0, 730, false), NODE_COOLDOWN_TICKS - 1);
        assertEquals(1, station(controller, 101).ticksRemaining()); assertFalse(controller.interact());
        controller.update(boss(280, 0, 730, false));
        assertEquals(NodeState.READY, station(controller, 101).state()); assertTrue(controller.interact());
        assertTrue(controller.drainEvents().stream().noneMatch(StationDisabled.class::isInstance));
    }

    @Test void pausedInputFreezesTimersAndCannotSweepAcrossAnUnsimulatedTeleport() {
        var controller = standard(13);
        controller.update(boss(280, 0, 730, false)); controller.interact();
        controller.drainEvents();
        Snapshot frozen = controller.snapshot(); var platforms = controller.platforms();
        Input pause = new Input(new Bounds(265, 450, 30, 30), 0, 960, 480, false, true,
                new Bounds(0, 330, 120, 150), 1, true);
        for (int step = 0; step < 800; step++) {
            controller.update(pause);
            assertSame(frozen, controller.snapshot()); assertSame(platforms, controller.platforms());
            assertFalse(controller.interact()); assertTrue(controller.drainEvents().isEmpty());
        }
        controller.update(boss(280, 0, 0, true));
        assertEquals(frozen.tick() + 1, controller.snapshot().tick());
        assertEquals(NODE_ARMED_TICKS - 1, station(controller, 101).ticksRemaining());
        assertTrue(controller.drainEvents().stream().noneMatch(Discharge.class::isInstance));
    }

    @Test void respawnClearsFaultsAndElectricDangerButPreservesRewardAndCooldownState() {
        var controller = standard(14);
        controller.update(route(720, 0)); controller.interact();
        controller.resetTransient();
        assertEquals(NodeState.DISABLED, station(controller, 1).state());
        assertEquals(1, controller.drainEvents().stream().filter(StationDisabled.class::isInstance).count());
        controller.update(boss(280, 0, 730, false)); controller.interact();
        controller.resetTransient();
        assertEquals(NodeState.READY, station(controller, 101).state());
        assertTrue(controller.interact());
        controller.update(boss(280, 0, 730, true));
        controller.update(boss(280, 0, 100, true));
        int cooldown = station(controller, 101).ticksRemaining();
        controller.resetTransient();
        assertEquals(NodeState.COOLDOWN, station(controller, 101).state());
        assertEquals(cooldown, station(controller, 101).ticksRemaining());
        assertEquals(0, controller.snapshot().bossExposeTicks());
        assertTrue(controller.drainEvents().stream().noneMatch(event -> event instanceof DamagePlayer || event instanceof Discharge));
        for (int step = 0; step < RESPAWN_SAFE_TICKS; step++) {
            controller.update(boss(800, 0, 730, false));
            assertTrue(controller.snapshot().rails().stream().allMatch(rail -> rail.phase() == RailPhase.SAFE));
            assertTrue(controller.drainEvents().stream().noneMatch(DamagePlayer.class::isInstance));
        }
    }

    @Test void bossArenaNodesAnchorOnceAndEndClearsEveryWorldMechanism() {
        var controller = standard(15);
        controller.update(boss(7680, 7400, 8130, false));
        assertEquals(7680, station(controller, 101).bounds().centerX());
        assertEquals(7980, station(controller, 102).bounds().centerX());
        controller.interact(); controller.update(boss(7680, 7410, 8130, false));
        assertEquals(7680, station(controller, 101).bounds().centerX());
        controller.update(route(7680, 7400));
        assertFalse(controller.snapshot().bossActive());
        assertEquals("kernel.objective.complete", controller.snapshot().objective());
        assertTrue(controller.snapshot().stations().isEmpty()); assertTrue(controller.snapshot().rails().isEmpty());
        assertTrue(controller.platforms().isEmpty()); assertFalse(controller.interact());
        controller.drainEvents(); advance(controller, route(7680, 7400), 900);
        assertTrue(controller.drainEvents().isEmpty());
    }

    @Test void coreWindowPausesRailsAndReturnsThroughAFullWarning() {
        var controller = standard(16);
        controller.update(boss(280, 0, 730, false)); controller.interact();
        controller.update(boss(280, 0, 100, true)); controller.drainEvents();
        for (int step = 0; step < BOSS_EXPOSE_TICKS; step++) {
            controller.update(boss(800, 0, 730, false));
            assertTrue(controller.snapshot().rails().stream().allMatch(rail -> rail.phase() == RailPhase.SAFE));
            assertTrue(controller.drainEvents().stream().noneMatch(DamagePlayer.class::isInstance));
        }
        for (int step = 0; step < 700; step++) {
            controller.update(boss(800, 0, 730, false));
            var warning = controller.snapshot().rails().stream().filter(rail -> rail.phase() == RailPhase.WARNING).findFirst();
            if (warning.isPresent()) { assertEquals(RAIL_WARNING_TICKS, warning.get().ticksRemaining()); return; }
        }
        fail("Unspent arena rail should resume with a complete warning");
    }

    @Test void eventsEntitiesAndStoryAnnouncementsAreBoundedWithoutDroppingFirstUseRewards() {
        var controller = standard(17);
        for (int center : new int[] {720, 2760, 4780}) { controller.update(route(center, 0)); assertTrue(controller.interact()); }
        for (int step = 0; step < 15_000; step++) {
            controller.update(boss(step % 800 < 3 ? 280 : 800, 0, step % 800 == 2 ? 100 : 730, step % 800 == 2));
            if (step % 800 == 0) controller.interact();
            assertEquals(2, controller.snapshot().stations().size());
            assertTrue(controller.snapshot().rails().size() <= MAX_RAILS);
            assertEquals(2, controller.platforms().size());
        }
        var events = controller.drainEvents();
        assertTrue(events.size() <= MAX_EVENTS);
        assertEquals(3, events.stream().filter(StationDisabled.class::isInstance).count());
        var storyIds = events.stream().filter(Story.class::isInstance).map(Story.class::cast).map(Story::id).toList();
        assertEquals(storyIds.size(), storyIds.stream().distinct().count());
        assertThrows(UnsupportedOperationException.class, () -> controller.snapshot().stations().clear());
        assertThrows(UnsupportedOperationException.class, () -> controller.snapshot().rails().clear());
        assertThrows(UnsupportedOperationException.class, () -> controller.platforms().clear());
    }

    @Test void invalidInputFailsBeforeCreatingAWorldWithInvalidGeometry() {
        assertThrows(IllegalArgumentException.class, () -> new Bounds(Double.NaN, 10, 30, 30));
        assertThrows(IllegalArgumentException.class, () -> new Bounds(10, 10, 0, 30));
        assertThrows(IllegalArgumentException.class, () -> new Input(new Bounds(0, 0, 30, 30), -1, 960, 480, true, false, null, 0, false));
        assertThrows(IllegalArgumentException.class, () -> new Input(new Bounds(0, 0, 30, 30), 0, 960, 480, true, true, null, 1, false));
        assertThrows(IllegalArgumentException.class, () -> new Input(new Bounds(0, 0, 30, 30), 0, 960, 480, true, false, null, 0, true));
    }

    private static KernelCoreController standard(long seed) { return KernelCoreController.standard(seed); }
    private static Input route(double centerX, double camera) {
        return new Input(new Bounds(centerX - 15, 450, 30, 30), camera, 960, 480, true, false, null, 0, false);
    }
    private static Input boss(double centerX, double camera, double bossX, boolean dashing) {
        return new Input(new Bounds(centerX - 15, 450, 30, 30), camera, 960, 480, true, true,
                new Bounds(bossX, 330, 120, 150), 1, dashing);
    }
    private static Input playerInput(Player player) {
        return new Input(new Bounds(player.getX(), player.getY(), 30, 30), 0, 960, 480, true, false, null, 0, false);
    }
    private static void advance(KernelCoreController controller, Input input, int steps) {
        for (int step = 0; step < steps; step++) controller.update(input);
    }
    private static void awaitWarning(KernelCoreController controller, Input input) {
        for (int step = 0; step < 900; step++) {
            controller.update(input);
            if (controller.snapshot().rails().stream().anyMatch(rail -> rail.phase() == RailPhase.WARNING)) return;
        }
        fail("Visible rail did not begin its warning");
    }
    private static StationView station(KernelCoreController controller, int id) {
        return controller.snapshot().stations().stream().filter(station -> station.id() == id).findFirst().orElseThrow();
    }
    private static void move(Player player, KernelCoreController controller, boolean right, boolean jump) {
        player.update(false, right, jump, false, 480, 10000, new ArrayList<>(), controller.platforms());
    }
    private static List<String> stationValues(KernelCoreController controller) {
        return controller.snapshot().stations().stream().map(station -> station.id() + ":" + station.bounds() + ":"
                + station.state() + ":" + station.ticksRemaining() + ":" + station.inReach() + ":"
                + station.safetyPlatform().x + ":" + station.safetyPlatform().y).toList();
    }
}
