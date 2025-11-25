package com.bigphil.mergehell.model;

import java.awt.*;

public class Projectile {
    public double x, y;
    public double vx, vy; // 改为 vx, vy
    public String type;
    public boolean dead = false;
    public int width, height;
    public int damage;

    public Projectile(double x, double y, double speedX, double speedY, String type) {
        this.x = x;
        this.y = y;
        this.vx = speedX;
        this.vy = speedY;
        this.type = type;
        this.width = type.equals("sudo") ? 60 : 30;
        this.height = type.equals("sudo") ? 15 : 10;
        this.damage = type.equals("sudo") ? 100 : 25; // Sudo 伤害更高
    }

    public void update() {
        x += vx;
        y += vy;
    }

    public void draw(Graphics2D g) {
        g.setColor(type.equals("sudo") ? Color.decode("#f2c55c") : Color.decode("#6aab73"));
        g.setFont(new Font("JetBrains Mono", type.equals("sudo") ? Font.BOLD : Font.PLAIN, type.equals("sudo") ? 16 : 12));
        g.drawString(type.equals("sudo") ? "rm -rf /" : "git push", (int)x, (int)y + height);
    }

    public Rectangle getBounds() {
        return new Rectangle((int)x, (int)y, width, height);
    }
}