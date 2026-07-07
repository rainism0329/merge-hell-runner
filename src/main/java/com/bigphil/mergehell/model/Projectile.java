package com.bigphil.mergehell.model;

import java.awt.*;

public class Projectile {
    private double x, y;
    private double vx, vy;
    private final ProjectileType type;
    private boolean dead = false;
    private final int width, height;
    private final int damage;

    public Projectile(double x, double y, double speedX, double speedY, ProjectileType type) {
        this.x = x;
        this.y = y;
        this.vx = speedX;
        this.vy = speedY;
        this.type = type;
        this.width = type.width;
        this.height = type.height;
        this.damage = type.damage;
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public ProjectileType getType() { return type; }
    public int getDamage() { return damage; }
    public boolean isDead() { return dead; }

    public void setDead(boolean dead) {
        this.dead = dead;
    }

    public void update() {
        x += vx;
        y += vy;
    }

    public void draw(Graphics2D g) {
        g.setColor(type.color);
        g.setFont(new Font("JetBrains Mono", type == ProjectileType.SUDO ? Font.BOLD : Font.PLAIN, type == ProjectileType.SUDO ? 16 : 12));
        g.drawString(type.label, (int) x, (int) y + height);
    }

    public Rectangle getBounds() {
        return new Rectangle((int) x, (int) y, width, height);
    }
}
