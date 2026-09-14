package com.bigphil.mergehell.boss;

import java.util.Objects;

/** A simulation-clock preparation window shared by every boss encounter. */
public final class BossArrivalController {
    /** Approximately three seconds at the game's existing 16 ms simulation step. */
    public static final int DURATION_TICKS = 188;
    public static final int COUNTDOWN_SECONDS = 3;

    public enum Direction { LEFT, RIGHT }

    public record Snapshot(String bossName, Direction direction, int remainingTicks, int durationTicks) {
        public Snapshot {
            Objects.requireNonNull(bossName, "bossName");
            Objects.requireNonNull(direction, "direction");
            if (durationTicks <= 0 || remainingTicks < 0 || remainingTicks > durationTicks)
                throw new IllegalArgumentException("Invalid arrival duration");
        }

        public boolean active() { return remainingTicks > 0; }

        public int secondsRemaining() {
            return (int) (((long) remainingTicks * COUNTDOWN_SECONDS + durationTicks - 1) / durationTicks);
        }

        public double progress() { return 1 - remainingTicks / (double) durationTicks; }
    }

    private String bossName = "";
    private Direction direction = Direction.RIGHT;
    private int remainingTicks;

    /** Duplicate spawn commands cannot postpone an arrival that is already counting down. */
    public boolean begin(String name, Direction approach) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(approach, "approach");
        if (name.isBlank()) throw new IllegalArgumentException("Boss name must not be blank");
        if (active()) return false;
        bossName = name;
        direction = approach;
        remainingTicks = DURATION_TICKS;
        return true;
    }

    /** Returns true exactly once, on the active tick which completes preparation. */
    public boolean tick(boolean active) {
        return active && remainingTicks > 0 && --remainingTicks == 0;
    }

    public void reset() {
        bossName = "";
        direction = Direction.RIGHT;
        remainingTicks = 0;
    }

    public boolean active() { return remainingTicks > 0; }
    public int remainingTicks() { return remainingTicks; }

    public Snapshot snapshot() {
        return new Snapshot(bossName, direction, remainingTicks, DURATION_TICKS);
    }
}
