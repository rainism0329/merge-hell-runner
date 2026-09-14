package com.bigphil.mergehell.world;

import com.bigphil.mergehell.model.Platform;
import com.bigphil.mergehell.model.Projectile;

import java.awt.Rectangle;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

/** Deterministic, bounded route geometry and foot contact effects, owned by the simulation. */
public final class TraversalEnvironment {
    public static final int CHUNK_WIDTH = 2048;
    public static final int MAX_CHUNKS = 8;
    public static final int MAX_RIPPLES = 24;
    public static final int MAX_DROPLETS = 96;
    private static final int RIPPLE_TICKS = 38;
    private static final int MAX_BURSTS_PER_TICK = 2;
    private static final double FOOT_TOLERANCE = 4;

    public enum PropKind { CAPACITOR, SUPPLY }

    public record WaterView(long id, double x, int width, int surfaceY, int depth) {
        boolean contains(double pointX) { return pointX >= x && pointX <= x + width; }
    }

    public record PropView(long id, PropKind kind, double x, double y, int width, int height) {
        public Rectangle bounds() { return new Rectangle((int) Math.floor(x), (int) Math.floor(y), width, height); }
        public double centerX() { return x + width / 2.0; }
        public double centerY() { return y + height / 2.0; }
    }

    public record RippleView(double x, double y, double radius, float alpha, float strength) { }
    public record DropletView(double x, double y, double radius, float alpha) { }
    public record BreakEvent(long id, PropKind kind, double x, double y, int radius) { }

    public record Snapshot(long tick, List<WaterView> water, List<Platform> platforms,
                           List<PropView> props, List<RippleView> ripples,
                           List<DropletView> droplets, boolean bossArena) {
        public Snapshot {
            water = List.copyOf(water);
            platforms = List.copyOf(platforms);
            props = List.copyOf(props);
            ripples = List.copyOf(ripples);
            droplets = List.copyOf(droplets);
        }
        public static Snapshot empty() {
            return new Snapshot(0, List.of(), List.of(), List.of(), List.of(), List.of(), false);
        }
    }

    /** Player coordinates are the left edge and the soles, before and after one fixed step. */
    public record Step(double previousX, double previousBottom, double x, double bottom,
                       int width, double cameraX, int viewWidth, boolean onGround,
                       boolean dashing, boolean active, boolean bossArena) {
        public Step {
            if (!Double.isFinite(previousX) || !Double.isFinite(previousBottom)
                    || !Double.isFinite(x) || !Double.isFinite(bottom) || width <= 0)
                throw new IllegalArgumentException("Invalid player contact geometry");
            validateViewport(cameraX, viewWidth);
        }
    }

    private static final class Chunk {
        final List<WaterView> water;
        final List<Platform> platforms;
        final List<PropView> props;
        int brokenMask;

        Chunk(List<WaterView> water, List<Platform> platforms, List<PropView> props) {
            this.water = List.copyOf(water);
            this.platforms = List.copyOf(platforms);
            this.props = List.copyOf(props);
        }
    }

    private record Ripple(double x, double y, float strength, long born) { }
    private record Droplet(double x, double y, double vx, double vy, double radius,
                           int lifetime, long born) { }

    private final int level;
    private final long seed;
    private final int groundY;
    private final Map<Long, Chunk> chunks = new LinkedHashMap<>();
    private final ArrayDeque<Ripple> ripples = new ArrayDeque<>();
    private final ArrayDeque<Droplet> droplets = new ArrayDeque<>();
    private List<WaterView> water = List.of();
    private List<Platform> platforms = List.of();
    private List<PropView> props = List.of();
    // Retired routes can be redrawn on backtracking, but their rewards never regenerate.
    private long retiredBefore;
    private long tick;
    private long lastWaterId = -1;
    private long burstSerial;
    private double footDistance;
    private boolean active;
    private boolean bossArena;
    private Snapshot snapshot = Snapshot.empty();

