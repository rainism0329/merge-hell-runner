package com.bigphil.mergehell.world;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/** Heap District mechanics owned by the simulation. No actors, rendering, wall clock or storage. */
public final class HeapDistrictController {
    public static final int POOL_WARNING_TICKS = 60;
    public static final int POOL_ACTIVE_TICKS = 250;
    public static final int MAX_POOLS = 2;
    public static final int MAX_BLOCKS = 2;
    public static final int CHANNEL_TICKS = 48;
    public static final int BOSS_NODE_COOLDOWN_TICKS = 750;
    public static final int GC_EXPOSE_TICKS = 150;
    public static final int BLOCK_EXPOSE_TICKS = 75;
    public static final int MAX_REFLUX_HEALING = 96;
    public static final int RESPAWN_SAFE_TICKS = 180;
    private static final int INTERACT_RANGE = 70;
    private static final int POOL_WIDTH = 120;
    private static final int POOL_DAMAGE = 8;
    private static final int DAMAGE_INTERVAL_TICKS = 60;
    private static final int BLOCK_WARNING_TICKS = 30;
    private static final int BLOCK_INTERVAL_TICKS = 300;

    public enum Choice { NONE, PURGE, SALVAGE }
    public enum PoolPhase { WARNING, ACTIVE }
    public enum NodeState { READY, CHANNELING, COOLDOWN, USED }

    /** Value geometry; rectangle() returns a new mutable adapter for the existing collision API. */
    public record Bounds(double x, double y, int width, int height) {
        public Bounds {
            if (!Double.isFinite(x) || !Double.isFinite(y) || width <= 0 || height <= 0)
                throw new IllegalArgumentException("Invalid bounds");
        }
        public double centerX() { return x + width / 2.0; }
        public double centerY() { return y + height / 2.0; }
        public Rectangle rectangle() { return new Rectangle((int) Math.floor(x), (int) Math.floor(y), width, height); }
        boolean intersects(Bounds other) {
            return x < other.x + other.width && x + width > other.x
                    && y < other.y + other.height && y + height > other.y;
        }
    }

    public record Input(Bounds player, double cameraX, int viewWidth, int groundY,
                        boolean active, boolean bossActive, Bounds boss, int bossStage) {
        public Input {
            Objects.requireNonNull(player, "player");
            if (!Double.isFinite(cameraX) || viewWidth < 360 || groundY < 160)
                throw new IllegalArgumentException("Invalid visible area");
            if (bossActive && (boss == null || bossStage < 1 || bossStage > 3))
                throw new IllegalArgumentException("An active boss needs bounds and a stage");
        }
    }

    public record PoolView(long id, Bounds bounds, PoolPhase phase, int ticksRemaining) { }
    public record NodeView(int id, Bounds bounds, boolean bossStation, NodeState state,
                           int ticksRemaining, boolean inReach) { }
    public record BlockView(long id, Bounds bounds, int warningTicksRemaining) { }
    public record Snapshot(long tick, int pressure, List<PoolView> pools, List<NodeView> nodes,
                           List<BlockView> blocks, String objective, String story,
                           int storyTicksRemaining, int bossExposeTicks) {
        public Snapshot {
            pools = List.copyOf(pools); nodes = List.copyOf(nodes); blocks = List.copyOf(blocks);
        }
    }

    public sealed interface Event permits DamagePlayer, Cleaned, Salvaged, RefluxArrived, RefluxBroken, Story { }
    public record DamagePlayer(int amount, long poolId) implements Event { }
    public record Cleaned(int nodeId, int xp, int score, Bounds clearedArea,
                          int bossExposeTicks) implements Event { }
    public record Salvaged(int nodeId, int xp, int score) implements Event { }
    public record RefluxArrived(long blockId, int healAmount) implements Event { }
    public record RefluxBroken(long blockId, double x, double y, boolean byGc,
                               int bossExposeTicks) implements Event { }
    public record Story(String id, String text) implements Event { }

