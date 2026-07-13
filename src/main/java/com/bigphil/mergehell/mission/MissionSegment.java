package com.bigphil.mergehell.mission;

import java.util.Objects;

public record MissionSegment(Kind kind, int durationTicks, int threatPerSecond, String objective) {
    public enum Kind { COMBAT, ARENA, RECOVERY, BOSS_GATE }

    public MissionSegment {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(objective, "objective");
        if (durationTicks <= 0 || threatPerSecond < 0) {
            throw new IllegalArgumentException("invalid mission segment");
        }
    }

    public static MissionSegment recovery(int ticks) {
        return new MissionSegment(Kind.RECOVERY, ticks, 0, "RECOVER // REBUILD");
    }
}
