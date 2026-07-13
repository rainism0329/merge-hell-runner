package com.bigphil.mergehell.engine;

import com.bigphil.mergehell.GameState;
import com.bigphil.mergehell.combat.CombatEvent;
import com.bigphil.mergehell.combat.CombatEventSink;
import com.bigphil.mergehell.combat.WeaponId;
import com.bigphil.mergehell.progression.BuildProgress;
import com.bigphil.mergehell.progression.OverclockMeter;
import com.bigphil.mergehell.progression.RunBuild;
import com.bigphil.mergehell.progression.UpgradeDefinition;
import com.bigphil.mergehell.progression.UpgradeDraftService;
import com.bigphil.mergehell.mission.DarkMissionDefinition;
import com.bigphil.mergehell.mission.DirectorCommand;
import com.bigphil.mergehell.mission.DirectorInput;
import com.bigphil.mergehell.mission.EncounterDirector;
import com.bigphil.mergehell.mission.MissionSegment;

import java.util.List;
import java.util.Objects;
import java.util.Random;

public final class GameSession implements CombatEventSink {
    private final RunBuild runBuild;
    private final BuildProgress buildProgress = new BuildProgress();
    private final UpgradeDraftService draftService;
    private final OverclockMeter overclock = new OverclockMeter(100, 480);
    private final EncounterDirector director;
    private List<UpgradeDefinition> upgradeChoices = List.of();
    private GameState state = GameState.RUNNING;
    private GameState resumeState = GameState.RUNNING;
    private long worldTick;
    private boolean rerollAvailable;
    private int upgradeCount;
    private int maxHostiles;
    private int metricsHostiles;
    private int metricsProjectiles;
    private int metricsEnemyProjectiles;
    private int metricsParticles;
    private int metricsFloatingTexts;
    private int rejectedProjectiles;
    private long bossSpawnTick = -1;
    private long completionTick = -1;
    private boolean progressionComplete;
    private int hostileKills;

    public GameSession(long seed) {
        this(seed, WeaponId.COMMIT_CANNON);
    }

    public GameSession(long seed, WeaponId startingWeapon) {
        runBuild = new RunBuild(Objects.requireNonNull(startingWeapon, "startingWeapon"));
        draftService = new UpgradeDraftService(new Random(seed));
        director = new EncounterDirector(DarkMissionDefinition.standard(), new Random(seed ^ 0x5DEECE66DL));
    }

    public void tick(InputFrame input) {
        Objects.requireNonNull(input, "input");
        if (!isGameplayState(state)) return;
        worldTick++;
        overclock.tick();
    }

    public void awardBuildXp(int amount) {
        if (progressionComplete) return;
        buildProgress.addXp(amount);
        if (buildProgress.pendingChoices() > 0 && state != GameState.UPGRADE_SELECTION) {
            openUpgradeDraft();
        }
    }

    public void chooseUpgrade(int index) {
        requireUpgradeSelection();
        UpgradeDefinition selected = upgradeChoices.get(index);
        runBuild.apply(selected);
        upgradeCount++;
        buildProgress.consumePendingChoice();
        if (buildProgress.pendingChoices() > 0) {
            upgradeChoices = draftService.draft(runBuild);
            rerollAvailable = true;
        } else {
            upgradeChoices = List.of();
            rerollAvailable = false;
            state = resumeState;
        }
    }

    public void rerollUpgrades() {
        requireUpgradeSelection();
        upgradeChoices = draftService.reroll(runBuild);
        rerollAvailable = false;
    }

    public void setGameplayState(GameState value) {
        Objects.requireNonNull(value, "value");
        if (!isGameplayState(value)) {
            throw new IllegalArgumentException("not an active gameplay state: " + value);
        }
        resumeState = value;
        state = value;
    }

    public List<DirectorCommand> directMission(DirectorInput input) {
        if (!isGameplayState(state)) return List.of();
        return director.tick(new DirectorInput(input.worldTick(), input.playerX(),
                input.pressure(), input.activeHostiles(), hostileKills));
    }

