package com.bigphil.mergehell.model;

import java.awt.*;

public enum EntityType {
    CONFLICT(GameColors.CONFLICT_BROWN, "<<HEAD", 40, 40, 4, 20, 1),
    BUG(GameColors.DANGER_RED, "🐛", 40, 40, 4, 20, 1),
    TECHDEBT(GameColors.TECHDEBT_GRAY, "TODO", 60, 80, 3, 30, 50),
    CRASH(GameColors.CRASH_ORANGE, "💥", 50, 50, 6, 40, 1),
    LOCK(GameColors.LOCK_GREEN, "🔒", 40, 40, 4, 20, 1),
    FIREWALL(GameColors.FIREWALL_BLUE, "🔥", 60, 120, 2, 50, 2),
    POWERUP_SUDO(GameColors.SUDO_YELLOW, "⚡", 30, 30, 4, 0, 1),
    POWERUP_SHIELD(GameColors.SHIELD_CYAN, "🛡️", 30, 30, 4, 0, 1),
    HEALTH(GameColors.HP_BAR, "❤️", 28, 28, 3, 0, 1);

    public final Color color;
    public final String symbol;
    public final int width;
    public final int height;
    public final double vx;
    public final int damage;
    public final int maxHp;

    EntityType(Color color, String symbol, int width, int height, double vx, int damage, int maxHp) {
        this.color = color;
        this.symbol = symbol;
        this.width = width;
        this.height = height;
        this.vx = vx;
        this.damage = damage;
        this.maxHp = maxHp;
    }

    public boolean isPowerup() {
        return this == POWERUP_SUDO || this == POWERUP_SHIELD || this == HEALTH;
    }

    public boolean isHostile() {
        return !isPowerup();
    }
}
