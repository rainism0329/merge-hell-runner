package com.bigphil.mergehell.model;

import java.awt.*;

public enum ProjectileType {
    COMMIT(GameColors.HP_BAR, "git push", 30, 10, 25),
    SUDO(GameColors.SUDO_YELLOW, "rm -rf /", 60, 15, 100);

    public final Color color;
    public final String label;
    public final int width;
    public final int height;
    public final int damage;

    ProjectileType(Color color, String label, int width, int height, int damage) {
        this.color = color;
        this.label = label;
        this.width = width;
        this.height = height;
        this.damage = damage;
    }
}
