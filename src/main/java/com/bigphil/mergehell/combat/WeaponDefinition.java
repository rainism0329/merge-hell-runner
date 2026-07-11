package com.bigphil.mergehell.combat;

import java.util.Objects;

public record WeaponDefinition(
        WeaponId id,
        String displayName,
        String evolutionName,
        CombatStats baseStats,
        String[] tags) {

    public WeaponDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(evolutionName, "evolutionName");
        Objects.requireNonNull(baseStats, "baseStats");
        tags = Objects.requireNonNull(tags, "tags").clone();
    }

    @Override
    public String[] tags() {
        return tags.clone();
    }
}
