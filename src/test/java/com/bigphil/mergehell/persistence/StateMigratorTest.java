package com.bigphil.mergehell.persistence;

import com.bigphil.mergehell.combat.WeaponId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StateMigratorTest {
    @Test
    void migratesSortsAndTruncatesLegacyScores() {
        MergeHellState state = new StateMigrator(() -> "40,200,bad,100,300,50").migrate(null);
        assertEquals(List.of(300, 200, 100, 50, 40), state.topScores);
        assertEquals(1, state.schemaVersion);
        assertTrue(state.legacyScoresMigrated);
    }

    @Test
    void invalidStateFallsBackWithoutThrowing() {
        MergeHellState invalid = new MergeHellState();
        invalid.schemaVersion = -20;
        invalid.settings.shakePercent = 900;
        MergeHellState repaired = new StateMigrator(() -> null).migrate(invalid);
        assertEquals(1, repaired.schemaVersion);
        assertEquals(Set.of(WeaponId.COMMIT_CANNON), repaired.unlockedWeapons);
        assertEquals(70, repaired.settings.shakePercent);
    }

    @Test
    void repairsCollectionsAndClampsSettings() {
        MergeHellState state = new MergeHellState();
        state.topScores = new java.util.ArrayList<>(java.util.Arrays.asList(-1, 30, null, 10));
        state.unlockedWeapons = null;
        state.completedMissions = null;
        state.settings.volumePercent = -5;
        MergeHellState repaired = new StateMigrator(() -> null).migrate(state);
        assertEquals(List.of(30, 10), repaired.topScores);
        assertEquals(0, repaired.settings.volumePercent);
        assertTrue(repaired.unlockedWeapons.contains(WeaponId.COMMIT_CANNON));
    }
}
