package com.bigphil.mergehell;

import java.awt.*;

public enum LevelTheme {
    DARK(new Color(30, 31, 34), new Color(35, 38, 42), new Color(50, 55, 60),
            new Color(80, 200, 120), new Color(53, 117, 243), new Color(80, 180, 120)),
    MONOKAI(new Color(39, 40, 34), new Color(45, 46, 40), new Color(60, 56, 54),
            new Color(249, 38, 114), new Color(166, 226, 46), new Color(249, 38, 114)),
    SOLARIZED(new Color(0, 43, 54), new Color(7, 54, 66), new Color(42, 65, 77),
            new Color(181, 137, 0), new Color(38, 139, 210), new Color(42, 161, 152)),
    NORD(new Color(46, 52, 64), new Color(59, 66, 82), new Color(67, 76, 94),
            new Color(136, 192, 208), new Color(143, 188, 187), new Color(180, 142, 173)),
    DRACULA(new Color(40, 42, 54), new Color(50, 52, 64), new Color(68, 71, 90),
            new Color(255, 121, 198), new Color(139, 233, 253), new Color(80, 250, 123));

    public final Color bg, grid, ground;
    public final Color accent, hud, codeRain;

    LevelTheme(Color bg, Color grid, Color ground, Color accent, Color hud, Color codeRain) {
        this.bg = bg; this.grid = grid; this.ground = ground;
        this.accent = accent; this.hud = hud; this.codeRain = codeRain;
    }

    public static LevelTheme forLevel(int level) {
        return values()[Math.min(level, values().length - 1)];
    }
}
