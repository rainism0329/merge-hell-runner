package com.bigphil.mergehell.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeaponCatalogTest {

    @Test
    void phaseOneWeaponsHaveDistinctCombatProfiles() {
        WeaponDefinition commit = WeaponCatalog.definition(WeaponId.COMMIT_CANNON);
        WeaponDefinition force = WeaponCatalog.definition(WeaponId.FORCE_PUSH);

        assertEquals(1, commit.baseStats().pellets());
        assertEquals(5, force.baseStats().pellets());
        assertTrue(commit.baseStats().ricochets() > force.baseStats().ricochets());
        assertTrue(force.baseStats().knockback() > commit.baseStats().knockback());
    }

    @Test
    void phaseOneWeaponsExposeTheirAuthoredDefinitions() {
        WeaponDefinition commit = WeaponCatalog.definition(WeaponId.COMMIT_CANNON);
        WeaponDefinition force = WeaponCatalog.definition(WeaponId.FORCE_PUSH);

        assertEquals("Commit Cannon", commit.displayName());
        assertEquals("Cherry Pick", commit.evolutionName());
        assertEquals(new CombatStats(25, 18, 1, 11, 0, 0, 2, 0.10, 1),
                commit.baseStats());
        assertArrayEquals(new String[]{"precision", "critical", "ricochet"}, commit.tags());
        assertEquals("Force Push", force.displayName());
        assertEquals("Force Push --force", force.evolutionName());
        assertEquals(new CombatStats(16, 26, 5, 9, 0.42, 0, 14, 0.05, 0),
                force.baseStats());
        assertArrayEquals(new String[]{"spread", "close-range", "knockback"}, force.tags());
    }

    @Test
    void everyWeaponHasAnAuthoredDefinitionAndRoundTripsThroughLegacyPickups() {
        for (WeaponId weapon : WeaponId.values()) {
            assertEquals(weapon, WeaponCatalog.definition(weapon).id());
            assertEquals(weapon, WeaponCatalog.fromLegacy(WeaponCatalog.toLegacy(weapon)));
            assertTrue(WeaponCatalog.definition(weapon).tags().length > 0);
        }
    }

    @Test
    void weaponDefinitionDefensivelyCopiesTags() {
        String[] source = {"precision"};
        WeaponDefinition definition = new WeaponDefinition(WeaponId.COMMIT_CANNON,
                "Commit Cannon", "Cherry Pick",
                new CombatStats(25, 18, 1, 11, 0, 0, 2, 0.10, 1), source);

        source[0] = "mutated";
        String[] firstRead = definition.tags();
        firstRead[0] = "also-mutated";

        assertArrayEquals(new String[]{"precision"}, definition.tags());
        assertNotSame(firstRead, definition.tags());
    }

    @Test
    void projectileSpecValidatesRequiredValues() {
        assertThrows(NullPointerException.class,
                () -> new ProjectileSpec(null, 1, 0, 0, false, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new ProjectileSpec(WeaponId.COMMIT_CANNON, 0, 0, 0, false, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new ProjectileSpec(WeaponId.COMMIT_CANNON, 1, 0, 0, false, -1, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new ProjectileSpec(WeaponId.COMMIT_CANNON, 1, 0, 0, false, 0, 0, -1));
    }

    @Test
    void projectileSpecCanReplaceVelocityWithoutLosingCombatState() {
        ProjectileSpec original = new ProjectileSpec(WeaponId.FORCE_PUSH, 16,
                1, 2, true, 3, 14, 4);

        ProjectileSpec moved = original.withVelocity(9, -3);

        assertEquals(new ProjectileSpec(WeaponId.FORCE_PUSH, 16,
                9, -3, true, 3, 14, 4), moved);
    }

    @Test
    void combatStatsValidatePositiveValuesAndCriticalChance() {
        assertThrows(IllegalArgumentException.class,
                () -> new CombatStats(0, 1, 1, 1, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new CombatStats(1, 0, 1, 1, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new CombatStats(1, 1, 0, 1, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new CombatStats(1, 1, 1, 0, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new CombatStats(1, 1, 1, 1, 0, 0, 0, -0.01, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new CombatStats(1, 1, 1, 1, 0, 0, 0, 1.01, 0));
    }

    @Test
    void combatStatsCopyMethodsOnlyReplaceRequestedValues() {
        CombatStats original = new CombatStats(25, 18, 1, 11, 0, 0, 2, 0.10, 1);

        assertEquals(new CombatStats(25, 18, 1, 11, 0, 0, 2, 0.10, 3),
                original.withRicochets(3));
        assertEquals(new CombatStats(25, 18, 1, 11, 0, 0, 2, 0.50, 1),
                original.withCriticalChance(0.50));
        assertEquals(new CombatStats(25, 18, 5, 11, 0.42, 0, 2, 0.10, 1),
                original.withPelletsAndSpread(5, 0.42));
        assertEquals(new CombatStats(25, 18, 1, 11, 0, 0, 14, 0.10, 1),
                original.withKnockback(14));
    }
}
