package com.bigphil.mergehell.combat;

import com.bigphil.mergehell.model.Projectile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Simulation-owned companions. Inputs and published poses contain no live actor references. */
public final class DroneController {
    public static final double MUZZLE_LENGTH = 18;
    private static final double BODY_MARGIN = 20;
    private static final double MIN_FORMATION_SPACING = 38;
    private static final double SHOT_SPEED = 14;
    private static final int SHOT_LIFETIME = 60;

    public record Profile(int count, int fireIntervalTicks, double damageMultiplier,
                          int pierces, double acquisitionRange) {
        public int damage(int weaponDamage) {
            if (weaponDamage <= 0) throw new IllegalArgumentException("Weapon damage must be positive");
            // Authored multipliers are whole percentages; floating ceil would turn 100 * .55 into 56.
            long percent = Math.round(damageMultiplier * 100);
            return Math.max(1, (int) ((weaponDamage * percent + 99) / 100));
        }
    }

    public static Profile profile(int rank) {
        requireRank(rank);
        return new Profile(Math.min(rank, 2), rank == 3 ? 48 : 72,
                rank == 3 ? 0.55 : 0.40, rank == 3 ? 1 : 0, 560);
    }

    public record Bounds(double minX, double minY, double maxX, double maxY) {
        public Bounds {
            if (!finite(minX, minY, maxX, maxY) || minX >= maxX || minY >= maxY)
                throw new IllegalArgumentException("Invalid visible bounds");
        }
        private boolean contains(double x, double y) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY;
        }
    }

    /** Stable positive ID, world center and current health. Protected actors are not attackable. */
    public record Target(long id, double x, double y, int hp, boolean marked, boolean attackable) {
        public Target {
            if (id <= 0 || !finite(x, y) || hp < 0) throw new IllegalArgumentException("Invalid target");
        }
    }

    public record Input(double playerCenterX, double playerCenterY, int facing, int rank,
                        WeaponId weapon, int weaponDamage, Bounds visible,
                        List<Target> targets, int projectileCapacity, boolean active) {
        public Input {
            if (!finite(playerCenterX, playerCenterY) || (facing != -1 && facing != 1)
                    || weaponDamage <= 0 || projectileCapacity < 0)
                throw new IllegalArgumentException("Invalid drone input");
            requireRank(rank);
            Objects.requireNonNull(weapon, "weapon");
            Objects.requireNonNull(visible, "visible");
            targets = List.copyOf(targets);
        }
    }

    /** Body center is also the turret pivot; recoil never changes the authoritative muzzle. */
    public record Pose(int slot, int rank, double x, double y, double aimRadians,
                       double muzzleX, double muzzleY, double recoil, double thrust,
                       long targetId, double targetX, double targetY) { }

    public record Snapshot(long tick, List<Pose> drones) {
        public Snapshot { drones = List.copyOf(drones); }
    }

    private static final class Drone {
        final int slot;
        double x, y, aim, recoil, thrust;
        int cooldown;
        long targetId;
        Drone(int slot, double x, double y, int facing, int cooldown) {
            this.slot = slot; this.x = x; this.y = y;
            aim = facing > 0 ? 0 : Math.PI; this.cooldown = cooldown;
        }
    }

    private final List<Drone> drones = new ArrayList<>(2);
    private Map<Long, Target> previousTargets = Map.of();
    private Snapshot snapshot = new Snapshot(0, List.of());
    private long tick;
    private long lastShotTick = -1_000;
    private int previousRank;
    private long rejectedProjectiles;

    public Snapshot snapshot() { return snapshot; }
    public long rejectedProjectiles() { return rejectedProjectiles; }

    /** Entrance/restore/respawn reset: rank is reapplied from the existing run build next update. */
    public void reset() {
        drones.clear(); previousTargets = Map.of(); tick = 0; previousRank = 0;
        rejectedProjectiles = 0;
        lastShotTick = -1_000; snapshot = new Snapshot(0, List.of());
    }

    /** Advance exactly one gameplay tick. Paused/upgrade inputs neither move nor fire. */
    public List<Projectile> update(Input input) {
        Objects.requireNonNull(input, "input");
        if (!input.active()) return List.of();
        tick++;
        Profile profile = profile(input.rank());
        if (input.rank() != previousRank && previousRank > 0) {
            int oldInterval = profile(previousRank).fireIntervalTicks();
            for (Drone drone : drones)
                drone.cooldown = (int) Math.ceil(drone.cooldown * profile.fireIntervalTicks() / (double) oldInterval);
        }
        while (drones.size() > profile.count()) drones.remove(drones.size() - 1);
        while (drones.size() < profile.count()) {
            int slot = drones.size();
            drones.add(new Drone(slot, desiredX(input, slot), desiredY(input, slot), input.facing(),
                    slot == 0 ? 0 : (drones.get(0).cooldown + profile.fireIntervalTicks() / 2) % profile.fireIntervalTicks()));
        }
        previousRank = input.rank();
        List<Projectile> shots = new ArrayList<>(2);
        List<Pose> poses = new ArrayList<>(2);
        List<Target> assigned = new ArrayList<>(2);
        Map<Long, Target> currentTargets = new HashMap<>();
        for (Target target : input.targets()) {
            if (currentTargets.put(target.id(), target) != null)
                throw new IllegalArgumentException("Duplicate target ID");
        }
        for (Drone drone : drones) follow(drone, input);
        separateAtEdges(input.visible());
        for (Drone drone : drones) {
            if (drone.cooldown > 0) drone.cooldown--;
            drone.recoil = Math.max(0, drone.recoil - 0.20);
            Target target = selectTarget(drone, input, profile, assigned);
            drone.targetId = target == null ? 0 : target.id();
            double aimX = drone.x + input.facing() * 100, aimY = drone.y;
            if (target != null) {
                assigned.add(target);
                double[] intercept = intercept(drone, target, previousTargets.get(target.id()));
                aimX = intercept[0]; aimY = intercept[1];
            }
            drone.aim = Math.atan2(aimY - drone.y, aimX - drone.x);
            double muzzleX = drone.x + Math.cos(drone.aim) * MUZZLE_LENGTH;
            double muzzleY = drone.y + Math.sin(drone.aim) * MUZZLE_LENGTH;
            // Enforce spacing even when both cooldowns have expired while no target was visible.
            int spacing = profile.count() > 1 ? profile.fireIntervalTicks() / 2 : profile.fireIntervalTicks();
            if (target != null && drone.cooldown == 0 && tick - lastShotTick >= spacing) {
                if (shots.size() < input.projectileCapacity()) {
                    ProjectileEffects effects = new ProjectileEffects(0, 0, 0, 0, 0, 0, 0, 0, SHOT_LIFETIME);
                    ProjectileSpec spec = new ProjectileSpec(input.weapon(), profile.damage(input.weaponDamage()),
                            Math.cos(drone.aim) * SHOT_SPEED, Math.sin(drone.aim) * SHOT_SPEED,
                            false, profile.pierces(), 0, 0, effects);
                    shots.add(Projectile.drone(muzzleX, muzzleY, spec));
                    drone.cooldown = profile.fireIntervalTicks();
                    drone.recoil = 1; lastShotTick = tick;
                } else if (rejectedProjectiles < Long.MAX_VALUE) {
                    rejectedProjectiles++;
                }
            }
            poses.add(new Pose(drone.slot, input.rank(), drone.x, drone.y, drone.aim,
                    muzzleX, muzzleY, drone.recoil, drone.thrust, drone.targetId,
                    target == null ? drone.x : target.x(), target == null ? drone.y : target.y()));
        }
        previousTargets = currentTargets;
        snapshot = new Snapshot(tick, poses);
        return List.copyOf(shots);
    }

    private Target selectTarget(Drone drone, Input input, Profile profile, List<Target> assigned) {
        List<Target> available = input.targets().stream()
                .filter(target -> target.attackable() && target.hp() > 0 && input.visible().contains(target.x(), target.y()))
                .filter(target -> Math.hypot(target.x() - drone.x, target.y() - drone.y) <= profile.acquisitionRange())
                .toList();
        List<Target> separate = available.stream()
                .filter(target -> assigned.stream().noneMatch(other -> other.id() == target.id())).toList();
        if (!separate.isEmpty()) available = separate;
        else available = available.stream().filter(target -> target.hp() > profile.damage(input.weaponDamage())).toList();
        return available.stream().min(Comparator.<Target, Boolean>comparing(target -> !target.marked())
                .thenComparing(target -> target.id() != drone.targetId)
                .thenComparingDouble(target -> Math.hypot(target.x() - drone.x, target.y() - drone.y))
                .thenComparingLong(Target::id)).orElse(null);
    }

    private void follow(Drone drone, Input input) {
        double dx = desiredX(input, drone.slot) - drone.x, dy = desiredY(input, drone.slot) - drone.y;
        double length = Math.hypot(dx, dy);
        double factor = length == 0 ? 0 : Math.min(0.22, 12 / length);
        drone.x = bound(drone.x + dx * factor, input.visible().minX(), input.visible().maxX());
        drone.y = bound(drone.y + dy * factor, input.visible().minY(), input.visible().maxY());
        drone.thrust = Math.min(1, 0.34 + length * factor / 14);
    }

    private double desiredX(Input input, int slot) {
        // Companions retain their world-side stations; turning aims the turrets, not a body swap.
        return bound(input.playerCenterX() + (slot == 0 ? -38 : 34),
                input.visible().minX(), input.visible().maxX());
    }

    /** Preserve room for both hulls when the camera edge compresses their follow stations. */
    private void separateAtEdges(Bounds visible) {
        if (drones.size() < 2) return;
        Drone left = drones.get(0), right = drones.get(1);
        double low = bound(visible.minX(), visible.minX(), visible.maxX());
        double high = bound(visible.maxX(), visible.minX(), visible.maxX());
        double gap = Math.min(MIN_FORMATION_SPACING, high - low);
        if (right.x - left.x >= gap) return;
        double center = Math.max(low + gap / 2, Math.min(high - gap / 2, (left.x + right.x) / 2));
        left.x = center - gap / 2;
        right.x = center + gap / 2;
    }

    private double desiredY(Input input, int slot) {
        return bound(input.playerCenterY() - (slot == 0 ? 48 : 70) + Math.sin(tick * 0.09 + slot * Math.PI) * 3,
                input.visible().minY(), input.visible().maxY());
    }

    private static double bound(double value, double low, double high) {
        double margin = Math.min(BODY_MARGIN, (high - low) / 2);
        return Math.max(low + margin, Math.min(high - margin, value));
    }

    /** Fixed launch prediction only. Teleports and excessive lead revert to a bounded aim point. */
    private static double[] intercept(Drone drone, Target target, Target previous) {
        double vx = previous == null ? 0 : target.x() - previous.x();
        double vy = previous == null ? 0 : target.y() - previous.y();
        if (Math.hypot(vx, vy) > 18) { vx = 0; vy = 0; }
        double dx = target.x() - drone.x, dy = target.y() - drone.y;
        double a = vx * vx + vy * vy - SHOT_SPEED * SHOT_SPEED;
        double b = 2 * (dx * vx + dy * vy), c = dx * dx + dy * dy;
        double time = Math.hypot(dx, dy) / SHOT_SPEED;
        double discriminant = b * b - 4 * a * c;
        if (Math.abs(a) > 1e-9 && discriminant >= 0) {
            double one = (-b - Math.sqrt(discriminant)) / (2 * a);
            double two = (-b + Math.sqrt(discriminant)) / (2 * a);
            if (one > 0 || two > 0) time = Math.min(one > 0 ? one : Double.POSITIVE_INFINITY,
                    two > 0 ? two : Double.POSITIVE_INFINITY);
        }
        time = Math.min(time, 40);
        double lead = Math.hypot(vx, vy) * time;
        if (lead > 160) time *= 160 / lead;
        return new double[]{target.x() + vx * time, target.y() + vy * time};
    }

    private static void requireRank(int rank) {
        if (rank < 0 || rank > 3) throw new IllegalArgumentException("Drone rank must be 0..3");
    }

    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }
}
