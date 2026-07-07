package com.bigphil.mergehell.model;

import java.awt.*;

public enum EntityType {
    CONFLICT(GameColors.CONFLICT_BROWN, "<<HEAD", 40, 40, 4, 20),
    BUG(GameColors.DANGER_RED, "🐛", 40, 40, 4, 20),
    TECHDEBT(GameColors.TECHDEBT_GRAY, "TODO", 60, 80, 3, 30),
    CRASH(GameColors.CRASH_ORANGE, "💥", 50, 50, 6, 40),
    LOCK(GameColors.LOCK_GREEN, "🔒", 40, 40, 4, 20),
    FIREWALL(GameColors.FIREWALL_BLUE, "🔥", 60, 120, 2, 50),
    POWERUP_SUDO(GameColors.SUDO_YELLOW, "⚡", 30, 30, 4, 0),
    POWERUP_SHIELD(GameColors.SHIELD_CYAN, "🛡️", 30, 30, 4, 0);

    public final Color color;
    public final String symbol;
    public final int width;
    public final int height;
    public final double vx;
    public final int damage;

    EntityType(Color color, String symbol, int width, int height, double vx, int damage) {
        this.color = color;
        this.symbol = symbol;
        this.width = width;
        this.height = height;
        this.vx = vx;
        this.damage = damage;
    }

    public boolean isPowerup() {
        return this == POWERUP_SUDO || this == POWERUP_SHIELD;
    }
}
