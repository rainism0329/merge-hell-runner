package com.bigphil.mergehell.world;

import com.bigphil.mergehell.model.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.bigphil.mergehell.world.SingularityEdgeController.*;
import static org.junit.jupiter.api.Assertions.*;

class SingularityEdgeControllerTest {
    @Test void equalSeedsProduceTheSameIndependentHazardCyclesAndStories() {
        var first = standard(42); var second = standard(42); var unrelated = standard(91);
        for (int step = 0; step < 2600; step++) {
            Input next = step < 800 ? route(1100, 500) : step < 1600 ? route(3180, 2600) : boss(280, 0);
            first.update(next); unrelated.update(route(5200, 4500)); second.update(next);
            if (step == 1800) { assertEquals(first.interact(), second.interact()); }
            assertEquals(first.snapshot().hazards(), second.snapshot().hazards());
            assertEquals(anchorValues(first), anchorValues(second));
            assertEquals(first.snapshot().objective(), second.snapshot().objective());
            assertEquals(first.snapshot().story(), second.snapshot().story());
            assertEquals(first.drainEvents(), second.drainEvents());
        }
    }

    @Test void threeEchoesPublishTheirExactDamageShapesAndACompleteWarning() {
        double[] positions = { 1100, 3180, 5200 };
        Bounds[] expected = { new Bounds(982, 462, 260, 18), new Bounds(3160, 310, 46, 170), new Bounds(5082, 464, 260, 16) };
        for (int index = 0; index < positions.length; index++) {
            var controller = standard(3);
            Input next = route(positions[index], positions[index] - 500);
            awaitWarning(controller, next);
            HazardView warning = hazard(controller);
            assertEquals(Echo.values()[index], warning.echo());
            assertEquals(expected[index], warning.bounds());
            assertEquals(HAZARD_WARNING_TICKS, warning.ticksRemaining());
            controller.drainEvents();
            advance(controller, next, HAZARD_WARNING_TICKS - 1);
            assertEquals(HazardPhase.WARNING, hazard(controller).phase());
            assertTrue(controller.drainEvents().stream().noneMatch(DamagePlayer.class::isInstance));
            controller.update(next);
            assertEquals(HazardPhase.ACTIVE, hazard(controller).phase());
            assertEquals(HAZARD_ACTIVE_TICKS, hazard(controller).ticksRemaining());
            assertEquals(List.of(new DamagePlayer(8, warning.id())), damage(controller));
            advance(controller, next, 59);
            assertTrue(damage(controller).isEmpty());
            controller.update(next);
            assertEquals(List.of(new DamagePlayer(8, warning.id())), damage(controller));
            advance(controller, next, HAZARD_ACTIVE_TICKS - 60);
            assertEquals(HazardPhase.SAFE, hazard(controller).phase());
        }
    }

    @Test void damageUsesThePublishedRectangleIncludingTheTallScanner() {
        var controller = standard(7);
        Input scanHeight = new Input(new Bounds(3168, 320, 30, 30), 2600, 960, 480, true, false, 0);
        awaitWarning(controller, scanHeight); controller.drainEvents();
        advance(controller, scanHeight, HAZARD_WARNING_TICKS);
        assertEquals(1, damage(controller).size());
        controller.update(new Input(new Bounds(3168, 280, 30, 30), 2600, 960, 480, true, false, 0));
        advance(controller, new Input(new Bounds(3168, 280, 30, 30), 2600, 960, 480, true, false, 0), 60);
        assertTrue(damage(controller).isEmpty(), "Touching the top edge from above does not overlap the scan");
    }

    @Test void allGroundInteractionPositionsAreOutsideTheHazardAndAirborneECannotActivate() {
        for (int center : new int[] {900, 3000, 5000}) {
            for (int offset : new int[] {-58, 58}) {
                var controller = standard(4);
                Input next = route(center + offset, center - 500);
                awaitWarning(controller, next); controller.drainEvents();
                advance(controller, next, HAZARD_WARNING_TICKS + HAZARD_ACTIVE_TICKS);
                assertTrue(damage(controller).isEmpty());
                assertTrue(controller.interact());
                assertEquals(AnchorState.STABILIZED, anchor(controller, center == 900 ? 1 : center == 3000 ? 2 : 3).state());
            }
        }
        var airborne = standard(4);
        airborne.update(new Input(new Bounds(885, 350, 30, 30), 500, 960, 480, true, false, 0));
        assertFalse(airborne.interact());
        airborne.update(route(959, 500)); assertFalse(airborne.interact());
    }

