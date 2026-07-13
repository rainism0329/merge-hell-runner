package com.bigphil.mergehell.progression;

import com.bigphil.mergehell.combat.CombatStats;

import java.util.Objects;

public record BuildStats(
        CombatStats combat,
        double dashCooldownMultiplier,
        int shieldReboots,
        int droneLevel,
        int comboGraceTicks) {

    public BuildStats {
        Objects.requireNonNull(combat, "combat");
        if (dashCooldownMultiplier <= 0
                || shieldReboots < 0
                || droneLevel < 0
                || comboGraceTicks < 0) {
            throw new IllegalArgumentException("build modifiers are invalid");
        }
    }

    public static BuildStats base(CombatStats combat) {
        return new BuildStats(combat, 1.0, 0, 0, 0);
    }

    public BuildStats withCombat(CombatStats value) {
        return new BuildStats(value, dashCooldownMultiplier, shieldReboots, droneLevel,
                comboGraceTicks);
    }

    public BuildStats withDashCooldown(double value) {
        return new BuildStats(combat, value, shieldReboots, droneLevel, comboGraceTicks);
    }

    public BuildStats withShieldReboots(int value) {
        return new BuildStats(combat, dashCooldownMultiplier, value, droneLevel,
                comboGraceTicks);
    }

    public BuildStats withDroneLevel(int value) {
        return new BuildStats(combat, dashCooldownMultiplier, shieldReboots, value,
                comboGraceTicks);
    }

    public BuildStats withComboGrace(int value) {
        return new BuildStats(combat, dashCooldownMultiplier, shieldReboots, droneLevel, value);
    }
}
