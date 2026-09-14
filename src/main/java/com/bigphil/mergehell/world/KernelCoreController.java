package com.bigphil.mergehell.world;

import com.bigphil.mergehell.model.Platform;

import java.awt.Rectangle;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/** Grounded power stations, readable rail cycles and dash-triggered faults for world four. */
public final class KernelCoreController {
    public static final int RAIL_WARNING_TICKS = 75;
    public static final int RAIL_ACTIVE_TICKS = 90;
    public static final int STATION_DISABLED_TICKS = 600;
    public static final int NODE_ARMED_TICKS = 450;
    public static final int NODE_COOLDOWN_TICKS = 600;
    public static final int BOSS_EXPOSE_TICKS = 150;
    public static final int RESPAWN_SAFE_TICKS = 180;
    public static final int MAX_RAILS = 2;
    public static final int MAX_EVENTS = 32;
    private static final int INTERACT_RANGE = 58;
    private static final int DAMAGE_INTERVAL_TICKS = 60;

    public enum NodeState { READY, DISABLED, ARMED, COOLDOWN }
    public enum RailPhase { SAFE, WARNING, ACTIVE }

    public record Bounds(double x, double y, int width, int height) {
        public Bounds {
            if (!Double.isFinite(x) || !Double.isFinite(y) || width <= 0 || height <= 0)
                throw new IllegalArgumentException("Invalid kernel bounds");
        }
        public double centerX() { return x + width / 2.0; }
        public double centerY() { return y + height / 2.0; }
        public Rectangle rectangle() { return new Rectangle((int) Math.floor(x), (int) Math.floor(y), width, height); }
        boolean intersects(Bounds other) {
            return x < other.x + other.width && x + width > other.x
                    && y < other.y + other.height && y + height > other.y;
        }
    }

    /** Bounds are world-space hurtboxes; active=false freezes simulation and blocks E input. */
    public record Input(Bounds player, double cameraX, int viewWidth, int groundY,
                        boolean active, boolean bossActive, Bounds boss, int bossStage, boolean bossDashing) {
        public Input {
            Objects.requireNonNull(player, "player");
            if (!Double.isFinite(cameraX) || cameraX < 0 || viewWidth < 360 || groundY < 160)
                throw new IllegalArgumentException("Invalid kernel viewport");
            if (bossActive && (boss == null || bossStage < 1 || bossStage > 3))
                throw new IllegalArgumentException("An active boss requires its bounds and stage");
            if (bossDashing && !bossActive) throw new IllegalArgumentException("Only an active boss can dash");
        }
    }

    public record StationView(int id, Bounds bounds, boolean bossNode, NodeState state,
                              int ticksRemaining, boolean inReach, Platform safetyPlatform) { }
    /** Render these exact rectangles; SAFE never deals contact damage. */
    public record RailView(long id, int stationId, Bounds bounds, RailPhase phase, int ticksRemaining) { }
    /** Objective and story strings are localization keys, resolved by the presentation layer. */
    public record Snapshot(long tick, List<StationView> stations, List<RailView> rails,
                           String objective, String story, int storyTicksRemaining,
                           int bossExposeTicks, boolean bossActive) {
        public Snapshot { stations = List.copyOf(stations); rails = List.copyOf(rails); }
        public static Snapshot empty() { return new Snapshot(0, List.of(), List.of(), "", "", 0, 0, false); }
    }

    public sealed interface Event permits DamagePlayer, StationDisabled, Discharge, Story { }
    public record DamagePlayer(int amount, long railId) implements Event { }
    /** Only the first successful use of each route station earns this reward. */
    public record StationDisabled(int id, int xp, int score) implements Event { }
    /** The integration clears enemy bullets and interrupts the real boss for this duration. */
    public record Discharge(int id, Bounds clearedArea, int bossExposeTicks) implements Event { }
    public record Story(String id, String text) implements Event { }

    private static final class Station {
        final int id;
        final double x;
        final boolean boss;
        NodeState state = NodeState.READY;
        int timer;
        boolean rewarded;
        RailPhase railPhase = RailPhase.SAFE;
        int railTimer;