    @Test void realPlayerCanReachAndWaitOnEachFixedSafeDeckDuringAnActiveEcho() {
        for (int center : new int[] {900, 3000, 5000}) {
            var controller = standard(5);
            controller.update(route(center, center - 500));
            var deck = anchor(controller, center == 900 ? 1 : center == 3000 ? 2 : 3).safetyPlatform();
            var player = new Player((int) deck.x - 80, 450);
            move(player, controller, false, false);
            for (int step = 0; step < 65; step++) {
                move(player, controller, step < 21, step == 0);
                controller.update(playerInput(player, center - 500));
            }
            assertTrue(player.isGrounded());
            assertEquals(deck.y, player.getY() + 30, 0.001);
            controller.drainEvents();
            boolean activeSeen = false;
            for (int step = 0; step < 650; step++) {
                move(player, controller, false, false);
                controller.update(playerInput(player, center - 500));
                activeSeen |= hazard(controller).phase() == HazardPhase.ACTIVE;
                assertEquals(deck.y, player.getY() + 30, 0.001);
                assertTrue(damage(controller).isEmpty());
            }
            assertTrue(activeSeen);
        }
    }

    @Test void routeStabilizationImmediatelyStopsTheEchoFor600TicksAndRewardsOnlyOnce() {
        var controller = standard(6);
        awaitWarning(controller, route(900, 500));
        advance(controller, route(900, 500), HAZARD_WARNING_TICKS);
        assertEquals(HazardPhase.ACTIVE, hazard(controller).phase());
        controller.drainEvents();
        assertTrue(controller.interact()); assertFalse(controller.interact());
        assertEquals(ANCHOR_STABILIZED_TICKS, anchor(controller, 1).ticksRemaining());
        assertTrue(controller.snapshot().hazards().isEmpty());
        assertEquals(List.of(new AnchorStabilized(1, 15, 250)), controller.drainEvents().stream()
                .filter(AnchorStabilized.class::isInstance).toList());
        advance(controller, route(1100, 500), ANCHOR_STABILIZED_TICKS - 1);
        assertEquals(AnchorState.STABILIZED, anchor(controller, 1).state());
        assertTrue(damage(controller).isEmpty());
        controller.update(route(900, 500));
        assertEquals(AnchorState.READY, anchor(controller, 1).state());
        assertEquals(HazardPhase.SAFE, hazard(controller).phase());
        assertTrue(controller.interact());
        assertTrue(controller.drainEvents().stream().noneMatch(AnchorStabilized.class::isInstance));
        assertEquals(3, controller.platforms().size());
    }

    @Test void onlyTheNearestVisibleUnsuppressedEchoCanRunEvenInAnOversizedViewport() {
        var controller = standard(8);
        Input wide = new Input(new Bounds(885, 450, 30, 30), 0, 8000, 480, true, false, 0);
        awaitWarning(controller, wide);
        assertEquals(1, controller.snapshot().hazards().size());
        assertEquals(1, hazard(controller).anchorId());
        assertTrue(controller.interact());
        controller.update(wide);
        assertEquals(1, controller.snapshot().hazards().size());
        assertEquals(2, hazard(controller).anchorId());
        assertEquals(HazardPhase.SAFE, hazard(controller).phase());
        controller.update(new Input(new Bounds(4985, 450, 30, 30), 0, 8000, 480, true, false, 0));
        assertEquals(3, hazard(controller).anchorId());
        assertEquals(HazardPhase.SAFE, hazard(controller).phase());
    }

    @Test void returningToAnOffscreenOrPreviouslyDeselectedEchoRequiresTheFullWarningAgain() {
        var controller = standard(9);
        awaitWarning(controller, route(1100, 500));
        advance(controller, route(1100, 500), HAZARD_WARNING_TICKS);
        assertEquals(HazardPhase.ACTIVE, hazard(controller).phase());
        controller.update(route(3180, 2600));
        controller.update(route(1100, 500)); controller.drainEvents();
        assertEquals(HazardPhase.SAFE, hazard(controller).phase());
        awaitWarning(controller, route(1100, 500));
        assertEquals(HAZARD_WARNING_TICKS, hazard(controller).ticksRemaining());
        assertTrue(damage(controller).isEmpty());
    }

