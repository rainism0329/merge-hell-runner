package com.bigphil.mergehell.model;

import java.awt.*;
import java.util.Random;

public class Boss {
    public double x, y;
    public int width = 120, height = 150;
    public int maxHp, hp;
    public String name;
    public String symbol;
    public boolean active = false;

    // 状态机
    private enum Phase { FLOAT, PREPARE_DASH, DASH, RETURN }
    private Phase phase = Phase.FLOAT;

    private double targetX;
    private int actionTimer = 0;
    private final Random random = new Random();

    private boolean isFlashing = false;

    public Boss(String name, int hp, String symbol, int panelWidth) {
        this.name = name;
        this.maxHp = hp / 2;
        this.hp = this.maxHp;
        this.symbol = symbol;
        this.x = panelWidth + 200;
        this.targetX = panelWidth - 250;
        this.y = 100;
    }

    public void activate() {
        this.active = true;
    }

    // --- 新增：判断是否正在冲刺 ---
    public boolean isDashing() {
        return phase == Phase.DASH;
    }

    public void update(ObstacleManager obstacleManager, int groundY, double playerY) {
        if (!active) return;

        if (x > targetX && phase == Phase.FLOAT) {
            x -= 4;
            return;
        }

        actionTimer++;

        switch (phase) {
            case FLOAT -> {
                double distY = playerY - y - height / 2.0;
                y += distY * 0.05;

                if (y < 20) y = 20;
                if (y > groundY - height) y = groundY - height;

                if (actionTimer % 60 == 0) {
                    attack(obstacleManager, groundY);
                }

                if (actionTimer > 180) {
                    phase = Phase.PREPARE_DASH;
                    actionTimer = 0;
                }
            }
            case PREPARE_DASH -> {
                x = targetX + (random.nextInt(10) - 5);
                isFlashing = (actionTimer / 5) % 2 == 0;

                if (actionTimer > 50) {
                    phase = Phase.DASH;
                    actionTimer = 0;
                }
            }
            case DASH -> {
                x -= 25;
                if (x < 100) {
                    phase = Phase.RETURN;
                }
            }
            case RETURN -> {
                x += 10;
                if (x >= targetX) {
                    x = targetX;
                    phase = Phase.FLOAT;
                    actionTimer = 0;
                    isFlashing = false;
                }
            }
        }
    }

    private void attack(ObstacleManager om, int groundY) {
        int r = random.nextInt(3);
        if (r == 0) om.spawnEnemy((int)x + width, groundY - 60, "bug");
        else if (r == 1) om.spawnEnemy((int)x + width, groundY - 120, "crash");
        else om.spawnEnemy((int)x + width, 0, "firewall");
    }

    public void takeDamage(int amount) {
        if (!active) return;
        hp -= amount;
    }

    public void draw(Graphics2D g) {
        if (!active) return;

        if (phase == Phase.PREPARE_DASH && isFlashing) {
            g.setColor(Color.WHITE);
        } else if (phase == Phase.DASH) {
            g.setColor(Color.YELLOW);
        } else {
            g.setColor(Color.decode("#e75c4c"));
        }
        g.fillRect((int)x, (int)y, width, height);

        g.setColor(Color.BLACK);
        g.setFont(new Font("SansSerif", Font.BOLD, 60));

        FontMetrics fm = g.getFontMetrics();
        int symW = fm.stringWidth(symbol);
        g.drawString(symbol, (int)x + (width - symW)/2, (int)y + 90);

        g.setColor(Color.decode("#e75c4c"));
        g.setFont(new Font("JetBrains Mono", Font.BOLD, 14));
        g.drawString(name, (int)x, (int)y - 10);

        if (phase == Phase.PREPARE_DASH) {
            g.setColor(new Color(255, 0, 0, 100));
            g.setStroke(new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{9}, 0));
            g.drawLine(0, (int)y + height/2, (int)x, (int)y + height/2);

            g.setColor(Color.RED);
            g.setFont(new Font("Arial", Font.BOLD, 20));
            g.drawString("!!!", (int)x - 30, (int)y + height/2);
        }
    }

    public Rectangle getBounds() {
        return new Rectangle((int)x, (int)y, width, height);
    }
}