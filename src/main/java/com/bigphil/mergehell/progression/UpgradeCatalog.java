package com.bigphil.mergehell.progression;

import com.bigphil.mergehell.combat.CombatStats;
import com.bigphil.mergehell.combat.DroneController;
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
                            "+4 knockback; slam enemies into platform sides for a blast", UpgradeTag.WEAPON, FORCE, 3,
                            combat(s -> s.withKnockback(s.knockback() + 4)))),
            Map.entry(UpgradeId.DASH_CACHE,
                    new UpgradeDefinition(UpgradeId.DASH_CACHE, "Dash Cache",
                            "-15% dash cooldown; next volley after dash crits and pierces", UpgradeTag.MOVEMENT, GLOBAL, 3,
                            s -> s.withDashCooldown(Math.max(0.55,
                                    s.dashCooldownMultiplier() * 0.85)))),
            Map.entry(UpgradeId.SHIELD_REBOOT,
                    new UpgradeDefinition(UpgradeId.SHIELD_REBOOT, "Hot Standby",
                            "+1 shield reboot", UpgradeTag.SHIELD, GLOBAL, 3,
                            s -> s.withShieldReboots(s.shieldReboots() + 1))),
            Map.entry(UpgradeId.DRONE_COPILOT,
                    new UpgradeDefinition(UpgradeId.DRONE_COPILOT, "AI Pair Programmer",
                            "Independent companions prioritize marks; add a partner, then upgrade both", UpgradeTag.DRONE, GLOBAL, 3,
                            s -> s.withDroneLevel(s.droneLevel() + 1))),
            Map.entry(UpgradeId.COMBO_WINDOW,
                    new UpgradeDefinition(UpgradeId.COMBO_WINDOW, "Long Transaction",
                            "+30 combo grace ticks; critical hits extend the current combo", UpgradeTag.COMBO, GLOBAL, 3,
                            s -> s.withComboGrace(s.comboGraceTicks() + 30))),
            Map.entry(UpgradeId.DEPENDENCY_CORE_COMMIT,
                    new UpgradeDefinition(UpgradeId.DEPENDENCY_CORE_COMMIT,
                            "Cherry-Pick Core", "Unlock Commit evolution",
                            UpgradeTag.EVOLUTION_CORE, COMMIT, 1, s -> s)),
            Map.entry(UpgradeId.DEPENDENCY_CORE_FORCE,
                    new UpgradeDefinition(UpgradeId.DEPENDENCY_CORE_FORCE, "Force Core",
                            "Unlock Force Push evolution", UpgradeTag.EVOLUTION_CORE,
                            FORCE, 1, s -> s)),
            weapon(UpgradeId.RAPID_PIPELINE, "Parallel Pipeline", "-1 firing cooldown tick",
                    WeaponId.RAPID_CI, combat(s -> s.withCooldown(Math.max(4, s.cooldownFrames() - 1)))),
            weapon(UpgradeId.RAPID_SIGNED_BUILDS, "Verified Builds", "+12% critical chance",
                    WeaponId.RAPID_CI, combat(s -> s.withCriticalChance(Math.min(0.75, s.criticalChance() + 0.12)))),
            weapon(UpgradeId.GC_COMPACTION, "Heap Compaction", "+20 direct damage",
                    WeaponId.GARBAGE_COLLECTOR, combat(s -> s.withDamage(s.damage() + 20))),
            weapon(UpgradeId.GC_GENERATIONS, "Generational Sweep", "+1 penetration",
                    WeaponId.GARBAGE_COLLECTOR, combat(s -> s.withPierces(s.pierces() + 1))),
            weapon(UpgradeId.FIREWALL_DENSITY, "Dense Rules", "+1 flame stream",
                    WeaponId.FIREWALL, combat(s -> s.withPelletsAndSpread(s.pellets() + 1,
                            Math.min(0.55, s.spreadRadians() + 0.05)))),
            weapon(UpgradeId.FIREWALL_PRESSURE, "Hot Rules", "+4 direct flame damage",
                    WeaponId.FIREWALL, combat(s -> s.withDamage(s.damage() + 4))),
            weapon(UpgradeId.BEAM_FOCUS, "Focused Refactor", "+20 beam damage",
                    WeaponId.REFACTOR_BEAM, combat(s -> s.withDamage(s.damage() + 20))),
            weapon(UpgradeId.BEAM_REFRACTION, "Extract Method", "+1 refraction after penetration",
                    WeaponId.REFACTOR_BEAM, combat(s -> s.withRicochets(s.ricochets() + 1))),
            core(UpgradeId.DEPENDENCY_CORE_RAPID, "Pipeline Core", WeaponId.RAPID_CI),
            core(UpgradeId.DEPENDENCY_CORE_GC, "Full-GC Core", WeaponId.GARBAGE_COLLECTOR),
            core(UpgradeId.DEPENDENCY_CORE_FIREWALL, "Zero-Trust Core", WeaponId.FIREWALL),
            core(UpgradeId.DEPENDENCY_CORE_BEAM, "Mass-Refactor Core", WeaponId.REFACTOR_BEAM),
            supply(UpgradeId.SUPPLY_REPAIR, "Repair Cache", "Restore 25 HP now"),
            supply(UpgradeId.SUPPLY_BOMB, "Emergency Cache", "Gain 1 bomb, up to 5"),
            supply(UpgradeId.SUPPLY_SHIELD, "Shield Cache", "Gain at least 180 shield ticks"));

    private static Map.Entry<UpgradeId, UpgradeDefinition> weapon(UpgradeId id, String name,
            String description, WeaponId weapon, UpgradeEffect effect) {
        return Map.entry(id, new UpgradeDefinition(id, name, description, UpgradeTag.WEAPON,
                Set.of(weapon), 3, effect));
    }

    private static Map.Entry<UpgradeId, UpgradeDefinition> core(UpgradeId id, String name, WeaponId weapon) {
        return Map.entry(id, new UpgradeDefinition(id, name, "Unlock " + weapon.name() + " evolution",
                UpgradeTag.EVOLUTION_CORE, Set.of(weapon), 1, s -> s));
    }

    private static Map.Entry<UpgradeId, UpgradeDefinition> supply(UpgradeId id, String name, String description) {
        return Map.entry(id, new UpgradeDefinition(id, name, description, UpgradeTag.SUPPLY, GLOBAL, 1, s -> s));
    }

    public static List<UpgradeDefinition> supplies() {
        return List.of(definition(UpgradeId.SUPPLY_REPAIR), definition(UpgradeId.SUPPLY_BOMB),
                definition(UpgradeId.SUPPLY_SHIELD));
    }

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

    /** Short, rank-specific lines for the three-card upgrade overlay. */
    public static List<String> droneDescriptionLines(int nextRank) {
        if (nextRank < 1 || nextRank > definition(UpgradeId.DRONE_COPILOT).maxRank()) {
            throw new IllegalArgumentException("Invalid next drone rank: " + nextRank);
        }
        var profile = DroneController.profile(nextRank);
        return switch (nextRank) {
            case 1 -> List.of("Deploy " + profile.count() + " drone.",
                    "Independent aim & fire;", "marked targets first.");
            case 2 -> List.of("Deploy " + profile.count() + " drones.",
                    "Split targets; one waits", "if a lone foe is weak.");
            default -> List.of("Keep " + profile.count() + " drones.",
                    "Faster, stronger shots;", "pierce " + profile.pierces() + " normal enemy.");
        };
    }

    private UpgradeCatalog() {
    }
}
