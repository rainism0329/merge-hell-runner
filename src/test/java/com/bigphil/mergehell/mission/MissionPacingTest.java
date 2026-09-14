package com.bigphil.mergehell.mission;

import com.bigphil.mergehell.engine.GameLoop;
import com.bigphil.mergehell.model.EntityType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MissionPacingTest {
    @Test void theStandardRouteHasFourMinutesTwentyOfTimedGameplayBeforeTheGate() {
        DarkMissionDefinition mission = DarkMissionDefinition.standard();
        assertEquals(260_000_000_000L, mission.bossGateStartTick() * GameLoop.LEGACY_STEP_NANOS);
        assertEquals(List.of(750, 1000), mission.segments().stream()
                .filter(segment -> segment.kind() == MissionSegment.Kind.RECOVERY)
                .map(MissionSegment::durationTicks).toList());
        EncounterDirector director = new EncounterDirector(mission, new Random(61));
        for (int tick = 0; tick < mission.bossGateStartTick(); tick++) {
            MissionSegment before = director.currentSegment();
            var commands = director.tick(new DirectorInput(tick, 0, .5, 0, tick));
            assertTrue(commands.stream().noneMatch(DirectorCommand.SpawnBoss.class::isInstance), "No early boss at " + tick);
            if (before.kind() == MissionSegment.Kind.RECOVERY) {
                assertTrue(commands.stream().noneMatch(DirectorCommand.Spawn.class::isInstance));
            }
        }
        assertEquals(MissionSegment.Kind.BOSS_GATE, director.currentSegment().kind());
        assertTrue(director.tick(new DirectorInput(mission.bossGateStartTick(), 0, .5, 0, 20_000))
                .stream().anyMatch(DirectorCommand.SpawnBoss.class::isInstance));
    }

    @Test void anEarlyKillTargetCannotEraseTheFinalStageTimer() {
        EncounterDirector director = finalStage(10);
        director.tick(new DirectorInput(1, 0, .5, 0, 0));
        assertEquals(9, director.segmentTicksRemaining(), "Entering the final stage must not force 00:00");
        for (int tick = 2; tick <= 9; tick++) {
            director.tick(new DirectorInput(tick, 0, .5, 0, EncounterDirector.BOSS_KILL_TARGET));
            assertEquals(10 - tick, director.segmentTicksRemaining());
            assertEquals(MissionSegment.Kind.COMBAT, director.currentSegment().kind());
        }
        director.tick(new DirectorInput(10, 0, .5, 0, EncounterDirector.BOSS_KILL_TARGET));
        assertEquals(MissionSegment.Kind.BOSS_GATE, director.currentSegment().kind());
    }

    @Test void theExpiredTimerWaitsForCurrentStageKillsAndTheGateWaitsForClearance() {
        int target = EncounterDirector.BOSS_KILL_TARGET;
        EncounterDirector director = new EncounterDirector(new DarkMissionDefinition(List.of(
                new MissionSegment(MissionSegment.Kind.COMBAT, 1, 1, "FIRST"),
                new MissionSegment(MissionSegment.Kind.COMBAT, 3, 1, "FINAL"),
                new MissionSegment(MissionSegment.Kind.BOSS_GATE, 2, 0, "BOSS")), 100), new Random(4));
        director.tick(new DirectorInput(1, 0, .5, 0, 100));
        for (int tick = 2; tick <= 20; tick++) director.tick(new DirectorInput(tick, 0, .5, 0, 100 + target - 1));
        MissionRouteProgress waiting = director.routeProgress(100 + target - 1);
        assertEquals(0, waiting.stageTicksRemaining());
        assertEquals(target - 1, waiting.kills());
        assertTrue(waiting.waitingForKills());
        assertTrue(waiting.routeFraction() < 1);
        director.tick(new DirectorInput(21, 0, .5, 3, 100 + target));
        for (int tick = 22; tick <= 30; tick++) {
            assertTrue(director.tick(new DirectorInput(tick, 0, .5, 3, 100 + target)).stream()
                    .noneMatch(command -> command instanceof DirectorCommand.Spawn || command instanceof DirectorCommand.SpawnBoss));
        }
        MissionRouteProgress gate = director.routeProgress(100 + target);
        assertTrue(gate.atBossGate());
        assertEquals(3, gate.activeHostiles());
        assertEquals(0, gate.stagesRemaining());
        assertFalse(gate.bossSpawned());
        assertTrue(director.tick(new DirectorInput(31, 0, .5, 0, 100 + target)).stream()
                .anyMatch(DirectorCommand.SpawnBoss.class::isInstance));
        assertTrue(director.routeProgress(100 + target).bossSpawned());
        assertEquals(1, director.routeProgress(100 + target).routeFraction());
        assertTrue(director.tick(new DirectorInput(32, 0, .5, 0, 100 + target)).stream()
                .noneMatch(DirectorCommand.SpawnBoss.class::isInstance));
    }

    @Test void routeStagesAndRemainingTimersAdvanceWithoutJumpingAhead() {
        var mission = DarkMissionDefinition.standard();
        EncounterDirector director = new EncounterDirector(mission, new Random(5));
        var start = director.routeProgress(0);
        assertEquals(1, start.stageNumber()); assertEquals(8, start.stageCount());
        assertEquals(7, start.stagesRemaining()); assertEquals(16_250, start.routeTicksRemaining());
        assertEquals(0, start.routeFraction());
        for (int tick = 0; tick < 2000; tick++) director.tick(new DirectorInput(tick, 0, .5, 0, 0));
        var recovery = director.routeProgress(0);
        assertEquals(2, recovery.stageNumber()); assertEquals(6, recovery.stagesRemaining());
        assertEquals(750, recovery.stageTicksRemaining());
        assertEquals(14_250, recovery.routeTicksRemaining());
        assertEquals(1 / 8.0, recovery.routeFraction());
    }

    @Test void anExplicitPracticeGateNeverWaitsForInvisibleEarlierKillRequirements() {
        EncounterDirector director = finalStage(10);
        director.advanceSegmentForTesting(0);
        assertTrue(director.tick(new DirectorInput(0, 0, .5, 0, 0)).stream()
                .anyMatch(DirectorCommand.SpawnBoss.class::isInstance));
    }

    @Test void openingSpawnsStayGentleAndElitesAreDelayedWithoutBurstSpawning() {
        var mission = DarkMissionDefinition.standard();
        EncounterDirector director = new EncounterDirector(mission, new Random(99));
        int openingSpawns = 0;
        for (int tick = 0; tick < mission.bossGateStartTick(); tick++) {
            var progress = director.routeProgress(tick);
            var commands = director.tick(new DirectorInput(tick, 0, 0, 2, tick));
            var spawns = commands.stream().filter(DirectorCommand.Spawn.class::isInstance)
                    .map(DirectorCommand.Spawn.class::cast).toList();
            assertTrue(spawns.size() <= 1);
            for (var spawn : spawns) {
                if (progress.stageNumber() == 1) {
                    openingSpawns++;
                    assertTrue(Set.of(EntityType.BUG, EntityType.CONFLICT).contains(spawn.type()));
                }
                if (progress.stageNumber() < 6) {
                    assertNotEquals(EntityType.TECHDEBT, spawn.type());
                    assertNotEquals(EntityType.FIREWALL, spawn.type());
                }
            }
        }
        assertTrue(openingSpawns > 0);
    }

    private static EncounterDirector finalStage(int ticks) {
        return new EncounterDirector(new DarkMissionDefinition(List.of(
                new MissionSegment(MissionSegment.Kind.COMBAT, ticks, 1, "FINAL"),
                new MissionSegment(MissionSegment.Kind.BOSS_GATE, 2, 0, "BOSS")), 100), new Random(3));
    }
}
