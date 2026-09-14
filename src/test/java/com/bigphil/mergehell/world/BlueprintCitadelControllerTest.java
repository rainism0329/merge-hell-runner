package com.bigphil.mergehell.world;

import com.bigphil.mergehell.model.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.bigphil.mergehell.world.BlueprintCitadelController.*;
import static org.junit.jupiter.api.Assertions.*;

class BlueprintCitadelControllerTest {
    @Test void equalSeedsAndInputsProduceEqualScansAndEventsWithoutSharedRandomState() {
        var first = standard(42); var second = standard(42); var unrelated = standard(8);
        for (int tick = 0; tick < 1800; tick++) {
            Input input = tick < 600 ? route(850, 0) : boss(190, 0);
            first.update(input); unrelated.update(boss(470, 0)); second.update(input);
            if (tick == 400 || tick == 900) {
                int id = tick == 400 ? 1 : 101;
                assertEquals(first.hitSupport(id, 1000), second.hitSupport(id, 1000));
            }
            assertEquals(first.snapshot().scans(), second.snapshot().scans());
            assertEquals(supportValues(first), supportValues(second));
            assertEquals(first.snapshot().objective(), second.snapshot().objective());
            assertEquals(first.snapshot().story(), second.snapshot().story());
            assertEquals(first.snapshot().storyTicksRemaining(), second.snapshot().storyTicksRemaining());
            assertEquals(first.snapshot().bossExposeTicks(), second.snapshot().bossExposeTicks());
            assertEquals(first.drainEvents(), second.drainEvents());
        }
    }

    @Test void damageAndOverloadBothBreakTheSameSupportExactlyOnce() {
        var controller = standard(3);
        controller.update(route(700, 0)); controller.drainEvents();
        assertFalse(controller.hitSupport(404, 25));
        assertFalse(controller.hitSupport(1, 0));
        assertFalse(controller.hitSupport(1, -1));
        assertTrue(controller.hitSupport(1, 25));
        assertEquals(ROUTE_SUPPORT_HP - 25, node(controller, 1).hp());
        assertTrue(controller.interact());
        assertFalse(controller.interact(), "Repeated E cannot start a second channel");
        assertTrue(controller.hitSupport(1, Integer.MAX_VALUE));
        assertEquals(SupportState.COLLAPSING, node(controller, 1).state());
        assertEquals(COLLAPSE_TICKS, node(controller, 1).ticksRemaining());
        assertEquals(0, node(controller, 1).hp());
        assertFalse(controller.hitSupport(1, 25)); assertFalse(controller.interact());
        advance(controller, route(700, 0), CHANNEL_TICKS + 100);
        var rewards = controller.drainEvents().stream().filter(SupportBroken.class::isInstance)
                .map(SupportBroken.class::cast).toList();
        assertEquals(1, rewards.size());
        assertEquals(20, rewards.get(0).xp()); assertEquals(350, rewards.get(0).score());
        assertEquals(0, rewards.get(0).bossExposeTicks());
        assertTrue(controller.drainEvents().isEmpty());
    }

    @Test void channelNeedsItsFullDurationAndLeavingOrJumpingCancelsWithoutRepairingDamage() {
        var controller = standard(4);
        controller.update(route(700, 0)); controller.hitSupport(1, 20); controller.interact();
        advance(controller, route(700, 0), CHANNEL_TICKS - 1);
        assertEquals(SupportState.OVERLOADING, node(controller, 1).state());
        assertEquals(1, node(controller, 1).ticksRemaining());
        controller.update(route(780, 0));
        assertEquals(SupportState.ONLINE, node(controller, 1).state());
        assertEquals(100, node(controller, 1).hp());
        assertTrue(controller.drainEvents().stream().noneMatch(SupportBroken.class::isInstance));
        controller.update(route(700, 0)); assertTrue(controller.interact());
        controller.update(new Input(new Bounds(685, 360, 30, 30), 0, 960, 480, true, false, null, 0));
        assertEquals(SupportState.ONLINE, node(controller, 1).state());
        assertFalse(controller.interact(), "An overhead platform or jump cannot operate a ground support");
        controller.update(route(700, 0)); assertTrue(controller.interact());
        advance(controller, route(700, 0), CHANNEL_TICKS);
        assertEquals(SupportState.COLLAPSING, node(controller, 1).state());
    }