    public TraversalEnvironment(int level, long seed, int groundY) {
        if (level < 0 || level > 4 || groundY < 160)
            throw new IllegalArgumentException("Invalid route configuration");
        this.level = level;
        this.seed = seed;
        this.groundY = groundY;
    }

    /** Call before moving actors, while gameplay is active, so landing uses this same geometry. */
    public void prepare(double cameraX, int viewWidth, boolean bossArena) {
        validateViewport(cameraX, viewWidth);
        long first = Math.max(0, (long) Math.floor(cameraX / CHUNK_WIDTH) - 1);
        long last = Math.min(first + MAX_CHUNKS - 1,
                (long) Math.floor((cameraX + viewWidth + CHUNK_WIDTH) / CHUNK_WIDTH));
        boolean changed = this.bossArena != bossArena;
        this.bossArena = bossArena;
        retiredBefore = Math.max(retiredBefore, first);
        var iterator = chunks.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getKey() < first || entry.getKey() > last) {
                // Also retire discarded future chunks after an exceptional backwards camera jump.
                // This keeps collection one-shot without an unbounded set of consumed IDs.
                if (entry.getValue().brokenMask != 0)
                    retiredBefore = Math.max(retiredBefore, entry.getKey() + 1);
                iterator.remove();
                changed = true;
            }
        }
        for (long index = first; index <= last; index++) {
            if (!chunks.containsKey(index)) {
                Chunk chunk = generate(index);
                if (index < retiredBefore) chunk.brokenMask = (1 << chunk.props.size()) - 1;
                chunks.put(index, chunk);
                changed = true;
            }
        }
        if (bossArena && (!ripples.isEmpty() || !droplets.isEmpty())) {
            ripples.clear(); droplets.clear(); lastWaterId = -1; footDistance = 0;
            changed = true;
        }
        if (changed) {
            rebuildGeometry();
            publish();
        }
    }

    public List<Platform> platforms() { return platforms; }
    public Snapshot snapshot() { return snapshot; }
    public int chunkCount() { return chunks.size(); }

    public void update(Step step) {
        Objects.requireNonNull(step, "step");
        active = step.active();
        if (!active) return;
        prepare(step.cameraX(), step.viewWidth(), step.bossArena());
        tick++;
        ripples.removeIf(ripple -> tick - ripple.born() >= RIPPLE_TICKS);
        droplets.removeIf(droplet -> expired(droplet, tick - droplet.born()));
        if (!bossArena) updateContact(step);
        publish();
    }

    /**
     * Claims the nearest intact prop on a friendly projectile's most recent sweep.
     * The caller must consume/remove the projectile immediately when a result is returned,
     * and owns combat damage, pickups and reward dispatch. No actor is changed here.
     */
    public Optional<BreakEvent> hit(Projectile projectile) {
        return hit(projectile, Double.POSITIVE_INFINITY);
    }

    /** Only claim a prop reached before the caller's next enemy or boss collision. */
    public Optional<BreakEvent> hit(Projectile projectile, double maxFraction) {
        Objects.requireNonNull(projectile, "projectile");
        if (Double.isNaN(maxFraction) || maxFraction < 0)
            throw new IllegalArgumentException("Invalid collision fraction");
        if (!active || bossArena || projectile.isDead() || projectile.getType().isHostile())
            return Optional.empty();
        PropView nearest = null;
        double firstHit = Double.POSITIVE_INFINITY;
        for (PropView prop : props) {
            double hit = projectile.hitFraction(prop.bounds());
            if (hit < firstHit) { firstHit = hit; nearest = prop; }
        }
        if (nearest == null || firstHit > maxFraction) return Optional.empty();
        for (Chunk chunk : chunks.values()) {
            for (int index = 0; index < chunk.props.size(); index++) {
                if (chunk.props.get(index).id() == nearest.id()) chunk.brokenMask |= 1 << index;
            }
        }
        rebuildGeometry();
        publish();
        return Optional.of(new BreakEvent(nearest.id(), nearest.kind(), nearest.centerX(),
                nearest.centerY(), nearest.kind() == PropKind.CAPACITOR ? 150 : 0));
    }

    private void updateContact(Step step) {
        double currentCenter = step.x() + step.width() / 2.0;
        double previousCenter = step.previousX() + step.width() / 2.0;
        boolean feetDown = step.onGround() && Math.abs(step.bottom() - groundY) <= FOOT_TOLERANCE;
        boolean previousFeetDown = Math.abs(step.previousBottom() - groundY) <= FOOT_TOLERANCE;
        boolean landing = feetDown && step.previousBottom() < groundY - FOOT_TOLERANCE;
        long currentWater = -1;
        int emitted = 0;
        for (WaterView pool : water) {
            boolean inside = feetDown && pool.contains(currentCenter);
            boolean crossed = feetDown && previousFeetDown
                    && Math.max(previousCenter, currentCenter) >= pool.x()
                    && Math.min(previousCenter, currentCenter) <= pool.x() + pool.width();
            if (inside) currentWater = pool.id();
            if (!inside && !crossed) continue;
            if (emitted >= MAX_BURSTS_PER_TICK) continue;
            double contactX = Math.max(pool.x() + 2, Math.min(pool.x() + pool.width() - 2, currentCenter));
            double movement = Math.abs(currentCenter - previousCenter);
            if (landing || (inside && lastWaterId != pool.id()) || (crossed && !inside)) {
                burst(contactX, pool.surfaceY(), landing ? 2.1f : step.dashing() ? 1.7f : 1.15f,
                        Math.signum(currentCenter - previousCenter));
                footDistance = 0;
                emitted++;
            } else if (inside && movement > 0.2) {
                footDistance += movement;
                if (footDistance >= (step.dashing() ? 34 : 28)) {
                    burst(contactX, pool.surfaceY(), step.dashing() ? 1.7f : 0.85f,
                            Math.signum(currentCenter - previousCenter));
                    footDistance %= step.dashing() ? 34 : 28;
                    emitted++;
                }
            }
        }
        lastWaterId = currentWater;
        if (currentWater < 0) footDistance = 0;
    }

    private void burst(double x, double y, float strength, double direction) {
        while (ripples.size() >= MAX_RIPPLES) ripples.removeFirst();
        ripples.addLast(new Ripple(x, y, strength, tick));
        Random random = new Random(mix(seed ^ (++burstSerial * 0x9e3779b97f4a7c15L)));
        int count = strength > 1.5 ? 12 : 7;
        for (int i = 0; i < count; i++) {
            while (droplets.size() >= MAX_DROPLETS) droplets.removeFirst();
            double vx = (random.nextDouble() - 0.5) * 3.0 * strength - direction * 0.65;
            double vy = -(1.7 + random.nextDouble() * 1.8) * strength;
            droplets.addLast(new Droplet(x + (random.nextDouble() - 0.5) * 12, y - 2,
                    vx, vy, 1.0 + random.nextDouble() * 1.4, 18 + random.nextInt(10), tick));
        }
    }

    private Chunk generate(long index) {
        Random random = new Random(mix(seed ^ (index * 0x9e3779b97f4a7c15L) ^ ((long) level << 48)));
        double start = index * (double) CHUNK_WIDTH;
        int waterWidth = 224 + random.nextInt(65);
        var water = List.of(new WaterView(index * 8, start + 330, waterWidth, groundY, 18 + random.nextInt(9)),
                new WaterView(index * 8 + 1, start + 1630, 190 + random.nextInt(61), groundY, 16 + random.nextInt(9)));
        var platforms = new ArrayList<Platform>();
        double platformStart = start + (level == 1 ? 980 : 800);
        Platform.Style support = switch (level) {
            case 1 -> Platform.Style.SERVER_BANK;
            case 2 -> Platform.Style.ROOFTOP;
            case 3 -> Platform.Style.PIPE;
            case 4 -> Platform.Style.FORTIFICATION;
            default -> Platform.Style.CATWALK;
        };
        int[] rises = {24, 48, 80, 48, 24};
        for (int i = 0; i < rises.length; i++) {
            double x = platformStart + i * 110;
            int width = i == 2 ? 150 : 110;
            if (!nearHeapStation(x, width) && !nearKernelCircuit(x, width) && !nearSingularityAnchor(x, width))
                platforms.add(new Platform(x, groundY - rises[i], width, 12,
                        i == 2 ? Platform.Style.CATWALK : support));
        }
        double capacitorX = start + 658 + random.nextInt(27);
        if (level == 2) for (int supportX : new int[]{700, 2680, 4680})
            if (Math.abs(capacitorX + 16 - supportX) < 125) capacitorX -= 150;
        if (level == 3) for (int stationX : new int[]{720, 2760, 4780})
            if (Math.abs(capacitorX + 16 - stationX) < 125) capacitorX -= 150;
        if (level == 4) for (int anchorX : new int[]{900, 3000, 5000})
            if (Math.abs(capacitorX + 16 - anchorX) < 125) capacitorX -= 150;
        var props = List.of(new PropView(index * 8 + 2, PropKind.CAPACITOR,
                        capacitorX, groundY - 42, 32, 42),
                new PropView(index * 8 + 3, PropKind.SUPPLY,
                        start + 1540 + random.nextInt(32), groundY - 34, 40, 34));
        return new Chunk(water, platforms, props);
    }

    /** Shared walk-up stairs must not silently bridge an authored powered floor section. */
    private boolean nearKernelCircuit(double x, int width) {
        if (level != 3) return false;
        for (int stationX : new int[]{720, 2760, 4780})
            if (x + width > stationX - 80 && x < stationX + 400) return true;
        return false;
    }

    private boolean nearHeapStation(double x, int width) {
        if (level != 1) return false;
        for (int stationX : new int[]{760, 2600, 4650})
            if (x < stationX + 120 && x + width > stationX - 120) return true;
        return false;
    }

    private boolean nearSingularityAnchor(double x, int width) {
        if (level != 4) return false;
        for (int anchorX : new int[]{900, 3000, 5000})
            if (x + width > anchorX - 80 && x < anchorX + 400) return true;
        return false;
    }

    private void rebuildGeometry() {
        var water = new ArrayList<WaterView>();
        var platforms = new ArrayList<Platform>();
        var props = new ArrayList<PropView>();
        chunks.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            Chunk chunk = entry.getValue();
            water.addAll(chunk.water);
            if (!bossArena) {
                platforms.addAll(chunk.platforms);
                for (int index = 0; index < chunk.props.size(); index++)
                    if ((chunk.brokenMask & (1 << index)) == 0) props.add(chunk.props.get(index));
            }
        });
        this.water = List.copyOf(water);
        this.platforms = List.copyOf(platforms);
        this.props = List.copyOf(props);
    }

    private void publish() {
        var rippleViews = new ArrayList<RippleView>(ripples.size());
        for (Ripple ripple : ripples) {
            long age = tick - ripple.born();
            rippleViews.add(new RippleView(ripple.x(), ripple.y(), (5 + age * 0.9) * ripple.strength(),
                    (1 - age / (float) RIPPLE_TICKS) * 0.7f, ripple.strength()));
        }
        var dropletViews = new ArrayList<DropletView>(droplets.size());
        for (Droplet droplet : droplets) {
            long age = tick - droplet.born();
            dropletViews.add(new DropletView(droplet.x() + droplet.vx() * age,
                    droplet.y() + droplet.vy() * age + 0.17 * age * age,
                    droplet.radius(), 1 - age / (float) droplet.lifetime()));
        }
        snapshot = new Snapshot(tick, water, platforms, props, rippleViews, dropletViews, bossArena);
    }

    private static boolean expired(Droplet droplet, long age) {
        return age >= droplet.lifetime() || (age > 2 && droplet.vy() * age + 0.17 * age * age >= 2);
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    private static void validateViewport(double cameraX, int viewWidth) {
        if (!Double.isFinite(cameraX) || cameraX < 0 || cameraX > 1e12 || viewWidth < 1 || viewWidth > 8192)
            throw new IllegalArgumentException("Invalid route viewport");
    }
}
