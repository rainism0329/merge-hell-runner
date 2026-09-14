package com.bigphil.mergehell.combat;

public record CombatStats(
        int damage,
        int cooldownFrames,
        int pellets,
        double speed,
        double spreadRadians,
        int pierces,
        double knockback,
        double criticalChance,
        int ricochets) {

    public CombatStats {
        if (damage <= 0 || cooldownFrames <= 0 || pellets <= 0 || speed <= 0) {
            throw new IllegalArgumentException("combat stats must be positive");
        }
        if (!Double.isFinite(speed) || !Double.isFinite(spreadRadians) || spreadRadians < 0
                || !Double.isFinite(knockback) || knockback < 0 || pierces < 0 || ricochets < 0
                || !Double.isFinite(criticalChance) || criticalChance < 0 || criticalChance > 1) {
            throw new IllegalArgumentException("criticalChance must be in [0, 1]");
        }
    }

    public CombatStats withDamage(int value) {
        return new CombatStats(value, cooldownFrames, pellets, speed, spreadRadians,
                pierces, knockback, criticalChance, ricochets);
    }

    public CombatStats withCooldown(int value) {
        return new CombatStats(damage, value, pellets, speed, spreadRadians,
                pierces, knockback, criticalChance, ricochets);
    }

    public CombatStats withPierces(int value) {
        return new CombatStats(damage, cooldownFrames, pellets, speed, spreadRadians,
                value, knockback, criticalChance, ricochets);
    }

    public CombatStats withRicochets(int value) {
        return new CombatStats(damage, cooldownFrames, pellets, speed, spreadRadians,
                pierces, knockback, criticalChance, value);
    }

    public CombatStats withCriticalChance(double value) {
        return new CombatStats(damage, cooldownFrames, pellets, speed, spreadRadians,
                pierces, knockback, value, ricochets);
    }

    public CombatStats withPelletsAndSpread(int pelletValue, double spreadValue) {
        return new CombatStats(damage, cooldownFrames, pelletValue, speed, spreadValue,
                pierces, knockback, criticalChance, ricochets);
    }

    public CombatStats withKnockback(double value) {
        return new CombatStats(damage, cooldownFrames, pellets, speed, spreadRadians,
                pierces, value, criticalChance, ricochets);
    }
}