    private static final class Node {
        final int id;
        final double x;
        final boolean bossStation;
        NodeState state = NodeState.READY;
        int timer;
        Node(int id, double x, boolean bossStation) { this.id = id; this.x = x; this.bossStation = bossStation; }
        Bounds bounds(int groundY) { return new Bounds(x - 24, groundY - 76, 48, 76); }
    }
    private static final class Pool {
        final long id;
        final Bounds bounds;
        PoolPhase phase = PoolPhase.WARNING;
        int timer = POOL_WARNING_TICKS;
        Pool(long id, Bounds bounds) { this.id = id; this.bounds = bounds; }
    }
    private static final class Block {
        final long id;
        double x, y;
        int warning = BLOCK_WARNING_TICKS;
        int lifetime = 500;
        Block(long id, double x, double y) { this.id = id; this.x = x; this.y = y; }
        Bounds bounds() { return new Bounds(x, y, 26, 26); }
    }

    private final Random random;
    private final List<Node> nodes = new ArrayList<>();
    private final List<Pool> pools = new ArrayList<>(MAX_POOLS);
    private final List<Block> blocks = new ArrayList<>(MAX_BLOCKS);
    private final List<Event> events = new ArrayList<>();
    private final Set<String> storiesSeen = new HashSet<>();
    private final ArrayDeque<Story> pendingStories = new ArrayDeque<>(2);
    private Input input;
    private long tick;
    private long nextObjectId = 1;
    private int pressure = 15;
    private int poolCooldown = 180;
    private int blockCooldown = 150;
    private int damageCooldown;
    private int safeTicks;
    private int bossHealingSpent;
    private int bossExposeTicks;
    private boolean bossEntered;
    private boolean bossEnded;
    private int lastBossStage;
    private int routeChapter;
    private int stationsPurged;
    private int stationsSalvaged;
    private String story = "";
    private int storyTicks;
    private Snapshot snapshot = new Snapshot(0, 15, List.of(), List.of(), List.of(),
            "Reach a GC station: purge leaks or risk salvaging", "", 0, 0);

    private HeapDistrictController(long seed) {
        random = new Random(seed);
        nodes.add(new Node(1, 760, false));
        nodes.add(new Node(2, 2600, false));
        nodes.add(new Node(3, 4650, false));
    }

    public static HeapDistrictController standard(long seed) { return new HeapDistrictController(seed); }
    public Snapshot snapshot() { return snapshot; }

    /** One 16 ms effective gameplay step. Inactive input preserves the published snapshot exactly. */
    public void update(Input next) {
        input = Objects.requireNonNull(next, "input");
        if (!next.active()) return;
        tick++;
        if (storyTicks > 0) storyTicks--;
        if (storyTicks == 0 && !pendingStories.isEmpty()) startStory(pendingStories.removeFirst());
        if (bossExposeTicks > 0) bossExposeTicks--;
        if (damageCooldown > 0) damageCooldown--;
        if (safeTicks > 0) safeTicks--;
        tell("ARRIVAL", "Intake: the old system kept every memory allocation. The leaks started here.");
        if (!bossEntered && !next.bossActive()) updateRouteStory();
        if (next.bossActive() && !bossEntered) enterBoss();
        if (next.bossActive() && next.bossStage() != lastBossStage) {
            if (next.bossStage() > lastBossStage) tell("BOSS_STAGE_" + next.bossStage(),
                    next.bossStage() == 2 ? "Protection has gone wrong: it is recruiting leftover objects as reinforcements."
                            : "The main pump is overloaded. Reflux is accelerating; save GC stations for heavy fire.");
            lastBossStage = next.bossStage();
        }
        if (bossEntered && !next.bossActive() && !bossEnded) {
            bossEnded = true;
            pools.clear(); blocks.clear(); bossExposeTicks = 0;
            pendingStories.clear(); storyTicks = 0; story = "";
            cancelChannels();
        }
        if (!bossEnded) {
            updateNodes();
            updatePools();
            if (next.bossActive()) updateBlocks();
            if (safeTicks == 0 && next.player().centerX() >= 420 && --poolCooldown <= 0) {
                spawnPool(false);
                poolCooldown = 625 - pressure * 2;
            }
        }
        publish();
    }

