package com.bigphil.mergehell.mission;

public record DirectorInput(long worldTick, double playerX, double pressure,
                            int activeHostiles, int hostileKills) {
    public DirectorInput(long worldTick, double playerX, double pressure, int activeHostiles) {
        this(worldTick, playerX, pressure, activeHostiles, 0);
    }

    public DirectorInput {
        pressure = Math.max(0, Math.min(1, pressure));
        if (activeHostiles < 0 || hostileKills < 0) {
            throw new IllegalArgumentException("director counts cannot be negative");
        }
    }
}
