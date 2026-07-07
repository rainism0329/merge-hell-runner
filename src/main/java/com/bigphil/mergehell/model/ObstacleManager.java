package com.bigphil.mergehell.model;

import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class ObstacleManager {

    public static class Enemy {
        private double x;
        private double y;
        private double vx;
        private final int width, height;
        private final EntityType type;
        private final Color color;
        private final String symbol;
        private boolean dead = false;
        private final int damage;

        public Enemy(double x, double y, EntityType type) {
            this.x = x;
            this.y = y;
            this.type = type;
            this.width = type.width;
            this.height = type.height;
            this.vx = type.vx;
            this.color = type.color;
            this.symbol = type.symbol;
            this.damage = type.damage > 0 ? type.damage : 20;
        }

        public double getX() { return x; }
        public double getY() { return y; }
        public double getWidth() { return width; }
        public EntityType getType() { return type; }
        public Color getColor() { return color; }
        public int getDamage() { return damage; }
        public boolean isDead() { return dead; }
        public void setDead(boolean dead) { this.dead = dead; }

        public void update() {
            x -= vx;
            if (type == EntityType.LOCK) {
                y += Math.sin(System.currentTimeMillis() / 100.0) * 2;
            }
        }

        public void draw(Graphics2D g) {
            if (type == EntityType.FIREWALL || type == EntityType.TECHDEBT) {
                g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 200));
                g.fillRect((int) x, (int) y, width, height);
            }

            g.setColor(Color.WHITE);

            if (type == EntityType.CONFLICT || type == EntityType.TECHDEBT) {
                g.setFont(new Font("JetBrains Mono", Font.BOLD, 14));
                g.drawString(symbol, (int) x + 2, (int) y + 25);
            } else if (type == EntityType.FIREWALL) {
                g.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 40));
                g.drawString(symbol, (int) x + 10, (int) y + 70);
            } else {
                g.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 32));
                g.drawString(symbol, (int) x, (int) y + 35);
            }
        }

        public Rectangle getBounds() {
            return new Rectangle((int) x, (int) y, width, height);
        }
    }

    private final List<Enemy> enemies = new ArrayList<>();
    private final Random random = new Random();

    public void reset() {
        enemies.clear();
    }

    public void spawnEnemy(int x, int y, EntityType type) {
        enemies.add(new Enemy(x, y, type));
    }

    public void spawnRandom(int panelWidth, int groundY) {
        if (random.nextInt(100) < 2) {
            int r = random.nextInt(100);
            EntityType type;
            int y = groundY - 40;

            if (r < 5) {
                type = EntityType.POWERUP_SUDO;
                y = groundY - 150;
            } else if (r < 10) {
                type = EntityType.POWERUP_SHIELD;
                y = groundY - 150;
            } else if (r < 30) {
                type = EntityType.CONFLICT;
            } else if (r < 50) {
                type = EntityType.TECHDEBT;
                y = groundY - 80;
            } else if (r < 70) {
                type = EntityType.LOCK;
                y = groundY - 100;
            } else if (r < 80) {
                type = EntityType.FIREWALL;
                y = 0;
            } else if (r < 90) {
                type = EntityType.CRASH;
            } else {
                type = EntityType.BUG;
            }

            enemies.add(new Enemy(panelWidth, y, type));
        }
    }

    public void update() {
        Iterator<Enemy> it = enemies.iterator();
        while (it.hasNext()) {
            Enemy e = it.next();
            e.update();
            if (isOffScreen(e) || e.isDead()) it.remove();
        }
    }

    private boolean isOffScreen(Enemy e) {
        return e.getX() + e.getWidth() < -100;
    }

    public void draw(Graphics2D g) {
        for (Enemy e : enemies) e.draw(g);
    }

    public List<Enemy> getEnemies() {
        return enemies;
    }
}