    /** Call only for a confirmed gameplay key press. A second press cannot queue a second reward. */
    public boolean interact(Choice choice) {
        Objects.requireNonNull(choice, "choice");
        if (choice == Choice.NONE || input == null || !input.active() || bossEnded) return false;
        for (Node node : nodes) {
            if (node.state != NodeState.READY || !available(node) || !inReach(node)) continue;
            if (choice == Choice.SALVAGE) {
                if (node.bossStation) return false;
                node.state = NodeState.USED;
                stationsSalvaged++;
                pressure = Math.min(100, pressure + 25);
                events.add(new Salvaged(node.id, 30, 600));
                spawnPool(true);
                tell("FIRST_CHOICE", "Salvage complete, but leftovers spilled out. Avoid the flashing leak zones.");
            } else {
                if (nodes.stream().anyMatch(candidate -> candidate.state == NodeState.CHANNELING)) return false;
                node.state = NodeState.CHANNELING;
                node.timer = CHANNEL_TICKS;
            }
            publish();
            return true;
        }
        return false;
    }

    /** A confirmed gun/melee intersection severs one block. Repeat intersections have no effect. */
    public boolean hitBlock(long id) {
        if (input == null || !input.active() || !input.bossActive() || bossEnded) return false;
        for (Iterator<Block> it = blocks.iterator(); it.hasNext();) {
            Block block = it.next();
            if (block.id != id) continue;
            if (block.warning > 0) return false;
            it.remove();
            broken(block, false);
            publish();
            return true;
        }
        return false;
    }

    public List<Event> drainEvents() {
        List<Event> drained = List.copyOf(events);
        events.clear();
        return drained;
    }

    /** Respawn clears danger, not spent stations, cooldowns, the boss heal budget or earned rewards. */
    public void resetTransient() {
        pools.clear(); blocks.clear(); cancelChannels();
        safeTicks = RESPAWN_SAFE_TICKS;
        poolCooldown = 1; blockCooldown = RESPAWN_SAFE_TICKS;
        damageCooldown = 0; bossExposeTicks = 0;
        events.removeIf(event -> !(event instanceof Cleaned || event instanceof Salvaged || event instanceof Story));
        if (input != null) publish();
    }

    private void enterBoss() {
        bossEntered = true;
        lastBossStage = input.bossStage();
        pools.clear(); blocks.clear(); cancelChannels();
        nodes.add(new Node(101, input.cameraX() + 190, true));
        nodes.add(new Node(102, input.cameraX() + 470, true));
        poolCooldown = 150; blockCooldown = 120;
        String feedback = stationsPurged > stationsSalvaged
                ? "You restored " + stationsPurged + " GC stations. The daemon still protects the reflux by mistake."
                : stationsSalvaged > 0 ? "You salvaged " + stationsSalvaged + " memory caches. Their leftovers now feed the main pump."
                : "The stations remain offline. The daemon mistakes reflux for protected memory.";
        tell("BOSS_ENTRY", feedback);
    }

    private boolean available(Node node) { return node.bossStation == input.bossActive(); }
    private boolean inReach(Node node) {
        Bounds player = input.player();
        return Math.abs(player.centerX() - node.x) <= INTERACT_RANGE
                && player.y() + player.height() >= input.groundY() - 90
                && player.y() < input.groundY() + 20;
    }

    private void updateNodes() {
        for (Node node : nodes) {
            if (node.state == NodeState.COOLDOWN && --node.timer <= 0) node.state = NodeState.READY;
            if (node.state != NodeState.CHANNELING) continue;
            if (!available(node) || !inReach(node)) {
                node.state = NodeState.READY; node.timer = 0;
            } else if (--node.timer <= 0) clean(node);
        }
    }

