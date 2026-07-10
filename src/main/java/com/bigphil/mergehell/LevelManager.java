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

    private static final double BOSS_GATE_X = 7600;
    private static final double LEVEL_WIDTH = 8600;
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
        battleZones = buildBattleZones(levelNum);
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

        // The campaign's later battle zones used to exist off-screen after the boss.
        // Populate the entire route so each run has a readable escalation: skirmish,
        // arena, recovery, then the boss gate.
        addEncounter(list, 3400, 4050, 120, EntityType.BUG, count + 1, 2);
        addEncounter(list, 3600, 4200, 180, EntityType.CONFLICT, 1, 0);
        list.add(new SpawnTrigger(3900, EntityType.HEALTH, 1, 0));
        list.add(new SpawnTrigger(4050, EntityType.PICKUP_HEAVY, 1, 0));

        addEncounter(list, 5100, 5750, 110,
                level >= 2 ? EntityType.LOCK : EntityType.CRASH, count + 1, 2);
        list.add(new SpawnTrigger(5400, EntityType.POWERUP_SHIELD, 1, 0));
        list.add(new SpawnTrigger(5650, EntityType.PICKUP_RAPID, 1, 0));

        addEncounter(list, 7100, 7480, 95, EntityType.CONFLICT, count + 1, 2);
        list.add(new SpawnTrigger(7250, EntityType.HEALTH, 1, 0));
        list.add(new SpawnTrigger(7420, EntityType.PICKUP_SPREAD, 1, 0));
        return list;
    }

    private void addEncounter(List<SpawnTrigger> list, double from, double to, double step,
                              EntityType type, int count, int direction) {
        for (double x = from; x < to; x += step) {
            list.add(new SpawnTrigger(x, type, count, direction));
        }
    }

    private List<BattleZone> buildBattleZones(int level) {
        int extra = Math.min(level, 3);
        return List.of(
            new BattleZone(900, 1500, new WaveDef[]{
                new WaveDef(EntityType.BUG, 5 + extra, 0, WaveType.RUSH),
                new WaveDef(EntityType.CRASH, 3 + extra, 2, WaveType.MIXED),
                new WaveDef(EntityType.CONFLICT, 4 + extra, 2, WaveType.SNIPER),
            }),
            new BattleZone(2300, 3000, new WaveDef[]{
                new WaveDef(EntityType.BUG, 6 + extra, 0, WaveType.RUSH),
                new WaveDef(EntityType.CRASH, 4 + extra, 2, WaveType.MIXED),
                new WaveDef(EntityType.LOCK, 3 + extra, 0, WaveType.SNIPER),
                new WaveDef(EntityType.TECHDEBT, 1 + extra / 2, 0, WaveType.MINIBOSS),
            }),
            new BattleZone(4200, 5000, new WaveDef[]{
                new WaveDef(EntityType.CRASH, 5 + extra, 2, WaveType.RUSH),
                new WaveDef(EntityType.LOCK, 4 + extra, 2, WaveType.MIXED),
                new WaveDef(EntityType.TECHDEBT, 2 + extra / 2, 0, WaveType.MINIBOSS),
                new WaveDef(EntityType.CONFLICT, 5 + extra, 2, WaveType.SNIPER),
            }),
            new BattleZone(6200, 7000, new WaveDef[]{
                new WaveDef(EntityType.BUG, 6 + extra, 0, WaveType.RUSH),
                new WaveDef(EntityType.FIREWALL, 2 + extra / 2, 0, WaveType.MINIBOSS),
                new WaveDef(EntityType.CRASH, 5 + extra, 2, WaveType.MIXED),
                new WaveDef(EntityType.LOCK, 4 + extra, 0, WaveType.SNIPER),
                new WaveDef(EntityType.TECHDEBT, 2 + extra / 2, 2, WaveType.MINIBOSS),
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
        list.addAll(List.of(
            new Platform(3500, 340, 130, 22), new Platform(3900, 290, 150, 22),
            new Platform(4450, 325, 120, 22), new Platform(4850, 270, 140, 22),
            new Platform(5300, 335, 150, 22), new Platform(5750, 300, 110, 22),
            new Platform(6400, 340, 140, 22), new Platform(6850, 280, 150, 22),
            new Platform(7250, 325, 130, 22)
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
        if (px >= BOSS_GATE_X) { bossTriggered = true; return true; }
        return false;
    }
    public void onBossDefeated() { bossDefeated = true; }
    public boolean isLevelComplete() { return bossDefeated; }

    public double getCameraMaxX() { return LEVEL_WIDTH; }
    public double getProgress(double playerX) {
        return Math.max(0, Math.min(1, playerX / BOSS_GATE_X));
    }
    public double getBossGateX() { return BOSS_GATE_X; }
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
