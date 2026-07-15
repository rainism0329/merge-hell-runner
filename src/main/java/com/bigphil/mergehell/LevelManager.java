package com.bigphil.mergehell;

import com.bigphil.mergehell.model.*;

import java.util.*;

public class LevelManager {

    public static boolean isLegacyMission(int level) { return level > 0; }

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
    private static final double DARK_LEVEL_WIDTH = 1_000_000;
    private final int levelNum;
    private final List<SpawnTrigger> triggers;
    private final List<BattleZone> battleZones;
    private final List<Platform> platforms;
    private boolean bossTriggered, bossDefeated;
    private final Random random = new Random();

    // Battle zone state
    private BattleZone activeBattle;
    private final Set<BattleZone> completedBattles = new HashSet<>();
    private int currentWave;
    private int waveTimer;

    public final String bossName, bossSymbol;
    public final int bossHp;

    private final List<Coin> coins = new ArrayList<>();

    public LevelManager(int levelNum) {
        this.levelNum = levelNum;
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
        switch (Math.max(1, level)) {
            case 1 -> buildMemoryRoute(list);
            case 2 -> buildArchitectRoute(list);
            case 3 -> buildKernelRoute(list);
            default -> buildSingularityRoute(list);
        }
        return list;
    }

    private void buildMemoryRoute(List<SpawnTrigger> list) {
        addEncounter(list, 200, 700, 95, EntityType.LEAK, 2, 2);
        list.add(new SpawnTrigger(620, EntityType.BUG, 3, 0));
        list.add(new SpawnTrigger(760, EntityType.PICKUP_SPREAD, 1, 0));
        addEncounter(list, 1550, 2200, 125, EntityType.LEAK, 2, 2);
        list.add(new SpawnTrigger(1900, EntityType.TECHDEBT, 1, 0));
        list.add(new SpawnTrigger(2150, EntityType.HEALTH, 1, 0));
        addEncounter(list, 3050, 4100, 145, EntityType.BUG, 2, 2);
        addEncounter(list, 3300, 4100, 220, EntityType.LEAK, 2, 0);
        list.add(new SpawnTrigger(4000, EntityType.POWERUP_SHIELD, 1, 0));
        addEncounter(list, 5050, 6100, 170, EntityType.LEAK, 3, 2);
        list.add(new SpawnTrigger(5550, EntityType.TECHDEBT, 2, 0));
        list.add(new SpawnTrigger(5950, EntityType.PICKUP_FLAME, 1, 0));
        addEncounter(list, 7050, 7480, 105, EntityType.LEAK, 3, 2);
        list.add(new SpawnTrigger(7240, EntityType.TECHDEBT, 1, 0));
        list.add(new SpawnTrigger(7440, EntityType.HEALTH, 1, 0));
    }

    private void buildArchitectRoute(List<SpawnTrigger> list) {
        addEncounter(list, 220, 720, 125, EntityType.SENTINEL, 1, 0);
        list.add(new SpawnTrigger(520, EntityType.CONFLICT, 2, 2));
        list.add(new SpawnTrigger(760, EntityType.PICKUP_LASER, 1, 0));
        addEncounter(list, 1550, 2200, 160, EntityType.LOCK, 2, 2);
        list.add(new SpawnTrigger(1760, EntityType.SENTINEL, 2, 0));
        list.add(new SpawnTrigger(2100, EntityType.FIREWALL, 1, 0));
        list.add(new SpawnTrigger(2190, EntityType.HEALTH, 1, 0));
        addEncounter(list, 3050, 4100, 190, EntityType.SENTINEL, 2, 0);
        addEncounter(list, 3250, 4100, 270, EntityType.CONFLICT, 2, 2);
        list.add(new SpawnTrigger(4020, EntityType.POWERUP_SHIELD, 1, 0));
        addEncounter(list, 5050, 6100, 180, EntityType.LOCK, 3, 2);
        list.add(new SpawnTrigger(5350, EntityType.FIREWALL, 2, 0));
        list.add(new SpawnTrigger(5950, EntityType.PICKUP_HEAVY, 1, 0));
        addEncounter(list, 7050, 7480, 135, EntityType.SENTINEL, 2, 2);
        list.add(new SpawnTrigger(7240, EntityType.FIREWALL, 1, 0));
        list.add(new SpawnTrigger(7440, EntityType.HEALTH, 1, 0));
    }

