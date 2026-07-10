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
        private int shootTimer;
        private int moveDir = 1; // 1 = right-to-left, -1 = left-to-right
        private int chargeTimer = 0;
        private double chargeVx = 0;

        public Enemy(double x, double y, EntityType type) {
            this(x, y, type, 1);
        }

        public Enemy(double x, double y, EntityType type, int moveDir) {
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
            this.shootTimer = 40 + random.nextInt(80);
            this.moveDir = moveDir;
            this.chargeTimer = 120 + random.nextInt(180);
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

        private static final Random random = new Random();

        public Projectile maybeShoot(double playerY) {
            if (!canShoot()) return null;
            if (--shootTimer > 0) return null;
            shootTimer = 60 + random.nextInt(100);
            double bulletX = x;
            double bulletY = y + height / 2.0;
            double dy = (playerY - bulletY) * 0.03;
            double bulletVx = -6 * moveDir;
            ProjectileType bulletType = random.nextInt(5) == 0 ? ProjectileType.CRITICAL : ProjectileType.ENEMY;
            return new Projectile(bulletX, bulletY, bulletVx, dy, bulletType);
        }

        private boolean canShoot() {
            return type == EntityType.CONFLICT || type == EntityType.LOCK
                    || type == EntityType.CRASH || type == EntityType.TECHDEBT
                    || type == EntityType.BUG || type == EntityType.FIREWALL;
        }

        public void update(double difficultySpeed, double playerX) {
            double t = System.currentTimeMillis() / 1000.0;

            // Charge attack: telegraph while advancing normally, then commit to a short dash.
            // The previous branching never entered the actual dash path once chargeVx was set.
            if (chargeVx != 0) {
                x += chargeVx;
                if (--chargeTimer <= 0) {
                    chargeVx = 0;
                    chargeTimer = 150 + random.nextInt(200);
                }
            } else {
                double speed = vx * difficultySpeed;
                x -= speed * moveDir;
                if (type.isHostile() && type != EntityType.FIREWALL && type != EntityType.TECHDEBT
                        && --chargeTimer <= 0) {
                    if (Math.abs(x - playerX) < 350) {
                        chargeVx = (playerX > x ? 1 : -1) * vx * difficultySpeed * 3.5;
                        chargeTimer = 15;
                    } else {
                        chargeTimer = 150 + random.nextInt(200);
                    }
                }
            }

            switch (type) {
                case LOCK -> y += Math.sin(t * 2.5 + spawnTime) * 2.5;
                case CRASH -> y += Math.sin(t * 8.0 + spawnTime) * 6;
                case BUG -> y += Math.cos(t * 4.0 + spawnTime) * 1.5;
                case CONFLICT -> { /* steady advance */ }
                case TECHDEBT -> { /* slow and heavy, no wobble */ }
                case FIREWALL -> { /* straight line, blocks path */ }
                case PICKUP_SPREAD, PICKUP_RAPID, PICKUP_HEAVY, PICKUP_FLAME, PICKUP_LASER, POWERUP_SHIELD, HEALTH ->
                        y += Math.sin(t * 1.5 + spawnTime) * 1.5;
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

            // Enemy personality effects
            if (!dead) {
                double t = System.currentTimeMillis() / 1000.0;
                if (type == EntityType.BUG) {
                    g.setColor(new Color(255, 100, 100, 100));
                    g.setFont(new Font("JetBrains Mono", Font.PLAIN, 9));
                    String[] bugs = {"NullPtr", "undef", "NaN", "404"};
                    int idx = (int) ((t * 3 + spawnTime) % bugs.length);
                    g.drawString(bugs[idx], (int) x + 5, (int) y - 5);
                } else if (type == EntityType.CONFLICT) {
                    g.setColor(new Color(255, 200, 120, 150));
                    g.setFont(new Font("JetBrains Mono", Font.BOLD, 10));
                    g.drawString("<<< HEAD", (int) x - 5, (int) y - 5);
                } else if (type == EntityType.TECHDEBT) {
                    int pulse = (int) (Math.sin(t * 3 + spawnTime) * 40 + 80);
                    g.setColor(new Color(180, 180, 180, pulse));
                    g.setFont(new Font("JetBrains Mono", Font.BOLD, 12));
                    g.drawString("// TODO: FIX ME", (int) x - 15, (int) y - 5);
                } else if (type == EntityType.CRASH) {
                    g.setColor(new Color(255, 150, 50, 150));
                    g.setFont(new Font("JetBrains Mono", Font.BOLD, 9));
                    g.drawString("SIGSEGV", (int) x + 5, (int) y - 5);
                }
            }
        }

        public Rectangle getBounds() {
            return new Rectangle((int) x, (int) y, width, height);
        }
    }

    private final List<Enemy> enemies = new ArrayList<>();
    private final List<Projectile> enemyBullets = new ArrayList<>();
    private final Random random = new Random();

    public void reset() {
        enemies.clear();
        enemyBullets.clear();
    }

    public List<Projectile> getEnemyBullets() {
        return enemyBullets;
    }

    public void spawnEnemy(int x, int y, EntityType type) {
        enemies.add(new Enemy(x, y, type));
    }

    public void spawnEnemy(int x, int y, EntityType type, int moveDir) {
        enemies.add(new Enemy(x, y, type, moveDir));
    }

    public void spawnFromLeft(int groundY, int cameraX) {
        EntityType[] types = { EntityType.BUG, EntityType.CRASH, EntityType.LOCK };
        spawnFromLeft(groundY, cameraX, types[random.nextInt(types.length)]);
    }

    /** Spawns the encounter's requested enemy type from the left side of the viewport. */
    public void spawnFromLeft(int groundY, int cameraX, EntityType type) {
        int y = groundY - 40 - random.nextInt(120);
        if (type == EntityType.TECHDEBT) y = groundY - 80;
        else if (type == EntityType.FIREWALL) y = 0;
        enemies.add(new Enemy(cameraX - 40 - random.nextInt(100), y, type, -1));
    }

    public void spawnFormation(int startX, int groundY) {
        int count = 3 + random.nextInt(3);
        for (int i = 0; i < count; i++) {
            int y = groundY - 30 - random.nextInt(100);
            enemies.add(new Enemy(startX + i * 50, y, EntityType.BUG));
        }
    }

    private static final Object[][] ENEMY_POOL = {
        { EntityType.BUG, 20 },
        { EntityType.CONFLICT, 20 },
        { EntityType.CRASH, 15, 1.5 },
        { EntityType.LOCK, 15, 2.0 },
        { EntityType.TECHDEBT, 15, 3.0 },
        { EntityType.FIREWALL, 10, 4.0 },
    };

    public void spawnRandom(int panelWidth, int groundY, double difficulty, int cameraX) {
        double spawnChance = 2.0 + difficulty * 1.5;
        if (random.nextInt(100) < spawnChance) {
            int r = random.nextInt(100);
            EntityType type;
            int y = groundY - 40;

            // Powerups & health (fixed chance, always available)
            if (r < 2) {
                type = EntityType.PICKUP_SPREAD; y = groundY - 150 - random.nextInt(80);
            } else if (r < 4) {
                type = EntityType.PICKUP_RAPID; y = groundY - 150 - random.nextInt(80);
            } else if (r < 6) {
                type = EntityType.PICKUP_HEAVY; y = groundY - 150 - random.nextInt(80);
            } else if (r < 8) {
                type = EntityType.PICKUP_FLAME; y = groundY - 150 - random.nextInt(80);
            } else if (r < 10) {
                type = EntityType.PICKUP_LASER; y = groundY - 150 - random.nextInt(80);
            } else if (r < 14) {
                type = EntityType.POWERUP_SHIELD;
                y = groundY - 150 - random.nextInt(80);
            } else if (r < 17) {
                type = EntityType.HEALTH;
                y = groundY - 150 - random.nextInt(80);
            } else {
                // Weighted enemy selection based on unlocked types
                int totalWeight = 0;
                for (Object[] entry : ENEMY_POOL) {
                    double unlockAt = entry.length > 2 ? (double) entry[2] : 0;
                    if (difficulty >= unlockAt) {
                        totalWeight += (int) entry[1];
                    }
                }

                int w = random.nextInt(totalWeight);
                int acc = 0;
                type = EntityType.BUG; // fallback
                for (Object[] entry : ENEMY_POOL) {
                    double unlockAt = entry.length > 2 ? (double) entry[2] : 0;
                    if (difficulty >= unlockAt) {
                        acc += (int) entry[1];
                        if (w < acc) {
                            type = (EntityType) entry[0];
                            break;
                        }
                    }
                }
            }

            // Set y position based on type
            if (type == EntityType.TECHDEBT) y = groundY - 80;
            else if (type == EntityType.LOCK) y = groundY - 60 - random.nextInt(80);
            else if (type == EntityType.FIREWALL) y = 0;

            // 30% chance to spawn from left during boss fights
            boolean fromLeft = random.nextInt(100) < 30;
            int spawnX = fromLeft
                    ? cameraX - 50 - random.nextInt(100)
                    : cameraX + panelWidth + random.nextInt(200);
            int spawnDir = fromLeft ? -1 : 1;
            enemies.add(new Enemy(spawnX, y, type, spawnDir));
        }
    }

    /** Removes entities once they have genuinely left the current camera viewport. */
    public void update(int cameraLeft, int cameraRight) {
        Iterator<Enemy> it = enemies.iterator();
        while (it.hasNext()) {
            Enemy e = it.next();
            if (isOffScreen(e, cameraLeft, cameraRight) || e.isDead()) {
                it.remove();
            }
        }
    }

    private boolean isOffScreen(Enemy e, int cameraLeft, int cameraRight) {
        return e.getX() + e.getWidth() < cameraLeft - 100 || e.getX() > cameraRight + 100;
    }

    public void draw(Graphics2D g) {
        for (Enemy e : enemies) e.draw(g);
    }

    public List<Enemy> getEnemies() {
        return enemies;
    }
}
