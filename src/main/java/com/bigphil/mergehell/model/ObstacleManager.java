package com.bigphil.mergehell.model;

import com.bigphil.mergehell.engine.EntityLimits;
import com.bigphil.mergehell.render.VectorEntityRenderer;

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
        private int spawnProtectionTicks;
        private int telegraphTicks;

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
        public int getTelegraphTicks() { return telegraphTicks; }
        public boolean isCollisionProtected() { return type.isHostile() && spawnProtectionTicks > 0; }
        Enemy protectOnSpawn() { spawnProtectionTicks = 30; return this; }
        public void setDead(boolean dead) { this.dead = dead; }

        public void takeDamage(int dmg) {
            hp -= dmg;
            if (hp <= 0) dead = true;
        }

        public void takeHit(int damage, double knockback, int hitDirection) {
            takeDamage(damage);
            if (!dead && knockback > 0) {
                x += Math.copySign(Math.min(knockback, 24), hitDirection);
            }
        }

        private static final Random random = new Random();

        public Projectile maybeShoot(double playerY) {
            if (!canShoot()) return null;
            if (telegraphTicks > 0) {
                if (--telegraphTicks > 0) return null;
                return createShot(playerY);
            }
            if (--shootTimer > 0) return null;
            telegraphTicks = 30;
            return null;
        }

        private Projectile createShot(double playerY) {
            shootTimer = 60 + random.nextInt(100);
            double bulletX = x;
            double bulletY = y + height / 2.0;
            double tracking = type == EntityType.SENTINEL ? 0.045
                    : type == EntityType.MIRROR ? 0.035 : 0.03;
            double dy = (playerY - bulletY) * tracking;
            double speed = type == EntityType.SENTINEL ? 8
                    : type == EntityType.MIRROR ? 5.5 : 6;
            double bulletVx = -speed * moveDir;
            int criticalChance = type == EntityType.SENTINEL ? 3
                    : type == EntityType.MIRROR ? 4 : 5;
            ProjectileType bulletType = random.nextInt(criticalChance) == 0
                    ? ProjectileType.CRITICAL : ProjectileType.ENEMY;
            return new Projectile(bulletX, bulletY, bulletVx, dy, bulletType);
        }

        private boolean canShoot() {
            return type == EntityType.CONFLICT || type == EntityType.LOCK
                    || type == EntityType.TECHDEBT || type == EntityType.SENTINEL
                    || type == EntityType.MIRROR;
        }

        public void update(double difficultySpeed, double playerX) {
            if (spawnProtectionTicks > 0) spawnProtectionTicks--;
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
                boolean canCharge = type == EntityType.BUG || type == EntityType.CRASH
                        || type == EntityType.INTERRUPT;
                if (canCharge && --chargeTimer <= 0) {
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
                case LEAK -> y += Math.sin(t * 3.2 + spawnTime) * 3.2;
                case SENTINEL -> y += Math.sin(t * 1.6 + spawnTime) * 1.1;
                case INTERRUPT -> y += Math.signum(Math.sin(t * 9.5 + spawnTime)) * 3.8;
                case MIRROR -> y += Math.cos(t * 2.1 + spawnTime) * 2.4;
                case PICKUP_SPREAD, PICKUP_RAPID, PICKUP_HEAVY, PICKUP_FLAME, PICKUP_LASER, POWERUP_SHIELD, HEALTH ->
                        y += Math.sin(t * 1.5 + spawnTime) * 1.5;
            }
        }

        public void draw(Graphics2D g) {
            if (type.isHostile()) {
                VectorEntityRenderer.render(g, type, (int) x, (int) y, width, height,
                        hp, type.maxHp, telegraphTicks);
                return;
            }
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
    private int rejectedHostiles;

    public void reset() {
        enemies.clear();
        enemyBullets.clear();
    }

    public void clearHostiles() {
        enemies.removeIf(enemy -> enemy.getType().isHostile());
        enemyBullets.clear();
    }

    public List<Projectile> getEnemyBullets() {
        return enemyBullets;
    }

    public void spawnEnemy(int x, int y, EntityType type) {
        if (!canSpawn(type)) return;
        enemies.add(new Enemy(x, y, type).protectOnSpawn());
    }

    public void spawnEnemy(int x, int y, EntityType type, int moveDir) {
        if (!canSpawn(type)) return;
        enemies.add(new Enemy(x, y, type, moveDir).protectOnSpawn());
    }

    public void spawnFromLeft(int groundY, int cameraX) {
        EntityType[] types = { EntityType.BUG, EntityType.CRASH, EntityType.LOCK };
        spawnFromLeft(groundY, cameraX, types[random.nextInt(types.length)]);
    }

    /** Spawns the encounter's requested enemy type from the left side of the viewport. */
    public void spawnFromLeft(int groundY, int cameraX, EntityType type) {
        if (!canSpawn(type)) return;
        int y = groundY - 40 - random.nextInt(120);
        if (type == EntityType.TECHDEBT) y = groundY - 80;
        else if (type == EntityType.FIREWALL) y = 0;
        else if (type == EntityType.SENTINEL) y = groundY - 170 - random.nextInt(130);
        else if (type == EntityType.MIRROR) y = groundY - 90 - random.nextInt(180);
        enemies.add(new Enemy(cameraX - 40 - random.nextInt(100), y, type, -1).protectOnSpawn());
    }

    public void spawnFormation(int startX, int groundY) {
        int count = 3 + random.nextInt(3);
        for (int i = 0; i < count; i++) {
            int y = groundY - 30 - random.nextInt(100);
            if (canSpawn(EntityType.BUG))
                enemies.add(new Enemy(startX + i * 50, y, EntityType.BUG).protectOnSpawn());
        }
    }

    public void spawnRandom(int panelWidth, int groundY, double difficulty, int cameraX) {
        spawnRandom(panelWidth, groundY, difficulty, cameraX, 1);
    }

    public void spawnRandom(int panelWidth, int groundY, double difficulty, int cameraX, int level) {
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
                EntityType[] roster = ambientRoster(level);
                type = roster[random.nextInt(roster.length)];
            }

            // Set y position based on type
            if (type == EntityType.TECHDEBT) y = groundY - 80;
            else if (type == EntityType.LOCK) y = groundY - 60 - random.nextInt(80);
            else if (type == EntityType.FIREWALL) y = 0;
            else if (type == EntityType.SENTINEL) y = groundY - 170 - random.nextInt(130);
            else if (type == EntityType.MIRROR) y = groundY - 90 - random.nextInt(180);

            // 30% chance to spawn from left during boss fights
            boolean fromLeft = random.nextInt(100) < 30;
            int spawnX = fromLeft
                    ? cameraX - 50 - random.nextInt(100)
                    : cameraX + panelWidth + random.nextInt(200);
            int spawnDir = fromLeft ? -1 : 1;
            if (canSpawn(type)) enemies.add(new Enemy(spawnX, y, type, spawnDir).protectOnSpawn());
        }
    }

    static EntityType[] ambientRoster(int level) {
        return switch (level) {
            case 1 -> new EntityType[]{EntityType.LEAK, EntityType.LEAK, EntityType.BUG,
                    EntityType.TECHDEBT, EntityType.CRASH};
            case 2 -> new EntityType[]{EntityType.SENTINEL, EntityType.SENTINEL,
                    EntityType.LOCK, EntityType.CONFLICT, EntityType.FIREWALL};
            case 3 -> new EntityType[]{EntityType.INTERRUPT, EntityType.INTERRUPT,
                    EntityType.CRASH, EntityType.LOCK, EntityType.FIREWALL};
            default -> new EntityType[]{EntityType.MIRROR, EntityType.LEAK,
                    EntityType.SENTINEL, EntityType.INTERRUPT, EntityType.TECHDEBT,
                    EntityType.CONFLICT};
        };
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

    private boolean canSpawn(EntityType type) {
        if (!type.isHostile()) return true;
        long hostiles = enemies.stream().filter(e -> !e.isDead() && e.getType().isHostile()).count();
        if (hostiles < EntityLimits.MAX_HOSTILES) return true;
        rejectedHostiles++;
        return false;
    }

    public int getRejectedHostiles() { return rejectedHostiles; }
}
