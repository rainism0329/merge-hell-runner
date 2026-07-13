package com.bigphil.mergehell.progression;

import com.bigphil.mergehell.combat.CombatStats;
import com.bigphil.mergehell.combat.WeaponCatalog;
import com.bigphil.mergehell.combat.WeaponId;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class RunBuild {
    private final WeaponId weapon;
    private final EnumMap<UpgradeId, Integer> ranks = new EnumMap<>(UpgradeId.class);
    private BuildStats buildStats;
    private int weaponLevel = 1;
    private boolean evolutionCoreInstalled;
    private boolean evolved;

    public RunBuild(WeaponId weapon) {
        this.weapon = Objects.requireNonNull(weapon, "weapon");
        this.buildStats = BuildStats.base(WeaponCatalog.definition(weapon).baseStats());
    }

    public void apply(UpgradeDefinition upgrade) {
        Objects.requireNonNull(upgrade, "upgrade");
        if ((upgrade.tag() == UpgradeTag.WEAPON
                || upgrade.tag() == UpgradeTag.EVOLUTION_CORE)
                && !upgrade.isWeaponRelevant(weapon)) {
            throw new IllegalArgumentException(
                    "upgrade " + upgrade.id() + " does not support " + weapon);
        }

        int rank = ranks.getOrDefault(upgrade.id(), 0);
        if (rank >= upgrade.maxRank()) {
            throw new IllegalStateException("upgrade is max rank");
        }

        buildStats = Objects.requireNonNull(upgrade.effect().apply(buildStats),
                "upgrade effect result");
        ranks.put(upgrade.id(), rank + 1);
        if (upgrade.tag() == UpgradeTag.WEAPON) {
            weaponLevel = Math.min(5, weaponLevel + 1);
        }
        if (upgrade.tag() == UpgradeTag.EVOLUTION_CORE) {
            evolutionCoreInstalled = true;
        }
    }

    public boolean tryEvolve() {
        if (evolved || weaponLevel < 5 || !evolutionCoreInstalled) {
            return false;
        }

        CombatStats current = buildStats.combat();
        CombatStats evolvedStats = weapon == WeaponId.COMMIT_CANNON
                ? new CombatStats(current.damage(), current.cooldownFrames(), current.pellets(),
                        current.speed(), current.spreadRadians(), current.pierces(),
                        current.knockback(), Math.min(1, current.criticalChance() + 0.15),
                        current.ricochets() + 2)
                : new CombatStats(current.damage(), current.cooldownFrames(),
                        current.pellets() + 4, current.speed(), current.spreadRadians(),
                        current.pierces() + 1, current.knockback() + 12,
                        current.criticalChance(), current.ricochets());
        buildStats = buildStats.withCombat(evolvedStats);
        evolved = true;
        return true;
    }

    public WeaponId weapon() {
        return weapon;
    }

    public CombatStats effectiveStats() {
        return buildStats.combat();
    }

    public BuildStats buildStats() {
        return buildStats;
    }

    public int rank(UpgradeId id) {
        return ranks.getOrDefault(Objects.requireNonNull(id, "id"), 0);
    }

    public int weaponLevel() {
        return weaponLevel;
    }

    public boolean evolved() {
        return evolved;
    }

    public Map<UpgradeId, Integer> ranks() { return Map.copyOf(ranks); }
}
