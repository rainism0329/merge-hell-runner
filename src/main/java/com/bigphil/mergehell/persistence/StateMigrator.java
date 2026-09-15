package com.bigphil.mergehell.persistence;

import com.bigphil.mergehell.progression.CharacterId;
import com.bigphil.mergehell.progression.GameDifficulty;

import com.bigphil.mergehell.combat.WeaponId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;

public final class StateMigrator {
    private final LegacyScoreSource legacy;

    public StateMigrator(LegacyScoreSource legacy) { this.legacy = legacy; }

    public MergeHellState migrate(MergeHellState value) {
        MergeHellState state = value == null ? new MergeHellState() : StateCopies.state(value);
        int sourceVersion = state.schemaVersion;
        if (sourceVersion < 0) state = new MergeHellState();
        state.schemaVersion = 2;
        if(state.unlockedCharacters==null)state.unlockedCharacters=EnumSet.noneOf(CharacterId.class);
        state.unlockedCharacters.remove(null);
        state.unlockedCharacters.addAll(EnumSet.of(CharacterId.REPAIR,CharacterId.SCOUT,CharacterId.WARDEN));
        if(state.foundSecrets==null)state.foundSecrets=new HashSet<>();
        state.foundSecrets.removeIf(key->key==null || !key.matches("chapter[1-5]-secret"));
        if(state.rankedScores==null)state.rankedScores=new java.util.EnumMap<>(GameDifficulty.class);
        state.rankedScores.replaceAll((key,list)->new ArrayList<>(list.stream().filter(score->score!=null&&score>=0)
                .sorted(Comparator.reverseOrder()).limit(5).toList()));
        if (state.topScores == null) state.topScores = new ArrayList<>();
        state.topScores.removeIf(score -> score == null || score < 0);
        if (state.unlockedWeapons == null) state.unlockedWeapons = EnumSet.noneOf(WeaponId.class);
        else {
            state.unlockedWeapons.remove(null);
            state.unlockedWeapons = state.unlockedWeapons.isEmpty()
                    ? EnumSet.noneOf(WeaponId.class) : EnumSet.copyOf(state.unlockedWeapons);
        }
        state.unlockedWeapons.add(WeaponId.COMMIT_CANNON);
        if (state.completedMissions == null) state.completedMissions = new HashSet<>();
        state.completedMissions.removeIf(mission -> mission == null || mission < 0 || mission > 4);
        if (state.settings == null) state.settings = new MergeHellState.Settings();
        clampSettings(state.settings);
        state.refactorPoints = Math.max(0, state.refactorPoints);

        if (!state.legacyScoresMigrated && state.topScores.isEmpty()) importLegacy(state);
        state.legacyScoresMigrated = true;
        state.topScores.sort(Comparator.reverseOrder());
        if (state.topScores.size() > 5) state.topScores = new ArrayList<>(state.topScores.subList(0, 5));
        else state.topScores = new ArrayList<>(state.topScores);
        repairActiveRun(state, sourceVersion);
        if (state.checkpointNotice == null) state.checkpointNotice = "";
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

    private static void repairActiveRun(MergeHellState state, int sourceVersion) {
        if (state.activeRun == null) return;
        try {
            if (sourceVersion != 2) throw new IllegalArgumentException("Old checkpoint format");
            CheckpointCodec.validate(state.activeRun);
        } catch (RuntimeException invalid) {
            state.legacyActiveRunBackup = StateCopies.run(state.activeRun);
            state.activeRun = null;
            state.checkpointNotice = sourceVersion < 2
                    ? "Previous run lacks a complete level-start checkpoint. Scores, unlocks and settings were kept."
                    : "The saved run cannot be restored. Its data was backed up; scores, unlocks and settings were kept.";
        }
    }

    private static void clampSettings(MergeHellState.Settings settings) {
        settings.language = com.bigphil.mergehell.i18n.GameLanguage.fromTag(settings.language).tag();
        settings.shakePercent = clamp(settings.shakePercent);
        settings.particlePercent = clamp(settings.particlePercent);
        settings.volumePercent = clamp(settings.volumePercent);
        settings.backgroundVolumePercent = clamp(settings.backgroundVolumePercent);
    }

    private static int clamp(int value) { return Math.max(0, Math.min(100, value)); }
}