    @Test void aBrokenDeckRemainsStandableForTheWholeCollapseWarningThenDropsThePlayerSafely() {
        var controller = standard(6);
        controller.update(route(700, 0));
        var deck = node(controller, 1).linkedPlatform();
        var player = new Player((int) deck.x + 40, (int) deck.y - 30);
        move(player, controller);
        assertTrue(player.isGrounded()); assertEquals(deck.y, player.getY() + 30, 0.001);
        assertTrue(controller.hitSupport(1, 120));
        for (int tick = 0; tick < COLLAPSE_TICKS - 1; tick++) {
            controller.update(route(700, 0)); move(player, controller);
            assertEquals(deck.y, player.getY() + 30, 0.001);
            assertTrue(node(controller, 1).ticksRemaining() > 0);
            assertTrue(controller.snapshot().objective().contains("collapsing"));
        }
        controller.update(route(700, 0));
        assertEquals(SupportState.DISABLED, node(controller, 1).state());
        assertNotNull(node(controller, 1).linkedPlatform(), "Disabled structure retains its rendering metadata");
        assertEquals(2, controller.platforms().size());
        move(player, controller); assertTrue(player.getY() + 30 > deck.y);
        for (int tick = 0; tick < 60; tick++) move(player, controller);
        assertEquals(480, player.getY() + 30, 0.001);
        assertTrue(player.isGrounded(), "The continuous floor prevents a collapsing walkway from soft-locking the run");
    }

    @Test void scannerHasTheFullWarningBeforeDamageAndAReadableSafeAlternative() {
        var controller = standard(7);
        awaitScan(controller, route(850, 0));
        var first = controller.snapshot().scans().get(0);
        assertEquals(ScanPhase.WARNING, first.phase());
        assertEquals(SCAN_WARNING_TICKS, first.ticksRemaining());
        assertEquals(480 - 18, first.bounds().y());
        assertEquals(first.bounds().x() + first.bounds().width(), first.originX());
        assertEquals(first.bounds().centerY(), first.originY());
        assertTrue(first.bounds().width() < 300);
        controller.drainEvents();
        advance(controller, route(850, 0), SCAN_WARNING_TICKS - 1);
        assertEquals(ScanPhase.WARNING, controller.snapshot().scans().get(0).phase());
        assertTrue(controller.drainEvents().stream().noneMatch(DamagePlayer.class::isInstance));
        controller.update(route(850, 0));
        assertEquals(ScanPhase.ACTIVE, controller.snapshot().scans().get(0).phase());
        var damage = controller.drainEvents().stream().filter(DamagePlayer.class::isInstance)
                .map(DamagePlayer.class::cast).findFirst().orElseThrow();
        assertEquals(8, damage.amount()); assertEquals(first.id(), damage.scanId());
        advance(controller, route(850, 0), 59);
        assertTrue(controller.drainEvents().stream().noneMatch(DamagePlayer.class::isInstance));
        controller.update(route(850, 0));
        assertEquals(1, controller.drainEvents().stream().filter(DamagePlayer.class::isInstance).count());
    }

    @Test void lowScansMissAirbornePlayersAndTheWholeOverloadZoneIsOutsideTheirRectangle() {
        var controller = standard(7);
        awaitScan(controller, route(850, 0)); controller.drainEvents();
        Input jump = new Input(new Bounds(835, 390, 30, 30), 0, 960, 480, true, false, null, 0);
        advance(controller, jump, SCAN_WARNING_TICKS + SCAN_ACTIVE_TICKS - 1);
        assertTrue(controller.drainEvents().stream().noneMatch(DamagePlayer.class::isInstance));
        var safe = standard(7);
        awaitScan(safe, route(758, 0)); safe.drainEvents();
        assertTrue(safe.interact());
        advance(safe, route(758, 0), CHANNEL_TICKS);
        assertTrue(safe.snapshot().scans().isEmpty());
        assertTrue(safe.drainEvents().stream().noneMatch(DamagePlayer.class::isInstance));
    }

    @Test void disconnectingSupportRemovesItsWarningImmediatelyAndNeverSpawnsItsScannerAgain() {
        var controller = standard(9);
        awaitScan(controller, route(850, 0));
        assertTrue(controller.hitSupport(1, 120));
        assertTrue(controller.snapshot().scans().isEmpty());
        for (int tick = 0; tick < 1400; tick++) {
            controller.update(route(850, 0));
            assertTrue(controller.snapshot().scans().stream().noneMatch(scan -> scan.supportId() == 1));
        }
        assertEquals(SupportState.DISABLED, node(controller, 1).state());
    }

    @Test void inactiveInputFreezesChannelsScansCooldownsAndRejectsHits() {
        var controller = standard(10);
        controller.update(route(700, 0)); controller.interact();
        advance(controller, route(700, 0), 21); controller.drainEvents();
        var frozen = controller.snapshot(); var decks = controller.platforms();
        Input paused = new Input(new Bounds(745, 450, 30, 30), 400, 960, 480, false, false, null, 0);
        for (int tick = 0; tick < 900; tick++) {
            controller.update(paused);
            assertSame(frozen, controller.snapshot()); assertSame(decks, controller.platforms());
            assertFalse(controller.interact()); assertFalse(controller.hitSupport(1, 999));
            assertTrue(controller.drainEvents().isEmpty());
        }
        controller.update(route(700, 0));
        assertEquals(frozen.tick() + 1, controller.snapshot().tick());
        assertEquals(nodeValue(frozen, 1).ticksRemaining() - 1, node(controller, 1).ticksRemaining());
    }

