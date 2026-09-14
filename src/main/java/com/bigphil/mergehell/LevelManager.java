package com.bigphil.mergehell;

import com.bigphil.mergehell.model.*;
import com.bigphil.mergehell.world.ChapterRouteController;

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
    private final Random random;

    // Battle zone state
    private BattleZone activeBattle;
    private final Set<BattleZone> completedBattles = new HashSet<>();
    private int currentWave;
    private int waveTimer;

    public final String bossName, bossSymbol;
    public final int bossHp;

    private final List<Coin> coins = new ArrayList<>();

    public LevelManager(int levelNum) {
        this(levelNum, new Random().nextLong());
    }

    public LevelManager(int levelNum, long seed) {
        this.levelNum = levelNum;
        this.random = new Random(seed);
        bossName = switch (levelNum) {
            case 0 -> "LEGACY CODE MONSTROSITY";
            case 1 -> "MEMORY LEAK DAEMON";
            case 2 -> "AERIAL GANTRY ARCHITECT";
            case 3 -> "GEOTHERMAL SIEGE ENGINE";
            default -> "ALIEN ROOTHEART";
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
        // Survey lines, guarded bridgeheads, then cable patrols: one species is introduced at a time.
        specialists(list, EntityType.SENTINEL, 320, 1100, 2690, 3310, 4100, 4920, 6570, 7430);
        specialists(list, EntityType.WARDEN, 600, 2490, 3510, 4350, 6110, 7270);
        specialists(list, EntityType.RIGGER, 1000, 2250, 3090, 3800, 4690, 6330, 6990);
        supplies(list, EntityType.PICKUP_LASER, 780);
        supplies(list, EntityType.HEALTH, 2150, 6070, 7460);
        supplies(list, EntityType.POWERUP_SHIELD, 3940);
        supplies(list, EntityType.PICKUP_HEAVY, 4840);
    }

    private void buildKernelRoute(List<SpawnTrigger> list) {
        // Burrow warnings teach the floor, welding dives teach the air, slag arcs teach conveyor timing.
        specialists(list, EntityType.DRILLER, 320, 2550, 3500, 5010, 5930, 7360);
        specialists(list, EntityType.INTERRUPT, 610, 1450, 2760, 3850, 5270, 6140, 7500);
        specialists(list, EntityType.SLAG_SPITTER, 1000, 3100, 4050, 5650, 7140);
        supplies(list, EntityType.PICKUP_RAPID, 800);
        supplies(list, EntityType.HEALTH, 2490, 4920, 7100, 7480);
        supplies(list, EntityType.POWERUP_SHIELD, 3600);
        supplies(list, EntityType.PICKUP_FLAME, 5800);
    }

    private void buildSingularityRoute(List<SpawnTrigger> list) {
        // The hive's nests add pressure themselves; free encounters leave room to read the organism.
        specialists(list, EntityType.LURKER, 300, 1440, 2680, 3760, 4720, 5730, 7280);
        specialists(list, EntityType.MIRROR, 650, 1680, 3320, 4420, 5460, 6960, 7480);
        specialists(list, EntityType.SPORE_POD, 1120, 3010, 4030, 5100, 6710);
        supplies(list, EntityType.PICKUP_FLAME, 780);
        supplies(list, EntityType.HEALTH, 2600, 4650, 6590, 7520);
        supplies(list, EntityType.POWERUP_SHIELD, 3550);
        supplies(list, EntityType.PICKUP_HEAVY, 5560);
    }

    private void specialists(List<SpawnTrigger> list, EntityType type, int... positions) {
        for (int x : positions) list.add(new SpawnTrigger(x, type, 1, 0));
    }

    private void supplies(List<SpawnTrigger> list, EntityType type, int... positions) {
        for (int x : positions) list.add(new SpawnTrigger(x, type, 1, 0));
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
                battle(1250, 2050, wave(EntityType.SENTINEL, 2, 0, WaveType.SNIPER),
                        wave(EntityType.WARDEN, 1, 0, WaveType.MINIBOSS),
                        wave(EntityType.RIGGER, 2, 0, WaveType.MIXED)),
                battle(5200, 6000, wave(EntityType.RIGGER, 1, 2, WaveType.MIXED),
                        wave(EntityType.SENTINEL, 2, 0, WaveType.SNIPER),
                        wave(EntityType.WARDEN, 2, 0, WaveType.MINIBOSS)));
    }

    private List<BattleZone> kernelBattles() {
        return List.of(
                battle(1700, 2400, wave(EntityType.DRILLER, 2, 0, WaveType.RUSH),
                        wave(EntityType.INTERRUPT, 2, 0, WaveType.MIXED)),
                battle(4200, 4850, wave(EntityType.SLAG_SPITTER, 2, 0, WaveType.SNIPER),
                        wave(EntityType.INTERRUPT, 1, 2, WaveType.MIXED),
                        wave(EntityType.DRILLER, 2, 0, WaveType.RUSH)),
                battle(6350, 7000, wave(EntityType.INTERRUPT, 2, 0, WaveType.MIXED),
                        wave(EntityType.DRILLER, 1, 2, WaveType.RUSH),
                        wave(EntityType.SLAG_SPITTER, 2, 0, WaveType.MINIBOSS)));
    }

    private List<BattleZone> singularityBattles() {
        return List.of(
                battle(1850, 2550, wave(EntityType.LURKER, 2, 0, WaveType.RUSH),
                        wave(EntityType.SPORE_POD, 1, 0, WaveType.SNIPER),
                        wave(EntityType.MIRROR, 2, 0, WaveType.MINIBOSS)),
                battle(5900, 6500, wave(EntityType.SPORE_POD, 2, 0, WaveType.SNIPER),
                        wave(EntityType.LURKER, 1, 2, WaveType.RUSH),
                        wave(EntityType.MIRROR, 2, 0, WaveType.MINIBOSS)));
    }

    private BattleZone battle(double start, double end, WaveDef... waves) {
        return new BattleZone(start, end, waves);
    }

    private WaveDef wave(EntityType type, int count, int direction, WaveType waveType) {
        return new WaveDef(type, count, direction, waveType);
    }

    private List<Platform> buildPlatforms(int level) {
        if (level >= 2 && level <= 4) {
            ChapterRouteController route = new ChapterRouteController(level, 480);
            Set<Platform> lifts = new HashSet<>();
            route.snapshot().lifts().forEach(lift -> lifts.add(lift.platform()));
            // Moving lifts belong only to the live route. Never leave a static duplicate under them.
            List<Platform> staticLedges = route.platforms().stream().filter(p -> !lifts.contains(p)).toList();
            for (int i = 0; i < staticLedges.size(); i++) {
                Platform p = staticLedges.get(i);
                if (p.width >= 120 && p.x < BOSS_GATE_X && (i % 2 == 0 || p.y < 360)) {
                    // Keep the authored reward count, but replay a seed-specific position safely inside each ledge.
                    double offset = (random.nextInt(3) - 1) * 18;
                    coins.add(new Coin(p.x + p.width / 2.0 + offset, p.y - 30));
                }
            }
            return staticLedges;
        }
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
            default -> throw new IllegalArgumentException("Unsupported legacy route: " + level);
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
