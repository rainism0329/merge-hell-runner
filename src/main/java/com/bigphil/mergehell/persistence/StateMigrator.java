package com.bigphil.mergehell.persistence;

import com.bigphil.mergehell.combat.WeaponId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;

public final class StateMigrator {
    private final LegacyScoreSource legacy;

    public StateMigrator(LegacyScoreSource legacy) { this.legacy = legacy; }

    public MergeHellState migrate(MergeHellState value) {
        MergeHellState state = value == null ? new MergeHellState() : value;
        if (state.schemaVersion < 0 || state.schemaVersion > 1) state = new MergeHellState();
        state.schemaVersion = 1;
        if (state.topScores == null) state.topScores = new ArrayList<>();
        state.topScores.removeIf(score -> score == null || score < 0);
        if (state.unlockedWeapons == null) state.unlockedWeapons = EnumSet.noneOf(WeaponId.class);
        else state.unlockedWeapons = state.unlockedWeapons.isEmpty()
                ? EnumSet.noneOf(WeaponId.class) : EnumSet.copyOf(state.unlockedWeapons);
        state.unlockedWeapons.add(WeaponId.COMMIT_CANNON);
        if (state.completedMissions == null) state.completedMissions = new HashSet<>();
        state.completedMissions.removeIf(mission -> mission == null || mission < 0);
        if (state.settings == null) state.settings = new MergeHellState.Settings();
        clampSettings(state.settings);
        state.refactorPoints = Math.max(0, state.refactorPoints);

        if (!state.legacyScoresMigrated && state.topScores.isEmpty()) importLegacy(state);
        state.legacyScoresMigrated = true;
        state.topScores.sort(Comparator.reverseOrder());
        if (state.topScores.size() > 5) state.topScores = new ArrayList<>(state.topScores.subList(0, 5));
        else state.topScores = new ArrayList<>(state.topScores);
        repairActiveRun(state);
        return state;
    }

    private void importLegacy(MergeHellState state) {
        String raw;
        try { raw = legacy == null ? null : legacy.read(); }
        catch (RuntimeException ignored) { return; }
        if (raw == null) return;
        for (String part : raw.split(",")) {
            try {
                int score = Integer.parseInt(part.trim());
                if (score >= 0) state.topScores.add(score);
            } catch (NumberFormatException ignored) { }
        }
    }

    private static void repairActiveRun(MergeHellState state) {
        if (state.activeRun == null) return;
        if (state.activeRun.mission < 0 || state.activeRun.lives <= 0) { state.activeRun = null; return; }
        if (state.activeRun.weapon == null) state.activeRun.weapon = WeaponId.COMMIT_CANNON;
        if (state.activeRun.upgradeRanks == null) state.activeRun.upgradeRanks = new java.util.EnumMap<>(com.bigphil.mergehell.progression.UpgradeId.class);
        state.activeRun.upgradeRanks.entrySet().removeIf(e -> e.getKey() == null || e.getValue() == null || e.getValue() < 0);
    }

    private static void clampSettings(MergeHellState.Settings settings) {
        settings.shakePercent = clamp(settings.shakePercent);
        settings.particlePercent = clamp(settings.particlePercent);
        settings.volumePercent = clamp(settings.volumePercent);
    }

    private static int clamp(int value) { return Math.max(0, Math.min(100, value)); }
}
