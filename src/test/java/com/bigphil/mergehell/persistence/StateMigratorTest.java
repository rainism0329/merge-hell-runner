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
        assertEquals(2, state.schemaVersion);
        assertTrue(state.legacyScoresMigrated);
    }

    @Test
    void invalidStateFallsBackWithoutThrowing() {
        MergeHellState invalid = new MergeHellState();
        invalid.schemaVersion = -20;
        invalid.settings.shakePercent = 900;
        MergeHellState repaired = new StateMigrator(() -> null).migrate(invalid);
        assertEquals(2, repaired.schemaVersion);
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

    @Test
    void schemaOneIncompleteRunIsBackedUpWithoutInventingAContinuation() {
        MergeHellState old = new MergeHellState();
        old.schemaVersion = 1;
        old.topScores = new java.util.ArrayList<>(List.of(7_000));
        old.unlockedWeapons.add(WeaponId.FORCE_PUSH);
        old.completedMissions.add(0);
        old.refactorPoints = 40;
        old.settings.volumePercent = 17;
        old.activeRun = new MergeHellState.ActiveRun();
        old.activeRun.mission = 2;
        old.activeRun.lives = 2;
        old.activeRun.checkpointX = 3_400;

        MergeHellState migrated = new StateMigrator(() -> null).migrate(old);

        org.junit.jupiter.api.Assertions.assertNull(migrated.activeRun);
        org.junit.jupiter.api.Assertions.assertNotNull(migrated.legacyActiveRunBackup);
        assertEquals(3_400, migrated.legacyActiveRunBackup.checkpointX);
        assertEquals(List.of(7_000), migrated.topScores);
        assertTrue(migrated.unlockedWeapons.contains(WeaponId.FORCE_PUSH));
        assertEquals(Set.of(0), migrated.completedMissions);
        assertEquals(40, migrated.refactorPoints);
        assertEquals(17, migrated.settings.volumePercent);
        assertTrue(migrated.checkpointNotice.contains("lacks a complete"));
        assertEquals(1, old.schemaVersion, "Migration must not mutate a caller-owned bean");
        migrated.legacyActiveRunBackup.checkpointX = 0;
        assertEquals(3_400, old.activeRun.checkpointX);
    }
}
