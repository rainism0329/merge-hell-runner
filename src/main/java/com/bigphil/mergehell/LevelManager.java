package com.bigphil.mergehell;

import com.bigphil.mergehell.model.*;
import com.bigphil.mergehell.world.ChapterRouteController;
import com.bigphil.mergehell.encounter.EncounterWaveController;

import java.awt.Rectangle;
import java.util.*;
import java.util.function.Predicate;

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

    public static final double BOSS_GATE_X = 9900;
    public static final double LEVEL_WIDTH = 10900;
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
    private final EncounterWaveController encounter = new EncounterWaveController();

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
        bossHp = com.bigphil.mergehell.progression.CombatBalance.bossHealth(levelNum, com.bigphil.mergehell.progression.GameDifficulty.STANDARD);

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
        if(level>0) {
            EntityType[] team=switch(level) {
                case 1 -> new EntityType[]{EntityType.LEAK,EntityType.LOCK,EntityType.TECHDEBT};
                case 2 -> new EntityType[]{EntityType.RIGGER,EntityType.WARDEN,EntityType.SENTINEL};
                case 3 -> new EntityType[]{EntityType.INTERRUPT,EntityType.DRILLER,EntityType.SLAG_SPITTER};
                default -> new EntityType[]{EntityType.LURKER,EntityType.SPORE_POD,EntityType.MIRROR};
            };
            for(int i=0;i<6;i++) list.add(new SpawnTrigger(7760+i*330,team[i%3],i%3==0?2:1,0));
            supplies(list,EntityType.HEALTH,9710);
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
                // Drain intake: a floor carrier draws fire while leakage passes above it.
                // The second group adds the familiar lock's aimed fire, rather than a wall of bodies.
                battle(900, 1500,
                        mixed(3, beat(EntityType.TECHDEBT, 0, 340, 0),
                                beat(EntityType.LEAK, 32, 460, 125), beat(EntityType.BUG, 80, 480, 0),
                                beat(EntityType.LEAK, 145, 385, 180)),
                        mixed(3, beat(EntityType.LOCK, 0, 455, 105),
                                beat(EntityType.LEAK, 36, 340, 175), beat(EntityType.TECHDEBT, 88, 490, 0),
                                beat(EntityType.BUG, 160, 405, 0))),
                // Pump gallery: high leakage and low charging bodies leave different dodge lanes.
                battle(2300, 3000,
                        mixed(3, beat(EntityType.LEAK, 0, 410, 165),
                                beat(EntityType.CRASH, 38, 530, 45), beat(EntityType.TECHDEBT, 100, 585, 0),
                                beat(EntityType.LOCK, 180, 480, 120)),
                        mixed(3, beat(EntityType.TECHDEBT, 0, 425, 0),
                                beat(EntityType.LOCK, 32, 580, 105), beat(EntityType.LEAK, 82, 475, 205),
                                beat(EntityType.CRASH, 170, 540, 30))),
                // Collector works: alternate grounded pressure and an elevated shooting lane.
                battle(4200, 5000,
                        mixed(3, beat(EntityType.LOCK, 0, 630, 145),
                                beat(EntityType.TECHDEBT, 35, 420, 0), beat(EntityType.LEAK, 90, 565, 210),
                                beat(EntityType.BUG, 175, 685, 0)),
                        mixed(3, beat(EntityType.LEAK, 0, 445, 175),
                                beat(EntityType.CRASH, 40, 570, 30), beat(EntityType.LOCK, 100, 665, 115),
                                beat(EntityType.TECHDEBT, 185, 490, 0), beat(EntityType.LEAK, 260, 630, 225))),
                // Outfall: repeat the learned roles in relief shifts, never simultaneous swarms.
                battle(6200, 7000,
                        mixed(3, beat(EntityType.TECHDEBT, 0, 440, 0),
                                beat(EntityType.LEAK, 30, 620, 180), beat(EntityType.LOCK, 85, 535, 105),
                                beat(EntityType.CRASH, 165, 665, 35)),
                        mixed(3, beat(EntityType.LOCK, 0, 650, 155),
                                beat(EntityType.LEAK, 35, 460, 215), beat(EntityType.TECHDEBT, 95, 535, 0),
                                beat(EntityType.CRASH, 185, 620, 35), beat(EntityType.LEAK, 260, 400, 125))));
    }

    private List<BattleZone> architectBattles() {
        return List.of(
                // Bridgehead: the shield approaches ahead of the marksman; a cable unit then
                // asks for an upward shot, leaving time to identify each new responsibility.
                battle(1250, 2050,
                        mixed(3, beat(EntityType.WARDEN, 0, 430, 0),
                                beat(EntityType.SENTINEL, 40, 690, 0),
                                beat(EntityType.RIGGER, 110, 590, 205)),
                        mixed(3, beat(EntityType.SENTINEL, 0, 650, 0),
                                beat(EntityType.RIGGER, 42, 400, 245),
                                beat(EntityType.WARDEN, 100, 500, 0),
                                beat(EntityType.RIGGER, 190, 690, 175))),
                // Relay yard: staggered high/low cable patrols contest the air while a
                // ground battery arrives. Reinforcements wait for a vacancy instead of stacking.
                battle(5200, 6000,
                        mixed(3, beat(EntityType.RIGGER, 0, 520, 235),
                                beat(EntityType.SENTINEL, 45, 680, 0),
                                beat(EntityType.RIGGER, 100, 340, 135),
                                beat(EntityType.WARDEN, 175, 460, 0)),
                        mixed(3, beat(EntityType.WARDEN, 0, 420, 0),
                                beat(EntityType.RIGGER, 40, 580, 230),
                                beat(EntityType.SENTINEL, 95, 690, 0),
                                beat(EntityType.RIGGER, 165, 330, 165),
                                beat(EntityType.SENTINEL, 240, 620, 0))));
    }

    private static EncounterWaveController.Beat beat(EntityType type, int delay, int x, int altitude) {
        return new EncounterWaveController.Beat(type, delay, x, altitude);
    }

    private static WaveDef mixed(int cap, EncounterWaveController.Beat... beats) {
        return new WaveDef(List.of(beats), cap);
    }

    private List<BattleZone> kernelBattles() {
        return List.of(
                // Furnace mouth: read the drill's floor warning before the welder contests jumps.
                battle(1700, 2400,
                        mixed(3, beat(EntityType.DRILLER, 0, 365, 0),
                                beat(EntityType.INTERRUPT, 85, 480, 155),
                                beat(EntityType.SLAG_SPITTER, 160, 580, 0)),
                        mixed(3, beat(EntityType.INTERRUPT, 0, 390, 180),
                                beat(EntityType.SLAG_SPITTER, 75, 565, 0),
                                beat(EntityType.DRILLER, 160, 470, 0))),
                // Casting bay: slag owns the back line while drilling forces a new landing spot.
                battle(4200, 4850,
                        mixed(3, beat(EntityType.SLAG_SPITTER, 0, 530, 0),
                                beat(EntityType.DRILLER, 70, 340, 0),
                                beat(EntityType.INTERRUPT, 155, 450, 190)),
                        mixed(3, beat(EntityType.DRILLER, 0, 360, 0),
                                beat(EntityType.SLAG_SPITTER, 80, 530, 0),
                                beat(EntityType.INTERRUPT, 165, 455, 135),
                                beat(EntityType.DRILLER, 270, 470, 0))),
                // Pressure chamber: the air threat arrives first; the ground pair follows slowly.
                battle(6350, 7000,
                        mixed(3, beat(EntityType.INTERRUPT, 0, 390, 210),
                                beat(EntityType.DRILLER, 85, 470, 0),
                                beat(EntityType.SLAG_SPITTER, 175, 535, 0)),
                        mixed(3, beat(EntityType.SLAG_SPITTER, 0, 530, 0),
                                beat(EntityType.INTERRUPT, 75, 380, 160),
                                beat(EntityType.DRILLER, 165, 470, 0),
                                beat(EntityType.INTERRUPT, 270, 530, 220))));
    }

    private List<BattleZone> singularityBattles() {
        return List.of(
                // Incubator guard: falling spores teach lateral movement before a lurker closes.
                battle(1850, 2550,
                        mixed(3, beat(EntityType.SPORE_POD, 0, 515, 155),
                                beat(EntityType.LURKER, 85, 340, 0),
                                beat(EntityType.MIRROR, 180, 585, 0)),
                        mixed(3, beat(EntityType.LURKER, 0, 350, 0),
                                beat(EntityType.MIRROR, 80, 565, 0),
                                beat(EntityType.SPORE_POD, 175, 460, 190))),
                // Root corridor: a retreating resin gunner and high spores cover the hunting pair.
                battle(5900, 6500,
                        mixed(3, beat(EntityType.SPORE_POD, 0, 445, 145),
                                beat(EntityType.MIRROR, 80, 490, 0),
                                beat(EntityType.LURKER, 175, 310, 0),
                                beat(EntityType.LURKER, 290, 450, 0)),
                        mixed(3, beat(EntityType.MIRROR, 0, 490, 0),
                                beat(EntityType.SPORE_POD, 95, 370, 180),
                                beat(EntityType.LURKER, 195, 305, 0))));
    }

    private BattleZone battle(double start, double end, WaveDef... waves) {
        return new BattleZone(start, end, waves);
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
        encounter.clear();
        currentWave = 0;
        waveTimer = 30; // brief pause before first wave
    }

    public boolean isInBattle() { return activeBattle != null; }

    public double getCameraLockX() {
        return activeBattle != null ? activeBattle.start : 0;
    }

    public boolean needsWaveSpawn(int aliveEnemies) {
        if (activeBattle == null) return false;
        if (encounter.hasPending()) return false;
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
        encounter.clear();
        discardTriggersThrough(BOSS_GATE_X);
    }

    private void completeActiveBattle() {
        completedBattles.add(activeBattle);
        activeBattle = null;
        currentWave = 0;
        waveTimer = 0;
        encounter.clear();
    }

    public void restoreThrough(double worldX) {
        discardTriggersThrough(worldX);
        for(var battle:battleZones)if(battle.end<=worldX)completedBattles.add(battle);
    }
    private void discardTriggersThrough(double worldX) {
        triggers.removeIf(trigger -> trigger.worldX <= worldX);
    }

    public WaveDef popWave() {
        WaveDef wave = activeBattle.waves[currentWave++];
        if (wave.scripted()) encounter.begin(wave.beats, wave.maxConcurrent, activeBattle.start, activeBattle.end);
        return wave;
    }

    public int advanceEncounter(int aliveHostiles, int groundY, List<Rectangle> solids,
                                List<Rectangle> occupied, Rectangle player,
                                Predicate<Rectangle> groundSupported,
                                Predicate<EncounterWaveController.Spawn> spawn) {
        return encounter.advance(aliveHostiles, groundY, solids, occupied, player, groundSupported, spawn);
    }

    public boolean hasPendingReinforcements() { return encounter.hasPending(); }

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
            for (WaveDef wave : battle.waves) {
                if (wave.type.isHostile()) roster.add(wave.type);
                wave.beats.forEach(beat -> roster.add(beat.type()));
            }
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
        public final List<EncounterWaveController.Beat> beats;
        public final int maxConcurrent;
        public WaveDef(EntityType t, int c, int d, WaveType wt) {
            type = t; count = c; fromDir = d; waveType = wt;
            beats = List.of(); maxConcurrent = c * (d == 2 ? 2 : 1);
        }
        public WaveDef(List<EncounterWaveController.Beat> beats, int maxConcurrent) {
            if (beats.isEmpty() || maxConcurrent < 1 || maxConcurrent > 8)
                throw new IllegalArgumentException("Invalid mixed wave");
            this.beats = List.copyOf(beats); this.maxConcurrent = maxConcurrent;
            type = beats.get(0).type(); count = beats.size(); fromDir = 0; waveType = WaveType.MIXED;
        }
        public boolean scripted() { return !beats.isEmpty(); }
    }

    public static class Coin {
        public double x, y;
        public boolean collected;
        public Coin(double x, double y) { this.x = x; this.y = y; }
    }
}
