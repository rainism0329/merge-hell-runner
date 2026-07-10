package com.bigphil.mergehell;

import com.bigphil.mergehell.model.*;

import java.util.*;

public class LevelManager {

    public enum ZoneType { FREE, BATTLE, BOSS }

    public static class SpawnTrigger {
        double worldX;
        EntityType type;
        int count;
        int fromLeft;
        SpawnTrigger(double x, EntityType t, int c, int dir) {
            this.worldX = x; this.type = t; this.count = c; this.fromLeft = dir;
        }
    }

    private static final double LEVEL_WIDTH = 10000;
    private final List<SpawnTrigger> triggers;
    private final List<BattleZone> battleZones;
    private final List<Platform> platforms;
    private boolean bossTriggered, bossDefeated;
    private final Random random = new Random();

    // Battle zone state
    private BattleZone activeBattle;
    private int currentWave;
    private int waveTimer;

    public final String bossName, bossSymbol;
    public final int bossHp;

    private final List<Coin> coins = new ArrayList<>();

    public LevelManager(int levelNum) {
        bossName = switch (levelNum) {
            case 0 -> "LEGACY CODE MONSTROSITY";
            case 1 -> "MEMORY LEAK DAEMON";
            case 2 -> "THE ARCHITECT";
            case 3 -> "KERNEL PANIC OVERLORD";
            default -> "SINGULARITY ENGINE";
        };
        bossSymbol = switch (levelNum) {
            case 0 -> "⚠️"; case 1 -> "💀"; case 2 -> "👑";
            case 3 -> "💀"; default -> "☠️";
        };
        bossHp = 2000 + levelNum * 1500;

        triggers = buildTriggers(levelNum);
        battleZones = buildBattleZones();
        platforms = buildPlatforms(levelNum);
    }

    private List<SpawnTrigger> buildTriggers(int level) {
        List<SpawnTrigger> list = new ArrayList<>();
        int count = level + 1;

        for (double x = 200; x < 600; x += 100)
            list.add(new SpawnTrigger(x, EntityType.BUG, count, 0));
        if (level >= 1)
            for (double x = 300; x < 600; x += 120)
                list.add(new SpawnTrigger(x, EntityType.CRASH, 1, 2));
        for (double x = 1300; x < 1800; x += 80) {
            EntityType t = x < 1600 ? EntityType.CONFLICT : (level >= 2 ? EntityType.LOCK : EntityType.BUG);
            list.add(new SpawnTrigger(x, t, 1, random.nextBoolean() ? 2 : 0));
        }
        list.add(new SpawnTrigger(1550, EntityType.HEALTH, 1, 0));
        list.add(new SpawnTrigger(1700, EntityType.PICKUP_SPREAD, 1, 0));
        if (level >= 1) list.add(new SpawnTrigger(1750, EntityType.PICKUP_RAPID, 1, 0));

        for (double x = 2600; x < 3100; x += 60) {
            EntityType t = level >= 2 ? EntityType.CRASH : (level >= 1 ? EntityType.LOCK : EntityType.BUG);
            list.add(new SpawnTrigger(x, t, 1, 2));
        }
        list.add(new SpawnTrigger(2800, EntityType.POWERUP_SHIELD, 1, 0));
        list.add(new SpawnTrigger(2900, EntityType.HEALTH, 1, 0));
        if (level >= 1) list.add(new SpawnTrigger(2950, EntityType.FIREWALL, 1, 0));
        if (level >= 2) list.add(new SpawnTrigger(3000, EntityType.FIREWALL, 2, 0));
        return list;
    }

    private List<BattleZone> buildBattleZones() {
        return List.of(
            new BattleZone(900, 1500, new WaveDef[]{
                new WaveDef(EntityType.BUG, 5, 0, WaveType.RUSH),
                new WaveDef(EntityType.CRASH, 3, 2, WaveType.MIXED),
                new WaveDef(EntityType.CONFLICT, 4, 2, WaveType.SNIPER),
            }),
            new BattleZone(2300, 3000, new WaveDef[]{
                new WaveDef(EntityType.BUG, 6, 0, WaveType.RUSH),
                new WaveDef(EntityType.CRASH, 4, 2, WaveType.MIXED),
                new WaveDef(EntityType.LOCK, 3, 0, WaveType.SNIPER),
                new WaveDef(EntityType.TECHDEBT, 1, 0, WaveType.MINIBOSS),
            }),
            new BattleZone(4200, 5000, new WaveDef[]{
                new WaveDef(EntityType.CRASH, 5, 2, WaveType.RUSH),
                new WaveDef(EntityType.LOCK, 4, 2, WaveType.MIXED),
                new WaveDef(EntityType.TECHDEBT, 2, 0, WaveType.MINIBOSS),
                new WaveDef(EntityType.CONFLICT, 5, 2, WaveType.SNIPER),
            }),
            new BattleZone(6200, 7000, new WaveDef[]{
                new WaveDef(EntityType.BUG, 6, 0, WaveType.RUSH),
                new WaveDef(EntityType.FIREWALL, 2, 0, WaveType.MINIBOSS),
                new WaveDef(EntityType.CRASH, 5, 2, WaveType.MIXED),
                new WaveDef(EntityType.LOCK, 4, 0, WaveType.SNIPER),
                new WaveDef(EntityType.TECHDEBT, 2, 2, WaveType.MINIBOSS),
            })
        );
    }