    @Test void bossSupportsAnchorOnceInterruptTheWholeGridAndRebuildWithoutRewardFarming() {
        var controller = standard(11);
        controller.update(boss(7590, 7400));
        assertEquals(2, controller.snapshot().supports().size());
        assertEquals(7590, node(controller, 101).bounds().centerX());
        assertEquals(7870, node(controller, 102).bounds().centerX());
        advance(controller, boss(7590, 7400), 220); controller.drainEvents();
        assertFalse(controller.snapshot().scans().isEmpty());
        assertTrue(controller.hitSupport(101, BOSS_SUPPORT_HP));
        assertTrue(controller.snapshot().scans().isEmpty());
        assertEquals(BOSS_EXPOSE_TICKS, controller.snapshot().bossExposeTicks());
        var broken = controller.drainEvents().stream().filter(SupportBroken.class::isInstance)
                .map(SupportBroken.class::cast).findFirst().orElseThrow();
        assertEquals(0, broken.xp()); assertEquals(0, broken.score());
        assertEquals(new Bounds(7400, 0, 960, 480), broken.clearedArea());
        assertEquals(BOSS_EXPOSE_TICKS, broken.bossExposeTicks());
        controller.update(boss(7590, 7410));
        assertEquals(7590, node(controller, 101).bounds().centerX());
        advance(controller, boss(7590, 7400), COLLAPSE_TICKS - 1);
        assertEquals(SupportState.COOLDOWN, node(controller, 101).state());
        assertEquals(BOSS_SUPPORT_COOLDOWN_TICKS, node(controller, 101).ticksRemaining());
        assertEquals(1, controller.platforms().size());
        advance(controller, boss(7590, 7400), BOSS_SUPPORT_COOLDOWN_TICKS - 1);
        assertFalse(controller.hitSupport(101, 1000));
        controller.update(boss(7590, 7400));
        assertEquals(SupportState.ONLINE, node(controller, 101).state());
        assertEquals(BOSS_SUPPORT_HP, node(controller, 101).hp());
        assertEquals(2, controller.platforms().size());
        assertTrue(controller.hitSupport(101, 1000));
        assertTrue(controller.drainEvents().stream().filter(SupportBroken.class::isInstance)
                .map(SupportBroken.class::cast).allMatch(event -> event.xp() == 0 && event.score() == 0));
    }

    @Test void overlappingBossBreakWindowsNeverStackBeyondTheirBoundedDuration() {
        var controller = standard(12);
        controller.update(boss(190, 0)); controller.hitSupport(101, 999);
        assertTrue(controller.hitSupport(102, 999));
        assertEquals(BOSS_EXPOSE_TICKS, controller.snapshot().bossExposeTicks());
        for (int tick = 0; tick < BOSS_EXPOSE_TICKS; tick++) {
            controller.update(boss(190, 0)); assertTrue(controller.snapshot().scans().isEmpty());
        }
        assertEquals(0, controller.snapshot().bossExposeTicks());
    }

    @Test void respawnClearsDangerAndChannelsWithoutRestoringSpentSupportsOrDiscardingRouteRewards() {
        var controller = standard(14);
        controller.update(route(700, 0)); controller.hitSupport(1, 999);
        int remainingCollapse = node(controller, 1).ticksRemaining();
        controller.resetTransient();
        assertEquals(SupportState.COLLAPSING, node(controller, 1).state());
        assertEquals(remainingCollapse, node(controller, 1).ticksRemaining());
        assertEquals(1, controller.drainEvents().stream().filter(SupportBroken.class::isInstance).count());
        controller.update(route(2680, 2400)); controller.interact();
        controller.resetTransient();
        assertEquals(SupportState.ONLINE, node(controller, 2).state());
        for (int tick = 0; tick < RESPAWN_SAFE_TICKS - 1; tick++) {
            controller.update(route(2850, 2400));
            assertTrue(controller.snapshot().scans().isEmpty());
            assertTrue(controller.drainEvents().stream().noneMatch(DamagePlayer.class::isInstance));
        }
        controller.update(boss(190, 0)); controller.hitSupport(101, 999);
        controller.resetTransient();
        assertEquals(0, controller.snapshot().bossExposeTicks());
        assertTrue(controller.drainEvents().stream().noneMatch(event -> event instanceof SupportBroken broken && broken.bossExposeTicks() > 0));
        assertEquals(SupportState.COLLAPSING, node(controller, 101).state());
    }

