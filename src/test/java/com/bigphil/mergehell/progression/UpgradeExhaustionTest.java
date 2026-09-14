package com.bigphil.mergehell.progression;

import com.bigphil.mergehell.combat.WeaponId;
import com.bigphil.mergehell.model.Player;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class UpgradeExhaustionTest {
    @Test
    void exhaustedGlobalsNeverHideAvailableWeaponUpgrades() {
        for (WeaponId weapon : WeaponId.values()) {
            RunBuild build = new RunBuild(weapon);
            for (UpgradeDefinition card : UpgradeCatalog.all()) {
                if (card.tag() != UpgradeTag.WEAPON && card.tag() != UpgradeTag.EVOLUTION_CORE && !card.isSupply()) max(build, card);
            }
            var cards = new UpgradeDraftService(new Random(4)).draft(build);
            assertEquals(3, cards.size());
            assertTrue(cards.stream().allMatch(card -> card.isWeaponRelevant(weapon)));
        }
    }

    @Test
    void allSixCompletedBuildsKeepReceivingThreeLegalDistinctSupplies() {
        for (WeaponId weapon : WeaponId.values()) {
            RunBuild build = new RunBuild(weapon);
            for (UpgradeDefinition card : UpgradeCatalog.all()) {
                if (!card.isSupply() && (card.isWeaponRelevant(weapon)
                        || card.tag() != UpgradeTag.WEAPON && card.tag() != UpgradeTag.EVOLUTION_CORE)) max(build, card);
            }
            var service = new UpgradeDraftService(new Random(17));
            var ranks = build.ranks();
            for (int i = 0; i < 10; i++) {
                var cards = service.draft(build);
                assertEquals(3, cards.size());
                assertEquals(3, cards.stream().map(UpgradeDefinition::id).distinct().count());
                assertTrue(cards.stream().allMatch(UpgradeDefinition::isSupply));
                service.reroll(build);
                assertThrows(IllegalStateException.class, () -> service.reroll(build));
                build.apply(cards.get(0));
                assertEquals(ranks, build.ranks());
            }
        }
    }

    @Test
    void suppliesHaveImmediateConcreteEffectsWithoutGrowingTheBuild() {
        Player player = new Player(100, 450);
        player.takeDamage(60);
        player.useBomb();
        assertTrue(player.applySupply(UpgradeId.SUPPLY_REPAIR));
        assertEquals(65, player.getHp());
        assertTrue(player.applySupply(UpgradeId.SUPPLY_BOMB));
        assertEquals(3, player.getBombs());
        assertTrue(player.applySupply(UpgradeId.SUPPLY_SHIELD));
        assertEquals(180, player.getShieldTimer());
        assertFalse(player.applySupply(UpgradeId.DASH_CACHE));
        assertTrue(player.getRunBuild().ranks().isEmpty());
    }

    private static void max(RunBuild build, UpgradeDefinition card) {
        for (int rank = 0; rank < card.maxRank(); rank++) build.apply(card);
    }
}