    @Override
    public void accept(CombatEvent event) {
        Objects.requireNonNull(event, "event");
        if (event instanceof CombatEvent.EnemyKilled killed && killed.type().isHostile()) {
            hostileKills++;
            int baseValue = killed.type().pointValue;
            awardBuildXp(5 + Math.min(15, baseValue / 50));
            overclock.addCharge(8 + Math.min(12, baseValue / 50));
        } else if (event instanceof CombatEvent.BossNodeDestroyed node) {
            awardBuildXp(node.buildXp());
        }
    }

    private void openUpgradeDraft() {
        if (isGameplayState(state)) resumeState = state;
        try {
            upgradeChoices = draftService.draft(runBuild);
        } catch (IllegalStateException exhausted) {
            progressionComplete = true;
            buildProgress.discardPendingChoices();
            upgradeChoices = List.of();
            state = resumeState;
            return;
        }
        rerollAvailable = true;
        state = GameState.UPGRADE_SELECTION;
    }

    private void requireUpgradeSelection() {
        if (state != GameState.UPGRADE_SELECTION) {
            throw new IllegalStateException("upgrade selection is not active");
        }
    }

    private static boolean isGameplayState(GameState value) {
        return value == GameState.RUNNING || value == GameState.BOSS_WARNING
                || value == GameState.BOSS_FIGHT || value == GameState.LEVEL_CLEAR;
    }

    public GameState state() { return state; }
    public long worldTick() { return worldTick; }
    public RunBuild runBuild() { return runBuild; }
    public BuildProgress buildProgress() { return buildProgress; }
    public List<UpgradeDefinition> upgradeChoices() { return upgradeChoices; }
    public boolean isOverclocked() { return overclock.isActive(); }
    public int overclockCharge() { return overclock.charge(); }
    public int overclockActiveTicks() { return overclock.activeTicks(); }
    public double overclockRatio() { return overclock.ratio(); }
    public MissionSegment currentMissionSegment() { return director.currentSegment(); }
    public int missionSegmentTicksRemaining() { return director.segmentTicksRemaining(); }
    public boolean rerollAvailable() { return rerollAvailable; }
    public boolean progressionComplete() { return progressionComplete; }
    public int hostileKills() { return hostileKills; }
    public int missionObjectiveKills() { return director.killsInCurrentSegment(hostileKills); }
    public double missionProgress() { return director.progress(); }
    public int comboWindowTicks() { return runBuild.buildStats().comboGraceTicks(); }

    public void advanceMissionSegmentForTesting() {
        director.advanceSegmentForTesting(hostileKills);
    }

    public void advanceToBossGateForTesting() {
        hostileKills = Math.max(hostileKills, EncounterDirector.BOSS_KILL_TARGET);
        director.advanceToBossGateForTesting(hostileKills);
    }

    public void updateMetrics(int hostiles, int projectiles, int enemyProjectiles,
                              int particles, int floatingTexts, int rejectedProjectiles) {
        metricsHostiles = Math.max(0, hostiles);
        metricsProjectiles = Math.max(0, projectiles);
        metricsEnemyProjectiles = Math.max(0, enemyProjectiles);
        metricsParticles = Math.max(0, particles);
        metricsFloatingTexts = Math.max(0, floatingTexts);
        this.rejectedProjectiles = Math.max(this.rejectedProjectiles, rejectedProjectiles);
        maxHostiles = Math.max(maxHostiles, metricsHostiles);
    }

    public void markBossSpawned() { if (bossSpawnTick < 0) bossSpawnTick = worldTick; }
    public void markComplete() { if (completionTick < 0) completionTick = worldTick; }
    public SessionMetrics metrics() {
        return new SessionMetrics(metricsHostiles, metricsProjectiles, metricsEnemyProjectiles,
                metricsParticles, metricsFloatingTexts, maxHostiles, rejectedProjectiles,
                worldTick, upgradeCount, bossSpawnTick, completionTick);
    }
}
