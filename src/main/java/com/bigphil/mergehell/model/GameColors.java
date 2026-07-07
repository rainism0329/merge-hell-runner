package com.bigphil.mergehell.model;

import java.awt.*;

public final class GameColors {
    private GameColors() {}

    public static final Color BG = Color.decode("#1e1f22");
    public static final Color TERMINAL_BG = new Color(30, 30, 30);
    public static final Color TERMINAL_BORDER = new Color(50, 50, 50);
    public static final Color GROUND = Color.decode("#323232");
    public static final Color OVERLAY = new Color(0, 0, 0, 180);
    public static final Color SCANLINE = new Color(0, 0, 0, 30);

    public static final Color PLAYER = Color.decode("#3574f0");
    public static final Color HP_BAR = Color.decode("#6aab73");
    public static final Color HP_LOW = Color.RED;

    public static final Color DANGER_RED = Color.decode("#e75c4c");
    public static final Color CONFLICT_BROWN = Color.decode("#cf8e6d");
    public static final Color TECHDEBT_GRAY = Color.decode("#5c6370");
    public static final Color CRASH_ORANGE = Color.decode("#ff9800");
    public static final Color LOCK_GREEN = Color.decode("#4caf50");
    public static final Color FIREWALL_BLUE = Color.decode("#1e88e5");

    public static final Color SUDO_YELLOW = Color.decode("#f2c55c");
    public static final Color SHIELD_CYAN = Color.decode("#40c4ff");
    public static final Color CONTROLS_TEXT = new Color(200, 200, 200);
}
