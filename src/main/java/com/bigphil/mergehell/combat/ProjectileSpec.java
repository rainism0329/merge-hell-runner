package com.bigphil.mergehell.combat;

import java.util.Objects;

public record ProjectileSpec(
        WeaponId weapon,
        int damage,
        double velocityX,
        double velocityY,
        boolean critical,
        int remainingPierces,
        double knockback,
        int remainingRicochets) {

    public ProjectileSpec {
        Objects.requireNonNull(weapon, "weapon");
        if (damage <= 0) {
            throw new IllegalArgumentException("damage must be positive");
        }
        if (remainingPierces < 0 || remainingRicochets < 0) {
            throw new IllegalArgumentException("counts cannot be negative");
        }
    }

    public ProjectileSpec withVelocity(double x, double y) {
        return new ProjectileSpec(weapon, damage, x, y,
                critical, remainingPierces, knockback, remainingRicochets);
    }
}
