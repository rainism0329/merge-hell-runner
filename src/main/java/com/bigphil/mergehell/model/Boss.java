package com.bigphil.mergehell.model;

import java.awt.*;
import java.util.List;
import java.util.Random;

public class Boss {
    private double x, y;
    private final int width = 120, height = 150;
    private final int maxHp;
    private int hp;
    private final String name;
    private final String symbol;
    private boolean active = false;

    private enum Phase { IDLE, DASH_WARN, DASH, BURST, RECOVER }
    private Phase phase = Phase.IDLE;

    private double targetX;
    private int actionTimer = 0;
    private int burstTimer = 0;
    private double dashDir = -1;
    private final Random random = new Random();
    private boolean isFlashing = false;
    private double playerX, playerY;

    private final int bossLevel;

    public Boss(String name, int hpPool, String symbol, double spawnWorldX, int bossLevel) {
        this.name = name;
        this.maxHp = hpPool / 2;
        this.hp = this.maxHp;
        this.symbol = symbol;
        this.bossLevel = bossLevel;
        this.x = spawnWorldX + 200;
        this.targetX = spawnWorldX - 150;
        this.y = 100;
        this.burstTimer = 250 + random.nextInt(150);
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getHp() { return hp; }
    public int getMaxHp() { return maxHp; }
    public String getName() { return name; }
    public boolean isActive() { return active; }

    public void activate() { this.active = true; }
    public boolean isDashing() { return phase == Phase.DASH; }

    private boolean isEnraged() {
        return hp < maxHp / 2;
    }

    public void update(ObstacleManager obstacleManager, int groundY, double px, double py,
                        List<Projectile> enemyBullets) {
        if (!active) return;
        this.playerX = px;
        this.playerY = py;
        boolean enraged = isEnraged();

        if (x > targetX && (phase == Phase.IDLE)) {
            x -= enraged ? 6 : 4;
            return;
        }

        actionTimer++;

        int burstInterval = enraged ? 140 : 220;
        burstTimer--;
        if (burstTimer <= 0 && phase == Phase.IDLE) {
            phase = Phase.BURST;
            actionTimer = 0;
            burstTimer = burstInterval + random.nextInt(burstInterval / 2);
            burstAttack(enemyBullets, enraged);
        }

        switch (phase) {
            case IDLE -> {
                // Track player vertically
                double distY = playerY - y - height / 2.0;
                y += distY * 0.04;
                if (y < 20) y = 20;
                if (y > groundY - height) y = groundY - height;

                // Spawn minions — faster when enraged
                int spawnInterval = enraged ? 35 : 50;
                if (actionTimer % spawnInterval == 0) {
                    attack(obstacleManager, groundY);
                }

                // Decide next action — more aggressive when enraged
                int idleTime = enraged ? 80 : 120;
                if (actionTimer > idleTime) {
                    if (random.nextInt(enraged ? 4 : 2) > 0) {
                        phase = Phase.DASH_WARN;
                    } else {
                        phase = Phase.IDLE;
                    }
                    actionTimer = 0;
                }
            }

            case DASH_WARN -> {
                dashDir = (playerX > x) ? 1 : -1;
                x = targetX + (random.nextInt(10) - 5);
                isFlashing = (actionTimer / 5) % 2 == 0;
                if (actionTimer > 40) {
                    phase = Phase.DASH;
                    actionTimer = 0;
                }
            }

            case DASH -> {
                double dashSpeed = enraged ? 28 : 22;
                x += dashDir * dashSpeed;
                if (x < 50 || x > targetX + 300) {
                    phase = Phase.RECOVER;
                    actionTimer = 0;
                }
            }

            case BURST -> {
                if (actionTimer > 30) {
                    phase = Phase.IDLE;
                    actionTimer = 0;
                }
            }

            case RECOVER -> {
                double backSpeed = enraged ? 12 : 8;
                double backDir = (targetX > x) ? 1 : -1;
                x += backDir * backSpeed;
                if (Math.abs(x - targetX) < 10) {
                    x = targetX;
                    phase = Phase.IDLE;
                    actionTimer = 0;
                    isFlashing = false;
                }
            }
        }
    }

    private void burstAttack(List<Projectile> bullets, boolean enraged) {
        double cx = x + width / 2.0;
        double cy = y + height / 2.0;
        int count = enraged ? 18 : 12;
        double speed = enraged ? 6 : 5;
        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2 / count) * i;
            bullets.add(new Projectile(cx, cy,
                    Math.cos(angle) * speed, Math.sin(angle) * speed,
                    ProjectileType.CRITICAL));
        }
    }

    private void attack(ObstacleManager om, int groundY) {
        int maxR = isEnraged() ? (10 + bossLevel * 2) : (6 + bossLevel);
        int r = random.nextInt(Math.max(8, maxR));
        switch (r) {
            case 0 -> om.spawnEnemy((int) x + width, groundY - 60, EntityType.BUG);
            case 1 -> om.spawnEnemy((int) x + width, groundY - 120, EntityType.CRASH);
            case 2 -> om.spawnEnemy((int) x + width, 0, EntityType.FIREWALL);
            case 3 -> {
                om.spawnEnemy((int) x + width, groundY - 60, EntityType.BUG);
                om.spawnEnemy((int) x + width + 40, groundY - 100, EntityType.CRASH);
            }
            case 4 -> om.spawnEnemy((int) x + width, groundY - 80, EntityType.LOCK);
            case 5 -> {
                om.spawnEnemy((int) x + width, 0, EntityType.FIREWALL);
                om.spawnEnemy((int) x + width + 30, groundY - 60, EntityType.BUG);
            }
            case 6, 7 -> om.spawnFromLeft(groundY, (int) x);
            case 8, 9 -> om.spawnFormation((int) x + width, groundY);
            case 10, 11 -> {
                om.spawnFromLeft(groundY, (int) x);
                om.spawnFromLeft(groundY, (int) x);
            }
            default -> {
                // Higher level bosses: extra attack variety
                om.spawnEnemy((int) x + width, groundY - 80, EntityType.TECHDEBT);
                om.spawnEnemy((int) x + width + 60, groundY - 60, EntityType.CRASH);
            }
        }
    }

    public void takeDamage(int amount) {
        if (!active) return;
        hp -= amount;
    }

    public void draw(Graphics2D g) {
        if (!active) return;

        boolean enraged = isEnraged();
        Color bodyColor;
        if (phase == Phase.DASH_WARN && isFlashing) {
            bodyColor = Color.WHITE;
        } else if (phase == Phase.DASH) {
            bodyColor = enraged ? Color.ORANGE : Color.YELLOW;
        } else if (phase == Phase.BURST) {
            bodyColor = new Color(255, 50, 50);
        } else if (enraged) {
            bodyColor = (actionTimer / 15) % 2 == 0
                    ? new Color(200, 30, 30) : GameColors.DANGER_RED;
        } else {
            bodyColor = GameColors.DANGER_RED;
        }

        // Shadow
        g.setColor(new Color(0, 0, 0, 80));
        g.fillRect((int) x + 6, (int) y + 6, width, height);
        // Body with gradient effect
        GradientPaint gp = new GradientPaint(
                (int) x, (int) y, bodyColor.brighter(),
                (int) x, (int) y + height, bodyColor.darker());
        g.setPaint(gp);
        g.fillRect((int) x, (int) y, width, height);
        // Thick border
        g.setColor(bodyColor.brighter().brighter());
        g.setStroke(new BasicStroke(3));
        g.drawRect((int) x, (int) y, width, height);
        // Inner highlight
        g.setColor(new Color(255, 255, 255, 40));
        g.drawRect((int) x + 4, (int) y + 4, width - 8, height - 8);

        // Emoji with glow
        g.setColor(Color.BLACK);
        g.setFont(new Font("SansSerif", Font.BOLD, 60));
        FontMetrics fm = g.getFontMetrics();
        int symW = fm.stringWidth(symbol);
        g.drawString(symbol, (int) x + 1 + (width - symW) / 2, (int) y + 91);
        g.setColor(enraged ? Color.ORANGE : Color.YELLOW);
        g.drawString(symbol, (int) x + (width - symW) / 2, (int) y + 90);

        // Personality particles
        long t = System.currentTimeMillis() / 100;
        if (t % 3 == 0 && bossLevel > 0) {
            g.setColor(bossLevel >= 3 ? Color.MAGENTA : bossLevel >= 2 ? Color.CYAN : Color.YELLOW);
            g.setFont(new Font("JetBrains Mono", Font.PLAIN, 8));
            String[] codes = {"0xDEAD", "PANIC", "CORE", "HALT", "FATAL", "ABORT"};
            String tag = codes[(int) ((t + bossLevel) % codes.length)];
            g.drawString(tag, (int) (x + 10 + Math.random() * width - 30),
                    (int) (y + 20 + Math.random() * 20));
        }

        // Name label with glow
        g.setFont(new Font("JetBrains Mono", Font.BOLD, 14));
        String label = name + (enraged ? " [ENRAGED]" : "");
        g.setColor(Color.BLACK);
        g.drawString(label, (int) x + 1, (int) y - 9);
        g.setColor(enraged ? Color.ORANGE : Color.WHITE);
        g.drawString(label, (int) x, (int) y - 10);

        if (phase == Phase.DASH_WARN) {
            // Directional warning line toward player
            g.setColor(new Color(255, 0, 0, 100));
            g.setStroke(new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{9}, 0));
            int startX = (int) (x + width / 2.0);
            int startY = (int) (y + height / 2.0);
            int endX = (int) (startX + dashDir * 300);
            g.drawLine(startX, startY, endX, startY);

            g.setColor(Color.RED);
            g.setFont(new Font("Arial", Font.BOLD, 20));
            String warning = dashDir > 0 ? ">>>" : "<<<";
            g.drawString(warning, (int) x + width / 2 - 25, (int) y + height / 2);
        }

        if (phase == Phase.BURST) {
            g.setColor(new Color(255, 0, 0, 80));
            g.setStroke(new BasicStroke(3));
            int rings = (actionTimer / 10) % 3;
            for (int i = 0; i <= rings; i++) {
                int r = 40 + i * 30;
                g.drawOval((int) x + width / 2 - r, (int) y + height / 2 - r, r * 2, r * 2);
            }
        }
    }

    public Rectangle getBounds() {
        return new Rectangle((int) x, (int) y, width, height);
    }
}
