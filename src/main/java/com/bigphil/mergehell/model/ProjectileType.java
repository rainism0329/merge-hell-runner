package com.bigphil.mergehell.model;

import java.awt.*;

public enum ProjectileType {
    COMMIT(GameColors.HP_BAR, "git push", 30, 10, 25),
    SUDO(GameColors.SUDO_YELLOW, "rm -rf /", 60, 15, 100),
    ENEMY(GameColors.CRASH_ORANGE, "!", 14, 14, 15, false),
    CRITICAL(GameColors.DANGER_RED, "!!", 16, 16, 25, true),
    DRONE(GameColors.SHIELD_CYAN, "", 12, 6, 10);

    public final Color color;
    public final String label;
    public final int width;
    public final int height;
    public final int damage;
    public final boolean undestroyable;

    ProjectileType(Color color, String label, int width, int height, int damage) {
        this(color, label, width, height, damage, false);
    }

    ProjectileType(Color color, String label, int width, int height, int damage, boolean undestroyable) {
        this.color = color;
        this.label = label;
        this.width = width;
        this.height = height;
        this.damage = damage;
        this.undestroyable = undestroyable;
    }

    public boolean isHostile() {
        return this == ENEMY || this == CRITICAL;
    }
}
