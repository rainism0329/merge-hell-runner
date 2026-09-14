package com.bigphil.mergehell.engine;

import java.util.Objects;

public final class FixedStepAccumulator {
    private final long stepNanos;
    private final int maxCatchUpTicks;
    private final long maxAccumulatedNanos;
    private boolean initialized;
    private long lastNanos;
    private long accumulated;

    public FixedStepAccumulator(long stepNanos, int maxCatchUpTicks) {
        if (stepNanos <= 0 || maxCatchUpTicks <= 0
                || stepNanos > Long.MAX_VALUE / maxCatchUpTicks) throw new IllegalArgumentException();
        this.stepNanos = stepNanos;
        this.maxCatchUpTicks = maxCatchUpTicks;
        this.maxAccumulatedNanos = stepNanos * maxCatchUpTicks;
    }

    public int consume(long nowNanos, Runnable tick) {
        Objects.requireNonNull(tick, "tick");
        if (!initialized) { reset(nowNanos); return 0; }
        long elapsed = nowNanos - lastNanos;
        // Do not count the same interval twice if an injected clock moves backwards.
        if (elapsed < 0) return 0;
        accumulated = elapsed >= maxAccumulatedNanos - accumulated
                ? maxAccumulatedNanos : accumulated + elapsed;
        lastNanos = nowNanos;
        int count = 0;
        while (accumulated >= stepNanos && count < maxCatchUpTicks) {
            accumulated -= stepNanos;
            count++;
            tick.run();
        }
        return count;
    }

    public void reset(long nowNanos) {
        initialized = true;
        lastNanos = nowNanos;
        accumulated = 0;
    }
}
