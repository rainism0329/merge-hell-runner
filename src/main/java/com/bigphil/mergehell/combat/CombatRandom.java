package com.bigphil.mergehell.combat;

import java.util.Random;

/** The java.util.Random 48-bit stream with an explicit checkpoint state for combat rolls. */
public final class CombatRandom extends Random {
    private static final long serialVersionUID = 1L;
    private static final long MASK = (1L << 48) - 1;
    private long state;

    public CombatRandom(long seed) { super(seed); }

    @Override
    public synchronized void setSeed(long seed) { state = (seed ^ 0x5DEECE66DL) & MASK; }

    @Override
    protected synchronized int next(int bits) {
        state = (state * 0x5DEECE66DL + 0xBL) & MASK;
        return (int) (state >>> (48 - bits));
    }

    public synchronized long checkpointState() { return state; }

    public static boolean isValidState(long state) { return state >= 0 && state <= MASK; }

    public synchronized void restoreState(long value) {
        if (!isValidState(value)) throw new IllegalArgumentException("Invalid combat random state");
        state = value;
    }
}
