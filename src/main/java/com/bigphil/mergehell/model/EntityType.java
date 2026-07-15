package com.bigphil.mergehell.model;

import java.awt.*;

public enum EntityType {
    CONFLICT(GameColors.CONFLICT_BROWN, "<<HEAD", 40, 40, 4, 20, 1, 150),
    BUG(GameColors.DANGER_RED, "🐛", 40, 40, 4, 20, 1, 100),
    TECHDEBT(GameColors.TECHDEBT_GRAY, "TODO", 60, 80, 3, 30, 50, 500),
    CRASH(GameColors.CRASH_ORANGE, "💥", 50, 50, 6, 40, 1, 200),
    LOCK(GameColors.LOCK_GREEN, "🔒", 40, 40, 4, 20, 1, 250),
    FIREWALL(GameColors.FIREWALL_BLUE, "🔥", 60, 120, 2, 50, 2, 300),
    LEAK(new Color(249, 38, 114), "MEM+", 44, 44, 2.7, 18, 3, 220),
    SENTINEL(new Color(42, 161, 152), "SCOPE", 46, 52, 2.3, 26, 4, 360),
    INTERRUPT(new Color(180, 142, 173), "IRQ", 36, 48, 7.2, 34, 2, 320),
    MIRROR(new Color(255, 121, 198), "COPY", 54, 54, 3.4, 30, 6, 620),
    PICKUP_SPREAD(GameColors.SUDO_YELLOW, "⚡", 28, 28, 3, 0, 1, 0),
    PICKUP_RAPID(GameColors.PLAYER, "🚀", 28, 28, 3, 0, 1, 0),
    PICKUP_HEAVY(GameColors.DANGER_RED, "💣", 28, 28, 3, 0, 1, 0),
    PICKUP_FLAME(GameColors.CRASH_ORANGE, "🔥", 28, 28, 3, 0, 1, 0),
    PICKUP_LASER(GameColors.SUDO_YELLOW, "⚡", 28, 28, 3, 0, 1, 0),
    POWERUP_SHIELD(GameColors.SHIELD_CYAN, "🛡️", 30, 30, 4, 0, 1, 0),
    HEALTH(GameColors.HP_BAR, "❤️", 28, 28, 3, 0, 1, 0);

    public final Color color;
    public final String symbol;
    public final int width;
    public final int height;
    public final double vx;
    public final int damage;
    public final int maxHp;
    public final int pointValue;

    EntityType(Color color, String symbol, int width, int height, double vx,
               int damage, int maxHp, int pointValue) {
        this.color = color;
        this.symbol = symbol;
        this.width = width;
        this.height = height;
        this.vx = vx;
        this.damage = damage;
        this.maxHp = maxHp;
        this.pointValue = pointValue;
    }

    public boolean isPowerup() {
        return switch (this) {
            case PICKUP_SPREAD, PICKUP_RAPID, PICKUP_HEAVY,
                    PICKUP_FLAME, PICKUP_LASER, POWERUP_SHIELD, HEALTH -> true;
            default -> false;
        };
    }

    public boolean isHostile() {
        return !isPowerup();
    }

    public WeaponType toWeapon() {
        return switch (this) {
            case PICKUP_SPREAD -> WeaponType.SPREAD;
            case PICKUP_RAPID -> WeaponType.RAPID;
            case PICKUP_HEAVY -> WeaponType.HEAVY;
            case PICKUP_FLAME -> WeaponType.FLAME;
            case PICKUP_LASER -> WeaponType.LASER;
            default -> null;
        };
    }
}
