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

/** Destructible supports, delayed deck collapse and scanner fields for the third world. */
public final class BlueprintCitadelController {
    public static final int ROUTE_SUPPORT_HP = 120;
    public static final int BOSS_SUPPORT_HP = 160;
    public static final int CHANNEL_TICKS = 60;
    public static final int COLLAPSE_TICKS = 60;
    public static final int SCAN_WARNING_TICKS = 75;
    public static final int SCAN_ACTIVE_TICKS = 90;
    public static final int BOSS_SUPPORT_COOLDOWN_TICKS = 600;
    public static final int BOSS_EXPOSE_TICKS = 150;
    public static final int RESPAWN_SAFE_TICKS = 180;
    public static final int MAX_SCANS = 2;
    public static final int MAX_EVENTS = 32;
    private static final int INTERACT_RANGE = 58;
    private static final int DAMAGE_INTERVAL_TICKS = 60;

    public enum SupportState { ONLINE, OVERLOADING, COLLAPSING, DISABLED, COOLDOWN }
    public enum ScanPhase { WARNING, ACTIVE }

    public record Bounds(double x, double y, int width, int height) {
        public Bounds {
            if (!Double.isFinite(x) || !Double.isFinite(y) || width <= 0 || height <= 0)
                throw new IllegalArgumentException("Invalid blueprint bounds");
        }
        public double centerX() { return x + width / 2.0; }
        public double centerY() { return y + height / 2.0; }
        public Rectangle rectangle() { return new Rectangle((int) Math.floor(x), (int) Math.floor(y), width, height); }
        boolean intersects(Bounds other) {
            return x < other.x + other.width && x + width > other.x
                    && y < other.y + other.height && y + height > other.y;
        }
    }

    /** Identical coordinate convention to the other world controllers: player bounds are the hurtbox. */
    public record Input(Bounds player, double cameraX, int viewWidth, int groundY,
                        boolean active, boolean bossActive, Bounds boss, int bossStage) {
        public Input {
            Objects.requireNonNull(player, "player");
            if (!Double.isFinite(cameraX) || cameraX < 0 || viewWidth < 360 || groundY < 160)
                throw new IllegalArgumentException("Invalid blueprint viewport");
            if (bossActive && (boss == null || bossStage < 1 || bossStage > 3))
                throw new IllegalArgumentException("An active boss requires its bounds and stage");
        }
    }

    /** linkedPlatform remains available as structural metadata after its physical surface collapses. */
    public record SupportView(int id, Bounds bounds, boolean bossSupport, SupportState state,
                              int hp, int maxHp, int ticksRemaining, boolean inReach, Platform linkedPlatform) { }
    /** A telescoping scan head at the deck's right end projects left through this exact rectangle. */
    public record ScanView(long id, int supportId, Bounds bounds, double originX, double originY,
                           ScanPhase phase, int ticksRemaining) { }
    public record Snapshot(long tick, List<SupportView> supports, List<ScanView> scans,
                           String objective, String story, int storyTicksRemaining,
                           int bossExposeTicks, boolean bossActive) {
        public Snapshot { supports = List.copyOf(supports); scans = List.copyOf(scans); }
        public static Snapshot empty() {
            return new Snapshot(0, List.of(), List.of(), "", "", 0, 0, false);
        }
    }

    public sealed interface Event permits DamagePlayer, SupportBroken, Story { }
    public record DamagePlayer(int amount, long scanId) implements Event { }
    /** Root integration clears projectiles in clearedArea and applies the announced boss vulnerability. */
    public record SupportBroken(int id, int xp, int score, Bounds clearedArea, int bossExposeTicks) implements Event { }
    public record Story(String id, String text) implements Event { }

    private static final class Support {
        final int id;
        final double x;
        final boolean boss;
        final int rise;
        final int deckWidth;
        final int maxHp;
        int hp;
        int timer;
        int scanCooldown;
        int scanSequence;
        SupportState state = SupportState.ONLINE;

        Support(int id, double x, boolean boss, int rise, int deckWidth) {
            this.id = id; this.x = x; this.boss = boss; this.rise = rise; this.deckWidth = deckWidth;
            maxHp = boss ? BOSS_SUPPORT_HP : ROUTE_SUPPORT_HP;
            hp = maxHp;
            scanCooldown = 150 + (id % 100) * 37;
        }
        Bounds bounds(int groundY) { return new Bounds(x - 22, groundY - 76, 44, 76); }
        Platform platform(int groundY) { return new Platform(x + 65, groundY - rise, deckWidth, 14, Platform.Style.CATWALK); }
        boolean carriesDeck() { return state == SupportState.ONLINE || state == SupportState.OVERLOADING || state == SupportState.COLLAPSING; }
        boolean hittable() { return state == SupportState.ONLINE || state == SupportState.OVERLOADING; }
    }

