package com.bigphil.mergehell.combat;

import com.bigphil.mergehell.model.WeaponType;
import java.util.Map;
import java.util.Objects;

public final class WeaponCatalog {
    private static final Map<WeaponId, WeaponDefinition> DEFINITIONS = Map.of(
            WeaponId.COMMIT_CANNON,
            new WeaponDefinition(WeaponId.COMMIT_CANNON, "Commit Cannon", "Cherry Pick",
                    new CombatStats(25, 18, 1, 11, 0, 0, 2, 0.10, 1),
                    new String[]{"precision", "critical", "ricochet"}),
            WeaponId.FORCE_PUSH,
            new WeaponDefinition(WeaponId.FORCE_PUSH, "Force Push", "Force Push --force",
                    new CombatStats(16, 26, 5, 9, 0.42, 0, 14, 0.05, 0),
                    new String[]{"spread", "close-range", "knockback"}),
            WeaponId.RAPID_CI,
            new WeaponDefinition(WeaponId.RAPID_CI, "Rapid CI", "Pipeline Storm",
                    new CombatStats(15, 8, 1, 14, 0, 0, 1, 0.05, 0),
                    new String[]{"automatic", "heat", "burst"}),
            WeaponId.GARBAGE_COLLECTOR,
            new WeaponDefinition(WeaponId.GARBAGE_COLLECTOR, "Garbage Collector", "Full GC",
                    new CombatStats(80, 25, 1, 7, 0, 2, 8, 0, 0),
                    new String[]{"heavy", "piercing", "area-recovery"}),
            WeaponId.FIREWALL,
            new WeaponDefinition(WeaponId.FIREWALL, "Firewall", "Zero Trust",
                    new CombatStats(12, 12, 3, 7, 0.28, 1, 0, 0, 0),
                    new String[]{"burn", "short-range", "interception"}),
            WeaponId.REFACTOR_BEAM,
            new WeaponDefinition(WeaponId.REFACTOR_BEAM, "Refactor Beam", "Mass Refactor",
                    new CombatStats(80, 20, 1, 22, 0, 5, 0, 0, 0),
                    new String[]{"charge", "piercing", "refraction"}));

    public static WeaponDefinition definition(WeaponId id) {
        WeaponDefinition value = DEFINITIONS.get(id);
        if (value == null) {
            throw new IllegalArgumentException("Weapon not implemented: " + id);
        }
        return value;
    }

    public static WeaponId fromLegacy(WeaponType type) {
        return switch (Objects.requireNonNull(type, "type")) {
            case COMMIT -> WeaponId.COMMIT_CANNON;
            case SPREAD -> WeaponId.FORCE_PUSH;
            case RAPID -> WeaponId.RAPID_CI;
            case HEAVY -> WeaponId.GARBAGE_COLLECTOR;
            case FLAME -> WeaponId.FIREWALL;
            case LASER -> WeaponId.REFACTOR_BEAM;
        };
    }

    public static WeaponType toLegacy(WeaponId id) {
        return switch (Objects.requireNonNull(id, "id")) {
            case COMMIT_CANNON -> WeaponType.COMMIT;
            case FORCE_PUSH -> WeaponType.SPREAD;
            case RAPID_CI -> WeaponType.RAPID;
            case GARBAGE_COLLECTOR -> WeaponType.HEAVY;
            case FIREWALL -> WeaponType.FLAME;
            case REFACTOR_BEAM -> WeaponType.LASER;
        };
    }

    private WeaponCatalog() {
    }
}