        Station(int id, double x, boolean boss, int safeDelay) {
            this.id = id; this.x = x; this.boss = boss; railTimer = safeDelay;
        }
        Bounds bounds(int groundY) { return new Bounds(x - 22, groundY - 70, 44, 70); }
        Bounds rail(int groundY) { return new Bounds(x + 82, groundY - 16, boss ? 200 : 300, 16); }
        Platform platform(int groundY) {
            return new Platform(x + (boss ? 105 : 148), groundY - 95, boss ? 130 : 145, 14, Platform.Style.CATWALK);
        }
    }

    private final Random random;
    private final List<Station> stations = new ArrayList<>(5);
    private final ArrayDeque<Event> events = new ArrayDeque<>(MAX_EVENTS);
    private final ArrayDeque<Story> pendingStories = new ArrayDeque<>(2);
    private final Set<String> storiesSeen = new HashSet<>();
    private Input input;
    private Bounds previousBoss;
    private long tick;
    private int safeTicks = 120;
    private int damageCooldown;
    private int bossExposeTicks;
    private boolean bossEntered;
    private boolean bossEnded;
    private int lastBossStage;
    private int routeChapter;
    private String story = "";
    private int storyTicks;
    private Snapshot snapshot = Snapshot.empty();
    private List<Platform> platforms = List.of();

    private KernelCoreController(long seed) {
        random = new Random(seed);
        stations.add(new Station(1, 720, false, 150 + random.nextInt(45)));
        stations.add(new Station(2, 2760, false, 150 + random.nextInt(45)));
        stations.add(new Station(3, 4780, false, 150 + random.nextInt(45)));
    }

    public static KernelCoreController standard(long seed) { return new KernelCoreController(seed); }
    public Snapshot snapshot() { return snapshot; }
    public List<Platform> platforms() { return platforms; }

    public void update(Input next) {
        input = Objects.requireNonNull(next, "input");
        if (!next.active()) {
            // Resume can reposition an actor; never sweep through an interval that was not simulated.
            previousBoss = null;
            return;
        }
        tick++;
        if (safeTicks > 0) safeTicks--;
        if (damageCooldown > 0) damageCooldown--;
        if (bossExposeTicks > 0) bossExposeTicks--;
        if (storyTicks > 0) storyTicks--;
        if (storyTicks == 0 && !pendingStories.isEmpty()) startStory(pendingStories.removeFirst());
        tell("ARRIVAL", "kernel.story.arrival");
        if (next.bossActive() && !bossEntered) enterBoss();
        if (bossEntered && !next.bossActive() && !bossEnded) finishBoss();
        if (!bossEntered) updateRouteStory();
        if (next.bossActive() && next.bossStage() > lastBossStage) {
            lastBossStage = next.bossStage();
            tellPriority("BOSS_STAGE_" + lastBossStage, "kernel.story.bossStage" + lastBossStage);
        }
        if (!bossEnded) {
            updateStations();
            detectDashFault();
            updateRails();
        }
        previousBoss = next.bossActive() && !bossEnded ? next.boss() : null;
        publish();
    }

    /** A nearby grounded E press turns off a route rail or arms an available boss fault. */
    public boolean interact() {
        if (!acceptsInput()) return false;
        for (Station station : stations) {
            if (!available(station) || station.state != NodeState.READY || !inReach(station)) continue;
            if (station.boss) {
                station.state = NodeState.ARMED; station.timer = NODE_ARMED_TICKS;
                tellPriority("NODE_ARMED", "kernel.story.armed");
            } else {
                station.state = NodeState.DISABLED; station.timer = STATION_DISABLED_TICKS;
                station.railPhase = RailPhase.SAFE; station.railTimer = STATION_DISABLED_TICKS;
                if (!station.rewarded) {
                    station.rewarded = true;
                    emit(new StationDisabled(station.id, 15, 250));
                    tell("STATION_" + station.id, "kernel.story.station" + station.id);
                }
            }
            publish();
            return true;
        }
        return false;
    }

    public List<Event> drainEvents() { List<Event> drained = List.copyOf(events); events.clear(); return drained; }