    @Test void twoDifferentBossAnchorsCompleteOneResonanceAndShareAnExactCooldown() {
        for (boolean reverse : new boolean[] { false, true }) {
            var controller = standard(10);
            controller.update(boss(reverse ? 580 : 280, 0)); controller.drainEvents();
            assertTrue(controller.interact()); assertFalse(controller.interact());
            assertEquals(1, controller.snapshot().anchors().stream().filter(anchor -> anchor.state() == AnchorState.ARMED).count());
            assertEquals("singularity.objective.second", controller.snapshot().objective());
            assertTrue(controller.drainEvents().stream().noneMatch(Resonance.class::isInstance));
            advance(controller, boss(reverse ? 580 : 280, 0), LINK_ARMED_TICKS - 2);
            controller.update(boss(reverse ? 280 : 580, 0));
            assertTrue(controller.interact(), "The other anchor remains usable on the last armed tick");
            var resonance = controller.drainEvents().stream().filter(Resonance.class::isInstance).toList();
            assertEquals(List.of(new Resonance(new Bounds(0, 0, 960, 480), BOSS_EXPOSE_TICKS)), resonance);
            assertEquals(BOSS_EXPOSE_TICKS, controller.snapshot().bossExposeTicks());
            assertTrue(controller.snapshot().anchors().stream().allMatch(anchor -> anchor.state() == AnchorState.COOLDOWN
                    && anchor.ticksRemaining() == LINK_COOLDOWN_TICKS));
            assertEquals("singularity.objective.exposed", controller.snapshot().objective());
            assertFalse(controller.interact());
            advance(controller, boss(280, 0), LINK_COOLDOWN_TICKS - 1);
            assertEquals(1, anchor(controller, 101).ticksRemaining()); assertFalse(controller.interact());
            assertEquals("singularity.objective.cooldown", controller.snapshot().objective());
            controller.update(boss(280, 0));
            assertEquals(AnchorState.READY, anchor(controller, 101).state());
            assertEquals(AnchorState.READY, anchor(controller, 102).state());
            assertEquals("singularity.objective.link", controller.snapshot().objective());
            assertTrue(controller.interact());
            assertTrue(controller.drainEvents().stream().noneMatch(AnchorStabilized.class::isInstance));
        }
    }

    @Test void repeatedEOnTheSameAnchorCannotRefreshOrCompleteIts500TickWindow() {
        var controller = standard(11);
        controller.update(boss(280, 0)); assertTrue(controller.interact()); controller.drainEvents();
        for (int step = 0; step < LINK_ARMED_TICKS - 1; step++) {
            controller.update(boss(280, 0));
            assertFalse(controller.interact());
        }
        assertEquals(1, anchor(controller, 101).ticksRemaining());
        controller.update(boss(580, 0));
        assertEquals(AnchorState.READY, anchor(controller, 101).state());
        assertTrue(controller.interact(), "After expiry this starts a fresh link at the other anchor");
        assertEquals(AnchorState.ARMED, anchor(controller, 102).state());
        assertEquals(LINK_ARMED_TICKS, anchor(controller, 102).ticksRemaining());
        assertEquals(0, controller.snapshot().bossExposeTicks());
        assertTrue(controller.drainEvents().stream().noneMatch(Resonance.class::isInstance));
    }

    @Test void bossArenaContainsNoEnvironmentalHazardsAndNodesAnchorOnlyOnce() {
        var controller = standard(12);
        controller.update(boss(7680, 7400));
        assertEquals(7680, anchor(controller, 101).bounds().centerX());
        assertEquals(7980, anchor(controller, 102).bounds().centerX());
        assertTrue(controller.interact());
        controller.update(boss(7680, 7410));
        assertEquals(7680, anchor(controller, 101).bounds().centerX());
        for (int step = 0; step < 1500; step++) {
            controller.update(boss(7980, 7410));
            assertTrue(controller.snapshot().hazards().isEmpty());
            assertEquals(2, controller.platforms().size());
            assertTrue(damage(controller).isEmpty());
        }
    }

