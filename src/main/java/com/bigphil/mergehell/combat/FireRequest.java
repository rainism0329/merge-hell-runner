package com.bigphil.mergehell.combat;

import java.util.random.RandomGenerator;
import java.util.Objects;

public record FireRequest(
        WeaponId weapon, double originX, double originY, int facing,
        CombatStats stats, boolean overclocked,
        RandomGenerator random, boolean evolved, double shooterVelocityX) {
    public FireRequest(WeaponId weapon, double originX, double originY, int facing,
                       CombatStats stats, boolean overclocked, RandomGenerator random) {
        this(weapon, originX, originY, facing, stats, overclocked, random, false);
    }
    public FireRequest(WeaponId weapon, double originX, double originY, int facing,
                       CombatStats stats, boolean overclocked, RandomGenerator random, boolean evolved) {
        this(weapon, originX, originY, facing, stats, overclocked, random, evolved, 0);
    }
    public FireRequest {
        Objects.requireNonNull(weapon, "weapon");
        Objects.requireNonNull(stats, "stats");
        Objects.requireNonNull(random, "random");
        if (facing != -1 && facing != 1) throw new IllegalArgumentException("facing must be -1 or 1");
        if (!Double.isFinite(originX) || !Double.isFinite(originY)) throw new IllegalArgumentException("Invalid firing origin");
        if (!Double.isFinite(shooterVelocityX)) throw new IllegalArgumentException("Invalid shooter velocity");
    }
}