    /** Clear short-lived hazards and armed faults while retaining route rewards and spent-node cooldowns. */
    public void resetTransient() {
        safeTicks = RESPAWN_SAFE_TICKS; damageCooldown = 0; bossExposeTicks = 0; previousBoss = null;
        for (Station station : stations) {
            if (station.state == NodeState.ARMED) { station.state = NodeState.READY; station.timer = 0; }
            resetRail(station, RESPAWN_SAFE_TICKS);
        }
        events.removeIf(event -> event instanceof DamagePlayer || event instanceof Discharge);
        if (input != null) publish();
    }

    private boolean acceptsInput() { return input != null && input.active() && !bossEnded; }
    private boolean available(Station station) { return !bossEnded && station.boss == input.bossActive(); }
    private boolean inReach(Station station) {
        return Math.abs(input.player().centerX() - station.x) <= INTERACT_RANGE
                && Math.abs(input.player().y() + input.player().height() - input.groundY()) <= 6;
    }
    private boolean visible(Station station) {
        Bounds rail = station.rail(input.groundY());
        return rail.x() < input.cameraX() + input.viewWidth() - 16
                && rail.x() + rail.width() > input.cameraX() + 16;
    }

    private void updateStations() {
        for (Station station : stations) {
            if (!available(station) || station.state == NodeState.READY) continue;
            if (--station.timer == 0) {
                NodeState old = station.state;
                station.state = NodeState.READY;
                if (old != NodeState.ARMED) resetRail(station, 90);
            }
        }
    }

    private void detectDashFault() {
        if (!input.bossActive() || !input.bossDashing() || bossExposeTicks > 0) return;
        Station first = null;
        double firstTime = Double.POSITIVE_INFINITY;
        for (Station station : stations) {
            if (!station.boss || station.state != NodeState.ARMED) continue;
            double contact = sweptContact(previousBoss, input.boss(), station.bounds(input.groundY()));
            if (contact < firstTime) { firstTime = contact; first = station; }
        }
        if (first == null) return;
        first.state = NodeState.COOLDOWN; first.timer = NODE_COOLDOWN_TICKS;
        bossExposeTicks = BOSS_EXPOSE_TICKS;
        for (Station station : stations) if (station.boss) resetRail(station, BOSS_EXPOSE_TICKS + 60);
        emit(new Discharge(first.id, new Bounds(input.cameraX(), 0, input.viewWidth(), input.groundY()), BOSS_EXPOSE_TICKS));
        tellPriority("DISCHARGE", "kernel.story.discharge");
    }

    /** Sweep the moving rectangle, including a fast dash which skips the narrow node in one step. */
    private static double sweptContact(Bounds from, Bounds to, Bounds target) {
        if (from == null) return to.intersects(target) ? 0 : Double.POSITIVE_INFINITY;
        double dx = to.x() - from.x(), dy = to.y() - from.y();
        double enter = 0, leave = 1;
        double[] starts = { from.x(), from.y() };
        double[] deltas = { dx, dy };
        double[] mins = { target.x() - Math.max(from.width(), to.width()), target.y() - Math.max(from.height(), to.height()) };
        double[] maxs = { target.x() + target.width(), target.y() + target.height() };
        for (int axis = 0; axis < 2; axis++) {
            if (Math.abs(deltas[axis]) < 1e-9) {
                if (starts[axis] <= mins[axis] || starts[axis] >= maxs[axis]) return Double.POSITIVE_INFINITY;
            } else {
                double first = (mins[axis] - starts[axis]) / deltas[axis];
                double last = (maxs[axis] - starts[axis]) / deltas[axis];
                enter = Math.max(enter, Math.min(first, last));
                leave = Math.min(leave, Math.max(first, last));
                if (enter > leave) return Double.POSITIVE_INFINITY;
            }
        }
        return enter <= leave ? enter : Double.POSITIVE_INFINITY;
    }

