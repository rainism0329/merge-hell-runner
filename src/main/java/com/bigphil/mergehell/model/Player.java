package com.bigphil.mergehell.model;

import java.awt.*;
import java.util.List;

public class Player {
    public double x, y, dy;
    public int width = 30, height = 30;
    public boolean grounded = false;

    // 状态
    public int hp = 100;
    public int maxHp = 100;
    public int sudoTimer = 0;
    public int shieldTimer = 0;
    public int invincibleTimer = 0;

    // 射击
    private int cooldown = 0;

    // 常量
    private final double GRAVITY = 0.6;
    private final double JUMP_FORCE = -13; // 稍微增加跳跃力度，手感更轻盈
    private final double SPEED = 5;

    public Player(int startX, int startY) {
        this.x = startX;
        this.y = startY;
    }

    public void reset(int startX, int startY) {
        this.x = startX;
        this.y = startY;
        this.hp = 100;
        this.dy = 0;
        this.sudoTimer = 0;
        this.shieldTimer = 0;
        this.invincibleTimer = 0;
        this.cooldown = 0;
    }

    public void update(boolean left, boolean right, boolean jump, boolean shoot, int groundY, int panelWidth, List<Projectile> projectiles) {
        // 移动
        if (left) x -= SPEED;
        if (right) x += SPEED;

        // 边界
        if (x < 0) x = 0;
        if (x > panelWidth - width) x = panelWidth - width;

        // 物理
        dy += GRAVITY;
        y += dy;

        // 地面检测
        if (y + height > groundY) {
            y = groundY - height;
            dy = 0;
            grounded = true;
        } else {
            grounded = false;
        }

        // 跳跃
        if (jump && grounded) {
            dy = JUMP_FORCE;
            grounded = false;
        }

        // 冷却与Buff计时
        if (cooldown > 0) cooldown--;
        if (sudoTimer > 0) sudoTimer--;
        if (shieldTimer > 0) shieldTimer--;
        if (invincibleTimer > 0) invincibleTimer--;

        // 射击逻辑
        if (shoot && cooldown <= 0) {
            boolean isSudo = sudoTimer > 0;

            if (isSudo) {
                // Sudo 模式：散射三发
                projectiles.add(new Projectile(x + width, y + height / 2.0, 12, 0, "sudo"));
                projectiles.add(new Projectile(x + width, y + height / 2.0, 11, -1.5, "sudo")); // 向上偏
                projectiles.add(new Projectile(x + width, y + height / 2.0, 11, 1.5, "sudo"));  // 向下偏
                cooldown = 10;
            } else {
                // 普通模式：单发
                projectiles.add(new Projectile(x + width, y + height / 2.0, 10, 0, "commit"));
                cooldown = 20;
            }
        }
    }

    public void takeDamage(int amount) {
        if (shieldTimer > 0 || invincibleTimer > 0) return;
        hp -= amount;
        invincibleTimer = 60;
    }

    public void draw(Graphics2D g) {
        // 无敌闪烁
        if (invincibleTimer > 0 && (invincibleTimer / 4) % 2 == 0) return;

        // 玩家本体
        g.setColor(sudoTimer > 0 ? Color.decode("#f2c55c") : Color.decode("#3574f0"));
        g.fillRoundRect((int)x, (int)y, width, height, 8, 8); // 更圆润一点

        // 文字
        g.setColor(Color.BLACK); // 金色状态下用黑字更清晰
        if (sudoTimer <= 0) g.setColor(Color.WHITE);
        g.setFont(new Font("JetBrains Mono", Font.BOLD, 20));
        g.drawString("J", (int)x + 10, (int)y + 22);

        // 护盾特效
        if (shieldTimer > 0) {
            g.setColor(Color.decode("#40c4ff"));
            g.setStroke(new BasicStroke(2));
            g.drawOval((int)x - 8, (int)y - 8, width + 16, height + 16);
        }
    }

    public Rectangle getBounds() {
        return new Rectangle((int)x, (int)y, width, height);
    }
}