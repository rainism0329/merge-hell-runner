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
    SENTINEL(new Color(73, 193, 207), "SCOPE", 58, 58, 2.3, 22, 90, 360),
    WARDEN(new Color(220, 171, 83), "GUARD", 60, 78, 1.5, 24, 240, 480),
    RIGGER(new Color(120, 219, 223), "RIG", 54, 44, 2.7, 18, 85, 340),
    INTERRUPT(new Color(170, 199, 247), "IRQ", 68, 42, 3.1, 22, 95, 360),
    DRILLER(new Color(229, 133, 66), "DRILL", 82, 36, 2.4, 24, 160, 460),
    SLAG_SPITTER(new Color(250, 156, 64), "SLAG", 68, 68, 1.5, 22, 220, 500),
    MIRROR(new Color(213, 109, 174), "RESIN", 82, 52, 2.4, 22, 190, 620),
    SPORE_POD(new Color(164, 209, 120), "SPORE", 45, 88, 1.4, 18, 140, 560),
    LURKER(new Color(191, 153, 214), "LURK", 70, 38, 3.6, 26, 105, 540),
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

    /** Authored chapter families have their own movement, attack and recovery contracts. */
    public boolean isChapterSpecialist() {
        return switch (this) {
            case SENTINEL, WARDEN, RIGGER, INTERRUPT, DRILLER, SLAG_SPITTER,
                    MIRROR, SPORE_POD, LURKER -> true;
            default -> false;
        };
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
