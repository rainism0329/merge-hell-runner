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

/** World five revisits three readable hazards, then combines two anchors into one boss counter. */
public final class SingularityEdgeController {
    public static final int HAZARD_WARNING_TICKS = 75;
    public static final int HAZARD_ACTIVE_TICKS = 90;
    public static final int ANCHOR_STABILIZED_TICKS = 600;
    public static final int LINK_ARMED_TICKS = 500;
    public static final int LINK_COOLDOWN_TICKS = 600;
    public static final int BOSS_EXPOSE_TICKS = 150;
    public static final int RESPAWN_SAFE_TICKS = 180;
    public static final int MAX_HAZARDS = 1;
    public static final int MAX_EVENTS = 32;
    private static final int INTERACT_RANGE = 58;
    private static final int DAMAGE_INTERVAL_TICKS = 60;

    public enum Echo { MEMORY, BLUEPRINT, KERNEL }
    public enum AnchorState { READY, STABILIZED, ARMED, COOLDOWN }
    public enum HazardPhase { SAFE, WARNING, ACTIVE }

    public record Bounds(double x, double y, int width, int height) {
        public Bounds {
            if (!Double.isFinite(x) || !Double.isFinite(y) || width <= 0 || height <= 0)
                throw new IllegalArgumentException("Invalid singularity bounds");
        }
        public double centerX() { return x + width / 2.0; }
        public double centerY() { return y + height / 2.0; }
        public Rectangle rectangle() { return new Rectangle((int) Math.floor(x), (int) Math.floor(y), width, height); }
        boolean intersects(Bounds other) {
            return x < other.x + other.width && x + width > other.x
                    && y < other.y + other.height && y + height > other.y;
        }
    }

    /** Player bounds are world-space hurtboxes. Inactive updates freeze all timers and block E. */
    public record Input(Bounds player, double cameraX, int viewWidth, int groundY,
                        boolean active, boolean bossActive, int bossStage) {
        public Input {
            Objects.requireNonNull(player, "player");
            if (!Double.isFinite(cameraX) || cameraX < 0 || viewWidth < 360 || groundY < 160)
                throw new IllegalArgumentException("Invalid singularity viewport");
            if (bossActive && (bossStage < 1 || bossStage > 3))
                throw new IllegalArgumentException("An active boss requires its stage");
        }
    }

    public record AnchorView(int id, Bounds bounds, boolean bossNode, Echo echo, AnchorState state,
                             int ticksRemaining, boolean inReach, Platform safetyPlatform) {
        /** The inert housing stays connected to its anchor even when its echo is suppressed. */
        public Bounds fieldBounds() {
            return bossNode ? null : echoBounds(echo, bounds.centerX(), (int) Math.round(bounds.y() + bounds.height()));
        }
    }
    /** Presentation must use this exact rectangle and phase; SAFE and WARNING never hurt the player. */
    public record HazardView(long id, int anchorId, Echo echo, Bounds bounds, HazardPhase phase,
                             int ticksRemaining) { }
    /** Objective and story are localization keys, resolved by the presentation layer. */
    public record Snapshot(long tick, List<AnchorView> anchors, List<HazardView> hazards,
                           String objective, String story, int storyTicksRemaining,
                           int bossExposeTicks, boolean bossActive) {
        public Snapshot { anchors = List.copyOf(anchors); hazards = List.copyOf(hazards); }
        public static Snapshot empty() { return new Snapshot(0, List.of(), List.of(), "", "", 0, 0, false); }
    }

    public sealed interface Event permits DamagePlayer, AnchorStabilized, Resonance, Story { }
    public record DamagePlayer(int amount, long hazardId) implements Event { }
    /** Only a route anchor's first successful stabilization earns a reward. */
    public record AnchorStabilized(int id, int xp, int score) implements Event { }
    /** Clear enemy bullets and interrupt the real boss before settling combat collisions. */
    public record Resonance(Bounds clearedArea, int bossExposeTicks) implements Event { }
    public record Story(String id, String text) implements Event { }

