package com.bigphil.mergehell.engine;

import java.util.Objects;

public final class FixedStepAccumulator {
    private final long stepNanos;
    private final int maxCatchUpTicks;
    private long lastNanos = Long.MIN_VALUE;
    private long accumulated;

    public FixedStepAccumulator(long stepNanos, int maxCatchUpTicks) {
        if (stepNanos <= 0 || maxCatchUpTicks <= 0) throw new IllegalArgumentException();
        this.stepNanos = stepNanos;
        this.maxCatchUpTicks = maxCatchUpTicks;
    }

    public int consume(long nowNanos, Runnable tick) {
        Objects.requireNonNull(tick, "tick");
        if (lastNanos == Long.MIN_VALUE) { lastNanos = nowNanos; return 0; }
        long elapsed = Math.max(0, nowNanos - lastNanos);
        accumulated = Math.min(accumulated + elapsed, stepNanos * maxCatchUpTicks);
        lastNanos = nowNanos;
        int count = 0;
        while (accumulated >= stepNanos && count < maxCatchUpTicks) {
            tick.run();
            accumulated -= stepNanos;
            count++;
        }
        return count;
    }

    public void reset(long nowNanos) { lastNanos = nowNanos; accumulated = 0; }
}