    private static final class Scan {
        final long id;
        final int supportId;
        final Bounds bounds;
        ScanPhase phase = ScanPhase.WARNING;
        int timer = SCAN_WARNING_TICKS;
        Scan(long id, int supportId, Bounds bounds) { this.id = id; this.supportId = supportId; this.bounds = bounds; }
    }

    private final Random random;
    private final List<Support> supports = new ArrayList<>(5);
    private final List<Scan> scans = new ArrayList<>(MAX_SCANS);
    private final ArrayDeque<Event> events = new ArrayDeque<>(MAX_EVENTS);
    private final Set<String> storiesSeen = new HashSet<>();
    private final ArrayDeque<Story> pendingStories = new ArrayDeque<>(2);
    private Input input;
    private long tick;
    private long nextScanId = 1;
    private boolean bossEntered;
    private boolean bossEnded;
    private int lastBossStage;
    private int routeChapter;
    private int restoredSupports;
    private int safeTicks = 120;
    private int damageCooldown;
    private int bossExposeTicks;
    private String story = "";
    private int storyTicks;
    private List<Platform> platforms = List.of();
    private Snapshot snapshot = Snapshot.empty();

    private BlueprintCitadelController(long seed) {
        random = new Random(seed);
        supports.add(new Support(1, 700, false, 100, 240));
        supports.add(new Support(2, 2680, false, 100, 240));
        supports.add(new Support(3, 4680, false, 100, 240));
    }

    public static BlueprintCitadelController standard(long seed) { return new BlueprintCitadelController(seed); }
    public Snapshot snapshot() { return snapshot; }
    public List<Platform> platforms() { return platforms; }

    /** One effective 16 ms gameplay step. Inactive input freezes all timers and the published snapshot. */
    public void update(Input next) {
        input = Objects.requireNonNull(next, "input");
        if (!next.active()) return;
        tick++;
        if (storyTicks > 0) storyTicks--;
        if (storyTicks == 0 && !pendingStories.isEmpty()) startStory(pendingStories.removeFirst());
        if (safeTicks > 0) safeTicks--;
        if (damageCooldown > 0) damageCooldown--;
        if (bossExposeTicks > 0) bossExposeTicks--;
        tell("ARRIVAL", "Blueprint intake: the fortress keeps rebuilding its own defenses. Break a support to disconnect its scanner.");
        if (!bossEntered && !next.bossActive()) updateRouteStory();
        if (next.bossActive() && !bossEntered) enterBoss();
        if (bossEntered && !next.bossActive() && !bossEnded) finishBoss();
        if (next.bossActive() && next.bossStage() > lastBossStage) {
            lastBossStage = next.bossStage();
            tell("BOSS_STAGE_" + lastBossStage, lastBossStage == 2
                    ? "The Architect is redrawing the grid. Break either support to interrupt the next firing pattern."
                    : "The final blueprint is unstable. Watch the collapse outline and strike while the core is exposed.");
        }
        if (!bossEnded) {
            updateSupports();
            updateScans();
            if (safeTicks == 0 && bossExposeTicks == 0) spawnScans();
        }
        publish();
    }

    /** A confirmed nearby E press begins a single overload. Walking or jumping out cancels it. */
    public boolean interact() {
        if (!acceptsInput() || supports.stream().anyMatch(support -> support.state == SupportState.OVERLOADING)) return false;
        for (Support support : supports) {
            if (!available(support) || support.state != SupportState.ONLINE || !inReach(support)) continue;
            support.state = SupportState.OVERLOADING;
            support.timer = CHANNEL_TICKS;
            publish();
            return true;
        }
        return false;
    }

    /** The caller resolves swept projectile order and consumes the shot when true is returned. */
    public boolean hitSupport(int id, int damage) {
        if (damage <= 0 || !acceptsInput()) return false;
        for (Support support : supports) {
            if (support.id != id || !available(support) || !support.hittable()) continue;
            support.hp -= Math.min(support.hp, damage);
            if (support.hp == 0) breakSupport(support);
            publish();
            return true;
        }
        return false;
    }

    public List<Event> drainEvents() {
        List<Event> drained = List.copyOf(events); events.clear(); return drained;
    }

    /** Respawn clears transient danger and charging, but preserves already-earned structural changes and cooldowns. */
    public void resetTransient() {
        scans.clear(); cancelOverloads();
        safeTicks = RESPAWN_SAFE_TICKS; damageCooldown = 0; bossExposeTicks = 0;
        for (Support support : supports) support.scanCooldown = Math.max(support.scanCooldown, RESPAWN_SAFE_TICKS);
        events.removeIf(event -> event instanceof DamagePlayer
                || event instanceof SupportBroken broken && broken.xp() == 0);
        if (input != null) publish();
    }

