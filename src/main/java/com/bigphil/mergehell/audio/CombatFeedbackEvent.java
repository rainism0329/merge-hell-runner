package com.bigphil.mergehell.audio;

import java.util.Objects;

/** A confirmed gameplay action, emitted once after it succeeds rather than on every held input. */
public record CombatFeedbackEvent(Cue cue, float intensity) {
    public enum Cue {
        COMMIT_SHOT(45, 1), FORCE_SHOT(100, 1), HIT(35, 1), PLAYER_HURT(160, 3),
        DASH(110, 2), UPGRADE(350, 4), SUDO(650, 4), BOSS_PHASE(500, 5),
        RAPID_SHOT(35, 1), GC_SHOT(140, 1), FIREWALL_SHOT(70, 1), BEAM_SHOT(160, 1),
        DRONE_SHOT(120, 0), BOSS_HIT(95, 2), BOSS_BREAK(350, 5), GC_PURGE(500, 5);

        private final int minimumIntervalMillis;
        private final int priority;

        Cue(int minimumIntervalMillis, int priority) {
            this.minimumIntervalMillis = minimumIntervalMillis;
            this.priority = priority;
        }

        public int minimumIntervalMillis() { return minimumIntervalMillis; }
        public int priority() { return priority; }
    }

    public CombatFeedbackEvent {
        Objects.requireNonNull(cue, "cue");
        if (!Float.isFinite(intensity) || intensity < 0 || intensity > 1) {
            throw new IllegalArgumentException("Feedback intensity must be between 0 and 1");
        }
    }

    public static CombatFeedbackEvent of(Cue cue) { return new CombatFeedbackEvent(cue, 1); }
}
