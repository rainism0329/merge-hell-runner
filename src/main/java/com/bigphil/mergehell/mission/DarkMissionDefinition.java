package com.bigphil.mergehell.mission;

import java.util.List;

public record DarkMissionDefinition(List<MissionSegment> segments, int bossHp) {
    public DarkMissionDefinition {
        segments = List.copyOf(segments);
        if (segments.isEmpty() || bossHp <= 0) throw new IllegalArgumentException();
    }

    public static DarkMissionDefinition standard() {
        return new DarkMissionDefinition(List.of(
                // 260 seconds before the gate at the retained 16 ms simulation cadence.
                new MissionSegment(MissionSegment.Kind.COMBAT, 2_000, 18, "SURVIVE THE FIRST PUSH"),
                MissionSegment.recovery(750),
                new MissionSegment(MissionSegment.Kind.ARENA, 2_500, 24, "PURGE THE ERROR SWARM"),
                new MissionSegment(MissionSegment.Kind.COMBAT, 3_500, 28, "KEEP THE BUILD ALIVE"),
                MissionSegment.recovery(1_000),
                new MissionSegment(MissionSegment.Kind.ARENA, 3_000, 34, "SURVIVE THE CI LOCKDOWN"),
                new MissionSegment(MissionSegment.Kind.COMBAT, 3_500, 38,
                        "CLEAR " + EncounterDirector.BOSS_KILL_TARGET + " HOSTILES"),
                new MissionSegment(MissionSegment.Kind.BOSS_GATE, 300, 0, "LEGACY DEPENDENCY")),
                2_400);
    }

    public int totalTicks() {
        return segments.stream().mapToInt(MissionSegment::durationTicks).sum();
    }

    public int bossGateStartTick() {
        return segments.stream().limit(segments.size() - 1L)
                .mapToInt(MissionSegment::durationTicks).sum();
    }
}
