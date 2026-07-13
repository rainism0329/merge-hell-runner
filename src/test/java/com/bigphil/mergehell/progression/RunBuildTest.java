package com.bigphil.mergehell.progression;

import com.bigphil.mergehell.combat.CombatStats;
import com.bigphil.mergehell.combat.WeaponId;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunBuildTest {

    @Test
    void commitRicochetChangesEffectiveStats() {
        RunBuild build = new RunBuild(WeaponId.COMMIT_CANNON);

        build.apply(UpgradeCatalog.definition(UpgradeId.COMMIT_RICOCHET));

        assertEquals(2, build.effectiveStats().ricochets());
        assertEquals(1, build.rank(UpgradeId.COMMIT_RICOCHET));
        assertEquals(2, build.weaponLevel());
    }

    @Test
    void authoredWeaponEffectsApplyTheirCaps() {
        RunBuild commit = new RunBuild(WeaponId.COMMIT_CANNON);
        UpgradeDefinition critical = UpgradeCatalog.definition(UpgradeId.COMMIT_CRITICAL);
        for (int i = 0; i < critical.maxRank(); i++) {
            commit.apply(critical);
        }
        assertEquals(0.40, commit.effectiveStats().criticalChance(), 0.0001);

        RunBuild force = new RunBuild(WeaponId.FORCE_PUSH);
        UpgradeDefinition pellets = UpgradeCatalog.definition(UpgradeId.FORCE_EXTRA_PELLETS);
        for (int i = 0; i < pellets.maxRank(); i++) {
            force.apply(pellets);
        }
        force.apply(UpgradeCatalog.definition(UpgradeId.FORCE_KNOCKBACK));

        assertEquals(9, force.effectiveStats().pellets());
        assertEquals(0.58, force.effectiveStats().spreadRadians(), 0.0001);
        assertEquals(18, force.effectiveStats().knockback(), 0.0001);
    }

    @Test
    void globalUpgradesChangeEveryNonWeaponModifier() {
        RunBuild build = new RunBuild(WeaponId.COMMIT_CANNON);

        build.apply(UpgradeCatalog.definition(UpgradeId.DASH_CACHE));
        build.apply(UpgradeCatalog.definition(UpgradeId.SHIELD_REBOOT));
        build.apply(UpgradeCatalog.definition(UpgradeId.DRONE_COPILOT));
        build.apply(UpgradeCatalog.definition(UpgradeId.COMBO_WINDOW));

        assertEquals(0.85, build.buildStats().dashCooldownMultiplier(), 0.0001);
        assertEquals(1, build.buildStats().shieldReboots());
        assertEquals(1, build.buildStats().droneLevel());
        assertEquals(30, build.buildStats().comboGraceTicks());
    }

    @Test
    void upgradesCannotExceedTheirMaximumRank() {
        RunBuild build = new RunBuild(WeaponId.COMMIT_CANNON);
        UpgradeDefinition dash = UpgradeCatalog.definition(UpgradeId.DASH_CACHE);
        for (int i = 0; i < dash.maxRank(); i++) {
            build.apply(dash);
        }

        assertThrows(IllegalStateException.class, () -> build.apply(dash));
        assertEquals(3, build.rank(UpgradeId.DASH_CACHE));
        assertEquals(0.614125, build.buildStats().dashCooldownMultiplier(), 0.000001);
    }

    @Test
    void wrongWeaponUpgradesAndCoresAreRejected() {
        RunBuild build = new RunBuild(WeaponId.COMMIT_CANNON);

        assertThrows(IllegalArgumentException.class,
                () -> build.apply(UpgradeCatalog.definition(UpgradeId.FORCE_KNOCKBACK)));
        assertThrows(IllegalArgumentException.class,
                () -> build.apply(UpgradeCatalog.definition(UpgradeId.DEPENDENCY_CORE_FORCE)));

        assertEquals(1, build.weaponLevel());
        assertEquals(0, build.rank(UpgradeId.FORCE_KNOCKBACK));
        assertFalse(build.tryEvolve());
    }

    @Test
    void evolutionRequiresWeaponLevelFiveAndMatchingCore() {
        RunBuild build = new RunBuild(WeaponId.COMMIT_CANNON);
        assertFalse(build.tryEvolve());
        for (int i = 0; i < 4; i++) {
            build.apply(UpgradeCatalog.definition(i % 2 == 0
                    ? UpgradeId.COMMIT_RICOCHET : UpgradeId.COMMIT_CRITICAL));
        }
        assertEquals(5, build.weaponLevel());
        assertFalse(build.tryEvolve());

        build.apply(UpgradeCatalog.definition(UpgradeId.DEPENDENCY_CORE_COMMIT));

        assertTrue(build.tryEvolve());
        assertTrue(build.evolved());
        assertEquals(5, build.effectiveStats().ricochets());
        assertEquals(0.45, build.effectiveStats().criticalChance(), 0.0001);
        assertFalse(build.tryEvolve());
    }

    @Test
    void forceEvolutionAppliesItsAuthoredCombatProfile() {
        RunBuild build = new RunBuild(WeaponId.FORCE_PUSH);
        for (int i = 0; i < 4; i++) {
            build.apply(UpgradeCatalog.definition(i % 2 == 0
                    ? UpgradeId.FORCE_EXTRA_PELLETS : UpgradeId.FORCE_KNOCKBACK));
        }
        build.apply(UpgradeCatalog.definition(UpgradeId.DEPENDENCY_CORE_FORCE));

        assertTrue(build.tryEvolve());
        CombatStats stats = build.effectiveStats();
        assertEquals(13, stats.pellets());
        assertEquals(1, stats.pierces());
        assertEquals(34, stats.knockback(), 0.0001);
    }

    @Test
    void upgradeDefinitionsValidateAndDefensivelyCopySupportedWeapons() {
        Set<WeaponId> source = new HashSet<>();
        source.add(WeaponId.COMMIT_CANNON);
        UpgradeDefinition definition = new UpgradeDefinition(
                UpgradeId.COMMIT_RICOCHET, "Test", "Test effect", UpgradeTag.WEAPON,
                source, 1, stats -> stats);

        source.clear();

        assertEquals(Set.of(WeaponId.COMMIT_CANNON), definition.supportedWeapons());
        assertThrows(UnsupportedOperationException.class,
                () -> definition.supportedWeapons().add(WeaponId.FORCE_PUSH));
        assertThrows(NullPointerException.class,
                () -> new UpgradeDefinition(null, "Test", "Test effect", UpgradeTag.WEAPON,
                        Set.of(), 1, stats -> stats));
        assertThrows(NullPointerException.class,
                () -> new UpgradeDefinition(UpgradeId.COMMIT_RICOCHET, "Test", "Test effect",
                        UpgradeTag.WEAPON, null, 1, stats -> stats));
        assertThrows(IllegalArgumentException.class,
                () -> new UpgradeDefinition(UpgradeId.COMMIT_RICOCHET, "Test", "Test effect",
                        UpgradeTag.WEAPON, Set.of(), 0, stats -> stats));
        assertThrows(UnsupportedOperationException.class, () -> UpgradeCatalog.all().clear());
    }
}