    private static final class Anchor {
        final int id;
        final double x;
        final boolean boss;
        final Echo echo;
        AnchorState state = AnchorState.READY;
        int timer;
        boolean rewarded;
        HazardPhase phase = HazardPhase.SAFE;
        int hazardTimer;

        Anchor(int id, double x, boolean boss, Echo echo, int delay) {
            this.id = id; this.x = x; this.boss = boss; this.echo = echo; hazardTimer = delay;
        }
        Bounds bounds(int groundY) { return new Bounds(x - 22, groundY - 70, 44, 70); }
        Bounds hazard(int groundY) {
            return echoBounds(echo, x, groundY);
        }
        Platform platform(int groundY) {
            if (boss) return new Platform(x - 65, groundY - 95, 130, 14, Platform.Style.CATWALK);
            // The scan is taller than the deck, so the deck ends safely before it and supports a jump over it.
            if (echo == Echo.BLUEPRINT) return new Platform(x + 50, groundY - 95, 90, 14, Platform.Style.CATWALK);
            return new Platform(x + 132, groundY - 95, 145, 14, Platform.Style.CATWALK);
        }
    }

    private final Random random;
    private final List<Anchor> anchors = new ArrayList<>(5);
    private final ArrayDeque<Event> events = new ArrayDeque<>(MAX_EVENTS);
    private final ArrayDeque<Story> pendingStories = new ArrayDeque<>(2);
    private final Set<String> storiesSeen = new HashSet<>();
    private Input input;
    private long tick;
    private int safeTicks = 120;
    private int damageCooldown;
    private int bossExposeTicks;
    private boolean bossEntered;
    private boolean bossEnded;
    private int lastBossStage;
    private int routeChapter;
    private Anchor selectedHazard;
    private String story = "";
    private int storyTicks;
    private Snapshot snapshot = Snapshot.empty();
    private List<Platform> platforms = List.of();

    private SingularityEdgeController(long seed) {
        random = new Random(seed);
        anchors.add(new Anchor(1, 900, false, Echo.MEMORY, 150 + random.nextInt(45)));
        anchors.add(new Anchor(2, 3000, false, Echo.BLUEPRINT, 150 + random.nextInt(45)));
        anchors.add(new Anchor(3, 5000, false, Echo.KERNEL, 150 + random.nextInt(45)));
    }

    public static SingularityEdgeController standard(long seed) { return new SingularityEdgeController(seed); }
    public Snapshot snapshot() { return snapshot; }
    public List<Platform> platforms() { return platforms; }

    public void update(Input next) {
        input = Objects.requireNonNull(next, "input");
        if (!next.active()) return;
        tick++;
        if (safeTicks > 0) safeTicks--;
        if (damageCooldown > 0) damageCooldown--;
        if (bossExposeTicks > 0) bossExposeTicks--;
        if (storyTicks > 0) storyTicks--;
        if (storyTicks == 0 && !pendingStories.isEmpty()) startStory(pendingStories.removeFirst());
        tell("ARRIVAL", "singularity.story.arrival");
        if (next.bossActive() && !bossEntered) enterBoss();
        if (bossEntered && !next.bossActive() && !bossEnded) finishBoss();
        if (!bossEntered) updateRouteStory();
        if (next.bossActive() && next.bossStage() > lastBossStage) {
            lastBossStage = next.bossStage();
            tellPriority("BOSS_STAGE_" + lastBossStage, "singularity.story.bossStage" + lastBossStage);
        }
        if (!bossEnded) {
            updateAnchors();
            updateHazard();
        }
        publish();
    }