    private boolean acceptsInput() { return input != null && input.active() && !bossEnded; }
    private boolean available(Support support) { return !bossEnded && support.boss == input.bossActive(); }
    private boolean inReach(Support support) {
        return Math.abs(input.player().centerX() - support.x) <= INTERACT_RANGE
                && Math.abs(input.player().y() + input.player().height() - input.groundY()) <= 6;
    }

    private void updateSupports() {
        for (Support support : supports) {
            if (!available(support)) continue;
            if (support.scanCooldown > 0) support.scanCooldown--;
            switch (support.state) {
                case OVERLOADING -> {
                    if (!inReach(support)) { support.state = SupportState.ONLINE; support.timer = 0; }
                    else if (--support.timer == 0) { support.hp = 0; breakSupport(support); }
                }
                case COLLAPSING -> {
                    if (--support.timer == 0) {
                        support.state = support.boss ? SupportState.COOLDOWN : SupportState.DISABLED;
                        support.timer = support.boss ? BOSS_SUPPORT_COOLDOWN_TICKS : 0;
                    }
                }
                case COOLDOWN -> {
                    if (--support.timer == 0) {
                        support.state = SupportState.ONLINE; support.hp = support.maxHp;
                        support.scanCooldown = SCAN_WARNING_TICKS + CHANNEL_TICKS;
                    }
                }
                default -> { }
            }
        }
    }

    private void breakSupport(Support support) {
        support.state = SupportState.COLLAPSING;
        support.timer = COLLAPSE_TICKS;
        scans.removeIf(scan -> scan.supportId == support.id);
        Bounds cleared;
        if (support.boss) {
            scans.clear(); bossExposeTicks = Math.max(bossExposeTicks, BOSS_EXPOSE_TICKS);
            for (Support node : supports) if (node.boss) node.scanCooldown = Math.max(node.scanCooldown, BOSS_EXPOSE_TICKS);
            cleared = new Bounds(input.cameraX(), 0, input.viewWidth(), input.groundY());
            tell("BOSS_WINDOW", "Support disconnected. The grid is interrupted; focus fire on the exposed core.");
        } else {
            restoredSupports++;
            cleared = new Bounds(support.x - 70, 0, support.deckWidth + 165, input.groundY());
            tell("SUPPORT_" + support.id, switch (support.id) {
                case 1 -> "First support disconnected. The scanner is off; its outlined walkway will collapse in one second.";
                case 2 -> "Second support disconnected. Each restored schematic removes one piece of the Architect's firing network.";
                default -> "Final route support disconnected. The lower road stays open, even when the elevated defenses collapse.";
            });
        }
        emit(new SupportBroken(support.id, support.boss ? 0 : 20, support.boss ? 0 : 350,
                cleared, support.boss ? BOSS_EXPOSE_TICKS : 0));
    }

    private void updateScans() {
        var iterator = scans.iterator();
        while (iterator.hasNext()) {
            Scan scan = iterator.next();
            if (scan.bounds.x() + scan.bounds.width() < input.cameraX() - 160
                    || scan.bounds.x() > input.cameraX() + input.viewWidth() + 160) { iterator.remove(); continue; }
            if (--scan.timer == 0) {
                if (scan.phase == ScanPhase.WARNING) { scan.phase = ScanPhase.ACTIVE; scan.timer = SCAN_ACTIVE_TICKS; }
                else { iterator.remove(); continue; }
            }
            if (scan.phase == ScanPhase.ACTIVE && safeTicks == 0 && damageCooldown == 0
                    && scan.bounds.intersects(input.player())) {
                emit(new DamagePlayer(8, scan.id)); damageCooldown = DAMAGE_INTERVAL_TICKS;
            }
        }
    }

    private void spawnScans() {
        for (Support support : supports) {
            if (scans.size() >= MAX_SCANS) break;
            if (!available(support) || !support.hittable() || support.scanCooldown > 0) continue;
            Platform deck = support.platform(input.groundY());
            double originX = deck.x + deck.width - 16;
            // Keep the complete 30 px player hurtbox clear throughout the nearby overload zone.
            double left = support.x + 80;
            if (originX < input.cameraX() + 20 || left > input.cameraX() + input.viewWidth() - 20) continue;
            if (scans.stream().anyMatch(scan -> scan.supportId == support.id)) continue;
            boolean low = (support.scanSequence++ & 1) == 0;
            int height = low ? 18 : 16;
            int y = low ? input.groundY() - height : (int) deck.y - 20;
            Bounds bounds = new Bounds(left, y, Math.max(1, (int) Math.round(originX - left)), height);
            scans.add(new Scan(nextScanId++, support.id, bounds));
            support.scanCooldown = (input.bossActive() ? 270 - input.bossStage() * 20 : 350) + random.nextInt(61);
        }
    }

