package com.bigphil.mergehell.mission;

import com.bigphil.mergehell.model.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

public final class EncounterDirector {
    public static final int BOSS_KILL_TARGET = 30;
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
    private int lastActiveHostiles;

    public EncounterDirector(DarkMissionDefinition mission, RandomGenerator random) {
        this.mission = Objects.requireNonNull(mission, "mission");
        this.random = Objects.requireNonNull(random, "random");
    }

    public List<DirectorCommand> tick(DirectorInput input) {
        Objects.requireNonNull(input, "input");
        lastActiveHostiles = input.activeHostiles();
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

        // The preceding combat stage enforces its own kill target. The gate only asks for clearance,
        // including when an explicitly unranked practice entry skips the earlier route.
        if (segment.kind() == MissionSegment.Kind.BOSS_GATE && !bossSpawned && input.activeHostiles() == 0) {
            bossSpawned = true;
            commands.add(DirectorCommand.SpawnBoss.INSTANCE);
        }

        if (segment.kind() == MissionSegment.Kind.COMBAT
                || segment.kind() == MissionSegment.Kind.ARENA) {
            addSpawns(segment, input, commands);
        } else {
            lastAvailableBudget = 0;
        }

        segmentTick = Math.min(segmentTick + 1, segment.durationTicks());
        boolean complete = segmentTick >= segment.durationTicks();
        boolean finalCombat = isFinalCombat(segment);
        if (finalCombat && killsInCurrentSegment(input.hostileKills()) < BOSS_KILL_TARGET) {
            complete = false;
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
            List<Candidate> pool = segmentIndex == 0 || input.pressure() > 0.75
                    ? STANDARD.subList(0, 2)
                    : segmentIndex >= 5 && input.pressure() < 0.30 && input.activeHostiles() <= 10
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
    public record Checkpoint(int segment, int tick, int startKills, double budget, int cooldown,
                             boolean announced, long randomState) { }
    public Checkpoint checkpoint() {
        if(bossSpawned || !(random instanceof com.bigphil.mergehell.combat.CombatRandom stream))
            throw new IllegalStateException("Director cannot be saved here");
        return new Checkpoint(segmentIndex,segmentTick,segmentStartHostileKills,budget,spawnCooldown,segmentAnnounced,stream.checkpointState());
    }
    public void restore(Checkpoint value) {
        if(value==null || value.segment()<0 || value.segment()>=mission.segments().size()
                || value.tick()<0 || value.tick()>mission.segments().get(value.segment()).durationTicks()
                || value.startKills()<0 || !Double.isFinite(value.budget()) || value.budget()<0 || value.budget()>1000
                || value.cooldown()<0 || value.cooldown()>1000 || !com.bigphil.mergehell.combat.CombatRandom.isValidState(value.randomState())
                || !(random instanceof com.bigphil.mergehell.combat.CombatRandom))
            throw new IllegalArgumentException("Invalid director checkpoint");
        segmentIndex=value.segment();segmentTick=value.tick();segmentStartHostileKills=value.startKills();
        budget=value.budget();spawnCooldown=value.cooldown();segmentAnnounced=value.announced();
        ((com.bigphil.mergehell.combat.CombatRandom)random).restoreState(value.randomState());
        bossSpawned=false;lastActiveHostiles=0;
    }
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

    private boolean isFinalCombat(MissionSegment segment) {
        return segmentIndex == mission.segments().size() - 2
                && segment.kind() == MissionSegment.Kind.COMBAT;
    }

    public MissionRouteProgress routeProgress(int totalHostileKills) {
        MissionSegment current = currentSegment();
        int count = mission.segments().size();
        if (current == null) return new MissionRouteProgress(count, count, 0, null, "ROUTE COMPLETE",
                0, 0, 0, 0, lastActiveHostiles, bossSpawned, 1);
        int target = isFinalCombat(current) ? BOSS_KILL_TARGET : 0;
        int kills = target == 0 ? 0 : Math.min(target, killsInCurrentSegment(totalHostileKills));
        double fraction = Math.min(1, segmentTick / (double) current.durationTicks());
        if (target > 0) fraction = Math.min(fraction, kills / (double) target);
        if (current.kind() == MissionSegment.Kind.BOSS_GATE) fraction = bossSpawned ? 1 : 0;
        int routeTicks = current.kind() == MissionSegment.Kind.BOSS_GATE ? 0 : segmentTicksRemaining();
        for (int i = segmentIndex + 1; i < count; i++) {
            MissionSegment later = mission.segments().get(i);
            if (later.kind() != MissionSegment.Kind.BOSS_GATE) routeTicks += later.durationTicks();
        }
        return new MissionRouteProgress(segmentIndex + 1, count, count - segmentIndex - 1,
                current.kind(), current.objective(), segmentTicksRemaining(), routeTicks, kills, target,
                lastActiveHostiles, bossSpawned, fraction);
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
        lastActiveHostiles = 0;
        budget = 0;
        spawnCooldown = 0;
    }
}