    /** A grounded E press stabilizes a route echo, or links two different available boss anchors. */
    public boolean interact() {
        if (input == null || !input.active() || bossEnded) return false;
        for (Anchor anchor : anchors) {
            if (!available(anchor) || anchor.state != AnchorState.READY || !inReach(anchor)) continue;
            if (anchor.boss) {
                Anchor armed = anchors.stream().filter(other -> other.boss && other.state == AnchorState.ARMED)
                        .findFirst().orElse(null);
                if (armed == null) {
                    anchor.state = AnchorState.ARMED; anchor.timer = LINK_ARMED_TICKS;
                    tellPriority("ARMED", "singularity.story.armed");
                } else {
                    armed.state = AnchorState.COOLDOWN; armed.timer = LINK_COOLDOWN_TICKS;
                    anchor.state = AnchorState.COOLDOWN; anchor.timer = LINK_COOLDOWN_TICKS;
                    bossExposeTicks = BOSS_EXPOSE_TICKS;
                    emit(new Resonance(new Bounds(input.cameraX(), 0, input.viewWidth(), input.groundY()), BOSS_EXPOSE_TICKS));
                    tellPriority("RESONANCE", "singularity.story.resonance");
                }
            } else {
                anchor.state = AnchorState.STABILIZED; anchor.timer = ANCHOR_STABILIZED_TICKS;
                resetHazard(anchor, ANCHOR_STABILIZED_TICKS);
                if (anchor == selectedHazard) selectedHazard = null;
                if (!anchor.rewarded) {
                    anchor.rewarded = true;
                    emit(new AnchorStabilized(anchor.id, 15, 250));
                    tell("ANCHOR_" + anchor.id, "singularity.story.anchor" + anchor.id);
                }
            }
            publish();
            return true;
        }
        return false;
    }

    public List<Event> drainEvents() { List<Event> drained = List.copyOf(events); events.clear(); return drained; }

    /** Respawn removes danger and an unfinished link without resetting earned rewards or spent cooldowns. */
    public void resetTransient() {
        safeTicks = RESPAWN_SAFE_TICKS; damageCooldown = 0; bossExposeTicks = 0; selectedHazard = null;
        for (Anchor anchor : anchors) {
            if (anchor.state == AnchorState.ARMED) { anchor.state = AnchorState.READY; anchor.timer = 0; }
            resetHazard(anchor, RESPAWN_SAFE_TICKS);
        }
        events.removeIf(event -> event instanceof DamagePlayer || event instanceof Resonance);
        if (input != null) publish();
    }

    private boolean available(Anchor anchor) { return !bossEnded && anchor.boss == input.bossActive(); }
    private boolean inReach(Anchor anchor) {
        return Math.abs(input.player().centerX() - anchor.x) <= INTERACT_RANGE
                && Math.abs(input.player().y() + input.player().height() - input.groundY()) <= 6;
    }
    private boolean visible(Anchor anchor) {
        Bounds hazard = anchor.hazard(input.groundY());
        return hazard.x() < input.cameraX() + input.viewWidth() - 16
                && hazard.x() + hazard.width() > input.cameraX() + 16;
    }

    private void updateAnchors() {
        for (Anchor anchor : anchors) {
            if (!available(anchor) || anchor.state == AnchorState.READY) continue;
            if (--anchor.timer == 0) {
                anchor.state = AnchorState.READY;
                resetHazard(anchor, 90);
            }
        }
    }

    private void updateHazard() {
        Anchor nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        if (!input.bossActive()) {
            for (Anchor anchor : anchors) {
                if (anchor.boss || anchor.state != AnchorState.READY || !visible(anchor)) continue;
                double distance = Math.abs(anchor.x - input.player().centerX());
                if (distance < nearestDistance) { nearest = anchor; nearestDistance = distance; }
            }
        }
        for (Anchor anchor : anchors) if (anchor != nearest) resetHazard(anchor, 90);
        if (nearest != selectedHazard && nearest != null) resetHazard(nearest, 90);
        selectedHazard = nearest;
        if (nearest == null) return;
        if (safeTicks > 0) { resetHazard(nearest, Math.max(60, safeTicks)); return; }
        if (--nearest.hazardTimer <= 0) {
            switch (nearest.phase) {
                case SAFE -> { nearest.phase = HazardPhase.WARNING; nearest.hazardTimer = HAZARD_WARNING_TICKS; }
                case WARNING -> { nearest.phase = HazardPhase.ACTIVE; nearest.hazardTimer = HAZARD_ACTIVE_TICKS; }
                case ACTIVE -> resetHazard(nearest, 120 + random.nextInt(61));
            }
        }
        if (nearest.phase == HazardPhase.ACTIVE && damageCooldown == 0
                && nearest.hazard(input.groundY()).intersects(input.player())) {
            emit(new DamagePlayer(8, nearest.id)); damageCooldown = DAMAGE_INTERVAL_TICKS;
        }
    }

