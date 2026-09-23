package com.bigphil.mergehell.encounter;

import com.bigphil.mergehell.model.EntityType;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class EncounterWaveControllerTest {
    private static final Rectangle PLAYER = new Rectangle(100, 450, 30, 30);
    private static final Predicate<Rectangle> FLOOR = ignored -> true;

    @Test void shieldMarksmanAndCableThreatOverlapButArriveInReadableStages() {
        var controller = new EncounterWaveController();
        controller.begin(List.of(beat(EntityType.WARDEN, 0, 430, 0),
                beat(EntityType.SENTINEL, 40, 690, 0), beat(EntityType.RIGGER, 110, 590, 205)), 3, 0, 800);
        List<EncounterWaveController.Spawn> spawned = new ArrayList<>();
        List<Integer> arrival = new ArrayList<>();
        for (int tick = 0; tick <= 120; tick++) {
            int now = tick;
            controller.advance(spawned.size(), 480, List.of(), bounds(spawned), PLAYER, FLOOR,
                    s -> { spawned.add(s); arrival.add(now); return true; });
        }
        assertEquals(List.of(0, 40, 110), arrival);
        assertEquals(List.of(EntityType.WARDEN, EntityType.SENTINEL, EntityType.RIGGER),
                spawned.stream().map(EncounterWaveController.Spawn::type).toList());
        assertTrue(spawned.get(0).x() < spawned.get(1).x(), "The shield must stand ahead of its marksman");
        assertTrue(spawned.get(2).bounds().getMaxY() < 300, "The cable unit creates a genuinely vertical aim problem");
        assertFalse(controller.hasPending());
    }

    @Test void fullCapacityDefersReinforcementsWithoutDroppingOrBurstingThem() {
        var controller = new EncounterWaveController();
        controller.begin(List.of(beat(EntityType.WARDEN, 0, 430, 0),
                beat(EntityType.SENTINEL, 0, 690, 0), beat(EntityType.RIGGER, 0, 500, 210)), 3, 0, 800);
        List<EncounterWaveController.Spawn> accepted = new ArrayList<>();
        for (int i = 0; i < 500; i++)
            assertEquals(0, controller.advance(3, 480, List.of(), List.of(), PLAYER, FLOOR,
                    s -> { accepted.add(s); return true; }));
        assertEquals(3, controller.pendingCount());
        List<Integer> admittedAt = new ArrayList<>();
        for (int tick = 0; tick < 70; tick++) {
            int now = tick;
            controller.advance(accepted.size(), 480, List.of(), bounds(accepted), PLAYER, FLOOR,
                    s -> { accepted.add(s); admittedAt.add(now); return true; });
            assertTrue(accepted.size() <= 3);
        }
        assertEquals(List.of(0, 24, 48), admittedAt);
        assertEquals(3, accepted.size());
    }

    @Test void refusesAnEntirelyBlockedArenaAndRetriesWhenTheEntranceIsClear() {
        var controller = new EncounterWaveController();
        controller.begin(List.of(beat(EntityType.WARDEN, 0, 430, 0)), 3, 0, 800);
        List<EncounterWaveController.Spawn> accepted = new ArrayList<>();
        Predicate<EncounterWaveController.Spawn> spawn = s -> { accepted.add(s); return true; };
        for (int i = 0; i < 90; i++)
            assertEquals(0, controller.advance(0, 480, List.of(new Rectangle(0, 0, 800, 480)),
                    List.of(), PLAYER, FLOOR, spawn));
        assertTrue(controller.hasPending());
        assertEquals(1, controller.advance(0, 480, List.of(), List.of(), PLAYER, FLOOR, spawn));
        assertFalse(controller.hasPending());
    }

    @Test void placementAvoidsWallsFloorHolesExistingActorsAndThePlayer() {
        var controller = new EncounterWaveController();
        controller.begin(List.of(beat(EntityType.WARDEN, 0, 430, 0)), 3, 0, 960);
        List<Rectangle> walls = List.of(new Rectangle(400, 250, 120, 230), new Rectangle(525, 340, 70, 140));
        Rectangle gap = new Rectangle(595, 475, 100, 20);
        List<Rectangle> occupied = List.of(new Rectangle(710, 400, 65, 80));
        List<EncounterWaveController.Spawn> accepted = new ArrayList<>();
        controller.advance(1, 480, walls, occupied, PLAYER, b -> !b.intersects(gap),
                s -> { accepted.add(s); return true; });
        assertEquals(1, accepted.size());
        Rectangle body = accepted.get(0).bounds();
        assertTrue(walls.stream().noneMatch(body::intersects));
        assertTrue(occupied.stream().noneMatch(body::intersects));
        assertFalse(body.intersects(gap));
        Rectangle personalSpace = new Rectangle(PLAYER); personalSpace.grow(110, 32);
        assertFalse(body.intersects(personalSpace));
        assertTrue(body.x >= 16 && body.getMaxX() <= 944);
    }

    @Test void flyersCanUseTheAirOverAGapButMustStillClearAnOverheadBeam() {
        var controller = new EncounterWaveController();
        controller.begin(List.of(beat(EntityType.RIGGER, 0, 430, 205)), 3, 0, 800);
        Rectangle beam = new Rectangle(400, 180, 120, 80);
        List<EncounterWaveController.Spawn> accepted = new ArrayList<>();
        controller.advance(0, 480, List.of(beam), List.of(), PLAYER, b -> false,
                s -> { accepted.add(s); return true; });
        assertEquals(1, accepted.size());
        assertFalse(accepted.get(0).bounds().intersects(beam));
        assertTrue(accepted.get(0).bounds().getMaxY() < 400);
    }

    @Test void rejectedAdmissionKeepsTheBeatAndClearCancelsItPermanently() {
        var controller = new EncounterWaveController();
        controller.begin(List.of(beat(EntityType.SENTINEL, 0, 600, 0)), 3, 0, 800);
        assertEquals(0, controller.advance(0, 480, List.of(), List.of(), PLAYER, FLOOR, s -> false));
        assertEquals(1, controller.pendingCount());
        controller.clear();
        assertFalse(controller.hasPending());
        assertEquals(0, controller.advance(0, 480, List.of(), List.of(), PLAYER, FLOOR,
                s -> { fail("A cancelled wave must never emit a late reinforcement"); return true; }));
    }

    @Test void elapsedTimeComesOnlyFromGameplayAdvancesAndDefinitionsAreValidated() {
        var controller = new EncounterWaveController();
        var beat = beat(EntityType.SENTINEL, 90, 600, 0);
        controller.begin(List.of(beat), 3, 0, 800);
        for (int i = 0; i < 30; i++) controller.advance(0, 480, List.of(), List.of(), PLAYER, FLOOR, s -> true);
        for (int i = 0; i < 500; i++) assertTrue(controller.hasPending()); // Paint/status polling is inert.
        assertEquals(29, controller.elapsedTicks());
        assertThrows(IllegalArgumentException.class, () -> beat(EntityType.HEALTH, 0, 400, 0));
        assertThrows(IllegalArgumentException.class, () -> controller.begin(List.of(beat), 0, 0, 800));
        assertThrows(IllegalArgumentException.class, () -> controller.begin(List.of(beat), 3, 800, 0));
    }

    private static EncounterWaveController.Beat beat(EntityType type, int delay, int x, int altitude) {
        return new EncounterWaveController.Beat(type, delay, x, altitude);
    }
    private static List<Rectangle> bounds(List<EncounterWaveController.Spawn> spawned) {
        return spawned.stream().map(EncounterWaveController.Spawn::bounds).toList();
    }
}
