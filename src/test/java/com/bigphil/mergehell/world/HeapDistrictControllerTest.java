package com.bigphil.mergehell.world;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.bigphil.mergehell.world.HeapDistrictController.*;
import static org.junit.jupiter.api.Assertions.*;

class HeapDistrictControllerTest {
    @Test void equalSeedsAndInputsStayIdenticalEvenWithAnotherControllerInterleaved() {
        var first = standard(6174); var second = standard(6174); var unrelated = standard(42);
        for (int tick = 0; tick < 1800; tick++) {
            Input input = tick < 700 ? route(760, 0) : boss(7590, 7400);
            first.update(input);
            unrelated.update(boss(190, 0)); unrelated.drainEvents();
            second.update(input);
            if (tick == 190 || tick == 850) {
                assertEquals(first.interact(Choice.PURGE), second.interact(Choice.PURGE));
            }
            assertEquals(first.snapshot(), second.snapshot());
            assertEquals(first.drainEvents(), second.drainEvents());
        }
    }

    @Test void separateSeedsChangeRefluxPositionsWithoutChangingAuthoredStations() {
        var first = standard(1); var second = standard(2);
        advance(first, boss(190, 0), 120); advance(second, boss(190, 0), 120);
        assertEquals(first.snapshot().nodes(), second.snapshot().nodes());
        assertNotEquals(first.snapshot().blocks(), second.snapshot().blocks());
    }

    @Test void riskRewardIsSingleUseAndLeakWaitsForItsFullWarningBeforeDamage() {
        var controller = standard(1);
        controller.update(route(760, 0)); controller.drainEvents();
        assertTrue(controller.interact(Choice.SALVAGE));
        assertFalse(controller.interact(Choice.SALVAGE));
        assertFalse(controller.interact(Choice.PURGE));
        assertFalse(controller.interact(Choice.NONE));
        assertEquals(1, controller.drainEvents().stream().filter(Salvaged.class::isInstance).count());
        assertEquals(NodeState.USED, node(controller, 1).state());
        var pool = controller.snapshot().pools().get(0);
        assertEquals(PoolPhase.WARNING, pool.phase());
        assertEquals(POOL_WARNING_TICKS, pool.ticksRemaining());
        Input standingInPool = route(pool.bounds().centerX(), 0);
        for (int i = 0; i < POOL_WARNING_TICKS - 1; i++) {
            controller.update(standingInPool);
            assertTrue(controller.drainEvents().stream().noneMatch(DamagePlayer.class::isInstance));
        }
        assertEquals(PoolPhase.WARNING, controller.snapshot().pools().get(0).phase());
        controller.update(standingInPool);
        assertEquals(PoolPhase.ACTIVE, controller.snapshot().pools().get(0).phase());
        assertEquals(List.of(new DamagePlayer(8, pool.id())), controller.drainEvents());
        advance(controller, standingInPool, 59);
        assertTrue(controller.drainEvents().stream().noneMatch(DamagePlayer.class::isInstance));
        controller.update(standingInPool);
        assertEquals(1, controller.drainEvents().stream().filter(DamagePlayer.class::isInstance).count());
    }

    @Test void channelsNeedFortyEightActiveStepsAndPausingFreezesEveryPublishedValue() {
        var controller = standard(3);
        controller.update(route(760, 0)); controller.drainEvents();
        assertTrue(controller.interact(Choice.PURGE));
        advance(controller, route(760, 0), CHANNEL_TICKS - 1);
        assertEquals(NodeState.CHANNELING, node(controller, 1).state());
        assertEquals(1, node(controller, 1).ticksRemaining());
        var frozen = controller.snapshot();
        var inactive = new Input(route(760, 0).player(), 900, 960, 480, false, false, null, 0);
        advance(controller, inactive, 500);
        assertSame(frozen, controller.snapshot());
        assertFalse(controller.interact(Choice.PURGE));
        assertFalse(controller.hitBlock(1));
        assertTrue(controller.drainEvents().isEmpty());
        controller.update(route(760, 0));
        assertEquals(NodeState.USED, node(controller, 1).state());
        var clean = controller.drainEvents().stream().filter(Cleaned.class::isInstance).map(Cleaned.class::cast).findFirst().orElseThrow();
        assertEquals(20, clean.xp()); assertEquals(300, clean.score()); assertEquals(0, clean.bossExposeTicks());
    }

