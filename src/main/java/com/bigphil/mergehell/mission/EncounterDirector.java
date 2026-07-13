package com.bigphil.mergehell.mission;

import com.bigphil.mergehell.model.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

public final class EncounterDirector {
    public static final int BOSS_KILL_TARGET = 50;
    private record Candidate(EntityType type, int cost) { }

    private static final List<Candidate> STANDARD = List.of(
            new Candidate(EntityType.BUG, 4),
            new Candidate(EntityType.CONFLICT, 7),
            new Candidate(EntityType.CRASH, 8),
            new Candidate(EntityType.LOCK, 10));
    private static final List<Candidate> ELITES = List.of(
            new Candidate(EntityType.TECHDEBT, 18),
            new Candidate(EntityType.FIREWALL, 16));

    private final DarkMissionDefinition mission;
    private final RandomGenerator random;
    private int segmentIndex;
    private int segmentTick;
    private boolean segmentAnnounced;
    private boolean bossSpawned;
    private double budget;
    private int lastAvailableBudget;
    private int spawnCooldown;
    private int segmentStartHostileKills;

    public EncounterDirector(DarkMissionDefinition mission, RandomGenerator random) {
        this.mission = Objects.requireNonNull(mission, "mission");
        this.random = Objects.requireNonNull(random, "random");
    }

    public List<DirectorCommand> tick(DirectorInput input) {
        Objects.requireNonNull(input, "input");
        if (segmentIndex >= mission.segments().size()) return List.of();
        MissionSegment segment = mission.segments().get(segmentIndex);
        List<DirectorCommand> commands = new ArrayList<>();

        if (!segmentAnnounced) {
            segmentAnnounced = true;
            if (segment.kind() == MissionSegment.Kind.RECOVERY) {
                commands.add(new DirectorCommand.BeginRecovery(segment.durationTicks()));
            } else if (segment.kind() == MissionSegment.Kind.ARENA) {
                commands.add(new DirectorCommand.BeginArena(segment.objective(), segment.durationTicks()));
            }
        }

        if (segment.kind() == MissionSegment.Kind.BOSS_GATE && !bossSpawned
                && input.hostileKills() >= BOSS_KILL_TARGET && input.activeHostiles() == 0) {
            bossSpawned = true;
            commands.add(DirectorCommand.SpawnBoss.INSTANCE);
        }

        if (segment.kind() == MissionSegment.Kind.COMBAT
                || segment.kind() == MissionSegment.Kind.ARENA) {
            addSpawns(segment, input, commands);
        } else {
            lastAvailableBudget = 0;
        }

        segmentTick++;
        boolean complete = segmentTick >= segment.durationTicks();
        boolean finalCombat = segmentIndex == mission.segments().size() - 2
                && segment.kind() == MissionSegment.Kind.COMBAT;
        if (finalCombat && killsInCurrentSegment(input.hostileKills()) < BOSS_KILL_TARGET) {
            complete = false;
            segmentTick = segment.durationTicks();
        }
        if (segment.kind() == MissionSegment.Kind.BOSS_GATE && !bossSpawned) {
            complete = false;
            segmentTick = Math.min(segmentTick, segment.durationTicks());
        }
        if (complete) {
            segmentIndex++;
            segmentTick = 0;
            segmentStartHostileKills = input.hostileKills();
            segmentAnnounced = false;
            budget = 0;
            spawnCooldown = 0;
        }
        return List.copyOf(commands);
    }

    private void addSpawns(MissionSegment segment, DirectorInput input,
                           List<DirectorCommand> commands) {
        budget = Math.min(segment.threatPerSecond() * 2.0,
                budget + segment.threatPerSecond() / 60.0);
        lastAvailableBudget = (int) Math.floor(budget);
        if (spawnCooldown > 0) {
            spawnCooldown--;
            return;
        }
        int slots = Math.max(0, 14 - input.activeHostiles());
        if (slots > 0) {
            List<Candidate> pool = input.pressure() > 0.75
                    ? STANDARD.subList(0, 2)
                    : input.pressure() < 0.30 && input.activeHostiles() <= 10
                      && segment.kind() == MissionSegment.Kind.ARENA
                        ? concatCandidates() : STANDARD;
            List<Candidate> affordable = pool.stream().filter(c -> c.cost <= budget).toList();
            if (!affordable.isEmpty()) {
                Candidate chosen = affordable.get(random.nextInt(affordable.size()));
                budget -= chosen.cost;
                commands.add(new DirectorCommand.Spawn(chosen.type,
                        random.nextBoolean() ? 1 : -1, chosen.cost));
                spawnCooldown = Math.max(42, 84 - segment.threatPerSecond() * 3 / 4);
            }
        }
    }

    private static List<Candidate> concatCandidates() {
        List<Candidate> result = new ArrayList<>(STANDARD);
        result.addAll(ELITES);
        return result;
    }

    public int lastAvailableBudget() { return lastAvailableBudget; }
    public MissionSegment currentSegment() {
        return segmentIndex < mission.segments().size() ? mission.segments().get(segmentIndex) : null;
    }
    public int segmentTicksRemaining() {
        MissionSegment current = currentSegment();
        return current == null ? 0 : Math.max(0, current.durationTicks() - segmentTick);
    }

    public double progress() {
        int completed = mission.segments().stream().limit(segmentIndex)
                .mapToInt(MissionSegment::durationTicks).sum();
        int current = currentSegment() == null ? 0
                : Math.min(segmentTick, currentSegment().durationTicks());
        return Math.max(0, Math.min(1, (completed + current) / (double) mission.totalTicks()));
    }

    public int killsInCurrentSegment(int totalHostileKills) {
        return Math.max(0, totalHostileKills - segmentStartHostileKills);
    }

    /** Advances one authored segment without bypassing the director's spawn pipeline. */
    public void advanceSegmentForTesting(int totalHostileKills) {
        if (segmentIndex >= mission.segments().size() - 1) return;
        segmentIndex++;
        segmentTick = 0;
        segmentStartHostileKills = Math.max(0, totalHostileKills);
        segmentAnnounced = false;
        budget = 0;
        spawnCooldown = 0;
    }

    /** Places the director at the real boss gate so the next tick emits SpawnBoss once. */
    public void advanceToBossGateForTesting(int totalHostileKills) {
        segmentIndex = mission.segments().size() - 1;
        segmentTick = 0;
        segmentStartHostileKills = Math.max(0, totalHostileKills);
        segmentAnnounced = false;
        bossSpawned = false;
        budget = 0;
        spawnCooldown = 0;
    }
}
