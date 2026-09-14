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
import java.util.Objects;
import java.util.Optional;

@Service(Service.Level.APP)
@State(name = "MergeHellRunner", storages = @Storage("mergeHellRunner.xml"))
public final class MergeHellStateService implements PersistentStateComponent<MergeHellState> {
    private static final String LEGACY_KEY = "com.bigphil.mergehell.topScores";
    private static final MergeHellStateService FALLBACK = new MergeHellStateService();
    private final StateMigrator migrator = new StateMigrator(this::readLegacyScores);
    private MergeHellState state = migrator.migrate(null);
    // Application-session ownership is intentionally not persisted across IDE restarts.
    private String activeOwner;
    private String claimedRunId;

    public static MergeHellStateService getInstance() {
        Application app = ApplicationManager.getApplication();
        return app == null ? FALLBACK : app.getService(MergeHellStateService.class);
    }

    @Override
    public synchronized MergeHellState getState() { return StateCopies.state(state); }

    @Override
    public synchronized void loadState(@NotNull MergeHellState value) {
        MergeHellState next = migrator.migrate(value);
        if (activeOwner != null) next.activeRun = StateCopies.run(state.activeRun);
        state = next;
    }

    public synchronized void recordScore(int score) {
        if (score < 0) return;
        state.topScores.add(score);
        state.topScores.sort(Comparator.reverseOrder());
        if (state.topScores.size() > 5) state.topScores = new ArrayList<>(state.topScores.subList(0, 5));
    }

    public synchronized void unlockWeapon(WeaponId weapon) { state.unlockedWeapons.add(Objects.requireNonNull(weapon)); }

    public synchronized boolean completeMission(int mission, int refactorPoints) {
        if (mission < 0 || mission > 4) return false;
        if (!state.completedMissions.add(mission)) return false;
        state.refactorPoints = (int) Math.min(Integer.MAX_VALUE, (long) state.refactorPoints + Math.max(0, refactorPoints));
        return true;
    }

    /** Publish rewards and the next safe entrance (or final score) in one persistence snapshot.
     * A second live window may earn its first-clear reward but cannot touch the owner's run. */
    public synchronized boolean settleCampaignMission(String ownerId, int mission, int refactorPoints,
                                                       MergeHellState.ActiveRun nextEntrance, int finalScore) {
        requireOwner(ownerId);
        if (mission < 0 || mission > 4) throw new IllegalArgumentException("Invalid mission");
        MergeHellState.ActiveRun next = StateCopies.run(nextEntrance);
        if (mission < 4) {
            CheckpointCodec.validate(next);
            if (next.mission != mission + 1) throw new IllegalArgumentException("Expected the next mission entrance");
            if (ownerId.equals(activeOwner) && !Objects.equals(claimedRunId, next.runId)) {
                throw new IllegalArgumentException("Checkpoint belongs to another run");
            }
        } else if (next != null || finalScore < 0) {
            throw new IllegalArgumentException("Final mission requires a score and no next entrance");
        }
        boolean firstClear = completeMission(mission, refactorPoints);
        if (mission < 4) {
            if (ownerId.equals(activeOwner)) {
                state.activeRun = next;
                state.checkpointNotice = "";
            }
        } else {
            recordScore(finalScore);
            clearCheckpoint(ownerId);
            releaseRun(ownerId);
        }
        return firstClear;
    }

    public synchronized void updateSettings(MergeHellState.Settings settings) {
        MergeHellState candidate = StateCopies.state(state);
        candidate.settings = StateCopies.settings(settings);
        state = migrator.migrate(candidate);
    }

    public synchronized void saveActiveRun(MergeHellState.ActiveRun run) {
        if (activeOwner != null) return; // Legacy callers cannot overwrite a claimed run.
        state.activeRun = StateCopies.run(run);
        state = migrator.migrate(state);
    }

    public synchronized void clearActiveRun() { if (activeOwner == null) state.activeRun = null; }

    /** Claim storage for a new run. A different live window retains exclusive ownership. */
    public synchronized boolean claimRun(String ownerId, String runId) {
        requireOwner(ownerId);
        if (!CheckpointCodec.validRunId(runId)) throw new IllegalArgumentException("Invalid run ID");
        if (activeOwner != null && !activeOwner.equals(ownerId)) return false;
        activeOwner = ownerId;
        claimedRunId = runId;
        return true;
    }

    /** Atomically claim and copy the current checkpoint, avoiding a read/claim race on Continue. */
    public synchronized Optional<MergeHellState.ActiveRun> claimCheckpoint(String ownerId) {
        requireOwner(ownerId);
        if (state.activeRun == null || activeOwner != null && !activeOwner.equals(ownerId)) return Optional.empty();
        activeOwner = ownerId;
        claimedRunId = state.activeRun.runId;
        return Optional.of(StateCopies.run(state.activeRun));
    }

    public synchronized Optional<MergeHellState.ActiveRun> readCheckpoint() {
        return Optional.ofNullable(StateCopies.run(state.activeRun));
    }

    public synchronized boolean saveCheckpoint(String ownerId, MergeHellState.ActiveRun run) {
        requireOwner(ownerId);
        if (!ownerId.equals(activeOwner) || run == null || !Objects.equals(claimedRunId, run.runId)) return false;
        MergeHellState.ActiveRun copy = StateCopies.run(run);
        CheckpointCodec.validate(copy);
        state.activeRun = copy;
        state.checkpointNotice = "";
        return true;
    }

    public synchronized boolean clearCheckpoint(String ownerId) {
        requireOwner(ownerId);
        if (!ownerId.equals(activeOwner)) return false;
        state.activeRun = null;
        state.checkpointNotice = "";
        return true;
    }

    public synchronized boolean releaseRun(String ownerId) {
        requireOwner(ownerId);
        if (!ownerId.equals(activeOwner)) return false;
        activeOwner = null;
        claimedRunId = null;
        return true;
    }

    public synchronized boolean ownedByAnotherWindow(String ownerId) {
        requireOwner(ownerId);
        return activeOwner != null && !activeOwner.equals(ownerId);
    }

    private static void requireOwner(String ownerId) {
        if (ownerId == null || ownerId.isBlank() || ownerId.length() > 128) throw new IllegalArgumentException("Invalid owner ID");
    }

    private String readLegacyScores() {
        try { return PropertiesComponent.getInstance().getValue(LEGACY_KEY); }
        catch (RuntimeException ignored) { return null; }
    }

}