    @Test void leavingInteractionRangeCancelsTheChannelWithoutSpendingItsStation() {
        var controller = standard(4);
        controller.update(route(760, 0)); controller.drainEvents();
        assertTrue(controller.interact(Choice.PURGE));
        advance(controller, route(760, 0), 20);
        controller.update(route(831, 0));
        assertEquals(NodeState.READY, node(controller, 1).state());
        assertTrue(controller.drainEvents().isEmpty());
        controller.update(new Input(new Bounds(745, 300, 30, 30), 0, 960, 480, true, false, null, 0));
        assertFalse(controller.interact(Choice.PURGE), "A station on the ground cannot be used from a high platform");
        controller.update(route(760, 0));
        assertTrue(controller.interact(Choice.PURGE));
    }

    @Test void leakBudgetRetainsGroundGapsAndDoesNotReachPlayersOnPlatforms() {
        var controller = standard(1);
        advance(controller, route(760, 0), 180); controller.drainEvents();
        assertTrue(controller.interact(Choice.SALVAGE));
        assertEquals(2, controller.snapshot().pools().size());
        var pools = controller.snapshot().pools();
        double gap = Math.abs(pools.get(0).bounds().centerX() - pools.get(1).bounds().centerX()) - 120;
        assertTrue(gap >= 120);
        Input platform = new Input(new Bounds(745, 320, 30, 30), 0, 960, 480, true, false, null, 0);
        for (int i = 0; i < 2000; i++) {
            controller.update(platform);
            assertTrue(controller.snapshot().pools().size() <= MAX_POOLS);
            assertTrue(controller.drainEvents().stream().noneMatch(DamagePlayer.class::isInstance));
            for (var pool : controller.snapshot().pools()) assertEquals(466, pool.bounds().y());
        }
    }

    @Test void bossStationsAnchorOnceAndGcClearsBlocksWithoutFarmingRewards() {
        var controller = standard(5);
        advance(controller, boss(7590, 7400), 120);
        assertEquals(2, controller.snapshot().nodes().size());
        assertEquals(7590, node(controller, 101).bounds().centerX());
        assertEquals(7870, node(controller, 102).bounds().centerX());
        assertEquals(1, controller.snapshot().blocks().size());
        controller.drainEvents();
        assertFalse(controller.interact(Choice.SALVAGE));
        assertTrue(controller.interact(Choice.PURGE));
        advance(controller, boss(7590, 7400), CHANNEL_TICKS);
        assertTrue(controller.snapshot().blocks().isEmpty());
        assertTrue(controller.snapshot().pools().isEmpty());
        assertEquals(NodeState.COOLDOWN, node(controller, 101).state());
        assertEquals(BOSS_NODE_COOLDOWN_TICKS, node(controller, 101).ticksRemaining());
        assertEquals(GC_EXPOSE_TICKS, controller.snapshot().bossExposeTicks());
        List<Event> events = controller.drainEvents();
        assertEquals(1, events.stream().filter(RefluxBroken.class::isInstance).map(RefluxBroken.class::cast).filter(RefluxBroken::byGc).count());
        var cleaned = events.stream().filter(Cleaned.class::isInstance).map(Cleaned.class::cast).findFirst().orElseThrow();
        assertEquals(0, cleaned.xp()); assertEquals(0, cleaned.score());
        assertEquals(new Bounds(7400, 0, 960, 480), cleaned.clearedArea());
        assertEquals(GC_EXPOSE_TICKS, cleaned.bossExposeTicks());
        controller.update(boss(7590, 7410));
        assertEquals(7590, node(controller, 101).bounds().centerX(), "Camera motion must not move the station");
        advance(controller, boss(7590, 7400), BOSS_NODE_COOLDOWN_TICKS - 2);
        assertFalse(controller.interact(Choice.PURGE));
        controller.update(boss(7590, 7400));
        assertTrue(controller.interact(Choice.PURGE));
    }

