package com.bigphil.mergehell.progression;

import com.bigphil.mergehell.combat.WeaponId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpgradeDraftServiceTest {

    @Test
    void draftIsUniqueAndReservesFirstSlotForCurrentWeaponChoice() {
        UpgradeDraftService service = new UpgradeDraftService(new Random(7));
        RunBuild build = new RunBuild(WeaponId.FORCE_PUSH);

        List<UpgradeDefinition> cards = service.draft(build);

        assertEquals(3, cards.size());
        assertEquals(3, cards.stream().map(UpgradeDefinition::id).distinct().count());
        assertTrue(cards.get(0).isWeaponRelevant(WeaponId.FORCE_PUSH));
        assertTrue(cards.subList(1, 3).stream()
                .noneMatch(card -> card.tag() == UpgradeTag.WEAPON
                        || card.tag() == UpgradeTag.EVOLUTION_CORE));
    }

    @Test
    void sameSeedAndEquivalentBuildProduceTheSameDraftAndReroll() {
        UpgradeDraftService first = new UpgradeDraftService(new Random(1234));
        UpgradeDraftService second = new UpgradeDraftService(new Random(1234));
        RunBuild firstBuild = new RunBuild(WeaponId.COMMIT_CANNON);
        RunBuild secondBuild = new RunBuild(WeaponId.COMMIT_CANNON);

        List<UpgradeId> firstDraft = ids(first.draft(firstBuild));
        List<UpgradeId> secondDraft = ids(second.draft(secondBuild));
        List<UpgradeId> firstReroll = ids(first.reroll(firstBuild));
        List<UpgradeId> secondReroll = ids(second.reroll(secondBuild));

        assertEquals(firstDraft, secondDraft);
        assertEquals(firstReroll, secondReroll);
        assertFalse(firstDraft.equals(firstReroll));
    }

    @Test
    void maxRankCardsAreNotDrafted() {
        RunBuild build = new RunBuild(WeaponId.COMMIT_CANNON);
        UpgradeDefinition ricochet = UpgradeCatalog.definition(UpgradeId.COMMIT_RICOCHET);
        UpgradeDefinition dash = UpgradeCatalog.definition(UpgradeId.DASH_CACHE);
        for (int i = 0; i < ricochet.maxRank(); i++) {
            build.apply(ricochet);
        }
        for (int i = 0; i < dash.maxRank(); i++) {
            build.apply(dash);
        }

        List<UpgradeDefinition> cards = new UpgradeDraftService(new Random(4)).draft(build);

        assertFalse(ids(cards).contains(UpgradeId.COMMIT_RICOCHET));
        assertFalse(ids(cards).contains(UpgradeId.DASH_CACHE));
        assertTrue(cards.get(0).isWeaponRelevant(WeaponId.COMMIT_CANNON));
    }

    @Test
    void draftAndCatalogExposeImmutableCollections() {
        UpgradeDraftService service = new UpgradeDraftService(new Random(3));
        List<UpgradeDefinition> cards = service.draft(new RunBuild(WeaponId.COMMIT_CANNON));

        assertThrows(UnsupportedOperationException.class,
                () -> cards.add(UpgradeCatalog.definition(UpgradeId.DASH_CACHE)));
    }

    @Test
    void rerollCanOnlyBeUsedOnceAndRequiresAnInitialDraft() {
        UpgradeDraftService service = new UpgradeDraftService(new Random(9));
        RunBuild build = new RunBuild(WeaponId.COMMIT_CANNON);

        assertThrows(IllegalStateException.class, () -> service.reroll(build));
        service.draft(build);
        service.reroll(build);

        assertThrows(IllegalStateException.class, () -> service.reroll(build));
    }

    private static List<UpgradeId> ids(List<UpgradeDefinition> definitions) {
        return definitions.stream().map(UpgradeDefinition::id).toList();
    }
}