    private void enterBoss() {
        bossEntered = true; lastBossStage = input.bossStage();
        scans.clear(); cancelOverloads(); safeTicks = Math.max(safeTicks, 90);
        int width = Math.min(170, Math.max(90, input.viewWidth() / 5));
        supports.add(new Support(101, input.cameraX() + input.viewWidth() * (190.0 / 960), true, 90, width));
        supports.add(new Support(102, input.cameraX() + input.viewWidth() * (470.0 / 960), true, 125, width));
        tell("BOSS_ENTRY", restoredSupports > 0
                ? "Your disconnected supports revealed the core circuit. Break either arena support to interrupt the Architect's grid."
                : "The Architect controls the arena through two supports. Break one to interrupt its grid and expose the core.");
    }

    private void finishBoss() {
        bossEnded = true; scans.clear(); cancelOverloads(); bossExposeTicks = 0;
        events.removeIf(event -> event instanceof DamagePlayer
                || event instanceof SupportBroken broken && broken.xp() == 0);
        pendingStories.clear(); storyTicks = 0; story = "";
        tell("BOSS_DEFEATED", "Blueprint accepted. The fortress can finally stop rebuilding its own failures.");
    }

    private void cancelOverloads() {
        for (Support support : supports) if (support.state == SupportState.OVERLOADING) {
            support.state = SupportState.ONLINE; support.timer = 0;
        }
    }

    private void updateRouteStory() {
        int chapter = input.player().centerX() >= 6200 ? 3 : input.player().centerX() >= 4000 ? 2
                : input.player().centerX() >= 1800 ? 1 : 0;
        if (chapter <= routeChapter) return;
        routeChapter = chapter;
        tell("ROUTE_" + chapter, switch (chapter) {
            case 1 -> "Assembly yard: every raised walkway shares power with a scanner. The cyan support shows what will collapse.";
            case 2 -> "Revision hall: the old blueprint marks maintenance crews as intruders. Disconnect its scanning network.";
            default -> "Core approach: two reusable arena supports feed the Architect. Their rebuild timers create another attack window.";
        });
    }

    private void tell(String id, String text) {
        if (!storiesSeen.add(id)) return;
        Story beat = new Story(id, text);
        if (storyTicks == 0) startStory(beat);
        else { if (pendingStories.size() == 2) pendingStories.removeFirst(); pendingStories.addLast(beat); }
    }
    private void startStory(Story beat) { story = beat.text(); storyTicks = 250; emit(beat); }
    private void emit(Event event) {
        // A stalled consumer may lose stale warnings, but never one of the three one-time route rewards.
        if (events.size() >= MAX_EVENTS) {
            var iterator = events.iterator();
            while (iterator.hasNext()) {
                Event old = iterator.next();
                if (!(old instanceof SupportBroken broken) || broken.xp() == 0) { iterator.remove(); break; }
            }
        }
        events.addLast(event);
    }

    private void publish() {
        if (input == null) return;
        var supportViews = new ArrayList<SupportView>();
        var activePlatforms = new ArrayList<Platform>();
        for (Support support : supports) if (available(support)) {
            Platform platform = support.platform(input.groundY());
            supportViews.add(new SupportView(support.id, support.bounds(input.groundY()), support.boss,
                    support.state, support.hp, support.maxHp, support.timer, inReach(support), platform));
            if (support.carriesDeck()) activePlatforms.add(platform);
        }
        platforms = List.copyOf(activePlatforms);
        List<ScanView> scanViews = scans.stream().map(scan -> new ScanView(scan.id, scan.supportId,
                scan.bounds, scan.bounds.x() + scan.bounds.width(), scan.bounds.centerY(), scan.phase, scan.timer)).toList();
        String objective;
        if (bossEnded) objective = "Citadel stable. Keep moving.";
        else if (supports.stream().anyMatch(support -> support.state == SupportState.OVERLOADING))
            objective = "Overloading support: stay on the ground nearby.";
        else if (supports.stream().anyMatch(support -> available(support) && support.state == SupportState.COLLAPSING))
            objective = "Walkway collapsing: leave the outlined platform.";
        else if (input.bossActive()) objective = bossExposeTicks > 0 ? "Core exposed: focus your fire!"
                : "Break an arena support to interrupt the grid.";
        else if (supports.stream().anyMatch(support -> available(support) && support.state == SupportState.ONLINE && inReach(support)))
            objective = "Shoot the support, or press E nearby to overload.";
        else objective = "Watch scanner warnings. Break supports to change the route.";
        snapshot = new Snapshot(tick, supportViews, scanViews, objective, storyTicks > 0 ? story : "",
                storyTicks, bossExposeTicks, input.bossActive() && !bossEnded);
    }
}
