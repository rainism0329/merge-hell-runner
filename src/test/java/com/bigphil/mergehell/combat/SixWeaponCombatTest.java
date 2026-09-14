package com.bigphil.mergehell.combat;

import com.bigphil.mergehell.model.Player;
import com.bigphil.mergehell.model.Projectile;
import com.bigphil.mergehell.model.WeaponType;
import com.bigphil.mergehell.progression.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class SixWeaponCombatTest {
    @Test
    void allSixCoreWeaponsFireWithTheirOwnAttributionAndUpgradedStats() {
        for (WeaponId weapon : WeaponId.values()) {
            RunBuild build = new RunBuild(weapon);
            UpgradeDefinition upgrade = UpgradeCatalog.all().stream()
                    .filter(card -> card.tag() == UpgradeTag.WEAPON && card.isWeaponRelevant(weapon))
                    .findFirst().orElseThrow();
            build.apply(upgrade);
            Player player = new Player(100, 450);
            player.bindRunBuild(build);
            player.setCombatSeed(31);
            List<Projectile> shots = new ArrayList<>();
            for (int i = 0; i < 12 && shots.isEmpty(); i++) tick(player, true, shots);
            assertFalse(shots.isEmpty(), weapon.name());
            assertEquals(WeaponCatalog.toLegacy(weapon), player.getWeapon());
            assertTrue(shots.stream().allMatch(shot -> shot.getWeapon() == weapon));
            assertEquals(build.effectiveStats().pierces(), shots.get(0).getRemainingPierces());
            assertEquals(1, player.getShotSequence());
        }
    }

    @Test
    void everyTemporaryPickupUsesItsDeclaredDamageAndReturnsToTheCore() {
        for (WeaponType weapon : WeaponType.values()) {
            Player player = new Player(100, 450);
            player.giveWeapon(weapon, 1);
            List<Projectile> shots = new ArrayList<>();
            for (int i = 0; i < 12 && shots.isEmpty(); i++) tick(player, true, shots);
            WeaponId id = WeaponCatalog.fromLegacy(weapon);
            int damage = WeaponCatalog.definition(id).baseStats().damage();
            assertTrue(shots.stream().allMatch(shot -> shot.getWeapon() == id));
            assertTrue(shots.stream().allMatch(shot -> shot.getDamage() == damage
                    || shot.getDamage() == damage * 2));
            assertFalse(player.isUsingTemporaryWeapon());
            assertEquals(WeaponType.COMMIT, player.getWeapon());
        }
    }

    @Test
    void sixEvolutionsAreGatedAndIdempotentAndAlterRealProjectiles() {
        for (WeaponId weapon : WeaponId.values()) {
            RunBuild build = evolved(weapon);
            var stats = build.effectiveStats();
            assertFalse(build.tryEvolve());
            assertEquals(stats, build.effectiveStats());
            List<Projectile> volley = new WeaponFireController().fire(new FireRequest(
                    weapon, 100, 200, 1, stats, false, new Random(1), true));
            Projectile first = volley.get(0);
            switch (weapon) {
                case COMMIT_CANNON -> {
                    assertTrue(first.getRemainingRicochets() >= 3);
                    assertTrue(first.getSpec().effects().markTicks() > 0);
                }
                case FORCE_PUSH -> {
                    assertTrue(volley.size() >= 9);
                    assertTrue(first.getSpec().effects().blastRadius() > 0);
                }
                case RAPID_CI -> assertTrue(first.getRemainingPierces() > 0);
                case GARBAGE_COLLECTOR -> assertEquals(2, first.getSpec().effects().chainDepth());
                case FIREWALL -> {
                    assertEquals(3, first.getRemainingInterceptions());
                    assertTrue(first.getSpec().effects().residueTicks() > 0);
                }
                case REFACTOR_BEAM -> {
                    assertEquals(3, volley.size());
                    assertNotEquals(volley.get(0).getVy(), volley.get(2).getVy());
                    assertTrue(first.getRemainingRicochets() >= 2);
                }
            }
        }
    }

    @Test
    void pipelineStormConvertsHeatThresholdIntoBoundedRicochetingBurst() {
        Player player = new Player(100, 450);
        player.bindRunBuild(evolved(WeaponId.RAPID_CI));
        List<Projectile> shots = new ArrayList<>();
        int burstSize = 0;
        for (int i = 0; i < 100; i++) {
            int before = shots.size();
            tick(player, true, shots);
            if (shots.size() - before > 1) {
                burstSize = shots.size() - before;
                assertTrue(shots.get(before).getRemainingRicochets() >= 2);
                assertEquals(28, player.getRapidHeat());
                break;
            }
        }
        assertEquals(3, burstSize);
    }

    @Test
    void beamRequiresUninterruptedChargeBeforeSpendingAmmoOrReportingShot() {
        Player player = new Player(100, 450);
        player.giveWeapon(WeaponType.LASER, 2);
        List<Projectile> shots = new ArrayList<>();
        for (int i = 0; i < 9; i++) tick(player, true, shots);
        assertTrue(shots.isEmpty());
        assertEquals(2, player.getWeaponAmmo());
        assertEquals(0, player.getShotSequence());
        tick(player, false, shots);
        assertEquals(0, player.getBeamChargeTicks());
        for (int i = 0; i < 10; i++) tick(player, true, shots);
        assertEquals(1, player.getWeaponAmmo());
        assertEquals(1, player.getShotSequence());
        assertEquals(WeaponId.REFACTOR_BEAM, shots.get(0).getWeapon());
    }

    @Test
    void campaignTransitionPreservesBuildAndSpentResources() {
        Player player = new Player(100, 450);
        RunBuild build = evolved(WeaponId.FIREWALL);
        build.apply(UpgradeCatalog.definition(UpgradeId.SHIELD_REBOOT));
        player.bindRunBuild(build);
        player.takeDamage(999);
        player.useBomb();
        player.loseLife();
        player.giveWeapon(WeaponType.HEAVY, 7);
        player.beginNextLevel(300, 450);
        assertEquals(25, player.getHp());
        assertEquals(2, player.getBombs());
        assertEquals(2, player.getLives());
        assertEquals(7, player.getWeaponAmmo());
        assertEquals(0, player.getShieldRebootsRemaining());
        assertSame(build, player.getRunBuild());
        assertTrue(build.evolved());
        assertEquals(300, player.getX());
    }

    @Test
    void combatSeedReproducesCriticalSequence() {
        Player first = new Player(100, 450);
        Player second = new Player(100, 450);
        first.setCombatSeed(19);
        second.setCombatSeed(19);
        List<Projectile> a = new ArrayList<>(), b = new ArrayList<>();
        for (int i = 0; i < 240; i++) { tick(first, true, a); tick(second, true, b); }
        assertEquals(a.stream().map(Projectile::getSpec).toList(), b.stream().map(Projectile::getSpec).toList());
    }

    @Test
    void dashAndWeaponSwapCancelPartialBeamCharge() {
        Player player = new Player(100, 450);
        player.bindRunBuild(new RunBuild(WeaponId.REFACTOR_BEAM));
        List<Projectile> shots = new ArrayList<>();
        for (int i = 0; i < 9; i++) tick(player, true, shots);
        player.dash();
        assertEquals(0, player.getBeamChargeTicks());
        for (int i = 0; i < 8; i++) tick(player, true, shots);
        for (int i = 0; i < 9; i++) tick(player, true, shots);
        assertTrue(shots.isEmpty());
        player.giveWeapon(WeaponType.COMMIT, 2);
        assertEquals(0, player.getBeamChargeTicks());
    }

    private static RunBuild evolved(WeaponId weapon) {
        RunBuild build = new RunBuild(weapon);
        assertFalse(build.evolutionReady());
        for (UpgradeDefinition upgrade : UpgradeCatalog.all()) {
            if (upgrade.tag() == UpgradeTag.WEAPON && upgrade.isWeaponRelevant(weapon)) {
                for (int rank = 0; rank < upgrade.maxRank() && build.weaponLevel() < 5; rank++) build.apply(upgrade);
            }
        }
        assertFalse(build.tryEvolve());
        build.apply(UpgradeCatalog.all().stream().filter(card -> card.tag() == UpgradeTag.EVOLUTION_CORE
                && card.isWeaponRelevant(weapon)).findFirst().orElseThrow());
        assertTrue(build.evolutionReady());
        assertTrue(build.tryEvolve());
        return build;
    }

    private static void tick(Player player, boolean shoot, List<Projectile> shots) {
        player.update(false, false, false, shoot, 480, 10000, shots, List.of());
    }
}
