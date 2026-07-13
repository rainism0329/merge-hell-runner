package com.bigphil.mergehell.model;

import com.bigphil.mergehell.combat.ProjectileSpec;
import com.bigphil.mergehell.combat.WeaponId;

import java.awt.*;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class Projectile {
    private double x, y;
    private double vx, vy;
    private final ProjectileType type;
    private boolean dead = false;
    private final int width, height;
    private final ProjectileSpec spec;
    private final double originalSpeed;
    private int remainingPierces;
    private int remainingRicochets;
    private final Set<ObstacleManager.Enemy> hitEnemies =
            Collections.newSetFromMap(new IdentityHashMap<>());

    public Projectile(double x, double y, double speedX, double speedY, ProjectileType type) {
        this(x, y, legacySpec(speedX, speedY, type), type);
    }

    public Projectile(double x, double y, ProjectileSpec spec) {
        this(x, y, spec, projectileTypeFor(spec));
    }

    private Projectile(double x, double y, ProjectileSpec spec, ProjectileType type) {
        this.x = x;
        this.y = y;
        this.spec = Objects.requireNonNull(spec, "spec");
        this.vx = spec.velocityX();
        this.vy = spec.velocityY();
        this.type = Objects.requireNonNull(type, "type");
        this.width = type.width;
        this.height = type.height;
        this.originalSpeed = Math.hypot(vx, vy);
        this.remainingPierces = spec.remainingPierces();
        this.remainingRicochets = spec.remainingRicochets();
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public double getVx() { return vx; }
    public double getVy() { return vy; }
    public ProjectileType getType() { return type; }
    public ProjectileSpec getSpec() { return spec; }
    public WeaponId getWeapon() { return spec.weapon(); }
    public int getDamage() { return spec.damage(); }
    public boolean isCritical() { return spec.critical(); }
    public int getRemainingPierces() { return remainingPierces; }
    public double getKnockback() { return spec.knockback(); }
    public int getRemainingRicochets() { return remainingRicochets; }
    public boolean isDead() { return dead; }

    public void setDead(boolean dead) {
        this.dead = dead;
    }

    public void update() {
        x += vx;
        y += vy;
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
        return true;
    }

    public void draw(Graphics2D g) {
        g.setColor(type.color);
        g.setFont(new Font("JetBrains Mono", type == ProjectileType.SUDO ? Font.BOLD : Font.PLAIN, type == ProjectileType.SUDO ? 16 : 12));
        g.drawString(type.label, (int) x, (int) y + height);
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