    @Test void oneConfirmedBlockHitOpensOneWindowAndDoesNotRepeatOrHeal() {
        var controller = standard(6);
        advance(controller, boss(190, 0), 120); controller.drainEvents();
        var block = controller.snapshot().blocks().get(0);
        assertEquals(30, block.warningTicksRemaining());
        assertFalse(controller.hitBlock(block.id()), "A visual warning is not yet a hittable memory block");
        advance(controller, boss(190, 0), 29);
        assertFalse(controller.hitBlock(block.id()));
        controller.update(boss(190, 0));
        assertTrue(controller.hitBlock(block.id()));
        assertFalse(controller.hitBlock(block.id()));
        assertTrue(controller.snapshot().blocks().isEmpty());
        assertEquals(BLOCK_EXPOSE_TICKS, controller.snapshot().bossExposeTicks());
        List<Event> events = controller.drainEvents();
        assertEquals(1, events.stream().filter(RefluxBroken.class::isInstance).count());
        assertTrue(events.stream().noneMatch(RefluxArrived.class::isInstance));
        assertTrue(controller.drainEvents().isEmpty());
    }

    @Test void unblockedRefluxHasABoundedSmallHealingBudgetAndBoundedEntityCount() {
        var controller = standard(7);
        Input closeBoss = new Input(new Bounds(175, 450, 30, 30), 0, 960, 480, true,
                true, new Bounds(80, 200, 120, 220), 3);
        int healing = 0;
        for (int i = 0; i < 4000; i++) {
            controller.update(closeBoss);
            assertTrue(controller.snapshot().blocks().size() <= MAX_BLOCKS);
            for (Event event : controller.drainEvents()) if (event instanceof RefluxArrived arrival) {
                assertTrue(arrival.healAmount() >= 0 && arrival.healAmount() <= 12);
                healing += arrival.healAmount();
            }
            assertTrue(healing <= MAX_REFLUX_HEALING);
        }
        assertEquals(MAX_REFLUX_HEALING, healing);
    }

    @Test void respawnClearsDangerAndChannelsButPreservesConsumedStationsAndEarnedRewards() {
        var controller = standard(8);
        controller.update(route(760, 0)); controller.drainEvents();
        assertTrue(controller.interact(Choice.SALVAGE));
        assertFalse(controller.snapshot().pools().isEmpty());
        controller.resetTransient();
        assertEquals(NodeState.USED, node(controller, 1).state());
        assertTrue(controller.snapshot().pools().isEmpty());
        assertEquals(1, controller.drainEvents().stream().filter(Salvaged.class::isInstance).count());
        assertTrue(controller.drainEvents().isEmpty());
        assertFalse(controller.interact(Choice.PURGE));
        for (int i = 0; i < RESPAWN_SAFE_TICKS - 1; i++) {
            controller.update(route(760, 0));
            assertTrue(controller.snapshot().pools().isEmpty());
            assertTrue(controller.drainEvents().stream().noneMatch(DamagePlayer.class::isInstance));
        }
        controller.update(route(2600, 2200));
        assertTrue(controller.interact(Choice.PURGE));
        controller.resetTransient();
        assertEquals(NodeState.READY, node(controller, 2).state());
    }

    @Test void respawnKeepsBossStationCooldownAndHealingBudget() {
        var controller = standard(9);
        Input closeBoss = new Input(new Bounds(175, 450, 30, 30), 0, 960, 480, true,
                true, new Bounds(80, 200, 120, 220), 3);
        advance(controller, closeBoss, 4000); controller.drainEvents();
        assertTrue(controller.interact(Choice.PURGE));
        advance(controller, closeBoss, CHANNEL_TICKS); controller.drainEvents();
        controller.resetTransient();
        assertEquals(BOSS_NODE_COOLDOWN_TICKS, node(controller, 101).ticksRemaining());
        assertTrue(controller.snapshot().blocks().isEmpty());
        assertFalse(controller.interact(Choice.PURGE));
        advance(controller, closeBoss, 1000);
        assertTrue(controller.drainEvents().stream().filter(RefluxArrived.class::isInstance)
                .map(RefluxArrived.class::cast).allMatch(event -> event.healAmount() == 0));
    }

