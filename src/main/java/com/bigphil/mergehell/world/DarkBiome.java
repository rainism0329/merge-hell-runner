package com.bigphil.mergehell.world;

import java.awt.Color;

/**
 * Visual and terrain identity for each act of the Dark mission.
 */
public enum DarkBiome {
    REPOSITORY_CITY(
            "REPOSITORY CITY", "Rooftops // neon commits // rain haze",
            new Color(9, 18, 33), new Color(35, 55, 78),
            new Color(72, 93, 116), new Color(29, 38, 50),
            new Color(65, 204, 255)),
    CI_FOUNDRY(
            "CI FOUNDRY", "Furnaces // cranes // pressure lines",
            new Color(30, 15, 19), new Color(91, 43, 30),
            new Color(111, 66, 43), new Color(47, 34, 31),
            new Color(255, 137, 54)),
    DATA_CENTER(
            "DATA CENTER", "Server canyons // coolant // cable decks",
            new Color(7, 24, 31), new Color(15, 64, 70),
            new Color(38, 102, 105), new Color(24, 44, 48),
            new Color(73, 238, 207)),
    DEPENDENCY_DUMP(
            "DEPENDENCY DUMP", "Broken packages // scrap // unstable stacks",
            new Color(25, 21, 33), new Color(69, 51, 70),
            new Color(99, 77, 93), new Color(45, 39, 48),
            new Color(222, 121, 255)),
    BOSS_GATE(
            "BOSS GATE", "Fortified mainline // no rollback",
            new Color(31, 8, 12), new Color(82, 19, 24),
            new Color(117, 38, 37), new Color(50, 26, 28),
            new Color(255, 70, 70));

    public final String displayName;
    public final String subtitle;
    public final Color skyTop;
    public final Color skyBottom;
    public final Color silhouette;
    public final Color ground;
    public final Color accent;

    DarkBiome(String displayName, String subtitle, Color skyTop, Color skyBottom,
              Color silhouette, Color ground, Color accent) {
        this.displayName = displayName;
        this.subtitle = subtitle;
        this.skyTop = skyTop;
        this.skyBottom = skyBottom;
        this.silhouette = silhouette;
        this.ground = ground;
        this.accent = accent;
    }

    public static DarkBiome forProgress(double progress) {
        double safe = Double.isFinite(progress) ? Math.max(0, Math.min(1, progress)) : 0;
        if (safe < 0.18) return REPOSITORY_CITY;
        if (safe < 0.38) return CI_FOUNDRY;
        if (safe < 0.60) return DATA_CENTER;
        if (safe < 0.82) return DEPENDENCY_DUMP;
        return BOSS_GATE;
    }
}
