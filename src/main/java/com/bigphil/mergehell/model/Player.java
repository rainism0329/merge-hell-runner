package com.bigphil.mergehell.model;

import java.awt.*;
import java.util.List;

public class Player {
    private double x, y, dy;
    private final int width = 30, height = 30;
    private boolean grounded = false;

    private int hp = 100;
    private final int maxHp = 100;
    private int sudoTimer = 0;
    private int shieldTimer = 0;
    private int invincibleTimer = 0;
    private int cooldown = 0;

    private static final double GRAVITY = 0.6;
    private static final double JUMP_FORCE = -13;
    private static final double SPEED = 5;

    public Player(int startX, int startY) {
        this.x = startX;
        this.y = startY;
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public int getHp() { return hp; }
    public int getMaxHp() { return maxHp; }
    public int getSudoTimer() { return sudoTimer; }
    public int getShieldTimer() { return shieldTimer; }
    public int getInvincibleTimer() { return invincibleTimer; }

    public void setSudoTimer(int t) { this.sudoTimer = t; }
    public void setShieldTimer(int t) { this.shieldTimer = t; }

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
        if (left) x -= SPEED;
        if (right) x += SPEED;

        if (x < 0) x = 0;
        if (x > panelWidth - width) x = panelWidth - width;

        dy += GRAVITY;
        y += dy;

        if (y + height > groundY) {
            y = groundY - height;
            dy = 0;
            grounded = true;
        } else {
            grounded = false;
        }

        if (jump && grounded) {
            dy = JUMP_FORCE;
            grounded = false;
        }

        if (cooldown > 0) cooldown--;
        if (sudoTimer > 0) sudoTimer--;
        if (shieldTimer > 0) shieldTimer--;
        if (invincibleTimer > 0) invincibleTimer--;

        if (shoot && cooldown <= 0) {
            if (sudoTimer > 0) {
                projectiles.add(new Projectile(x + width, y + height / 2.0, 12, 0, ProjectileType.SUDO));
                projectiles.add(new Projectile(x + width, y + height / 2.0, 11, -1.5, ProjectileType.SUDO));
                projectiles.add(new Projectile(x + width, y + height / 2.0, 11, 1.5, ProjectileType.SUDO));
                cooldown = 10;
            } else {
                projectiles.add(new Projectile(x + width, y + height / 2.0, 10, 0, ProjectileType.COMMIT));
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
        if (invincibleTimer > 0 && (invincibleTimer / 4) % 2 == 0) return;

        g.setColor(sudoTimer > 0 ? GameColors.SUDO_YELLOW : GameColors.PLAYER);
        g.fillRoundRect((int) x, (int) y, width, height, 8, 8);

        g.setColor(sudoTimer <= 0 ? Color.WHITE : Color.BLACK);
        g.setFont(new Font("JetBrains Mono", Font.BOLD, 20));
        g.drawString("J", (int) x + 10, (int) y + 22);

        if (shieldTimer > 0) {
            g.setColor(GameColors.SHIELD_CYAN);
            g.setStroke(new BasicStroke(2));
            g.drawOval((int) x - 8, (int) y - 8, width + 16, height + 16);
        }
    }

    public Rectangle getBounds() {
        return new Rectangle((int) x, (int) y, width, height);
    }
}
