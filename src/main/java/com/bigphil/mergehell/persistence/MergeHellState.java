package com.bigphil.mergehell.persistence;

import com.bigphil.mergehell.progression.CharacterId;
import com.bigphil.mergehell.progression.GameDifficulty;

import com.bigphil.mergehell.combat.WeaponId;
import com.bigphil.mergehell.progression.UpgradeId;
import com.bigphil.mergehell.model.WeaponType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MergeHellState {
    public int schemaVersion = 2;
    public Set<CharacterId> unlockedCharacters=EnumSet.of(CharacterId.REPAIR,CharacterId.SCOUT,CharacterId.WARDEN);
    public Set<String> foundSecrets=new HashSet<>();
    public Map<GameDifficulty,List<Integer>> rankedScores=new EnumMap<>(GameDifficulty.class);
    public List<Integer> topScores = new ArrayList<>();
    public Set<WeaponId> unlockedWeapons = EnumSet.of(WeaponId.COMMIT_CANNON);
    public Set<Integer> completedMissions = new HashSet<>();
    public int refactorPoints;
    public boolean legacyScoresMigrated;
    public Settings settings = new Settings();
    public ActiveRun activeRun;
    public ActiveRun legacyActiveRunBackup;
    public String checkpointNotice = "";

    public static final class Settings {
        /** Empty older saves use the OS language; explicit choices serialize even on matching OSes. */
        public String language = "";
        public int shakePercent = 70;
        public boolean crt = true;
        public boolean flashes = true;
        public int particlePercent = 100;
        public int volumePercent = 35;
        public int backgroundVolumePercent = 20;
        /** New profiles and older XML without an explicit choice start quietly inside the IDE. */
        public boolean muted = true;
        public boolean autoFire;
        public boolean highContrast;
    }

    public static final class ActiveRun {
        public Set<Integer> explorationVisited = new HashSet<>();
        public Map<Integer,Integer> routeHealth = new java.util.HashMap<>();
        public Map<Integer,Integer> routeHatches = new java.util.HashMap<>();
        public Set<Integer> heapUsed = new HashSet<>();
        public Set<Integer> collectedCoins = new HashSet<>();
        public DirectorData director;
        public Map<Long,Integer> environmentBreakage = new java.util.HashMap<>();
        public long environmentRetired;
        public int checkpointVersion;
        public String checkpointKind = "";
        public String runId = "";
        public long seed;
        public int score;
        public int nextLifeThreshold;
        public PlayerData player;
        public SessionData session;
        // Retained for schema-1 XML import and backup; schema-2 restoration uses the complete nested data.
        public int mission;
        public double checkpointX;
        public double checkpointY;
        public int lives;
        public WeaponId weapon = WeaponId.COMMIT_CANNON;
        public Map<UpgradeId, Integer> upgradeRanks = new EnumMap<>(UpgradeId.class);
        public long worldTick;
    }

    public static final class PlayerData {
        public int hp, lives, bombs, sudoTicks, shieldTicks, invincibleTicks;
        public int cooldown, dashCooldown, meleeCooldown, bombCooldown;
        public boolean temporaryWeapon;
        public WeaponType weapon;
        public int ammo;
        public Map<WeaponType, Integer> ammoReserve = new EnumMap<>(WeaponType.class);
        public int shieldRebootsUsed, rapidHeat;
        public long combatRandomState;
    }

    public static final class DirectorData {
        public int segment,tick,startKills,cooldown;
        public double budget;
        public boolean announced;
        public long randomState;
    }

    public static final class SessionData {
        public CharacterId character=CharacterId.REPAIR;
        public GameDifficulty difficulty=GameDifficulty.STANDARD;
        public WeaponId weapon;
        public Map<UpgradeId, Integer> ranks = new EnumMap<>(UpgradeId.class);
        public int weaponLevel;
        public boolean evolutionCoreInstalled, evolved;
        public int buildLevel, currentXp, pendingChoices;
        public int overclockCharge, overclockActiveTicks;
        public long draftRandomState, worldTick;
        public int upgradeCount, hostileKills, maxHostiles;
        public boolean progressionComplete;
    }
}
