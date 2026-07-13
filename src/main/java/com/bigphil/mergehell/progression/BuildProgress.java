package com.bigphil.mergehell.progression;

public final class BuildProgress {
    private int level = 1;
    private int currentXp;
    private int pendingChoices;

    public int addXp(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("XP cannot be negative");
        }

        currentXp = Math.addExact(currentXp, amount);
        int newlyPending = 0;
        while (currentXp >= nextThreshold()) {
            currentXp -= nextThreshold();
            level++;
            pendingChoices++;
            newlyPending++;
        }
        return newlyPending;
    }

    public int currentXp() {
        return currentXp;
    }

    public int nextThreshold() {
        return Math.addExact(100, Math.multiplyExact(level - 1, 50));
    }

    public int pendingChoices() {
        return pendingChoices;
    }

    public void consumePendingChoice() {
        if (pendingChoices <= 0) {
            throw new IllegalStateException("no pending upgrade choice");
        }
        pendingChoices--;
    }

    public void discardPendingChoices() { pendingChoices = 0; }

    public int level() {
        return level;
    }
}