    private void buildKernelRoute(List<SpawnTrigger> list) {
        addEncounter(list, 180, 720, 90, EntityType.INTERRUPT, 1, 2);
        list.add(new SpawnTrigger(580, EntityType.CRASH, 3, 0));
        list.add(new SpawnTrigger(760, EntityType.PICKUP_RAPID, 1, 0));
        addEncounter(list, 1550, 2200, 115, EntityType.INTERRUPT, 2, 2);
        list.add(new SpawnTrigger(1800, EntityType.LOCK, 2, 0));
        list.add(new SpawnTrigger(2160, EntityType.HEALTH, 1, 0));
        addEncounter(list, 3050, 4100, 125, EntityType.CRASH, 2, 2);
        addEncounter(list, 3400, 4100, 170, EntityType.INTERRUPT, 2, 0);
        list.add(new SpawnTrigger(4000, EntityType.POWERUP_SHIELD, 1, 0));
        addEncounter(list, 5050, 6100, 120, EntityType.INTERRUPT, 2, 2);
        list.add(new SpawnTrigger(5400, EntityType.FIREWALL, 2, 0));
        list.add(new SpawnTrigger(5900, EntityType.PICKUP_FLAME, 1, 0));
        addEncounter(list, 7050, 7480, 85, EntityType.INTERRUPT, 2, 2);
        list.add(new SpawnTrigger(7250, EntityType.CRASH, 4, 2));
        list.add(new SpawnTrigger(7440, EntityType.HEALTH, 1, 0));
    }

    private void buildSingularityRoute(List<SpawnTrigger> list) {
        addEncounter(list, 190, 720, 110, EntityType.MIRROR, 1, 2);
        list.add(new SpawnTrigger(460, EntityType.LEAK, 3, 0));
        list.add(new SpawnTrigger(760, EntityType.PICKUP_LASER, 1, 0));
        addEncounter(list, 1550, 2200, 135, EntityType.SENTINEL, 2, 2);
        list.add(new SpawnTrigger(1780, EntityType.MIRROR, 2, 0));
        list.add(new SpawnTrigger(2150, EntityType.HEALTH, 1, 0));
        addEncounter(list, 3050, 4100, 120, EntityType.INTERRUPT, 2, 2);
        addEncounter(list, 3350, 4100, 180, EntityType.LEAK, 3, 0);
        list.add(new SpawnTrigger(4000, EntityType.POWERUP_SHIELD, 1, 0));
        addEncounter(list, 5050, 6100, 150, EntityType.MIRROR, 2, 2);
        list.add(new SpawnTrigger(5400, EntityType.TECHDEBT, 2, 0));
        list.add(new SpawnTrigger(5900, EntityType.PICKUP_HEAVY, 1, 0));
        addEncounter(list, 7050, 7480, 95, EntityType.MIRROR, 2, 2);
        list.add(new SpawnTrigger(7200, EntityType.SENTINEL, 2, 2));
        list.add(new SpawnTrigger(7360, EntityType.INTERRUPT, 3, 2));
        list.add(new SpawnTrigger(7460, EntityType.HEALTH, 1, 0));
    }

    private void addEncounter(List<SpawnTrigger> list, double from, double to, double step,
                              EntityType type, int count, int direction) {
        for (double x = from; x < to; x += step) {
            list.add(new SpawnTrigger(x, type, count, direction));
        }
    }

    private List<BattleZone> buildBattleZones(int level) {
        return switch (Math.max(1, level)) {
            case 1 -> memoryBattles();
            case 2 -> architectBattles();
            case 3 -> kernelBattles();
            default -> singularityBattles();
        };
    }

