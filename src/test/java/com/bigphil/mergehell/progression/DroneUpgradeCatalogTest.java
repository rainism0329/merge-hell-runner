package com.bigphil.mergehell.progression;

import com.bigphil.mergehell.combat.DroneController;
import com.bigphil.mergehell.combat.WeaponId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DroneUpgradeCatalogTest {
    @Test void thirdRankStrengthensThePairInsteadOfDeployingAThirdDrone() {
        RunBuild build = new RunBuild(WeaponId.COMMIT_CANNON);
        var card = UpgradeCatalog.definition(UpgradeId.DRONE_COPILOT);
        build.apply(card);
        assertEquals(1, DroneController.profile(build.buildStats().droneLevel()).count());
        assertTrue(UpgradeCatalog.droneDescriptionLines(1).contains("Independent aim & fire;"));
        build.apply(card);
        var pair = DroneController.profile(build.buildStats().droneLevel());
        assertEquals(2, pair.count());
        assertTrue(String.join(" ", UpgradeCatalog.droneDescriptionLines(2)).contains("Split targets"));
        build.apply(card);
        var strengthened = DroneController.profile(build.buildStats().droneLevel());
        assertEquals(pair.count(), strengthened.count());
        assertTrue(strengthened.fireIntervalTicks() < pair.fireIntervalTicks());
        assertTrue(strengthened.damage(25) > pair.damage(25));
        assertEquals(1, strengthened.pierces());
        assertTrue(UpgradeCatalog.droneDescriptionLines(3).contains("pierce 1 normal enemy."));
        assertThrows(IllegalStateException.class, () -> build.apply(card));
    }

    @Test void descriptionsOnlyExposeLegalNextRanksAndCannotBeMutated() {
        assertThrows(IllegalArgumentException.class, () -> UpgradeCatalog.droneDescriptionLines(0));
        assertThrows(IllegalArgumentException.class, () -> UpgradeCatalog.droneDescriptionLines(4));
        assertThrows(UnsupportedOperationException.class,
                () -> UpgradeCatalog.droneDescriptionLines(1).add("Deploy a third drone"));
    }
}
