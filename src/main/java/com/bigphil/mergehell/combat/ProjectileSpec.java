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
        int remainingRicochets,
        ProjectileEffects effects) {

    public ProjectileSpec(WeaponId weapon, int damage, double velocityX, double velocityY,
                          boolean critical, int remainingPierces, double knockback, int remainingRicochets) {
        this(weapon, damage, velocityX, velocityY, critical, remainingPierces, knockback,
                remainingRicochets, ProjectileEffects.NONE);
    }

    public ProjectileSpec {
        Objects.requireNonNull(weapon, "weapon");
        Objects.requireNonNull(effects, "effects");
        if (damage <= 0) {
            throw new IllegalArgumentException("damage must be positive");
        }
        if (remainingPierces < 0 || remainingRicochets < 0) {
            throw new IllegalArgumentException("counts cannot be negative");
        }
        if (!Double.isFinite(velocityX) || !Double.isFinite(velocityY)
                || !Double.isFinite(knockback) || knockback < 0) throw new IllegalArgumentException("Invalid projectile motion");
    }

    public ProjectileSpec withVelocity(double x, double y) {
        return new ProjectileSpec(weapon, damage, x, y,
                critical, remainingPierces, knockback, remainingRicochets, effects);
    }
}
