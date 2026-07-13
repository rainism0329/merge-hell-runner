package com.bigphil.mergehell.persistence;

import com.bigphil.mergehell.combat.WeaponId;
import com.bigphil.mergehell.progression.UpgradeId;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MergeHellState {
    public int schemaVersion = 1;
    public List<Integer> topScores = new ArrayList<>();
    public Set<WeaponId> unlockedWeapons = EnumSet.of(WeaponId.COMMIT_CANNON);
    public Set<Integer> completedMissions = new HashSet<>();
    public int refactorPoints;
    public boolean legacyScoresMigrated;
    public Settings settings = new Settings();
    public ActiveRun activeRun;

    public static final class Settings {
        public int shakePercent = 70;
        public boolean crt = true;
        public boolean flashes = true;
        public int particlePercent = 100;
        public int volumePercent = 35;
        public boolean autoFire;
        public boolean highContrast;
    }

    public static final class ActiveRun {
        public int mission;
        public double checkpointX;
        public double checkpointY;
        public int lives;
        public WeaponId weapon = WeaponId.COMMIT_CANNON;
        public Map<UpgradeId, Integer> upgradeRanks = new EnumMap<>(UpgradeId.class);
        public long worldTick;
    }
}