    private List<BattleZone> memoryBattles() {
        return List.of(
                battle(900, 1500, wave(EntityType.LEAK, 7, 2, WaveType.RUSH),
                        wave(EntityType.BUG, 6, 2, WaveType.MIXED), wave(EntityType.TECHDEBT, 1, 0, WaveType.MINIBOSS)),
                battle(2300, 3000, wave(EntityType.LEAK, 9, 2, WaveType.RUSH),
                        wave(EntityType.CRASH, 5, 2, WaveType.MIXED), wave(EntityType.TECHDEBT, 2, 0, WaveType.MINIBOSS)),
                battle(4200, 5000, wave(EntityType.LEAK, 11, 2, WaveType.RUSH),
                        wave(EntityType.BUG, 8, 2, WaveType.MIXED), wave(EntityType.TECHDEBT, 3, 2, WaveType.MINIBOSS)),
                battle(6200, 7000, wave(EntityType.LEAK, 12, 2, WaveType.RUSH),
                        wave(EntityType.CRASH, 7, 2, WaveType.MIXED), wave(EntityType.LEAK, 8, 2, WaveType.SNIPER),
                        wave(EntityType.TECHDEBT, 3, 2, WaveType.MINIBOSS)));
    }

    private List<BattleZone> architectBattles() {
        return List.of(
                battle(900, 1500, wave(EntityType.SENTINEL, 4, 0, WaveType.SNIPER),
                        wave(EntityType.LOCK, 5, 2, WaveType.MIXED), wave(EntityType.FIREWALL, 1, 0, WaveType.MINIBOSS)),
                battle(2300, 3000, wave(EntityType.CONFLICT, 6, 2, WaveType.SNIPER),
                        wave(EntityType.SENTINEL, 5, 2, WaveType.SNIPER), wave(EntityType.FIREWALL, 2, 0, WaveType.MINIBOSS)),
                battle(4200, 5000, wave(EntityType.LOCK, 7, 2, WaveType.MIXED),
                        wave(EntityType.SENTINEL, 6, 2, WaveType.SNIPER), wave(EntityType.FIREWALL, 3, 2, WaveType.MINIBOSS)),
                battle(6200, 7000, wave(EntityType.SENTINEL, 7, 2, WaveType.SNIPER),
                        wave(EntityType.LOCK, 8, 2, WaveType.MIXED), wave(EntityType.CONFLICT, 7, 2, WaveType.SNIPER),
                        wave(EntityType.FIREWALL, 3, 2, WaveType.MINIBOSS)));
    }

    private List<BattleZone> kernelBattles() {
        return List.of(
                battle(900, 1500, wave(EntityType.INTERRUPT, 7, 2, WaveType.RUSH),
                        wave(EntityType.CRASH, 5, 2, WaveType.MIXED), wave(EntityType.LOCK, 4, 0, WaveType.SNIPER)),
                battle(2300, 3000, wave(EntityType.INTERRUPT, 9, 2, WaveType.RUSH),
                        wave(EntityType.CRASH, 7, 2, WaveType.RUSH), wave(EntityType.FIREWALL, 2, 0, WaveType.MINIBOSS)),
                battle(4200, 5000, wave(EntityType.INTERRUPT, 10, 2, WaveType.RUSH),
                        wave(EntityType.LOCK, 6, 2, WaveType.SNIPER), wave(EntityType.CRASH, 8, 2, WaveType.MIXED)),
                battle(6200, 7000, wave(EntityType.INTERRUPT, 12, 2, WaveType.RUSH),
                        wave(EntityType.CRASH, 9, 2, WaveType.MIXED), wave(EntityType.FIREWALL, 3, 2, WaveType.MINIBOSS),
                        wave(EntityType.INTERRUPT, 10, 2, WaveType.RUSH)));
    }

    private List<BattleZone> singularityBattles() {
        return List.of(
                battle(900, 1500, wave(EntityType.MIRROR, 4, 2, WaveType.MIXED),
                        wave(EntityType.LEAK, 7, 2, WaveType.RUSH), wave(EntityType.SENTINEL, 4, 2, WaveType.SNIPER)),
                battle(2300, 3000, wave(EntityType.INTERRUPT, 8, 2, WaveType.RUSH),
                        wave(EntityType.MIRROR, 5, 2, WaveType.MIXED), wave(EntityType.TECHDEBT, 2, 2, WaveType.MINIBOSS)),
                battle(4200, 5000, wave(EntityType.SENTINEL, 6, 2, WaveType.SNIPER),
                        wave(EntityType.LEAK, 10, 2, WaveType.RUSH), wave(EntityType.MIRROR, 6, 2, WaveType.MIXED)),
                battle(6200, 7000, wave(EntityType.MIRROR, 7, 2, WaveType.MIXED),
                        wave(EntityType.INTERRUPT, 10, 2, WaveType.RUSH), wave(EntityType.SENTINEL, 7, 2, WaveType.SNIPER),
                        wave(EntityType.LEAK, 12, 2, WaveType.RUSH), wave(EntityType.TECHDEBT, 3, 2, WaveType.MINIBOSS)));
    }