    @Test void pausingFreezesTheLinkTimerStoriesAndSnapshotAndBlocksInput() {
        var controller = standard(13);
        controller.update(boss(280, 0)); controller.interact(); controller.drainEvents();
        Snapshot frozen = controller.snapshot(); var platforms = controller.platforms();
        Input pause = new Input(new Bounds(565, 450, 30, 30), 0, 960, 480, false, true, 1);
        for (int step = 0; step < 800; step++) {
            controller.update(pause);
            assertSame(frozen, controller.snapshot()); assertSame(platforms, controller.platforms());
            assertFalse(controller.interact()); assertTrue(controller.drainEvents().isEmpty());
        }
        controller.update(boss(580, 0));
        assertEquals(frozen.tick() + 1, controller.snapshot().tick());
        assertEquals(LINK_ARMED_TICKS - 1, anchor(controller, 101).ticksRemaining());
        assertTrue(controller.interact());
        assertEquals(BOSS_EXPOSE_TICKS, controller.snapshot().bossExposeTicks());
    }

    @Test void respawnClearsDangerAndUnfinishedLinksButKeepsFirstUseRewardsAndCooldowns() {
        var controller = standard(14);
        controller.update(route(900, 500)); controller.interact();
        controller.resetTransient();
        assertEquals(AnchorState.STABILIZED, anchor(controller, 1).state());
        assertEquals(1, controller.drainEvents().stream().filter(AnchorStabilized.class::isInstance).count());
        controller.update(boss(280, 0)); controller.interact();
        controller.resetTransient();
        assertEquals(AnchorState.READY, anchor(controller, 101).state());
        assertTrue(controller.interact());
        controller.update(boss(580, 0)); assertTrue(controller.interact());
        controller.resetTransient();
        assertEquals(0, controller.snapshot().bossExposeTicks());
        assertEquals(AnchorState.COOLDOWN, anchor(controller, 101).state());
        assertEquals(LINK_COOLDOWN_TICKS, anchor(controller, 101).ticksRemaining());
        assertEquals(LINK_COOLDOWN_TICKS, anchor(controller, 102).ticksRemaining());
        assertTrue(controller.drainEvents().stream().noneMatch(event -> event instanceof DamagePlayer || event instanceof Resonance));
        var route = standard(14);
        awaitWarning(route, route(1100, 500));
        advance(route, route(1100, 500), HAZARD_WARNING_TICKS);
        route.resetTransient();
        assertTrue(route.snapshot().hazards().isEmpty());
        for (int step = 0; step < RESPAWN_SAFE_TICKS; step++) {
            route.update(route(1100, 500));
            assertEquals(HazardPhase.SAFE, hazard(route).phase());
            assertTrue(damage(route).isEmpty());
        }
        awaitWarning(route, route(1100, 500));
        assertEquals(HAZARD_WARNING_TICKS, hazard(route).ticksRemaining());
    }

    @Test void bossInstructionsReplaceRouteChatterAndDefeatClearsAllMechanisms() {
        var controller = standard(15);
        controller.update(route(900, 500)); controller.interact();
        controller.update(boss(280, 0));
        assertEquals("singularity.story.bossEntry", controller.snapshot().story());
        controller.interact();
        assertEquals("singularity.story.armed", controller.snapshot().story());
        controller.update(boss(580, 0)); controller.interact();
        assertEquals("singularity.story.resonance", controller.snapshot().story());
        controller.update(new Input(new Bounds(565, 450, 30, 30), 0, 960, 480, true, true, 2));
        assertEquals("singularity.story.bossStage2", controller.snapshot().story());
        controller.update(route(580, 0));
        assertEquals("singularity.objective.complete", controller.snapshot().objective());
        assertEquals("singularity.story.bossDefeated", controller.snapshot().story());
        assertFalse(controller.snapshot().bossActive());
        assertEquals(0, controller.snapshot().bossExposeTicks());
        assertTrue(controller.snapshot().anchors().isEmpty()); assertTrue(controller.snapshot().hazards().isEmpty());
        assertTrue(controller.platforms().isEmpty()); assertFalse(controller.interact());
        controller.drainEvents(); advance(controller, route(580, 0), 900);
        assertTrue(controller.drainEvents().isEmpty());
    }