    @Test void choiceAndBossStoryBeatsWaitTheirTurnAndFollowRealWindowsOnlyOnce() {
        var controller = standard(10);
        List<String> stories = new ArrayList<>();
        controller.update(route(760, 0));
        collectStories(controller, stories);
        assertTrue(controller.snapshot().objective().contains("E Purge"));
        controller.interact(Choice.PURGE);
        assertTrue(controller.snapshot().objective().contains("Starting GC"));
        advance(controller, route(760, 0), 480);
        collectStories(controller, stories);
        advance(controller, boss(7590, 7400), 150);
        collectStories(controller, stories);
        controller.hitBlock(controller.snapshot().blocks().get(0).id());
        collectStories(controller, stories);
        assertTrue(controller.snapshot().objective().contains("Core exposed"));
        advance(controller, boss(7590, 7400), 700);
        collectStories(controller, stories);
        assertEquals(List.of("ARRIVAL", "FIRST_CHOICE", "BOSS_ENTRY", "BOSS_WINDOW"), stories);
    }

    @Test void regionStoryRequiresCrossingTheRealThresholdAndBacktrackingDoesNotReplayIt() {
        var controller = standard(13);
        List<String> stories = new ArrayList<>();
        advance(controller, route(1799, 1400), 500); collectStories(controller, stories);
        assertEquals(List.of("ARRIVAL"), stories);
        controller.update(route(1800, 1400)); collectStories(controller, stories);
        assertEquals(List.of("ARRIVAL", "ARCHIVE"), stories);
        advance(controller, route(4000, 3600), 250); collectStories(controller, stories);
        advance(controller, route(6200, 5800), 250); collectStories(controller, stories);
        advance(controller, route(1000, 500), 250); collectStories(controller, stories);
        advance(controller, route(6200, 5800), 500); collectStories(controller, stories);
        assertEquals(List.of("ARRIVAL", "ARCHIVE", "REFLUX_MAIN", "PUMP_ROOM"), stories);
    }

    @Test void crossingMultipleRegionsDoesNotOverwriteTheActiveLineOrQueueAnEntireBacklog() {
        var controller = standard(14);
        controller.update(route(100, 0)); controller.drainEvents();
        String activeLine = controller.snapshot().story();
        controller.update(route(6200, 5800));
        assertEquals(activeLine, controller.snapshot().story());
        assertTrue(controller.drainEvents().stream().noneMatch(Story.class::isInstance));
        List<String> ids = new ArrayList<>();
        advance(controller, route(6200, 5800), 1000); collectStories(controller, ids);
        assertEquals(List.of("PUMP_ROOM"), ids);
        // A phase burst also keeps only two waiting stories and does not replace the current one.
        controller.update(boss(7590, 7400)); controller.drainEvents();
        String bossLine = controller.snapshot().story();
        controller.update(stageBoss(2)); controller.update(stageBoss(3));
        assertEquals(bossLine, controller.snapshot().story());
        advance(controller, stageBoss(3), 1000);
        ids.clear(); collectStories(controller, ids);
        assertEquals(List.of("BOSS_STAGE_2", "BOSS_STAGE_3"), ids);
    }

    @Test void bossEntryAcknowledgesPriorChoicesWithoutRequiringAnyStationToBeUsed() {
        String ignored = bossEntryAfterChoice(Choice.NONE);
        String purged = bossEntryAfterChoice(Choice.PURGE);
        String salvaged = bossEntryAfterChoice(Choice.SALVAGE);
        assertTrue(ignored.contains("remain offline"));
        assertTrue(purged.contains("restored 1 GC"));
        assertTrue(salvaged.contains("salvaged 1 memory"));
        assertNotEquals(purged, salvaged);
    }

    @Test void rapidRegionChangesKeepOnlyTwoWaitingLinesWithoutInterruptingTheCurrentStory() {
        var controller = standard(17);
        controller.update(route(100, 0)); controller.drainEvents();
        String initial = controller.snapshot().story();
        controller.update(route(1800, 1400));
        controller.update(route(4000, 3600));
        controller.update(route(6200, 5800));
        assertEquals(initial, controller.snapshot().story());
        assertTrue(controller.drainEvents().isEmpty());
        advance(controller, route(6200, 5800), 1200);
        List<String> ids = new ArrayList<>(); collectStories(controller, ids);
        assertEquals(List.of("REFLUX_MAIN", "PUMP_ROOM"), ids);
    }

