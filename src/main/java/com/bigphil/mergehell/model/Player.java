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

    private int dashTimer = 0;
    private int dashCooldown = 0;
    private double dashVx = 0;
    private int facingDir = 1;

    private static final double GRAVITY = 0.6;
    private static final double JUMP_FORCE = -13;
    private static final double SPEED = 5;
    private static final int DASH_FRAMES = 8;
    private static final int DASH_COOLDOWN_MAX = 45;
    private static final double DASH_SPEED = 10;

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
    public int getDashCooldown() { return dashCooldown; }
    public boolean isDashing() { return dashTimer > 0; }

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
        this.dashTimer = 0;
        this.dashCooldown = 0;
        this.facingDir = 1;
    }

    public void dash() {
        dash(facingDir);
    }

    public void dash(int direction) {
        if (dashCooldown > 0 || dashTimer > 0) return;
        int dir = direction >= 0 ? 1 : -1;
        dashTimer = DASH_FRAMES;
        dashVx = dir * DASH_SPEED;
        facingDir = dir;
        invincibleTimer = Math.max(invincibleTimer, DASH_FRAMES + 8);
    }

    public void heal(int amount) {
        hp = Math.min(hp + amount, maxHp);
    }

    public void update(boolean left, boolean right, boolean jump, boolean shoot,
                        int groundY, int panelWidth, List<Projectile> projectiles) {
        if (dashTimer > 0) {
            x += dashVx;
            dashTimer--;
            if (dashTimer == 0) {
                dashCooldown = DASH_COOLDOWN_MAX;
            }
            if (x < 0) x = 0;
            if (x > panelWidth - width) x = panelWidth - width;
            dy = 0;
            return;
        }

        if (left) { x -= SPEED; facingDir = -1; }
        if (right) { x += SPEED; facingDir = 1; }

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
        if (dashCooldown > 0) dashCooldown--;

        if (shoot && cooldown <= 0) {
            double bulletX = (facingDir > 0) ? x + width : x;
            double bulletVx = facingDir * (sudoTimer > 0 ? 12 : 10);

            if (sudoTimer > 0) {
                projectiles.add(new Projectile(bulletX, y + height / 2.0, bulletVx, 0, ProjectileType.SUDO));
                projectiles.add(new Projectile(bulletX, y + height / 2.0, bulletVx, -1.5, ProjectileType.SUDO));
                projectiles.add(new Projectile(bulletX, y + height / 2.0, bulletVx, 1.5, ProjectileType.SUDO));
                cooldown = 10;
            } else {
                projectiles.add(new Projectile(bulletX, y + height / 2.0, bulletVx, 0, ProjectileType.COMMIT));
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
        if (invincibleTimer > 0 && !isDashing() && (invincibleTimer / 4) % 2 == 0) return;

        Composite originalComposite = g.getComposite();

        if (isDashing() && dashTimer < DASH_FRAMES - 1) {
            for (int i = 1; i <= 3; i++) {
                float alpha = 0.15f * (4 - i);
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                g.setColor(sudoTimer > 0 ? GameColors.SUDO_YELLOW : GameColors.PLAYER);
                int trailX = (int) (x - dashVx * i * 0.6);
                g.fillRoundRect(trailX, (int) y, width, height, 8, 8);
            }
            g.setComposite(originalComposite);
        }

        g.setColor(sudoTimer > 0 ? GameColors.SUDO_YELLOW : GameColors.PLAYER);
        g.fillRoundRect((int) x, (int) y, width, height, 8, 8);

        // Direction indicator
        g.setColor(sudoTimer <= 0 ? Color.WHITE : Color.BLACK);
        g.setFont(new Font("JetBrains Mono", Font.BOLD, 20));
        String dirSymbol = facingDir > 0 ? "J>" : "<J";
        g.drawString(dirSymbol, (int) x + 3, (int) y + 22);

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
