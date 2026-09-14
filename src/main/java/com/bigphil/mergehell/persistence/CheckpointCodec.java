package com.bigphil.mergehell.persistence;

import com.bigphil.mergehell.combat.WeaponCatalog;
import com.bigphil.mergehell.engine.GameSession;
import com.bigphil.mergehell.model.Player;
import com.bigphil.mergehell.progression.BuildProgress;
import com.bigphil.mergehell.progression.RunBuild;

import java.util.Objects;
import java.util.UUID;

/** Converts XML beans to validated immutable entry state. Never infers an interrupted director segment. */
public final class CheckpointCodec {
    public static final String LEVEL_START = "LEVEL_START";
    public static final double START_X = 100;
    public static final double START_Y = 480;

    public static MergeHellState.ActiveRun capture(String runId, int score, int nextLifeThreshold,
                                                 GameSession session, Player.Checkpoint player) {
        return capture(runId, score, nextLifeThreshold, session.checkpointAtMissionStart(), player);
    }

    public static MergeHellState.ActiveRun captureNextMission(String runId, int score, int nextLifeThreshold,
                                                            GameSession session, Player.Checkpoint player) {
        return capture(runId, score, nextLifeThreshold, session.checkpointForNextMission(), player);
    }

    private static MergeHellState.ActiveRun capture(String runId, int score, int nextLifeThreshold,
                                                   GameSession.Checkpoint snapshot, Player.Checkpoint player) {
        MergeHellState.ActiveRun run = new MergeHellState.ActiveRun();
        run.checkpointVersion = 2;
        run.checkpointKind = LEVEL_START;
        run.runId = runId;
        run.seed = snapshot.seed();
        run.mission = snapshot.mission();
        run.score = score;
        run.nextLifeThreshold = nextLifeThreshold;
        run.checkpointX = START_X;
        run.checkpointY = START_Y;
        run.player = playerData(player);
        run.session = sessionData(snapshot);
        run.lives = player.lives();
        run.weapon = snapshot.build().weapon();
        run.upgradeRanks.putAll(snapshot.build().ranks());
        run.worldTick = snapshot.worldTick();
        validate(run);
        return run;
    }

    public static void validate(MergeHellState.ActiveRun run) {
        Objects.requireNonNull(run, "run");
        if (run.checkpointVersion != 2 || !LEVEL_START.equals(run.checkpointKind)
                || !validRunId(run.runId) || run.mission < 0 || run.mission > 4 || run.score < 0
                || run.nextLifeThreshold <= 0 || run.checkpointX != START_X || run.checkpointY != START_Y) {
            throw new IllegalArgumentException("Unsupported or invalid level-start checkpoint");
        }
        GameSession session = GameSession.restoreCheckpoint(sessionCheckpoint(run));
        Player.Checkpoint player = playerCheckpoint(run);
        if (player.shieldRebootsUsed() > session.runBuild().buildStats().shieldReboots()
                || !player.temporaryWeapon() && player.weapon() != WeaponCatalog.toLegacy(session.runBuild().weapon())) {
            throw new IllegalArgumentException("Player resources do not match the saved build");
        }
    }

    public static boolean validRunId(String value) {
        if (value == null) return false;
        try { return UUID.fromString(value).toString().equalsIgnoreCase(value); }
        catch (IllegalArgumentException invalid) { return false; }
    }

    public static GameSession restoreSession(MergeHellState.ActiveRun run) {
        validate(run);
        return GameSession.restoreCheckpoint(sessionCheckpoint(run));
    }

    public static Player.Checkpoint playerCheckpoint(MergeHellState.ActiveRun run) {
        MergeHellState.PlayerData data = Objects.requireNonNull(run.player, "player data");
        return new Player.Checkpoint(data.hp, data.lives, data.bombs, data.sudoTicks, data.shieldTicks,
                data.invincibleTicks, data.cooldown, data.dashCooldown, data.meleeCooldown,
                data.temporaryWeapon, data.weapon, data.ammo, data.ammoReserve,
                data.shieldRebootsUsed, data.rapidHeat, data.combatRandomState);
    }

    public static GameSession.Checkpoint sessionCheckpoint(MergeHellState.ActiveRun run) {
        MergeHellState.SessionData data = Objects.requireNonNull(run.session, "session data");
        return new GameSession.Checkpoint(run.seed, run.mission, data.worldTick,
                new RunBuild.Checkpoint(data.weapon, data.ranks, data.weaponLevel, data.evolutionCoreInstalled, data.evolved),
                new BuildProgress.Checkpoint(data.buildLevel, data.currentXp, data.pendingChoices),
                data.overclockCharge, data.overclockActiveTicks, data.draftRandomState,
                data.upgradeCount, data.progressionComplete, data.hostileKills, data.maxHostiles);
    }

    private static MergeHellState.PlayerData playerData(Player.Checkpoint value) {
        MergeHellState.PlayerData out = new MergeHellState.PlayerData();
        out.hp = value.hp(); out.lives = value.lives(); out.bombs = value.bombs();
        out.sudoTicks = value.sudoTicks(); out.shieldTicks = value.shieldTicks(); out.invincibleTicks = value.invincibleTicks();
        out.cooldown = value.cooldown(); out.dashCooldown = value.dashCooldown(); out.meleeCooldown = value.meleeCooldown();
        out.temporaryWeapon = value.temporaryWeapon(); out.weapon = value.weapon(); out.ammo = value.ammo();
        out.ammoReserve.putAll(value.ammoReserve()); out.shieldRebootsUsed = value.shieldRebootsUsed();
        out.rapidHeat = value.rapidHeat(); out.combatRandomState = value.combatRandomState();
        return out;
    }

    private static MergeHellState.SessionData sessionData(GameSession.Checkpoint value) {
        MergeHellState.SessionData out = new MergeHellState.SessionData();
        out.weapon = value.build().weapon(); out.ranks.putAll(value.build().ranks());
        out.weaponLevel = value.build().weaponLevel(); out.evolutionCoreInstalled = value.build().evolutionCoreInstalled();
        out.evolved = value.build().evolved(); out.buildLevel = value.progress().level();
        out.currentXp = value.progress().currentXp(); out.pendingChoices = value.progress().pendingChoices();
        out.overclockCharge = value.overclockCharge(); out.overclockActiveTicks = value.overclockActiveTicks();
        out.draftRandomState = value.draftRandomState(); out.worldTick = value.worldTick();
        out.upgradeCount = value.upgradeCount(); out.hostileKills = value.hostileKills(); out.maxHostiles = value.maxHostiles();
        out.progressionComplete = value.progressionComplete();
        return out;
    }

    private CheckpointCodec() { }
}