    private BattleZone battle(double start, double end, WaveDef... waves) {
        return new BattleZone(start, end, waves);
    }

    private WaveDef wave(EntityType type, int count, int direction, WaveType waveType) {
        return new WaveDef(type, count, direction, waveType);
    }

    private List<Platform> buildPlatforms(int level) {
        List<Platform> list = new ArrayList<>(switch (Math.max(1, level)) {
            case 1 -> List.of(
                    platform(500, 360, 140, Platform.Style.SERVER_BANK), platform(900, 305, 155, Platform.Style.PIPE),
                    platform(1400, 345, 120, Platform.Style.CABLE), platform(1750, 285, 145, Platform.Style.SERVER_BANK),
                    platform(2400, 325, 135, Platform.Style.PIPE), platform(2850, 280, 150, Platform.Style.CABLE),
                    platform(3500, 340, 145, Platform.Style.SERVER_BANK), platform(3950, 290, 150, Platform.Style.PIPE),
                    platform(4450, 320, 130, Platform.Style.CABLE), platform(4900, 265, 145, Platform.Style.SERVER_BANK),
                    platform(5400, 340, 155, Platform.Style.PIPE), platform(5850, 295, 125, Platform.Style.CABLE),
                    platform(6400, 335, 145, Platform.Style.SERVER_BANK), platform(6900, 275, 155, Platform.Style.PIPE),
                    platform(7300, 320, 135, Platform.Style.CABLE));
            case 2 -> List.of(
                    platform(450, 350, 120, Platform.Style.CATWALK), platform(800, 300, 130, Platform.Style.FORTIFICATION),
                    platform(1150, 250, 140, Platform.Style.CATWALK), platform(1500, 300, 140, Platform.Style.FORTIFICATION),
                    platform(1900, 350, 130, Platform.Style.CATWALK), platform(2450, 330, 150, Platform.Style.FORTIFICATION),
                    platform(2850, 270, 150, Platform.Style.CATWALK), platform(3500, 315, 140, Platform.Style.FORTIFICATION),
                    platform(3900, 255, 160, Platform.Style.CATWALK), platform(4450, 335, 130, Platform.Style.FORTIFICATION),
                    platform(4850, 275, 150, Platform.Style.CATWALK), platform(5350, 320, 150, Platform.Style.FORTIFICATION),
                    platform(5800, 260, 140, Platform.Style.CATWALK), platform(6450, 335, 150, Platform.Style.FORTIFICATION),
                    platform(6900, 270, 160, Platform.Style.CATWALK), platform(7300, 315, 135, Platform.Style.FORTIFICATION));
            case 3 -> List.of(
                    platform(420, 365, 115, Platform.Style.PIPE), platform(720, 285, 125, Platform.Style.SERVER_BANK),
                    platform(1080, 350, 120, Platform.Style.FORTIFICATION), platform(1450, 245, 145, Platform.Style.PIPE),
                    platform(1850, 330, 130, Platform.Style.SERVER_BANK), platform(2400, 260, 145, Platform.Style.FORTIFICATION),
                    platform(2800, 345, 120, Platform.Style.PIPE), platform(3450, 235, 150, Platform.Style.SERVER_BANK),
                    platform(3900, 340, 135, Platform.Style.FORTIFICATION), platform(4450, 270, 125, Platform.Style.PIPE),
                    platform(4850, 350, 155, Platform.Style.SERVER_BANK), platform(5350, 245, 135, Platform.Style.FORTIFICATION),
                    platform(5800, 330, 150, Platform.Style.PIPE), platform(6400, 260, 145, Platform.Style.SERVER_BANK),
                    platform(6850, 350, 130, Platform.Style.FORTIFICATION), platform(7300, 275, 140, Platform.Style.PIPE));
            default -> List.of(
                    platform(460, 340, 120, Platform.Style.RUBBLE), platform(780, 270, 145, Platform.Style.CABLE),
                    platform(1180, 355, 110, Platform.Style.SERVER_BANK), platform(1500, 235, 155, Platform.Style.CATWALK),
                    platform(1950, 325, 130, Platform.Style.RUBBLE), platform(2380, 255, 145, Platform.Style.CABLE),
                    platform(2850, 345, 125, Platform.Style.FORTIFICATION), platform(3450, 260, 150, Platform.Style.SERVER_BANK),
                    platform(3900, 350, 120, Platform.Style.RUBBLE), platform(4380, 225, 155, Platform.Style.CABLE),
                    platform(4900, 330, 135, Platform.Style.CATWALK), platform(5350, 250, 145, Platform.Style.SERVER_BANK),
                    platform(5850, 355, 125, Platform.Style.RUBBLE), platform(6350, 240, 155, Platform.Style.CABLE),
                    platform(6850, 335, 140, Platform.Style.FORTIFICATION), platform(7300, 265, 145, Platform.Style.RUBBLE));
        });
        for (Platform p : list) {
            if (random.nextBoolean())
                coins.add(new Coin(p.x + p.width / 2, p.y - 30));
        }
        return list;
    }

