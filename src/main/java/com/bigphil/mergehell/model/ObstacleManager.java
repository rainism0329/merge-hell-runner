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
        private final double vx;
        private final int width, height;
        private final EntityType type;
        private final Color color;
        private final String symbol;
        private boolean dead = false;
        private final int damage;
        private int hp;
        private double spawnTime;

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
            this.hp = type.maxHp;
            this.spawnTime = System.currentTimeMillis() / 1000.0;
        }

        public double getX() { return x; }
        public double getY() { return y; }
        public double getWidth() { return width; }
        public EntityType getType() { return type; }
        public Color getColor() { return color; }
        public int getDamage() { return damage; }
        public int getHp() { return hp; }
        public int getMaxHp() { return type.maxHp; }
        public boolean isDead() { return dead; }
        public void setDead(boolean dead) { this.dead = dead; }

        public void takeDamage(int dmg) {
            hp -= dmg;
            if (hp <= 0) dead = true;
        }

        public void update(double difficultySpeed) {
            double t = System.currentTimeMillis() / 1000.0;
            double speed = vx * difficultySpeed;
            x -= speed;

            switch (type) {
                case LOCK -> y += Math.sin(t * 2.5 + spawnTime) * 2.5;
                case CRASH -> y += Math.sin(t * 8.0 + spawnTime) * 6;
                case BUG -> y += Math.cos(t * 4.0 + spawnTime) * 1.5;
                case CONFLICT -> { /* steady advance */ }
                case TECHDEBT -> { /* slow and heavy, no wobble */ }
                case FIREWALL -> { /* straight line, blocks path */ }
                case POWERUP_SUDO, POWERUP_SHIELD, HEALTH -> y += Math.sin(t * 1.5 + spawnTime) * 1.5;
            }
        }

        public void draw(Graphics2D g) {
            if (type == EntityType.FIREWALL || type == EntityType.TECHDEBT) {
                g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 200));
                g.fillRect((int) x, (int) y, width, height);
            }

            // HP bar for multi-hit enemies
            if (type.maxHp > 1 && !dead) {
                g.setColor(Color.DARK_GRAY);
                g.fillRect((int) x, (int) y - 10, width, 5);
                g.setColor(color);
                double hpRatio = (double) hp / type.maxHp;
                g.fillRect((int) x, (int) y - 10, (int) (width * hpRatio), 5);
            }

            g.setColor(Color.WHITE);

            if (type == EntityType.CONFLICT || type == EntityType.TECHDEBT) {
                g.setFont(new Font("JetBrains Mono", Font.BOLD, 14));
                g.drawString(symbol, (int) x + 2, (int) y + 25);
            } else if (type == EntityType.FIREWALL) {
                g.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 40));
                g.drawString(symbol, (int) x + 10, (int) y + 70);
            } else if (type == EntityType.HEALTH) {
                g.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 28));
                g.drawString(symbol, (int) x + 2, (int) y + 28);
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

    public void spawnRandom(int panelWidth, int groundY, double difficulty) {
        double spawnChance = 2.0 + difficulty * 1.5;
        if (random.nextInt(100) < spawnChance) {
            int r = random.nextInt(100);
            EntityType type;
            int y = groundY - 40;

            if (r < 4) {
                type = EntityType.POWERUP_SUDO;
                y = groundY - 150 - random.nextInt(80);
            } else if (r < 8) {
                type = EntityType.POWERUP_SHIELD;
                y = groundY - 150 - random.nextInt(80);
            } else if (r < 13) {
                type = EntityType.HEALTH;
                y = groundY - 150 - random.nextInt(80);
            } else if (r < 33) {
                type = EntityType.CONFLICT;
            } else if (r < 48) {
                type = EntityType.TECHDEBT;
                y = groundY - 80;
            } else if (r < 63) {
                type = EntityType.LOCK;
                y = groundY - 60 - random.nextInt(80);
            } else if (r < 75) {
                type = EntityType.FIREWALL;
                y = 0;
            } else if (r < 90) {
                type = EntityType.CRASH;
            } else {
                type = EntityType.BUG;
            }

            enemies.add(new Enemy(panelWidth + random.nextInt(200), y, type));
        }
    }

    public void update() {
        Iterator<Enemy> it = enemies.iterator();
        while (it.hasNext()) {
            Enemy e = it.next();
            if (isOffScreen(e) || e.isDead()) {
                it.remove();
            }
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
