package com.bigphil.mergehell.mission;

import java.util.List;

public record DarkMissionDefinition(List<MissionSegment> segments, int bossHp) {
    public DarkMissionDefinition {
        segments = List.copyOf(segments);
        if (segments.isEmpty() || bossHp <= 0) throw new IllegalArgumentException();
    }

    public static DarkMissionDefinition standard() {
        return new DarkMissionDefinition(List.of(
                new MissionSegment(MissionSegment.Kind.COMBAT, 3_600, 24, "SURVIVE THE FIRST PUSH"),
                MissionSegment.recovery(600),
                new MissionSegment(MissionSegment.Kind.ARENA, 4_200, 30, "PURGE THE ERROR SWARM"),
                new MissionSegment(MissionSegment.Kind.COMBAT, 7_200, 34, "KEEP THE BUILD ALIVE"),
                MissionSegment.recovery(900),
                new MissionSegment(MissionSegment.Kind.ARENA, 6_000, 42, "SURVIVE THE CI LOCKDOWN"),
                new MissionSegment(MissionSegment.Kind.COMBAT, 9_000, 48, "CLEAR 50 HOSTILES"),
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
