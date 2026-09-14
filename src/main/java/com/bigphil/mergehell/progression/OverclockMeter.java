package com.bigphil.mergehell.progression;

public final class OverclockMeter {
    private final int capacity;
    private final int durationTicks;
    private int charge;
    private int activeTicks;

    public OverclockMeter(int capacity, int durationTicks) {
        if (capacity <= 0 || durationTicks <= 0) {
            throw new IllegalArgumentException("capacity and duration must be positive");
        }
        this.capacity = capacity;
        this.durationTicks = durationTicks;
    }

    public void addCharge(int amount) {
        if (activeTicks > 0 || amount <= 0) return;
        charge = Math.min(capacity, Math.addExact(charge, amount));
        if (charge == capacity) activeTicks = durationTicks;
    }

    public void tick() {
        if (activeTicks > 0 && --activeTicks == 0) charge = 0;
    }

    public boolean isActive() { return activeTicks > 0; }
    public int charge() { return charge; }
    public int activeTicks() { return activeTicks; }
    public double ratio() { return isActive() ? 1.0 : charge / (double) capacity; }

    public void restoreCheckpoint(int charge, int activeTicks) {
        if (charge < 0 || charge > capacity || activeTicks < 0 || activeTicks > durationTicks
                || activeTicks > 0 && charge != capacity || activeTicks == 0 && charge == capacity) {
            throw new IllegalArgumentException("Invalid saved overclock state");
        }
        this.charge = charge;
        this.activeTicks = activeTicks;
    }
}
