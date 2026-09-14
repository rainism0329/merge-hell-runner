package com.bigphil.mergehell.model;

import com.bigphil.mergehell.combat.ProjectileSpec;
import com.bigphil.mergehell.combat.WeaponId;
import com.bigphil.mergehell.combat.CombatEvent;

import java.awt.*;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class Projectile {
    private double x, y;
    private double previousX, previousY;
    private double vx, vy;
    private double verticalAcceleration;
    private final ProjectileType type;
    private boolean dead = false;
    private final int width, height;
    private final ProjectileSpec spec;
    private final double originalSpeed;
    private int remainingPierces;
    private int remainingRicochets;
    private int remainingInterceptions;
    private int ticksRemaining;
    private boolean impactConsumed;
    private long rootEventId;
    private boolean criticalComboConsumed;
    private CombatEvent.DamageKind damageKind = CombatEvent.DamageKind.DIRECT;
    private final Set<ObstacleManager.Enemy> hitEnemies =
            Collections.newSetFromMap(new IdentityHashMap<>());

    public Projectile(double x, double y, double speedX, double speedY, ProjectileType type) {
        this(x, y, legacySpec(speedX, speedY, type), type);
    }

    public Projectile(double x, double y, ProjectileSpec spec) {
        this(x, y, spec, projectileTypeFor(spec));
    }

    /** Gravity is opt-in for authored enemy lobbers; every existing projectile stays linear. */
    public static Projectile ballistic(double x, double y, double speedX, double speedY,
                                       ProjectileType type, double gravity) {
        if (!type.isHostile() || !Double.isFinite(gravity) || gravity < 0 || gravity > 1)
            throw new IllegalArgumentException("Ballistic shots require hostile type and bounded gravity");
        Projectile projectile = new Projectile(x, y, speedX, speedY, type);
        projectile.verticalAcceleration = gravity;
        return projectile;
    }

    /** Drone emission uses its own small body centered on the rendered muzzle. */
    public static Projectile drone(double muzzleX, double muzzleY, ProjectileSpec spec) {
        Projectile projectile = new Projectile(muzzleX - ProjectileType.DRONE.width / 2.0,
                muzzleY - ProjectileType.DRONE.height / 2.0, spec, ProjectileType.DRONE);
        projectile.damageKind = CombatEvent.DamageKind.DRONE;
        return projectile;
    }

    private Projectile(double x, double y, ProjectileSpec spec, ProjectileType type) {
        this.x = x;
        this.y = y;
        previousX = x; previousY = y;
        this.spec = Objects.requireNonNull(spec, "spec");
        this.vx = spec.velocityX();
        this.vy = spec.velocityY();
        this.type = Objects.requireNonNull(type, "type");
        this.width = type.width;
        this.height = type.height;
        this.originalSpeed = Math.hypot(vx, vy);
        this.remainingPierces = spec.remainingPierces();
        this.remainingRicochets = spec.remainingRicochets();
        this.remainingInterceptions = spec.effects().interceptions();
        this.ticksRemaining = spec.effects().lifetimeTicks();
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public double getVx() { return vx; }
    public double getVy() { return vy; }
    public double getVerticalAcceleration() { return verticalAcceleration; }
    public ProjectileType getType() { return type; }
    public ProjectileSpec getSpec() { return spec; }
    public WeaponId getWeapon() { return spec.weapon(); }
    public int getDamage() { return spec.damage(); }
    public boolean isCritical() { return spec.critical(); }
    public int getRemainingPierces() { return remainingPierces; }
    public double getKnockback() { return spec.knockback(); }
    public int getRemainingRicochets() { return remainingRicochets; }
    public boolean isDead() { return dead; }
    public int getRemainingInterceptions() { return remainingInterceptions; }
    public long getRootEventId() { return rootEventId; }
    public CombatEvent.DamageKind getDamageKind() { return damageKind; }
    public boolean isDrone() { return type == ProjectileType.DRONE; }
    public void markReflected() { damageKind = CombatEvent.DamageKind.REFLECTED; }
    public boolean consumeCriticalCombo() {
        if (!isCritical() || criticalComboConsumed) return false;
        criticalComboConsumed = true;
        return true;
    }
    public void assignRootEventId(long value) {
        if (value <= 0) throw new IllegalArgumentException("Event ID must be positive");
        if (rootEventId == 0) rootEventId = value;
    }
    public boolean consumeImpact() {
        if (impactConsumed) return false;
        impactConsumed = true;
        return true;
    }
    public void interceptNormalBullet() {
        if (remainingInterceptions > 0) remainingInterceptions--;
        if (remainingInterceptions == 0) dead = true;
    }

    public void setDead(boolean dead) {
        this.dead = dead;
    }

    public void update() {
        if (dead) return;
        previousX = x; previousY = y;
        x += vx;
        y += vy;
        vy += verticalAcceleration;
        if (--ticksRemaining <= 0) dead = true;
    }

    public boolean canHit(ObstacleManager.Enemy enemy) {
        return !hitEnemies.contains(enemy);
    }

    public void recordHit(ObstacleManager.Enemy enemy) {
        if (!hitEnemies.add(enemy)) return;
        if (remainingPierces > 0) {
            remainingPierces--;
        } else {
            dead = true;
        }
    }

    public boolean ricochetToward(List<ObstacleManager.Enemy> enemies) {
        if (remainingRicochets <= 0) return false;

        ObstacleManager.Enemy nearest = null;
        double nearestDistanceSquared = Double.POSITIVE_INFINITY;
        for (ObstacleManager.Enemy enemy : enemies) {
            if (enemy.isDead() || !enemy.getType().isHostile() || hitEnemies.contains(enemy)) continue;
            Rectangle bounds = enemy.getBounds();
            double dx = bounds.getCenterX() - x;
            double dy = bounds.getCenterY() - y;
            double distanceSquared = dx * dx + dy * dy;
            if (distanceSquared < nearestDistanceSquared) {
                nearest = enemy;
                nearestDistanceSquared = distanceSquared;
            }
        }
        if (nearest == null) return false;

        Rectangle bounds = nearest.getBounds();
        double dx = bounds.getCenterX() - x;
        double dy = bounds.getCenterY() - y;
        double distance = Math.hypot(dx, dy);
        if (distance > 0) {
            vx = dx / distance * originalSpeed;
            vy = dy / distance * originalSpeed;
        }
        remainingRicochets--;
        dead = false;
        // A redirected shot starts a new path next tick; never reuse the incoming sweep.
        previousX = x; previousY = y;
        return true;
    }

    /** Friendly-shot collision over the most recent fixed step, retaining the existing hitbox size. */
    public boolean hits(Rectangle target) { return Double.isFinite(hitFraction(target)); }

    /** Entry order along the step; expanding the target by this rectangle gives a swept AABB test. */
    public double hitFraction(Rectangle target) {
        if (target.width <= 0 || target.height <= 0) return Double.POSITIVE_INFINITY;
        double entry = 0, exit = 1, dx = x - previousX, dy = y - previousY;
        double left = target.getMinX() - width + 1e-9, right = target.getMaxX() - 1e-9;
        double top = target.getMinY() - height + 1e-9, bottom = target.getMaxY() - 1e-9;
        if (Math.abs(dx) < 1e-12) {
            if (previousX < left || previousX > right) return Double.POSITIVE_INFINITY;
        } else {
            double a = (left - previousX) / dx, b = (right - previousX) / dx;
            entry = Math.max(entry, Math.min(a, b)); exit = Math.min(exit, Math.max(a, b));
        }
        if (Math.abs(dy) < 1e-12) {
            if (previousY < top || previousY > bottom) return Double.POSITIVE_INFINITY;
        } else {
            double a = (top - previousY) / dy, b = (bottom - previousY) / dy;
            entry = Math.max(entry, Math.min(a, b)); exit = Math.min(exit, Math.max(a, b));
        }
        return entry <= exit ? entry : Double.POSITIVE_INFINITY;
    }

    public void draw(Graphics2D g) {
        Graphics2D local = (Graphics2D) g.create();
        try {
            local.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            local.translate(x + width / 2.0, y + height / 2.0);
            if (type.isHostile()) {
                drawHostile(local);
                return;
            }
            local.rotate(Math.atan2(vy, vx));
            if (isDrone()) {
                local.setColor(new Color(112, 243, 255, 60));
                local.fillOval(-9, -5, 18, 10);
                local.setColor(new Color(100, 232, 255));
                local.fillPolygon(new int[]{-6, 3, 7, 3, -6, -3}, new int[]{-3, -3, 0, 3, 3, 0}, 6);
                local.setColor(new Color(240, 255, 255));
                local.fillRoundRect(-3, -1, 8, 2, 2, 2);
                return;
            }
            Color color = switch (spec.weapon()) {
                case COMMIT_CANNON -> new Color(99, 238, 207);
                case FORCE_PUSH -> new Color(255, 207, 103);
                case RAPID_CI -> new Color(124, 185, 255);
                case GARBAGE_COLLECTOR -> new Color(255, 150, 77);
                case FIREWALL -> new Color(255, 103, 48);
                case REFACTOR_BEAM -> new Color(214, 168, 255);
            };
            local.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 55));
            local.fillOval(-width / 2 - 3, -height / 2 - 4, width + 6, height + 8);
            local.setColor(color);
            switch (spec.weapon()) {
                case COMMIT_CANNON -> {
                    local.fillPolygon(new int[]{-width / 2, width / 2, width / 2 - 7, -width / 2},
                            new int[]{-3, 0, 4, 2}, 4);
                    local.setColor(new Color(237, 255, 250));
                    local.fillRect(-width / 4, -1, width / 2, 2);
                }
                case FORCE_PUSH -> {
                    local.setStroke(new BasicStroke(3));
                    local.drawArc(-width / 3, -height / 2, width / 2, height, -70, 140);
                    local.fillRoundRect(0, -3, width / 3, 6, 5, 5);
                    local.setColor(Color.WHITE);
                    local.fillOval(width / 4, -2, 4, 4);
                }
                case RAPID_CI -> {
                    local.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    local.drawLine(-width / 2, 0, width / 2, 0);
                    local.setColor(new Color(237, 248, 255));
                    local.setStroke(new BasicStroke(1));
                    local.drawLine(0, 0, width / 2, 0);
                }
                case GARBAGE_COLLECTOR -> {
                    local.setColor(new Color(61, 40, 33));
                    local.fillRoundRect(-width / 3, -height / 2, width * 2 / 3, height, 6, 6);
                    local.setColor(color);
                    local.setStroke(new BasicStroke(2));
                    local.drawRoundRect(-width / 3, -height / 2, width * 2 / 3, height, 6, 6);
                    local.fillRect(0, -height / 2 + 2, 4, Math.max(2, height - 4));
                    local.setColor(new Color(255, 237, 173));
                    local.fillOval(width / 3 - 4, -2, 4, 4);
                }
                case FIREWALL -> {
                    local.fillPolygon(new int[]{-width / 2, -5, -width / 3, width / 2, -width / 3, -6},
                            new int[]{-height / 2, -2, 0, 0, height / 2, 2}, 6);
                    local.setColor(new Color(255, 220, 97));
                    local.fillOval(-4, -3, width / 2, 6);
                    if (remainingInterceptions > 0) {
                        local.setColor(new Color(139, 239, 238));
                        local.setStroke(new BasicStroke(1.5f));
                        local.drawArc(-width / 2, -height / 2 - 2, width, height + 4, -70, 140);
                    }
                }
                case REFACTOR_BEAM -> {
                    local.setStroke(new BasicStroke(5, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    local.drawLine(-width / 2, 0, width / 2, 0);
                    local.setColor(new Color(252, 242, 255));
                    local.setStroke(new BasicStroke(2));
                    local.drawLine(-width / 2, 0, width / 2, 0);
                    local.setStroke(new BasicStroke(1));
                    local.drawLine(width / 3, -4, width / 2, 0);
                    local.drawLine(width / 3, 4, width / 2, 0);
                }
            }
            if (spec.critical()) {
                local.setColor(new Color(255, 255, 240));
                local.setStroke(new BasicStroke(1.5f));
                local.drawLine(0, -height / 2 - 3, 0, height / 2 + 3);
            }
        } finally {
            local.dispose();
        }
    }

    private void drawHostile(Graphics2D local) {
        int radius = Math.min(width, height) / 2;
        if (type.undestroyable) {
            // A double diamond is distinct from ordinary round bullets even without color.
            local.setColor(new Color(255, 42, 78, 65));
            local.fillOval(-radius - 3, -radius - 3, radius * 2 + 6, radius * 2 + 6);
            local.setColor(new Color(255, 60, 88));
            local.fillPolygon(new int[]{0, radius, 0, -radius}, new int[]{-radius, 0, radius, 0}, 4);
            local.setColor(new Color(255, 222, 225));
            local.setStroke(new BasicStroke(1.5f));
            local.drawPolygon(new int[]{0, radius - 3, 0, -radius + 3},
                    new int[]{-radius + 3, 0, radius - 3, 0}, 4);
        } else {
            local.setColor(new Color(255, 76, 96, 60));
            local.fillOval(-radius - 2, -radius - 2, radius * 2 + 4, radius * 2 + 4);
            local.setColor(new Color(255, 84, 104));
            local.fillOval(-radius + 1, -radius + 1, radius * 2 - 2, radius * 2 - 2);
            local.setColor(new Color(255, 229, 216));
            local.fillOval(-2, -2, 4, 4);
        }
    }

    public Rectangle getBounds() {
        return new Rectangle((int) x, (int) y, width, height);
    }

    private static ProjectileSpec legacySpec(double speedX, double speedY, ProjectileType type) {
        Objects.requireNonNull(type, "type");
        WeaponId weapon = type == ProjectileType.SUDO
                ? WeaponId.FORCE_PUSH : WeaponId.COMMIT_CANNON;
        int pierces = type == ProjectileType.SUDO ? Integer.MAX_VALUE : 0;
        return new ProjectileSpec(weapon, type.damage, speedX, speedY,
                type == ProjectileType.CRITICAL, pierces, 0, 0);
    }

    private static ProjectileType projectileTypeFor(ProjectileSpec spec) {
        Objects.requireNonNull(spec, "spec");
        return spec.weapon() == WeaponId.FORCE_PUSH
                ? ProjectileType.SUDO : ProjectileType.COMMIT;
    }
}
