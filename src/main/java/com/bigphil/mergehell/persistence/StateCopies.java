package com.bigphil.mergehell.persistence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

final class StateCopies {
    static MergeHellState state(MergeHellState source) {
        MergeHellState out = new MergeHellState();
        out.schemaVersion = source.schemaVersion;
        out.topScores = source.topScores == null ? null : new ArrayList<>(source.topScores);
        out.unlockedWeapons = source.unlockedWeapons == null ? null : new HashSet<>(source.unlockedWeapons);
        out.completedMissions = source.completedMissions == null ? null : new HashSet<>(source.completedMissions);
        out.refactorPoints = source.refactorPoints; out.legacyScoresMigrated = source.legacyScoresMigrated;
        out.settings = settings(source.settings); out.activeRun = run(source.activeRun);
        out.legacyActiveRunBackup = run(source.legacyActiveRunBackup); out.checkpointNotice = source.checkpointNotice;
        return out;
    }

    static MergeHellState.Settings settings(MergeHellState.Settings source) {
        MergeHellState.Settings out = new MergeHellState.Settings();
        if (source == null) return out;
        out.language = com.bigphil.mergehell.i18n.GameLanguage.fromTag(source.language).tag();
        out.shakePercent = source.shakePercent; out.crt = source.crt; out.flashes = source.flashes;
        out.particlePercent = source.particlePercent; out.volumePercent = source.volumePercent;
        out.muted = source.muted;
        out.backgroundVolumePercent = source.backgroundVolumePercent;
        out.autoFire = source.autoFire; out.highContrast = source.highContrast;
        return out;
    }

    static MergeHellState.ActiveRun run(MergeHellState.ActiveRun source) {
        if (source == null) return null;
        MergeHellState.ActiveRun out = new MergeHellState.ActiveRun();
        out.checkpointVersion = source.checkpointVersion; out.checkpointKind = source.checkpointKind;
        out.runId = source.runId; out.seed = source.seed; out.score = source.score; out.nextLifeThreshold = source.nextLifeThreshold;
        out.mission = source.mission; out.checkpointX = source.checkpointX; out.checkpointY = source.checkpointY;
        out.lives = source.lives; out.weapon = source.weapon; out.worldTick = source.worldTick;
        out.upgradeRanks = source.upgradeRanks == null ? null : new HashMap<>(source.upgradeRanks);
        out.player = player(source.player); out.session = session(source.session);
        return out;
    }

    private static MergeHellState.PlayerData player(MergeHellState.PlayerData source) {
        if (source == null) return null;
        MergeHellState.PlayerData out = new MergeHellState.PlayerData();
        out.hp = source.hp; out.lives = source.lives; out.bombs = source.bombs;
        out.sudoTicks = source.sudoTicks; out.shieldTicks = source.shieldTicks; out.invincibleTicks = source.invincibleTicks;
        out.cooldown = source.cooldown; out.dashCooldown = source.dashCooldown; out.meleeCooldown = source.meleeCooldown;
        out.temporaryWeapon = source.temporaryWeapon; out.weapon = source.weapon; out.ammo = source.ammo;
        out.ammoReserve = source.ammoReserve == null ? null : new HashMap<>(source.ammoReserve);
        out.shieldRebootsUsed = source.shieldRebootsUsed; out.rapidHeat = source.rapidHeat; out.combatRandomState = source.combatRandomState;
        return out;
    }

    private static MergeHellState.SessionData session(MergeHellState.SessionData source) {
        if (source == null) return null;
        MergeHellState.SessionData out = new MergeHellState.SessionData();
        out.weapon = source.weapon; out.ranks = source.ranks == null ? null : new HashMap<>(source.ranks);
        out.weaponLevel = source.weaponLevel; out.evolutionCoreInstalled = source.evolutionCoreInstalled; out.evolved = source.evolved;
        out.buildLevel = source.buildLevel; out.currentXp = source.currentXp; out.pendingChoices = source.pendingChoices;
        out.overclockCharge = source.overclockCharge; out.overclockActiveTicks = source.overclockActiveTicks;
        out.draftRandomState = source.draftRandomState; out.worldTick = source.worldTick; out.upgradeCount = source.upgradeCount;
        out.hostileKills = source.hostileKills; out.maxHostiles = source.maxHostiles; out.progressionComplete = source.progressionComplete;
        return out;
    }

    private StateCopies() { }
}