    private void clean(Node node) {
        node.state = node.bossStation ? NodeState.COOLDOWN : NodeState.USED;
        if (!node.bossStation) stationsPurged++;
        node.timer = node.bossStation ? BOSS_NODE_COOLDOWN_TICKS : 0;
        Bounds visible = new Bounds(input.cameraX(), 0, input.viewWidth(), input.groundY());
        pools.removeIf(pool -> pool.bounds.intersects(visible));
        for (Iterator<Block> it = blocks.iterator(); it.hasNext();) {
            Block block = it.next();
            if (block.bounds().intersects(visible)) { it.remove(); broken(block, true); }
        }
        pressure = Math.max(0, pressure - 25);
        poolCooldown = Math.max(poolCooldown, 250);
        int expose = input.bossActive() ? GC_EXPOSE_TICKS : 0;
        bossExposeTicks = Math.max(bossExposeTicks, expose);
        events.add(new Cleaned(node.id, node.bossStation ? 0 : 20, node.bossStation ? 0 : 300,
                visible, expose));
        if (input.bossActive()) tell("BOSS_WINDOW", "Reflux cut. The shell is unstable. Focus your fire now!");
        else tell("FIRST_CHOICE", "GC power restored. The leak is gone, but more stations are still offline ahead.");
    }

    private void updatePools() {
        for (Iterator<Pool> it = pools.iterator(); it.hasNext();) {
            Pool pool = it.next();
            if (pool.bounds.x() + pool.bounds.width() < input.cameraX() - 180
                    || pool.bounds.x() > input.cameraX() + input.viewWidth() + 180) {
                it.remove(); continue;
            }
            if (--pool.timer <= 0) {
                if (pool.phase == PoolPhase.WARNING) { pool.phase = PoolPhase.ACTIVE; pool.timer = POOL_ACTIVE_TICKS; }
                else { it.remove(); continue; }
            }
            if (pool.phase == PoolPhase.ACTIVE && safeTicks == 0 && damageCooldown == 0
                    && pool.bounds.intersects(input.player())) {
                events.add(new DamagePlayer(POOL_DAMAGE, pool.id));
                damageCooldown = DAMAGE_INTERVAL_TICKS;
            }
        }
    }

    private void spawnPool(boolean risk) {
        if (pools.size() >= MAX_POOLS || safeTicks > 0) return;
        double preferred = risk ? input.player().centerX() + 80
                : input.cameraX() + input.viewWidth() * (random.nextBoolean() ? .30 : .70);
        double x = Math.max(input.cameraX() + 45, Math.min(preferred - POOL_WIDTH / 2.0,
                input.cameraX() + input.viewWidth() - POOL_WIDTH - 45));
        Bounds bounds = new Bounds(x, input.groundY() - 14, POOL_WIDTH, 14);
        // Two pools keep at least 120 px of open ground between their collision bounds.
        if (pools.stream().anyMatch(pool -> Math.abs(pool.bounds.centerX() - bounds.centerX()) < POOL_WIDTH + 120)) return;
        pools.add(new Pool(nextObjectId++, bounds));
    }

