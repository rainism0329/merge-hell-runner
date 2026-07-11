package com.bigphil.mergehell.combat;

import java.util.Map;

public final class WeaponCatalog {
    private static final Map<WeaponId, WeaponDefinition> DEFINITIONS = Map.of(
            WeaponId.COMMIT_CANNON,
            new WeaponDefinition(WeaponId.COMMIT_CANNON, "Commit Cannon", "Cherry Pick",
                    new CombatStats(25, 18, 1, 11, 0, 0, 2, 0.10, 1),
                    new String[]{"precision", "critical", "ricochet"}),
            WeaponId.FORCE_PUSH,
            new WeaponDefinition(WeaponId.FORCE_PUSH, "Force Push", "Force Push --force",
                    new CombatStats(16, 26, 5, 9, 0.42, 0, 14, 0.05, 0),
                    new String[]{"spread", "close-range", "knockback"}));

    public static WeaponDefinition definition(WeaponId id) {
        WeaponDefinition value = DEFINITIONS.get(id);
        if (value == null) {
            throw new IllegalArgumentException("Weapon not implemented: " + id);
        }
        return value;
    }

    private WeaponCatalog() {
    }
}