    @Test void bossEndClearsDangerAndPreventsLateSupportRewards() {
        var controller = standard(15);
        controller.update(boss(190, 0)); controller.hitSupport(101, 999); controller.drainEvents();
        controller.update(route(190, 0));
        assertFalse(controller.snapshot().bossActive());
        assertEquals(0, controller.snapshot().bossExposeTicks());
        assertTrue(controller.snapshot().scans().isEmpty());
        assertTrue(controller.snapshot().supports().isEmpty());
        assertTrue(controller.platforms().isEmpty());
        assertFalse(controller.hitSupport(102, 999)); assertFalse(controller.interact());
        advance(controller, route(190, 0), 1000);
        assertTrue(controller.drainEvents().stream().noneMatch(event -> event instanceof DamagePlayer || event instanceof SupportBroken));
    }

    @Test void skippedRouteRegionsAndRepeatedUpdatesDoNotRepeatStoryAnnouncements() {
        var controller = standard(16);
        controller.update(route(700, 0));
        var arrival = controller.drainEvents();
        assertEquals(1, arrival.stream().filter(Story.class::isInstance).count());
        controller.update(route(6500, 6200));
        advance(controller, route(6500, 6200), 250);
        var stories = controller.drainEvents().stream().filter(Story.class::isInstance).map(Story.class::cast).toList();
        assertEquals(1, stories.size()); assertEquals("ROUTE_3", stories.get(0).id());
        advance(controller, route(6500, 6200), 600);
        assertTrue(controller.drainEvents().stream().noneMatch(Story.class::isInstance));
        controller.update(route(700, 0)); controller.update(route(6500, 6200));
        assertTrue(controller.drainEvents().stream().noneMatch(Story.class::isInstance));
    }

    @Test void hostileFieldsEventQueueAndPublishedCollectionsRemainBounded() {
        var controller = standard(19);
        for (int id = 1; id <= 3; id++) {
            controller.update(route(id == 1 ? 700 : id == 2 ? 2680 : 4680, 0));
            assertTrue(controller.hitSupport(id, 999));
        }
        for (int tick = 0; tick < 15_000; tick++) {
            controller.update(boss(300, 0));
            if (tick % 800 == 0) controller.hitSupport(101, 999);
            assertTrue(controller.snapshot().scans().size() <= MAX_SCANS);
            assertEquals(2, controller.snapshot().supports().size());
            assertTrue(controller.platforms().size() <= 2);
        }
        var events = controller.drainEvents();
        assertTrue(events.size() <= MAX_EVENTS);
        assertEquals(3, events.stream().filter(SupportBroken.class::isInstance).map(SupportBroken.class::cast)
                .filter(event -> event.xp() > 0).count(), "A stalled consumer must retain all one-time route rewards");
        assertThrows(UnsupportedOperationException.class, () -> controller.snapshot().supports().clear());
        assertThrows(UnsupportedOperationException.class, () -> controller.snapshot().scans().clear());
        assertThrows(UnsupportedOperationException.class, () -> controller.platforms().clear());
    }

    private static BlueprintCitadelController standard(long seed) { return BlueprintCitadelController.standard(seed); }
    private static Input route(double centerX, double camera) {
        return new Input(new Bounds(centerX - 15, 450, 30, 30), camera, 960, 480, true, false, null, 0);
    }
    private static Input boss(double centerX, double camera) {
        return new Input(new Bounds(centerX - 15, 450, 30, 30), camera, 960, 480, true, true,
                new Bounds(camera + 730, 250, 120, 230), 1);
    }
    private static void advance(BlueprintCitadelController controller, Input input, int ticks) {
        for (int tick = 0; tick < ticks; tick++) controller.update(input);
    }
    private static void awaitScan(BlueprintCitadelController controller, Input input) {
        for (int tick = 0; tick < 700 && controller.snapshot().scans().isEmpty(); tick++) controller.update(input);
        assertFalse(controller.snapshot().scans().isEmpty());
    }
    private static SupportView node(BlueprintCitadelController controller, int id) { return nodeValue(controller.snapshot(), id); }
    private static SupportView nodeValue(Snapshot snapshot, int id) {
        return snapshot.supports().stream().filter(node -> node.id() == id).findFirst().orElseThrow();
    }
    private static List<String> supportValues(BlueprintCitadelController controller) {
        return controller.snapshot().supports().stream().map(node -> node.id() + ":" + node.bounds() + ":"
                + node.state() + ":" + node.hp() + ":" + node.maxHp() + ":" + node.ticksRemaining() + ":" + node.inReach()
                + ":" + node.linkedPlatform().x + ":" + node.linkedPlatform().y + ":" + node.linkedPlatform().width).toList();
    }
    private static void move(Player player, BlueprintCitadelController controller) {
        player.update(false, false, false, false, 480, 10000, new ArrayList<>(), controller.platforms());
    }
}
