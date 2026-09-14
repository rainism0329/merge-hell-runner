package com.bigphil.mergehell.mission;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EncounterDirectorTest {
    @Test
    void recoveryNeverSpawnsAndBossSpawnsExactlyOnce() {
        DarkMissionDefinition mission = new DarkMissionDefinition(List.of(
                MissionSegment.recovery(12),
                new MissionSegment(MissionSegment.Kind.BOSS_GATE, 4, 0, "BOSS")), 120);
        EncounterDirector director = new EncounterDirector(mission, new Random(3));
        int bosses = 0;
        for (int i = 0; i < 25; i++) {
            List<DirectorCommand> commands = director.tick(new DirectorInput(i, 0, .5, 0,
                    EncounterDirector.BOSS_KILL_TARGET));
            if (i < 12) assertTrue(commands.stream().noneMatch(DirectorCommand.Spawn.class::isInstance));
            bosses += (int) commands.stream().filter(DirectorCommand.SpawnBoss.class::isInstance).count();
        }
        assertEquals(1, bosses);
    }

    @Test
    void finalCombatWaitsForKillGoalBeforeOpeningBossGate() {
        DarkMissionDefinition mission = new DarkMissionDefinition(List.of(
                new MissionSegment(MissionSegment.Kind.COMBAT, 2, 24, "FINAL KILL TARGET"),
                new MissionSegment(MissionSegment.Kind.BOSS_GATE, 5, 0, "BOSS")), 120);
        EncounterDirector director = new EncounterDirector(mission, new Random(8));
        for (int i = 0; i < 5; i++) {
            List<DirectorCommand> commands = director.tick(new DirectorInput(i, 0, .5, 0, EncounterDirector.BOSS_KILL_TARGET - 1));
            assertTrue(commands.stream().noneMatch(DirectorCommand.SpawnBoss.class::isInstance));
            assertEquals(MissionSegment.Kind.COMBAT, director.currentSegment().kind());
        }
        director.tick(new DirectorInput(6, 0, .5, 0, EncounterDirector.BOSS_KILL_TARGET));
        List<DirectorCommand> gate = director.tick(new DirectorInput(7, 0, .5, 0, EncounterDirector.BOSS_KILL_TARGET));
        assertTrue(gate.stream().anyMatch(DirectorCommand.SpawnBoss.class::isInstance));
    }

    @Test
    void finalKillGoalCountsOnlyKillsEarnedInsideTheFinalCombatSegment() {
        DarkMissionDefinition mission = new DarkMissionDefinition(List.of(
                new MissionSegment(MissionSegment.Kind.COMBAT, 1, 1, "OPENING"),
                new MissionSegment(MissionSegment.Kind.COMBAT, 1, 1, "FINAL KILL TARGET"),
                new MissionSegment(MissionSegment.Kind.BOSS_GATE, 5, 0, "BOSS")), 120);
        EncounterDirector director = new EncounterDirector(mission, new Random(9));

        director.tick(new DirectorInput(0, 0, .5, 0, EncounterDirector.BOSS_KILL_TARGET));
        director.tick(new DirectorInput(1, 0, .5, 0, EncounterDirector.BOSS_KILL_TARGET));

        assertEquals(MissionSegment.Kind.COMBAT, director.currentSegment().kind());
        assertEquals(0, director.killsInCurrentSegment(EncounterDirector.BOSS_KILL_TARGET));

        director.tick(new DirectorInput(2, 0, .5, 0, EncounterDirector.BOSS_KILL_TARGET * 2));
        List<DirectorCommand> gate = director.tick(new DirectorInput(3, 0, .5, 0, EncounterDirector.BOSS_KILL_TARGET * 2));
        assertTrue(gate.stream().anyMatch(DirectorCommand.SpawnBoss.class::isInstance));
    }

    @Test
    void labBossSkipUsesTheNormalOneShotSpawnCommand() {
        EncounterDirector director = new EncounterDirector(DarkMissionDefinition.standard(), new Random(10));
        director.advanceToBossGateForTesting(EncounterDirector.BOSS_KILL_TARGET);

        List<DirectorCommand> first = director.tick(new DirectorInput(0, 0, 0, 0,
                EncounterDirector.BOSS_KILL_TARGET));
        List<DirectorCommand> second = director.tick(new DirectorInput(1, 0, 0, 0,
                EncounterDirector.BOSS_KILL_TARGET));

        assertEquals(1, first.stream().filter(DirectorCommand.SpawnBoss.class::isInstance).count());
        assertEquals(0, second.stream().filter(DirectorCommand.SpawnBoss.class::isInstance).count());
    }

    @Test
    void spawnCostsStayWithinAvailableThreat() {
        DarkMissionDefinition mission = new DarkMissionDefinition(List.of(
                new MissionSegment(MissionSegment.Kind.COMBAT, 600, 30, "FIGHT")), 120);
        EncounterDirector director = new EncounterDirector(mission, new Random(4));
        int lastSpawnTick = -100;
        for (int i = 0; i < 300; i++) {
            List<DirectorCommand> commands = director.tick(new DirectorInput(i, 0, .5, 3));
            int cost = commands.stream().filter(DirectorCommand.Spawn.class::isInstance)
                    .map(DirectorCommand.Spawn.class::cast).mapToInt(DirectorCommand.Spawn::cost).sum();
            assertTrue(cost <= director.lastAvailableBudget());
            long spawnCount = commands.stream().filter(DirectorCommand.Spawn.class::isInstance).count();
            assertTrue(spawnCount <= 1);
            if (spawnCount == 1) {
                assertTrue(i - lastSpawnTick >= 30);
                lastSpawnTick = i;
            }
        }
    }
}
