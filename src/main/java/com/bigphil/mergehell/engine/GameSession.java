package com.bigphil.mergehell.engine;

import com.bigphil.mergehell.GameState;
import com.bigphil.mergehell.combat.CombatEvent;
import com.bigphil.mergehell.combat.CombatEventSink;
import com.bigphil.mergehell.combat.CombatRandom;
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
import com.bigphil.mergehell.mission.MissionRouteProgress;

import java.util.List;
import java.util.Objects;

public final class GameSession implements CombatEventSink {
    public record Checkpoint(long seed, int mission, long worldTick, RunBuild.Checkpoint build,
                             BuildProgress.Checkpoint progress, int overclockCharge, int overclockActiveTicks,
                             long draftRandomState, int upgradeCount, boolean progressionComplete,
                             int hostileKills, int maxHostiles) {
        public Checkpoint {
            Objects.requireNonNull(build, "build");
            Objects.requireNonNull(progress, "progress");
            if (mission < 0 || mission > 4 || worldTick < 0 || worldTick == Long.MAX_VALUE
                    || progress.pendingChoices() != 0 || !CombatRandom.isValidState(draftRandomState)
                    || upgradeCount < 0 || hostileKills < 0 || maxHostiles < 0) {
                throw new IllegalArgumentException("Invalid mission-start session checkpoint");
            }
        }
    }

    private final long seed;
    private final RunBuild runBuild;
    private final BuildProgress buildProgress = new BuildProgress();
    private final UpgradeDraftService draftService;
    private final CombatRandom draftRandom;
    private final OverclockMeter overclock = new OverclockMeter(100, 480);
    private EncounterDirector director;
    private int mission;
    private boolean atMissionStart = true;
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
        this(seed, new RunBuild(Objects.requireNonNull(startingWeapon, "startingWeapon")));
    }

    private GameSession(long seed, RunBuild build) {
        this.seed = seed;
        runBuild = build;
        draftRandom = new CombatRandom(seed);
        draftService = new UpgradeDraftService(draftRandom);
        beginMission(0);
    }

    public void tick(InputFrame input) {
        Objects.requireNonNull(input, "input");
        if (!isGameplayState(state)) return;
        atMissionStart = false;
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
        atMissionStart = false;
        return director.tick(new DirectorInput(input.worldTick(), input.playerX(),
                input.pressure(), input.activeHostiles(), hostileKills));
    }

    @Override
    public void accept(CombatEvent event) {
        Objects.requireNonNull(event, "event");
        atMissionStart = false;
        if (event instanceof CombatEvent.EnemyKilled killed && killed.type().isHostile()) {
            hostileKills++;
            int baseValue = killed.type().pointValue;
            boolean bomb=killed.cause()==CombatEvent.DamageKind.BOMB;
            awardBuildXp(bomb?2:5 + Math.min(15, baseValue / 50));
            if(!bomb) overclock.addCharge(8 + Math.min(12, baseValue / 50));
        } else if (event instanceof CombatEvent.BossNodeDestroyed node) {
            awardBuildXp(node.buildXp());
        } else if (event instanceof CombatEvent.ProjectileReflected reflected) {
            overclock.addCharge(reflected.charge());
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
    public MissionRouteProgress routeProgress() { return director.routeProgress(hostileKills); }
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
        // The panel supplies a cumulative count for the current level/restore interval.
        this.rejectedProjectiles = Math.max(0, rejectedProjectiles);
        maxHostiles = Math.max(maxHostiles, metricsHostiles);
    }

    public void markBossSpawned() { if (bossSpawnTick < 0) bossSpawnTick = worldTick; }
    public void markComplete() { if (completionTick < 0) completionTick = worldTick; }
    public SessionMetrics metrics() {
        return new SessionMetrics(metricsHostiles, metricsProjectiles, metricsEnemyProjectiles,
                metricsParticles, metricsFloatingTexts, maxHostiles, rejectedProjectiles,
                worldTick, upgradeCount, bossSpawnTick, completionTick);
    }

    /** Establish an authored level entry. Build/resources carry over; director state starts fresh. */
    public void beginMission(int mission) {
        if (mission < 0 || mission > 4 || buildProgress.pendingChoices() != 0) {
            throw new IllegalStateException("Cannot begin a mission with an invalid index or unresolved upgrades");
        }
        this.mission = mission;
        director = new EncounterDirector(DarkMissionDefinition.standard(),
                new CombatRandom(seed ^ 0x5DEECE66DL ^ (mission * 0x9E3779B97F4A7C15L)));
        state = GameState.RUNNING;
        resumeState = GameState.RUNNING;
        upgradeChoices = List.of();
        rerollAvailable = false;
        bossSpawnTick = -1;
        completionTick = -1;
        atMissionStart = true;
    }

    public Checkpoint checkpointAtMissionStart() {
        if (!atMissionStart || state != GameState.RUNNING || buildProgress.pendingChoices() != 0) {
            throw new IllegalStateException("Only an unplayed mission entry can be checkpointed");
        }
        return checkpointForMission(mission);
    }

    /** Caller supplies a clear, grounded route boundary; no active combat entities are restored. */
    public Checkpoint checkpointAtSafeSegment() {
        if(state!=GameState.RUNNING || buildProgress.pendingChoices()!=0 || bossSpawnTick>=0)
            throw new IllegalStateException("An active, resolved route is required");
        return checkpointForMission(mission);
    }
    public EncounterDirector.Checkpoint directorCheckpoint() {return director.checkpoint();}
    public void restoreDirector(EncounterDirector.Checkpoint value) {
        if(value.startKills()>hostileKills)throw new IllegalArgumentException("Director kill count exceeds session");
        director.restore(value);atMissionStart=false;
    }

    /** A detached next entrance keeps the completed mission available for its results screen. */
    public Checkpoint checkpointForNextMission() {
        if (completionTick < 0 || mission >= 4 || buildProgress.pendingChoices() != 0) {
            throw new IllegalStateException("A completed mission with resolved upgrades is required");
        }
        return checkpointForMission(mission + 1);
    }

    private Checkpoint checkpointForMission(int targetMission) {
        return new Checkpoint(seed, targetMission, worldTick, runBuild.checkpoint(), buildProgress.checkpoint(),
                overclock.charge(), overclock.activeTicks(), draftRandom.checkpointState(), upgradeCount,
                progressionComplete, hostileKills, maxHostiles);
    }

    public static GameSession restoreCheckpoint(Checkpoint checkpoint) {
        Objects.requireNonNull(checkpoint, "checkpoint");
        GameSession restored = new GameSession(checkpoint.seed(), RunBuild.restoreCheckpoint(checkpoint.build()));
        restored.buildProgress.restoreCheckpoint(checkpoint.progress());
        restored.overclock.restoreCheckpoint(checkpoint.overclockCharge(), checkpoint.overclockActiveTicks());
        restored.draftRandom.restoreState(checkpoint.draftRandomState());
        restored.worldTick = checkpoint.worldTick();
        restored.upgradeCount = checkpoint.upgradeCount();
        restored.progressionComplete = checkpoint.progressionComplete();
        restored.hostileKills = checkpoint.hostileKills();
        restored.maxHostiles = checkpoint.maxHostiles();
        restored.beginMission(checkpoint.mission());
        return restored;
    }

    public long seed() { return seed; }
    public int mission() { return mission; }
}