    private List<Platform> buildPlatforms(int level) {
        List<Platform> list = new ArrayList<>(List.of(
            new Platform(500, 365, 130, 22), new Platform(900, 305, 150, 22),
            new Platform(1600, 285, 140, 22), new Platform(2700, 335, 110, 22),
            new Platform(2900, 295, 150, 22)
        ));
        if (level >= 1) list.addAll(List.of(
            new Platform(700, 335, 110, 22), new Platform(1400, 345, 110, 22),
            new Platform(1800, 325, 120, 22), new Platform(2400, 315, 130, 22)
        ));
        if (level >= 2) list.addAll(List.of(
            new Platform(1100, 325, 130, 22), new Platform(1500, 265, 110, 22),
            new Platform(2000, 335, 140, 22), new Platform(2600, 305, 150, 22),
            new Platform(3000, 275, 120, 22)
        ));
        for (Platform p : list) {
            if (random.nextBoolean())
                coins.add(new Coin(p.x + p.width / 2, p.y - 30));
        }
        return list;
    }

    public List<Coin> getCoins() { return coins; }

    // ── Battle zone logic ────────────────────────────

    public BattleZone getBattleAt(double playerX) {
        if (activeBattle != null) return activeBattle;
        for (BattleZone bz : battleZones) {
            if (playerX >= bz.start && playerX < bz.end) return bz;
        }
        return null;
    }

    public void enterBattle(BattleZone bz) {
        if (activeBattle != null) return;
        activeBattle = bz;
        currentWave = 0;
        waveTimer = 30; // brief pause before first wave
    }

    public boolean isInBattle() { return activeBattle != null; }

    public double getCameraLockX() {
        return activeBattle != null ? activeBattle.start : 0;
    }

    public boolean needsWaveSpawn(int aliveEnemies) {
        if (activeBattle == null) return false;
        if (aliveEnemies > 0) return false;
        if (waveTimer > 0) {
            waveTimer--;
            return false;
        }
        if (currentWave >= activeBattle.waves.length) {
            activeBattle = null; // all waves done
            return false;
        }
        return true;
    }

    public WaveDef popWave() {
        return activeBattle.waves[currentWave++];
    }

    public void startNextWaveTimer() {
        waveTimer = 40;
    }

    public int getCurrentWave() { return currentWave; }
    public int getTotalWaves() { return activeBattle != null ? activeBattle.waves.length : 0; }

    // ── Triggers ─────────────────────────────────────

    public List<SpawnTrigger> getPendingTriggers(double px) {
        List<SpawnTrigger> pending = new ArrayList<>();
        Iterator<SpawnTrigger> it = triggers.iterator();
        while (it.hasNext()) {
            SpawnTrigger t = it.next();
            if (px >= t.worldX) { pending.add(t); it.remove(); }
        }
        return pending;
    }

    // ── Boss ─────────────────────────────────────────

    public boolean shouldSpawnBoss(double px) {
        if (bossTriggered || bossDefeated) return false;
        if (px >= 3100) { bossTriggered = true; return true; }
        return false;
    }
    public void onBossDefeated() { bossDefeated = true; }
    public boolean isLevelComplete() { return bossDefeated; }

    public double getCameraMaxX() { return LEVEL_WIDTH; }
    public List<Platform> getPlatforms() { return platforms; }

    // ── Types ────────────────────────────────────────

    public static class BattleZone {
        public final double start, end;
        public final WaveDef[] waves;
        BattleZone(double s, double e, WaveDef[] w) { start = s; end = e; waves = w; }
    }

    public enum WaveType { RUSH, MIXED, SNIPER, MINIBOSS }

    public static class WaveDef {
        public final EntityType type;
        public final int count;
        public final int fromDir;
        public final WaveType waveType;
        public WaveDef(EntityType t, int c, int d, WaveType wt) {
            type = t; count = c; fromDir = d; waveType = wt;
        }
    }

    public static class Coin {
        public double x, y;
        public boolean collected;
        public Coin(double x, double y) { this.x = x; this.y = y; }
    }
}
