package com.bigphil.mergehell.progression;

import com.bigphil.mergehell.combat.CombatStats;
import com.bigphil.mergehell.combat.WeaponId;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

public final class UpgradeCatalog {
    private static final Set<WeaponId> COMMIT = Set.of(WeaponId.COMMIT_CANNON);
    private static final Set<WeaponId> FORCE = Set.of(WeaponId.FORCE_PUSH);
    private static final Set<WeaponId> GLOBAL = Set.of();

    private static UpgradeEffect combat(UnaryOperator<CombatStats> effect) {
        return stats -> stats.withCombat(effect.apply(stats.combat()));
    }

    private static final Map<UpgradeId, UpgradeDefinition> DEFINITIONS = Map.ofEntries(
            Map.entry(UpgradeId.COMMIT_RICOCHET,
                    new UpgradeDefinition(UpgradeId.COMMIT_RICOCHET, "Cherry Pick",
                            "+1 projectile ricochet", UpgradeTag.WEAPON, COMMIT, 3,
                            combat(s -> s.withRicochets(s.ricochets() + 1)))),
            Map.entry(UpgradeId.COMMIT_CRITICAL,
                    new UpgradeDefinition(UpgradeId.COMMIT_CRITICAL, "Signed Commit",
                            "+10% critical chance", UpgradeTag.WEAPON, COMMIT, 3,
                            combat(s -> s.withCriticalChance(
                                    Math.min(0.75, s.criticalChance() + 0.10))))),
            Map.entry(UpgradeId.FORCE_EXTRA_PELLETS,
                    new UpgradeDefinition(UpgradeId.FORCE_EXTRA_PELLETS, "More Reviewers",
                            "+2 pellets", UpgradeTag.WEAPON, FORCE, 2,
                            combat(s -> s.withPelletsAndSpread(s.pellets() + 2,
                                    Math.min(0.62, s.spreadRadians() + 0.08))))),
            Map.entry(UpgradeId.FORCE_KNOCKBACK,
                    new UpgradeDefinition(UpgradeId.FORCE_KNOCKBACK, "Protected Branch",
                            "+4 knockback", UpgradeTag.WEAPON, FORCE, 3,
                            combat(s -> s.withKnockback(s.knockback() + 4)))),
            Map.entry(UpgradeId.DASH_CACHE,
                    new UpgradeDefinition(UpgradeId.DASH_CACHE, "Dash Cache",
                            "-15% dash cooldown", UpgradeTag.MOVEMENT, GLOBAL, 3,
                            s -> s.withDashCooldown(Math.max(0.55,
                                    s.dashCooldownMultiplier() * 0.85)))),
            Map.entry(UpgradeId.SHIELD_REBOOT,
                    new UpgradeDefinition(UpgradeId.SHIELD_REBOOT, "Hot Standby",
                            "+1 shield reboot", UpgradeTag.SHIELD, GLOBAL, 3,
                            s -> s.withShieldReboots(s.shieldReboots() + 1))),
            Map.entry(UpgradeId.DRONE_COPILOT,
                    new UpgradeDefinition(UpgradeId.DRONE_COPILOT, "AI Pair Programmer",
                            "+1 drone level", UpgradeTag.DRONE, GLOBAL, 3,
                            s -> s.withDroneLevel(s.droneLevel() + 1))),
            Map.entry(UpgradeId.COMBO_WINDOW,
                    new UpgradeDefinition(UpgradeId.COMBO_WINDOW, "Long Transaction",
                            "+30 combo grace ticks", UpgradeTag.COMBO, GLOBAL, 3,
                            s -> s.withComboGrace(s.comboGraceTicks() + 30))),
            Map.entry(UpgradeId.DEPENDENCY_CORE_COMMIT,
                    new UpgradeDefinition(UpgradeId.DEPENDENCY_CORE_COMMIT,
                            "Cherry-Pick Core", "Unlock Commit evolution",
                            UpgradeTag.EVOLUTION_CORE, COMMIT, 1, s -> s)),
            Map.entry(UpgradeId.DEPENDENCY_CORE_FORCE,
                    new UpgradeDefinition(UpgradeId.DEPENDENCY_CORE_FORCE, "Force Core",
                            "Unlock Force Push evolution", UpgradeTag.EVOLUTION_CORE,
                            FORCE, 1, s -> s)));

    public static UpgradeDefinition definition(UpgradeId id) {
        UpgradeDefinition value = DEFINITIONS.get(id);
        if (value == null) {
            throw new IllegalArgumentException("Unknown upgrade: " + id);
        }
        return value;
    }

    public static Collection<UpgradeDefinition> all() {
        return List.copyOf(DEFINITIONS.values());
    }

    private UpgradeCatalog() {
    }
}