    @Test void longRunsBoundEntitiesEventsAndStoriesWithoutDroppingRouteRewards() {
        var controller = standard(17);
        for (int center : new int[] {900, 3000, 5000}) {
            controller.update(route(center, center - 500)); assertTrue(controller.interact());
        }
        for (int step = 0; step < 30_000; step++) {
            controller.update(boss(step % 700 == 1 ? 580 : 280, 0));
            if (step % 700 <= 1) controller.interact();
            assertEquals(2, controller.snapshot().anchors().size());
            assertTrue(controller.snapshot().hazards().size() <= MAX_HAZARDS);
            assertEquals(2, controller.platforms().size());
        }
        var events = controller.drainEvents();
        assertTrue(events.size() <= MAX_EVENTS);
        assertEquals(3, events.stream().filter(AnchorStabilized.class::isInstance).count());
        var storyIds = events.stream().filter(Story.class::isInstance).map(Story.class::cast).map(Story::id).toList();
        assertEquals(storyIds.size(), storyIds.stream().distinct().count());
        assertThrows(UnsupportedOperationException.class, () -> controller.snapshot().anchors().clear());
        assertThrows(UnsupportedOperationException.class, () -> controller.snapshot().hazards().clear());
        assertThrows(UnsupportedOperationException.class, () -> controller.platforms().clear());
    }

    @Test void invalidInputsFailBeforePublishingInvalidGeometry() {
        assertThrows(IllegalArgumentException.class, () -> new Bounds(Double.NaN, 10, 30, 30));
        assertThrows(IllegalArgumentException.class, () -> new Bounds(10, 10, 0, 30));
        assertThrows(NullPointerException.class, () -> new Input(null, 0, 960, 480, true, false, 0));
        assertThrows(IllegalArgumentException.class, () -> new Input(new Bounds(0, 0, 30, 30), -1, 960, 480, true, false, 0));
        assertThrows(IllegalArgumentException.class, () -> new Input(new Bounds(0, 0, 30, 30), 0, 300, 480, true, false, 0));
        assertThrows(IllegalArgumentException.class, () -> new Input(new Bounds(0, 0, 30, 30), 0, 960, 480, true, true, 0));
    }

    private static SingularityEdgeController standard(long seed) { return SingularityEdgeController.standard(seed); }
    private static Input route(double centerX, double camera) {
        return new Input(new Bounds(centerX - 15, 450, 30, 30), camera, 960, 480, true, false, 0);
    }
    private static Input boss(double centerX, double camera) {
        return new Input(new Bounds(centerX - 15, 450, 30, 30), camera, 960, 480, true, true, 1);
    }
    private static Input playerInput(Player player, double camera) {
        return new Input(new Bounds(player.getX(), player.getY(), 30, 30), camera, 960, 480, true, false, 0);
    }
    private static void move(Player player, SingularityEdgeController controller, boolean right, boolean jump) {
        player.update(false, right, jump, false, 480, 10000, new ArrayList<>(), controller.platforms());
    }
    private static void advance(SingularityEdgeController controller, Input input, int steps) {
        for (int step = 0; step < steps; step++) controller.update(input);
    }
    private static void awaitWarning(SingularityEdgeController controller, Input input) {
        for (int step = 0; step < 900; step++) {
            controller.update(input);
            if (controller.snapshot().hazards().stream().anyMatch(hazard -> hazard.phase() == HazardPhase.WARNING)) return;
        }
        fail("Visible echo did not start a warning");
    }
    private static HazardView hazard(SingularityEdgeController controller) { return controller.snapshot().hazards().get(0); }
    private static AnchorView anchor(SingularityEdgeController controller, int id) {
        return controller.snapshot().anchors().stream().filter(anchor -> anchor.id() == id).findFirst().orElseThrow();
    }
    private static List<DamagePlayer> damage(SingularityEdgeController controller) {
        return controller.drainEvents().stream().filter(DamagePlayer.class::isInstance).map(DamagePlayer.class::cast).toList();
    }
    private static List<String> anchorValues(SingularityEdgeController controller) {
        return controller.snapshot().anchors().stream().map(anchor -> anchor.id() + ":" + anchor.bounds() + ":"
                + anchor.state() + ":" + anchor.ticksRemaining() + ":" + anchor.inReach() + ":"
                + anchor.safetyPlatform().x + ":" + anchor.safetyPlatform().y).toList();
    }
}