    private Platform platform(double x, double y, int width, Platform.Style style) {
        return new Platform(x, y, width, 22, style);
    }

    public List<Coin> getCoins() { return coins; }

    // ── Battle zone logic ────────────────────────────

    public BattleZone getBattleAt(double playerX) {
        if (activeBattle != null) return activeBattle;
        for (BattleZone bz : battleZones) {
            if (!completedBattles.contains(bz) && playerX >= bz.start && playerX < bz.end) return bz;
        }
        return null;
    }

    public void enterBattle(BattleZone bz) {
        if (activeBattle != null || completedBattles.contains(bz)) return;
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
            completeActiveBattle();
            return false;
        }
        return true;
    }

    /** LAB shortcut: advances to the next authored arena, or clears the current one. */
    public double advanceToNextEncounterForTesting(double playerX) {
        double targetX;
        if (activeBattle != null) {
            targetX = activeBattle.end + 1;
            completeActiveBattle();
        } else {
            targetX = BOSS_GATE_X;
            for (BattleZone battle : battleZones) {
                if (completedBattles.contains(battle)) continue;
                if (playerX < battle.start) {
                    targetX = battle.start;
                    break;
                }
                if (playerX < battle.end) {
                    completedBattles.add(battle);
                    targetX = battle.end + 1;
                    break;
                }
            }
        }
        discardTriggersThrough(targetX);
        return targetX;
    }

    /** LAB shortcut: clears route encounters so the legacy boss can spawn immediately. */
    public void advanceToBossGateForTesting() {
        completedBattles.addAll(battleZones);
        activeBattle = null;
        currentWave = 0;
        waveTimer = 0;
        discardTriggersThrough(BOSS_GATE_X);
    }

    private void completeActiveBattle() {
        completedBattles.add(activeBattle);
        activeBattle = null;
        currentWave = 0;
        waveTimer = 0;
    }

    private void discardTriggersThrough(double worldX) {
        triggers.removeIf(trigger -> trigger.worldX <= worldX);
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

    public double getCameraMaxX() { return levelNum == 0 ? DARK_LEVEL_WIDTH : LEVEL_WIDTH; }
    public double getProgress(double playerX) {
        return Math.max(0, Math.min(1, playerX / BOSS_GATE_X));
    }
    public double getBossGateX() { return BOSS_GATE_X; }
    public List<Platform> getPlatforms() { return platforms; }
    public Set<EntityType> getEnemyRoster() {
        EnumSet<EntityType> roster = EnumSet.noneOf(EntityType.class);
        triggers.stream().map(trigger -> trigger.type).filter(EntityType::isHostile).forEach(roster::add);
        for (BattleZone battle : battleZones) {
            for (WaveDef wave : battle.waves) if (wave.type.isHostile()) roster.add(wave.type);
        }
        return Collections.unmodifiableSet(roster);
    }

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
