package com.bigphil.mergehell.render;

import com.bigphil.mergehell.i18n.GameText;
import java.awt.*;

/** Shared geometry for progress protection and the actions after a failed run. */
public final class RunFlowRenderer {
    private RunFlowRenderer() { }
    public static Rectangle confirmBounds() { return new Rectangle(490, 330, 250, 48); }
    public static Rectangle cancelBounds() { return new Rectangle(220, 330, 250, 48); }
    public static Rectangle retryBounds() { return new Rectangle(200, 350, 270, 46); }
    public static Rectangle menuBounds() { return new Rectangle(490, 350, 270, 46); }
    public static Rectangle practiceBounds() { return new Rectangle(330, 410, 300, 40); }
    public static Rectangle resumeBounds() { return new Rectangle(305, 320, 350, 52); }
    public static Rectangle pauseMenuBounds() { return new Rectangle(230, 402, 240, 40); }
    public static Rectangle pauseSettingsBounds() { return new Rectangle(490, 402, 240, 40); }

    public static void confirmation(Graphics2D target, boolean saved) {
        Graphics2D g = (Graphics2D) target.create();
        try {
            g.setColor(new Color(3, 7, 11, 220)); g.fillRect(0, 0, 960, 600);
            g.setColor(new Color(17, 26, 33)); g.fillRoundRect(180, 170, 600, 245, 12, 12);
            g.setColor(new Color(241, 178, 71)); g.drawRoundRect(180, 170, 600, 245, 12, 12);
            centered(g, "flow.new.title", 216, 26, Color.WHITE);
            centered(g, saved ? "flow.new.saved" : "flow.new.current", 260, 18, new Color(226, 204, 171));
            centered(g, "flow.new.detail", 292, 17, new Color(190, 206, 215));
            button(g, cancelBounds(), "flow.cancel", false);
            button(g, confirmBounds(), "flow.confirm", true);
        } finally { g.dispose(); }
    }

    public static void failureActions(Graphics2D g) {
        button(g, retryBounds(), "flow.retry", true);
        button(g, menuBounds(), "flow.menu", false);
        button(g, practiceBounds(), "flow.practice", false);
    }

    public static void pause(Graphics2D target, String savedDescription, boolean canRestore) {
        Graphics2D g = (Graphics2D) target.create();
        try {
            g.setColor(new Color(8, 11, 16, 224)); g.fillRect(0, 0, 960, 600);
            centered(g, "flow.paused", 223, 32, new Color(241, 178, 71));
            text(g, savedDescription, 264, 18, Color.WHITE);
            centered(g, canRestore ? "flow.pause.detail" : "flow.pause.unsaved", 292, 16, new Color(180, 197, 204));
            button(g, resumeBounds(), "flow.resume", true);
            button(g, pauseMenuBounds(), "flow.menu", false);
            button(g, pauseSettingsBounds(), "flow.settings", false);
        } finally { g.dispose(); }
    }

    private static void button(Graphics2D g, Rectangle r, String key, boolean primary) {
        g.setColor(primary ? new Color(221, 162, 72) : new Color(22, 35, 44));
        g.fillRoundRect(r.x, r.y, r.width, r.height, 6, 6);
        g.setColor(new Color(214, 168, 97)); g.drawRoundRect(r.x, r.y, r.width, r.height, 6, 6);
        g.setFont(GameText.font(new Font(Font.SANS_SERIF, Font.BOLD, 18)));
        String label = GameText.message(key); GameText.fitFont(g, label, r.width - 20, 15);
        g.setColor(primary ? new Color(20, 27, 31) : Color.WHITE);
        GameText.draw(g, label, r.x + (r.width - g.getFontMetrics().stringWidth(label)) / 2,
                r.y + (r.height + g.getFontMetrics().getAscent()) / 2 - 3);
    }
    private static void centered(Graphics2D g, String key, int y, int size, Color color) {
        text(g, GameText.message(key), y, size, color);
    }
    private static void text(Graphics2D g, String text, int y, int size, Color color) {
        g.setFont(GameText.font(new Font(Font.SANS_SERIF, Font.BOLD, size)));
        GameText.fitFont(g, text, 710, 14); g.setColor(color);
        GameText.draw(g, text, (960 - g.getFontMetrics().stringWidth(GameText.text(text))) / 2, y);
    }
}