    @Test void phaseStoriesFollowActualStageChangesAndDoNotReplayAfterAHealingBoundary() {
        var controller = standard(15);
        advance(controller, boss(7590, 7400), 500); controller.drainEvents();
        controller.update(stageBoss(2));
        List<String> ids = new ArrayList<>(); collectStories(controller, ids);
        assertEquals(List.of("BOSS_STAGE_2"), ids);
        controller.update(stageBoss(1)); controller.update(stageBoss(2));
        advance(controller, stageBoss(2), 500); collectStories(controller, ids);
        assertEquals(List.of("BOSS_STAGE_2"), ids);
    }

    @Test void finishedBossCannotLeaveLethalPoolsOrAcceptFurtherInteractions() {
        var controller = standard(11);
        advance(controller, boss(190, 0), 200); controller.drainEvents();
        controller.update(route(190, 0));
        assertTrue(controller.snapshot().pools().isEmpty());
        assertTrue(controller.snapshot().blocks().isEmpty());
        assertEquals(0, controller.snapshot().bossExposeTicks());
        assertFalse(controller.interact(Choice.PURGE));
        advance(controller, route(760, 0), 2000);
        assertTrue(controller.drainEvents().isEmpty());
        assertTrue(controller.snapshot().objective().contains("District stable"));
    }

    @Test void snapshotsEventsAndGeometryAdaptersCannotMutateTheController() {
        var controller = standard(12);
        controller.update(route(760, 0));
        assertThrows(UnsupportedOperationException.class, () -> controller.snapshot().nodes().clear());
        var bounds = node(controller, 1).bounds();
        bounds.rectangle().translate(900, 900);
        assertEquals(736, bounds.x());
        assertThrows(UnsupportedOperationException.class, () -> controller.drainEvents().add(new Story("fake", "fake")));
        assertThrows(IllegalArgumentException.class, () -> new Bounds(Double.NaN, 0, 10, 10));
        assertThrows(IllegalArgumentException.class, () -> new Input(bounds, 0, 960, 480, true, true, null, 1));
    }

    private static void collectStories(HeapDistrictController controller, List<String> ids) {
        for (Event event : controller.drainEvents()) if (event instanceof Story story) ids.add(story.id());
    }
    private static String bossEntryAfterChoice(Choice choice) {
        var controller = standard(16);
        controller.update(route(760, 0)); controller.interact(choice);
        advance(controller, route(760, 0), 500); controller.drainEvents();
        controller.update(boss(7590, 7400));
        assertEquals(2, controller.snapshot().nodes().size(), "Every choice still reaches the same boss encounter");
        return controller.drainEvents().stream().filter(Story.class::isInstance).map(Story.class::cast)
                .filter(story -> story.id().equals("BOSS_ENTRY")).map(Story::text).findFirst().orElseThrow();
    }
    private static Input stageBoss(int stage) {
        var input = boss(7590, 7400);
        return new Input(input.player(), input.cameraX(), input.viewWidth(), input.groundY(), true, true, input.boss(), stage);
    }
    private static NodeView node(HeapDistrictController controller, int id) {
        return controller.snapshot().nodes().stream().filter(node -> node.id() == id).findFirst().orElseThrow();
    }
    private static Input route(double playerCenterX, double cameraX) {
        return new Input(new Bounds(playerCenterX - 15, 450, 30, 30), cameraX, 960, 480, true, false, null, 0);
    }
    private static Input boss(double playerCenterX, double cameraX) {
        return new Input(route(playerCenterX, cameraX).player(), cameraX, 960, 480, true, true,
                new Bounds(cameraX + 690, 230, 120, 150), 1);
    }
    private static void advance(HeapDistrictController controller, Input input, int count) {
        for (int i = 0; i < count; i++) controller.update(input);
    }
}
