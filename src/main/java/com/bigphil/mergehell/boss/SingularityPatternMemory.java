package com.bigphil.mergehell.boss;

/** Bounded recent combat history. Selection happens only at the start of an announced attack. */
public final class SingularityPatternMemory {
    public enum Pattern { MEMORY_ECHO, BLUEPRINT_ECHO, KERNEL_ECHO, EVENT_HORIZON }
    public record Reading(Pattern pattern, double targetX, double targetY, int samples, int shots) { }
    public static final int CAPACITY = 90;
    private final double[] xs = new double[CAPACITY], ys = new double[CAPACITY];
    private final boolean[] shots = new boolean[CAPACITY];
    private int next, size;
    private Pattern previous;
    private int usedPatterns;

    public void observe(double x, double y, boolean fired) {
        if (!Double.isFinite(x) || !Double.isFinite(y)) return;
        xs[next] = x; ys[next] = y; shots[next] = fired;
        next = (next + 1) % CAPACITY; size = Math.min(CAPACITY, size + 1);
    }

    public Reading select(double fallbackX, double fallbackY) {
        double sumX = 0, sumY = 0, min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        int fired = 0;
        for (int i = 0; i < size; i++) {
            sumX += xs[i]; sumY += ys[i]; min = Math.min(min, xs[i]); max = Math.max(max, xs[i]);
            if (shots[i]) fired++;
        }
        Pattern desired = size < 30 ? Pattern.EVENT_HORIZON
                : max - min < 90 ? Pattern.MEMORY_ECHO
                : fired >= 4 ? Pattern.BLUEPRINT_ECHO
                : max - min >= 180 ? Pattern.KERNEL_ECHO : Pattern.EVENT_HORIZON;
        // Each four-attack cycle uses every echo once; behavior only changes their order.
        // This gives every build a predictable limit on counters, even when standing still.
        if (usedPatterns == 15) usedPatterns = 0;
        if (desired == previous && usedPatterns == 0)
            desired = Pattern.values()[(desired.ordinal() + 1) % Pattern.values().length];
        while ((usedPatterns & 1 << desired.ordinal()) != 0)
            desired = Pattern.values()[(desired.ordinal() + 1) % Pattern.values().length];
        usedPatterns |= 1 << desired.ordinal();
        previous = desired;
        return new Reading(desired, size == 0 ? fallbackX : sumX / size,
                size == 0 ? fallbackY : sumY / size, size, fired);
    }

    public void reset() { next = size = usedPatterns = 0; previous = null; }
}
