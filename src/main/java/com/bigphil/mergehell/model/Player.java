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

    private int meleeTimer = 0;
    private int meleeCooldown = 0;
    private int bombs = 3;
    private int lives = 3;

    private WeaponType currentWeapon = WeaponType.COMMIT;
    private int weaponAmmo = 0;

    private static final double GRAVITY = 0.6;
    private static final double JUMP_FORCE = -13;
    private static final double SPEED = 5;
    private static final int DASH_FRAMES = 8;
    private static final int DASH_COOLDOWN_MAX = 45;
    private static final double DASH_SPEED = 10;
    private static final int MELEE_FRAMES = 8;
    private static final int MELEE_COOLDOWN = 30;

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
    public boolean isMeleeActive() { return meleeTimer > 0; }
    public int getFacingDir() { return facingDir; }
    public WeaponType getWeapon() { return currentWeapon; }
    public int getWeaponAmmo() { return weaponAmmo; }
    public int getBombs() { return bombs; }
    public int getLives() { return lives; }

    public boolean loseLife() {
        if (lives <= 0) return false;
        lives--;
        return lives > 0;
    }

    public boolean useBomb() {
        if (bombs <= 0) return false;
        bombs--;
        return true;
    }
    public void addBomb() { if (bombs < 5) bombs++; }

    public void setX(double x) { this.x = x; }
    public void setSudoTimer(int t) { this.sudoTimer = t; }
    public void setShieldTimer(int t) { this.shieldTimer = t; }

    public void giveWeapon(WeaponType weapon, int ammo) {
        this.currentWeapon = weapon;
        this.weaponAmmo = ammo;
    }

    public void reset(int startX, int startY) {
        this.x = startX; this.y = startY;
        this.hp = 100; this.dy = 0;
        this.sudoTimer = 0; this.shieldTimer = 0; this.invincibleTimer = 0;
        this.cooldown = 0; this.dashTimer = 0; this.dashCooldown = 0;
        this.meleeTimer = 0; this.meleeCooldown = 0;
        this.bombs = 3; this.facingDir = 1;
        this.lives = 3;
        this.currentWeapon = WeaponType.COMMIT;
        this.weaponAmmo = 0;
    }

    public void dash() { dash(facingDir); }

    public void dash(int direction) {
        if (dashCooldown > 0 || dashTimer > 0) return;
        int dir = direction >= 0 ? 1 : -1;
        dashTimer = DASH_FRAMES;
        dashVx = dir * DASH_SPEED;
        facingDir = dir;
        invincibleTimer = Math.max(invincibleTimer, DASH_FRAMES + 8);
    }

    public void melee() {
        if (meleeCooldown > 0 || meleeTimer > 0) return;
        meleeTimer = MELEE_FRAMES;
        meleeCooldown = MELEE_COOLDOWN;
    }

    public Rectangle getMeleeBounds() {
        if (meleeTimer <= 0) return null;
        int mx = facingDir > 0 ? (int) x + width : (int) x - 50;
        return new Rectangle(mx, (int) y - 10, 50, height + 20);
    }

    public void heal(int amount) {
        hp = Math.min(hp + amount, maxHp);
    }

    public void update(boolean left, boolean right, boolean jump, boolean shoot,
                        int groundY, double levelWidth, List<Projectile> projectiles,
                        List<Platform> platforms) {
        if (dashTimer > 0) {
            x += dashVx;
            dashTimer--;
            if (dashTimer == 0) dashCooldown = DASH_COOLDOWN_MAX;
            if (x < 0) x = 0;
            if (x > levelWidth - width) x = levelWidth - width;
            dy = 0;
            return;
        }

        if (left) { x -= SPEED; facingDir = -1; }
        if (right) { x += SPEED; facingDir = 1; }

        if (x < 0) x = 0;
        if (x > levelWidth - width) x = levelWidth - width;

        dy += GRAVITY;
        y += dy;

        // Ground or platform landing
        boolean landed = false;
        if (y + height > groundY) {
            y = groundY - height;
            dy = 0;
            landed = true;
        }
        for (Platform p : platforms) {
            if (dy >= 0 && p.canStandOn(x, y + height, x + width)) {
                y = p.y - height;
                dy = 0;
                landed = true;
                break;
            }
        }
        grounded = landed;

        if (jump && grounded) {
            dy = JUMP_FORCE;
            grounded = false;
        }

        if (cooldown > 0) cooldown--;
        if (sudoTimer > 0) sudoTimer--;
        if (shieldTimer > 0) shieldTimer--;
        if (invincibleTimer > 0) invincibleTimer--;
        if (dashCooldown > 0) dashCooldown--;
        if (meleeTimer > 0) meleeTimer--;
        if (meleeCooldown > 0) meleeCooldown--;

        if (shoot && cooldown <= 0 && meleeTimer <= 0) {
            WeaponType w = (sudoTimer > 0) ? WeaponType.SPREAD : currentWeapon;
            boolean piercing = (sudoTimer > 0) || w == WeaponType.HEAVY;
            ProjectileType ptype = piercing ? ProjectileType.SUDO : ProjectileType.COMMIT;
            double bulletX = (facingDir > 0) ? x + width : x;
            double bulletVx = facingDir * 10;

            for (int i = 0; i < w.bulletCount; i++) {
                double spreadY = (w.bulletCount == 1) ? 0
                        : (i - (w.bulletCount - 1) / 2.0) * 2.0;
                projectiles.add(new Projectile(bulletX, y + height / 2.0, bulletVx, spreadY, ptype));
            }
            cooldown = w.cooldown;

            if (sudoTimer <= 0 && weaponAmmo > 0 && currentWeapon != WeaponType.COMMIT) {
                weaponAmmo--;
                if (weaponAmmo <= 0) currentWeapon = WeaponType.COMMIT;
            }
        }
    }

    public void takeDamage(int amount) {
        if (shieldTimer > 0 || invincibleTimer > 0) return;
        hp -= amount;
        invincibleTimer = 60;
    }

    public void draw(Graphics2D g) {
        if (invincibleTimer > 0 && !isDashing() && !isMeleeActive() && (invincibleTimer / 4) % 2 == 0) return;

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

        g.setColor(sudoTimer <= 0 ? Color.WHITE : Color.BLACK);
        g.setFont(new Font("JetBrains Mono", Font.BOLD, 20));
        String dirSymbol = facingDir > 0 ? "J>" : "<J";
        g.drawString(dirSymbol, (int) x + 3, (int) y + 22);

        if (shieldTimer > 0) {
            g.setColor(GameColors.SHIELD_CYAN);
            g.setStroke(new BasicStroke(2));
            g.drawOval((int) x - 8, (int) y - 8, width + 16, height + 16);
        }

        // Melee slash effect
        if (meleeTimer > 0) {
            g.setComposite(originalComposite);
            float alpha = meleeTimer / (float) MELEE_FRAMES;
            g.setColor(new Color(1f, 1f, 1f, alpha * 0.7f));
            int sx = facingDir > 0 ? (int) x + width : (int) x - 50;
            g.setStroke(new BasicStroke(3));
            g.drawArc(sx, (int) y - 20, 50, height + 40, facingDir > 0 ? -60 : 120, 120);
        }
    }

    public Rectangle getBounds() {
        return new Rectangle((int) x, (int) y, width, height);
    }
}