    private void updateBlocks() {
        for (Iterator<Block> it = blocks.iterator(); it.hasNext();) {
            Block block = it.next();
            if (--block.lifetime <= 0) { it.remove(); continue; }
            if (block.warning > 0) { block.warning--; continue; }
            double dx = input.boss().centerX() - block.bounds().centerX();
            double dy = input.boss().centerY() - block.bounds().centerY();
            double distance = Math.hypot(dx, dy);
            double speed = 2.2 + input.bossStage() * .3;
            if (distance <= speed || block.bounds().intersects(input.boss())) {
                int heal = Math.min(12, MAX_REFLUX_HEALING - bossHealingSpent);
                bossHealingSpent += heal;
                pressure = Math.min(100, pressure + 8);
                events.add(new RefluxArrived(block.id, heal));
                it.remove();
            } else {
                block.x += dx / distance * speed; block.y += dy / distance * speed;
            }
        }
        if (safeTicks == 0 && --blockCooldown <= 0) {
            if (blocks.size() < MAX_BLOCKS) {
                double x = input.cameraX() + 65 + random.nextInt(90);
                double y = input.groundY() - 145 - random.nextInt(100);
                blocks.add(new Block(nextObjectId++, x, y));
            }
            blockCooldown = BLOCK_INTERVAL_TICKS;
        }
    }

    private void broken(Block block, boolean byGc) {
        bossExposeTicks = Math.max(bossExposeTicks, BLOCK_EXPOSE_TICKS);
        events.add(new RefluxBroken(block.id, block.bounds().centerX(), block.bounds().centerY(), byGc, BLOCK_EXPOSE_TICKS));
        tell("BOSS_WINDOW", "Reflux cut. The shell is unstable. Focus your fire now!");
    }

    private void cancelChannels() {
        for (Node node : nodes) if (node.state == NodeState.CHANNELING) { node.state = NodeState.READY; node.timer = 0; }
    }

    private void tell(String id, String text) {
        if (!storiesSeen.add(id)) return;
        Story beat = new Story(id, text);
        if (storyTicks == 0) startStory(beat);
        else {
            // Keep the active line intact and retain at most the two most relevant upcoming beats.
            if (pendingStories.size() == 2) pendingStories.removeFirst();
            pendingStories.addLast(beat);
        }
    }

    private void startStory(Story beat) {
        story = beat.text(); storyTicks = 240;
        events.add(beat);
    }

    private void updateRouteStory() {
        int chapter = input.player().centerX() >= 6200 ? 3 : input.player().centerX() >= 4000 ? 2
                : input.player().centerX() >= 1800 ? 1 : 0;
        if (chapter <= routeChapter) return;
        // A practice warp or large crossing announces only the latest reached region.
        routeChapter = chapter;
        switch (chapter) {
            case 1 -> tell("ARCHIVE", "Lost archive: retention never expired. Cleanup conflicts forced the GC stations offline.");
            case 2 -> tell("REFLUX_MAIN", "Reflux main: discarded memory never left the district. It was sent back to the pump.");
            case 3 -> tell("PUMP_ROOM", "Pump room: the daemon protects leftover objects and keeps pumping them back.");
            default -> { }
        }
    }

    private void publish() {
        List<PoolView> poolViews = pools.stream().map(pool -> new PoolView(pool.id, pool.bounds, pool.phase, pool.timer)).toList();
        List<NodeView> nodeViews = nodes.stream().filter(this::available).map(node -> new NodeView(node.id,
                node.bounds(input.groundY()), node.bossStation, node.state, node.timer, inReach(node))).toList();
        List<BlockView> blockViews = blocks.stream().map(block -> new BlockView(block.id, block.bounds(), block.warning)).toList();
        String objective;
        if (bossEnded) objective = "District stable. Keep moving.";
        else if (nodes.stream().anyMatch(node -> node.state == NodeState.CHANNELING)) objective = "Starting GC: stay nearby; leaving cancels.";
        else if (input.bossActive()) objective = bossExposeTicks > 0 ? "Core exposed: focus your fire!" : "Cut reflux; press E near a GC station.";
        else if (nodes.stream().anyMatch(node -> !node.bossStation && node.state == NodeState.READY && inReach(node))) objective = "E Purge: safer / F Salvage: more reward, more leaks";
        else objective = "Reach the next GC station. Jump over leaks.";
        snapshot = new Snapshot(tick, pressure, poolViews, nodeViews, blockViews,
                objective, storyTicks > 0 ? story : "", storyTicks, bossExposeTicks);
    }
}
