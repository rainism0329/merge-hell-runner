package com.bigphil.mergehell.persistence;

import com.bigphil.mergehell.combat.WeaponId;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;

@Service(Service.Level.APP)
@State(name = "MergeHellRunner", storages = @Storage("mergeHellRunner.xml"))
public final class MergeHellStateService implements PersistentStateComponent<MergeHellState> {
    private static final String LEGACY_KEY = "com.bigphil.mergehell.topScores";
    private static final MergeHellStateService FALLBACK = new MergeHellStateService();
    private final StateMigrator migrator = new StateMigrator(this::readLegacyScores);
    private MergeHellState state = migrator.migrate(null);

    public static MergeHellStateService getInstance() {
        Application app = ApplicationManager.getApplication();
        return app == null ? FALLBACK : app.getService(MergeHellStateService.class);
    }

    @Override
    public synchronized MergeHellState getState() { return copy(state); }

    @Override
    public synchronized void loadState(@NotNull MergeHellState value) { state = migrator.migrate(value); }

    public synchronized void recordScore(int score) {
        if (score < 0) return;
        state.topScores.add(score);
        state.topScores.sort(Comparator.reverseOrder());
        if (state.topScores.size() > 5) state.topScores = new ArrayList<>(state.topScores.subList(0, 5));
    }

    public synchronized void unlockWeapon(WeaponId weapon) { state.unlockedWeapons.add(weapon); }

    public synchronized boolean completeMission(int mission, int refactorPoints) {
        if (!state.completedMissions.add(mission)) return false;
        state.refactorPoints = Math.max(0, state.refactorPoints + Math.max(0, refactorPoints));
        return true;
    }

    public synchronized void updateSettings(MergeHellState.Settings settings) {
        MergeHellState candidate = copy(state);
        candidate.settings = copySettings(settings);
        state = migrator.migrate(candidate);
    }

    public synchronized void saveActiveRun(MergeHellState.ActiveRun run) {
        state.activeRun = copyRun(run);
        state = migrator.migrate(state);
    }

    public synchronized void clearActiveRun() { state.activeRun = null; }

    private String readLegacyScores() {
        try { return PropertiesComponent.getInstance().getValue(LEGACY_KEY); }
        catch (RuntimeException ignored) { return null; }
    }

    private static MergeHellState copy(MergeHellState source) {
        MergeHellState out = new MergeHellState();
        out.schemaVersion = source.schemaVersion;
        out.topScores = new ArrayList<>(source.topScores);
        out.unlockedWeapons = source.unlockedWeapons.isEmpty()
                ? java.util.EnumSet.noneOf(WeaponId.class) : java.util.EnumSet.copyOf(source.unlockedWeapons);
        out.completedMissions = new java.util.HashSet<>(source.completedMissions);
        out.refactorPoints = source.refactorPoints;
        out.legacyScoresMigrated = source.legacyScoresMigrated;
        out.settings = copySettings(source.settings);
        out.activeRun = copyRun(source.activeRun);
        return out;
    }

    private static MergeHellState.Settings copySettings(MergeHellState.Settings source) {
        MergeHellState.Settings out = new MergeHellState.Settings();
        if (source == null) return out;
        out.shakePercent = source.shakePercent; out.crt = source.crt; out.flashes = source.flashes;
        out.particlePercent = source.particlePercent; out.volumePercent = source.volumePercent;
        out.autoFire = source.autoFire; out.highContrast = source.highContrast;
        return out;
    }

    private static MergeHellState.ActiveRun copyRun(MergeHellState.ActiveRun source) {
        if (source == null) return null;
        MergeHellState.ActiveRun out = new MergeHellState.ActiveRun();
        out.mission = source.mission; out.checkpointX = source.checkpointX;
        out.checkpointY = source.checkpointY; out.lives = source.lives;
        out.weapon = source.weapon; out.worldTick = source.worldTick;
        out.upgradeRanks = new java.util.EnumMap<>(com.bigphil.mergehell.progression.UpgradeId.class);
        if (source.upgradeRanks != null) out.upgradeRanks.putAll(source.upgradeRanks);
        return out;
    }
}
