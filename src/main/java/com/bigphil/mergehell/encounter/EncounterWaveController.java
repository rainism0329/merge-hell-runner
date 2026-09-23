package com.bigphil.mergehell.encounter;

import com.bigphil.mergehell.model.EntityType;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/** Authored reinforcement timing. Advance only with gameplay, never with paint or wall-clock time. */
public final class EncounterWaveController {
    /** Position is relative to the battle entrance; altitude is the feet's height above the floor. */
    public record Beat(EntityType type, int delayTicks, int offsetX, int altitude) {
        public Beat {
            Objects.requireNonNull(type);
            if (!type.isHostile() || delayTicks < 0 || offsetX < 0 || altitude < 0)
                throw new IllegalArgumentException("Invalid reinforcement beat");
        }
    }

    public record Spawn(EntityType type, int x, int y, int moveDir) {
        public Rectangle bounds() { return new Rectangle(x, y, type.width, type.height); }
    }

    private static final int MIN_SPAWN_INTERVAL = 24;
    private final List<Beat> pending = new ArrayList<>();
    private int elapsed = -1, nextAdmission;
    private int maxConcurrent;
    private double start, end;

    public void begin(List<Beat> beats, int maxConcurrent, double start, double end) {
        Objects.requireNonNull(beats);
        if (beats.isEmpty() || maxConcurrent < 1 || maxConcurrent > 8
                || !Double.isFinite(start) || !Double.isFinite(end) || end - start < 160)
            throw new IllegalArgumentException("Invalid encounter bounds or population budget");
        clear();
        pending.addAll(beats);
        pending.sort(Comparator.comparingInt(Beat::delayTicks));
        this.maxConcurrent = maxConcurrent;
        this.start = start;
        this.end = end;
    }

    /**
     * At most one reinforcement is admitted per call. Saturation, a blocked entrance or a rejected
     * spawn keeps the beat pending; it cannot complete a battle early or burst later in one frame.
     * Ground support is checked for floor units only; flyers may cross the chapter's shafts.
     */
    public int advance(int aliveHostiles, int groundY, List<Rectangle> solids,
                       List<Rectangle> occupied, Rectangle player,
                       Predicate<Rectangle> groundSupported, Predicate<Spawn> spawn) {
        if (!hasPending()) return 0;
        elapsed++;
        if (aliveHostiles >= maxConcurrent || elapsed < nextAdmission) return 0;
        for (int i = 0; i < pending.size(); i++) {
            Beat beat = pending.get(i);
            if (beat.delayTicks() > elapsed) break;
            Spawn candidate = findPlacement(beat, groundY, solids, occupied, player, groundSupported);
            if (candidate == null || !spawn.test(candidate)) continue;
            pending.remove(i);
            nextAdmission = elapsed + MIN_SPAWN_INTERVAL;
            return 1;
        }
        return 0;
    }

    private Spawn findPlacement(Beat beat, int groundY, List<Rectangle> solids,
                                List<Rectangle> occupied, Rectangle player,
                                Predicate<Rectangle> groundSupported) {
        int minX = (int) Math.ceil(start + 16);
        int maxX = (int) Math.floor(end - 16 - beat.type().width);
        int desiredX = Math.max(minX, Math.min(maxX, (int) Math.round(start) + beat.offsetX()));
        int baseY = groundY - beat.type().height - beat.altitude();
        Rectangle playerSpace = new Rectangle(player);
        playerSpace.grow(110, 32);
        int[] vertical = beat.altitude() == 0 ? new int[]{0} : new int[]{0, -24, 24, -48, 48};
        for (int distance = 0; distance <= maxX - minX + 16; distance += 16) {
            for (int sign : distance == 0 ? new int[]{1} : new int[]{1, -1}) {
                int x = desiredX + sign * distance;
                if (x < minX || x > maxX) continue;
                for (int dy : vertical) {
                    int y = baseY + dy;
                    if (y < 40 || y + beat.type().height > groundY) continue;
                    Rectangle bounds = new Rectangle(x, y, beat.type().width, beat.type().height);
                    Rectangle clearance = new Rectangle(bounds);
                    clearance.grow(3, 3);
                    if (clearance.intersects(playerSpace)
                            || solids.stream().anyMatch(clearance::intersects)) continue;
                    Rectangle separation = new Rectangle(bounds);
                    separation.grow(18, 12);
                    if (occupied.stream().anyMatch(separation::intersects)) continue;
                    if (beat.altitude() == 0 && !groundSupported.test(bounds)) continue;
                    int moveDir = player.getCenterX() < bounds.getCenterX() ? 1 : -1;
                    return new Spawn(beat.type(), x, y, moveDir);
                }
            }
        }
        return null;
    }

    public boolean hasPending() { return !pending.isEmpty(); }
    public int pendingCount() { return pending.size(); }
    public int elapsedTicks() { return elapsed; }
    public void clear() { pending.clear(); elapsed = -1; nextAdmission = 0; }
}