    private void updateRails() {
        int displayed = 0;
        for (Station station : stations) {
            if (!available(station)) continue;
            if (!visible(station) || displayed++ >= MAX_RAILS) { resetRail(station, 90); continue; }
            if (station.state == NodeState.DISABLED || station.state == NodeState.COOLDOWN) {
                resetRail(station, station.timer); continue;
            }
            if (safeTicks > 0 || bossExposeTicks > 0) {
                resetRail(station, Math.max(60, Math.max(safeTicks, bossExposeTicks))); continue;
            }
            if (--station.railTimer <= 0) {
                switch (station.railPhase) {
                    case SAFE -> { station.railPhase = RailPhase.WARNING; station.railTimer = RAIL_WARNING_TICKS; }
                    case WARNING -> { station.railPhase = RailPhase.ACTIVE; station.railTimer = RAIL_ACTIVE_TICKS; }
                    case ACTIVE -> resetRail(station, 120 + random.nextInt(61));
                }
            }
            if (station.railPhase == RailPhase.ACTIVE && damageCooldown == 0
                    && station.rail(input.groundY()).intersects(input.player())) {
                emit(new DamagePlayer(8, station.id)); damageCooldown = DAMAGE_INTERVAL_TICKS;
            }
        }
    }

    private static void resetRail(Station station, int safeDelay) {
        station.railPhase = RailPhase.SAFE; station.railTimer = safeDelay;
    }

    private void enterBoss() {
        bossEntered = true; lastBossStage = input.bossStage(); previousBoss = null;
        safeTicks = Math.max(safeTicks, 120);
        stations.add(new Station(101, input.cameraX() + input.viewWidth() * (280.0 / 960), true, 150));
        stations.add(new Station(102, input.cameraX() + input.viewWidth() * (580.0 / 960), true, 210));
        for (Station station : stations) resetRail(station, 150);
        tellPriority("BOSS_ENTRY", "kernel.story.bossEntry");
    }

    private void finishBoss() {
        bossEnded = true; bossExposeTicks = 0; previousBoss = null;
        events.removeIf(event -> event instanceof DamagePlayer || event instanceof Discharge);
        pendingStories.clear(); story = ""; storyTicks = 0;
        tell("BOSS_DEFEATED", "kernel.story.bossDefeated");
    }

    private void updateRouteStory() {
        int chapter = input.player().centerX() >= 6200 ? 3 : input.player().centerX() >= 4000 ? 2
                : input.player().centerX() >= 1800 ? 1 : 0;
        if (chapter > routeChapter) {
            routeChapter = chapter; tell("ROUTE_" + chapter, "kernel.story.route" + chapter);
        }
    }

    /** Timely counterplay instructions replace old route chatter when the encounter changes. */
    private void tellPriority(String id, String key) {
        if (storiesSeen.contains(id)) return;
        pendingStories.clear(); story = ""; storyTicks = 0;
        tell(id, key);
    }

    private void tell(String id, String key) {
        if (!storiesSeen.add(id)) return;
        Story beat = new Story(id, key);
        if (storyTicks == 0) startStory(beat);
        else { if (pendingStories.size() == 2) pendingStories.removeFirst(); pendingStories.addLast(beat); }
    }
    private void startStory(Story beat) { story = beat.text(); storyTicks = 250; emit(beat); }
    private void emit(Event event) {
        if (events.size() >= MAX_EVENTS) {
            var iterator = events.iterator();
            while (iterator.hasNext()) if (!(iterator.next() instanceof StationDisabled)) { iterator.remove(); break; }
        }
        events.addLast(event);
    }

    private void publish() {
        if (input == null) return;
        var views = new ArrayList<StationView>();
        var rails = new ArrayList<RailView>();
        var decks = new ArrayList<Platform>();
        for (Station station : stations) if (available(station)) {
            Platform deck = station.platform(input.groundY());
            decks.add(deck);
            views.add(new StationView(station.id, station.bounds(input.groundY()), station.boss,
                    station.state, station.timer, inReach(station), deck));
            if (visible(station) && rails.size() < MAX_RAILS)
                rails.add(new RailView(station.id, station.id, station.rail(input.groundY()), station.railPhase, station.railTimer));
        }
        platforms = List.copyOf(decks);
        String objective = bossEnded ? "kernel.objective.complete" : input.bossActive()
                ? bossExposeTicks > 0 ? "kernel.objective.exposed"
                : views.stream().anyMatch(station -> station.state() == NodeState.ARMED) ? "kernel.objective.lure" : "kernel.objective.arm"
                : views.stream().anyMatch(station -> station.inReach() && station.state() == NodeState.READY)
                ? "kernel.objective.switch" : "kernel.objective.route";
        snapshot = new Snapshot(tick, views, rails, objective, story, storyTicks, bossExposeTicks, input.bossActive() && !bossEnded);
    }
}
