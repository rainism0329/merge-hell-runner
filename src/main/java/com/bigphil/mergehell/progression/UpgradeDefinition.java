package com.bigphil.mergehell.progression;

import com.bigphil.mergehell.combat.WeaponId;

import java.util.Objects;
import java.util.Set;

public record UpgradeDefinition(
        UpgradeId id,
        String title,
        String description,
        UpgradeTag tag,
        Set<WeaponId> supportedWeapons,
        int maxRank,
        UpgradeEffect effect) {

    public UpgradeDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(tag, "tag");
        supportedWeapons = Set.copyOf(Objects.requireNonNull(
                supportedWeapons, "supportedWeapons"));
        Objects.requireNonNull(effect, "effect");
        if (maxRank <= 0) {
            throw new IllegalArgumentException("maxRank must be positive");
        }
    }

    public boolean isWeaponRelevant(WeaponId weapon) {
        return (tag == UpgradeTag.WEAPON || tag == UpgradeTag.EVOLUTION_CORE)
                && supportedWeapons.contains(weapon);
    }

    public boolean isSupply() { return tag == UpgradeTag.SUPPLY; }
}