    private static void resetHazard(Anchor anchor, int delay) { anchor.phase = HazardPhase.SAFE; anchor.hazardTimer = delay; }

    private static Bounds echoBounds(Echo echo, double x, int groundY) {
        return switch (echo) {
            case MEMORY -> new Bounds(x + 82, groundY - 18, 260, 18);
            case BLUEPRINT -> new Bounds(x + 160, groundY - 170, 46, 170);
            case KERNEL -> new Bounds(x + 82, groundY - 16, 260, 16);
        };
    }

    private void enterBoss() {
        bossEntered = true; lastBossStage = input.bossStage(); selectedHazard = null;
        // The combat camera is fixed. Anchors are anchored once and never follow later camera changes.
        anchors.add(new Anchor(101, input.cameraX() + 280, true, Echo.MEMORY, 0));
        anchors.add(new Anchor(102, input.cameraX() + 580, true, Echo.KERNEL, 0));
        for (Anchor anchor : anchors) resetHazard(anchor, 150);
        tellPriority("BOSS_ENTRY", "singularity.story.bossEntry");
    }

    private void finishBoss() {
        bossEnded = true; bossExposeTicks = 0; selectedHazard = null;
        events.removeIf(event -> event instanceof DamagePlayer || event instanceof Resonance);
        pendingStories.clear(); story = ""; storyTicks = 0;
        tell("BOSS_DEFEATED", "singularity.story.bossDefeated");
    }

    private void updateRouteStory() {
        int chapter = input.player().centerX() >= 4200 ? 3 : input.player().centerX() >= 2200 ? 2
                : input.player().centerX() >= 250 ? 1 : 0;
        if (chapter > routeChapter) { routeChapter = chapter; tell("ROUTE_" + chapter, "singularity.story.route" + chapter); }
    }

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
            while (iterator.hasNext()) if (!(iterator.next() instanceof AnchorStabilized)) { iterator.remove(); break; }
        }
        events.addLast(event);
    }

    private void publish() {
        if (input == null) return;
        var views = new ArrayList<AnchorView>();
        var decks = new ArrayList<Platform>();
        for (Anchor anchor : anchors) if (available(anchor)) {
            Platform deck = anchor.platform(input.groundY());
            decks.add(deck);
            views.add(new AnchorView(anchor.id, anchor.bounds(input.groundY()), anchor.boss, anchor.echo,
                    anchor.state, anchor.timer, inReach(anchor), deck));
        }
        List<HazardView> hazards = selectedHazard == null ? List.of()
                : List.of(new HazardView(selectedHazard.id, selectedHazard.id, selectedHazard.echo,
                    selectedHazard.hazard(input.groundY()), selectedHazard.phase, selectedHazard.hazardTimer));
        platforms = List.copyOf(decks);
        String objective = bossEnded ? "singularity.objective.complete" : input.bossActive()
                ? bossExposeTicks > 0 ? "singularity.objective.exposed"
                : views.stream().anyMatch(anchor -> anchor.state() == AnchorState.ARMED)
                    ? "singularity.objective.second"
                    : views.stream().anyMatch(anchor -> anchor.state() == AnchorState.COOLDOWN)
                        ? "singularity.objective.cooldown" : "singularity.objective.link"
                : views.stream().anyMatch(anchor -> anchor.inReach() && anchor.state() == AnchorState.READY)
                    ? "singularity.objective.switch" : "singularity.objective.route";
        snapshot = new Snapshot(tick, views, hazards, objective, story, storyTicks, bossExposeTicks,
                input.bossActive() && !bossEnded);
    }
}
