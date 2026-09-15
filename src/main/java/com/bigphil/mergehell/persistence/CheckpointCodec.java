package com.bigphil.mergehell.persistence;

import com.bigphil.mergehell.progression.CharacterId;
import com.bigphil.mergehell.progression.GameDifficulty;

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
    public static final String SAFE_SEGMENT = "SAFE_SEGMENT";
    public record Navigation(double x, java.util.Set<Integer> visited, java.util.Map<Integer,Integer> health,
                             java.util.Map<Integer,Integer> hatches,java.util.Set<Integer> heapUsed,java.util.Set<Integer> coins,
                             java.util.Map<Long,Integer> environmentBreakage,long environmentRetired) {
        public Navigation(double x,java.util.Set<Integer> visited,java.util.Map<Integer,Integer> health,
                          java.util.Map<Integer,Integer> hatches,java.util.Set<Integer> heapUsed,java.util.Set<Integer> coins) {
            this(x,visited,health,hatches,heapUsed,coins,java.util.Map.of(),0);
        }
    }
    public static MergeHellState.ActiveRun captureSegment(String runId,int score,int nextLifeThreshold,
            GameSession session,Player.Checkpoint player,Navigation navigation) {
        var run=capture(runId,score,nextLifeThreshold,session.checkpointAtSafeSegment(),player);
        run.checkpointVersion=3;run.checkpointKind=SAFE_SEGMENT;run.checkpointX=navigation.x();run.checkpointY=450;
        run.explorationVisited=new java.util.HashSet<>(navigation.visited());
        run.routeHealth=new java.util.HashMap<>(navigation.health());run.routeHatches=new java.util.HashMap<>(navigation.hatches());
        run.heapUsed=new java.util.HashSet<>(navigation.heapUsed());run.collectedCoins=new java.util.HashSet<>(navigation.coins());
        run.environmentBreakage=new java.util.HashMap<>(navigation.environmentBreakage());run.environmentRetired=navigation.environmentRetired();
        var value=session.directorCheckpoint();var data=new MergeHellState.DirectorData();
        data.segment=value.segment();data.tick=value.tick();data.startKills=value.startKills();data.budget=value.budget();
        data.cooldown=value.cooldown();data.announced=value.announced();data.randomState=value.randomState();run.director=data;
        validate(run);return run;
    }
    private static com.bigphil.mergehell.mission.EncounterDirector.Checkpoint director(MergeHellState.ActiveRun run) {
        var d=Objects.requireNonNull(run.director,"director");
        return new com.bigphil.mergehell.mission.EncounterDirector.Checkpoint(d.segment,d.tick,d.startKills,d.budget,d.cooldown,d.announced,d.randomState);
    }
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
        boolean entry=run.checkpointVersion==2 && LEVEL_START.equals(run.checkpointKind);
        boolean segment=run.checkpointVersion==3 && SAFE_SEGMENT.equals(run.checkpointKind);
        if ((!entry && !segment) || !validRunId(run.runId) || run.mission < 0 || run.mission > 4 || run.score < 0
                || run.nextLifeThreshold <= 0 || entry && (run.checkpointX!=START_X || run.checkpointY!=START_Y)
                || segment && (!Double.isFinite(run.checkpointX) || run.checkpointX<0
                || run.checkpointX>(run.mission==0?1_000_000:9900) || run.checkpointY!=450)) {
            throw new IllegalArgumentException("Unsupported or invalid level-start checkpoint");
        }
        GameSession session = GameSession.restoreCheckpoint(sessionCheckpoint(run));
        if(segment) {
            session.restoreDirector(director(run));
            var route=new com.bigphil.mergehell.world.ExplorationRoute(run.mission);route.restore(run.explorationVisited);
            if(route.solids().stream().anyMatch(b->b.intersects(new java.awt.Rectangle((int)run.checkpointX,450,30,30))))
                throw new IllegalArgumentException("Checkpoint intersects solid scenery");
            if(run.mission>=2)new com.bigphil.mergehell.world.ChapterRouteController(run.mission,480).restoreStructures(run.routeHealth,run.routeHatches);
            if(run.mission==1)com.bigphil.mergehell.world.HeapDistrictController.standard(run.seed).restoreUsedNodes(run.heapUsed);
            if(run.mission<2)new com.bigphil.mergehell.world.TraversalEnvironment(run.mission,
                    run.seed^0x5445525241494EL^(0x9E3779B97F4A7C15L*(run.mission+1)),480).restoreBreakage(run.environmentBreakage,run.environmentRetired);
            if(run.collectedCoins==null || run.collectedCoins.size()>5000 || run.collectedCoins.stream().anyMatch(i->i==null || i<0 || i>5000))
                throw new IllegalArgumentException("Invalid collected coins");
        }
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
        var session=GameSession.restoreCheckpoint(sessionCheckpoint(run));
        if(SAFE_SEGMENT.equals(run.checkpointKind))session.restoreDirector(director(run));
        return session;
    }

    public static Player.Checkpoint playerCheckpoint(MergeHellState.ActiveRun run) {
        MergeHellState.PlayerData data = Objects.requireNonNull(run.player, "player data");
        return new Player.Checkpoint(data.hp, data.lives, data.bombs, data.sudoTicks, data.shieldTicks,
                data.invincibleTicks, data.cooldown, data.dashCooldown, data.meleeCooldown,
                data.temporaryWeapon, data.weapon, data.ammo, data.ammoReserve,
                data.shieldRebootsUsed, data.rapidHeat, data.combatRandomState,data.bombCooldown);
    }

    public static GameSession.Checkpoint sessionCheckpoint(MergeHellState.ActiveRun run) {
        MergeHellState.SessionData data = Objects.requireNonNull(run.session, "session data");
        return new GameSession.Checkpoint(run.seed, run.mission, data.worldTick,
                new RunBuild.Checkpoint(data.weapon, data.ranks, data.weaponLevel, data.evolutionCoreInstalled, data.evolved,
                        data.character==null?CharacterId.REPAIR:data.character,data.difficulty==null?GameDifficulty.STANDARD:data.difficulty),
                new BuildProgress.Checkpoint(data.buildLevel, data.currentXp, data.pendingChoices),
                data.overclockCharge, data.overclockActiveTicks, data.draftRandomState,
                data.upgradeCount, data.progressionComplete, data.hostileKills, data.maxHostiles);
    }

    private static MergeHellState.PlayerData playerData(Player.Checkpoint value) {
        MergeHellState.PlayerData out = new MergeHellState.PlayerData();
        out.hp = value.hp(); out.lives = value.lives(); out.bombs = value.bombs(); out.bombCooldown=value.bombCooldown();
        out.sudoTicks = value.sudoTicks(); out.shieldTicks = value.shieldTicks(); out.invincibleTicks = value.invincibleTicks();
        out.cooldown = value.cooldown(); out.dashCooldown = value.dashCooldown(); out.meleeCooldown = value.meleeCooldown();
        out.temporaryWeapon = value.temporaryWeapon(); out.weapon = value.weapon(); out.ammo = value.ammo();
        out.ammoReserve.putAll(value.ammoReserve()); out.shieldRebootsUsed = value.shieldRebootsUsed();
        out.rapidHeat = value.rapidHeat(); out.combatRandomState = value.combatRandomState();
        return out;
    }

    private static MergeHellState.SessionData sessionData(GameSession.Checkpoint value) {
        MergeHellState.SessionData out = new MergeHellState.SessionData();
        out.character=value.build().character();out.difficulty=value.build().difficulty();
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
