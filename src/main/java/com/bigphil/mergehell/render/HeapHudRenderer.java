package com.bigphil.mergehell.render;

import com.bigphil.mergehell.i18n.GameText;

import com.bigphil.mergehell.world.HeapDistrictController;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** Chapter objectives and radio dialogue use the world simulation's actual state. */
public final class HeapHudRenderer {
    public void render(Graphics2D target, HeapDistrictController.Snapshot state, boolean compact,
                       boolean boss, boolean labNotice) {
        Graphics2D g = (Graphics2D) target.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int left = compact ? 354 : 308, width = compact ? 326 : 390;
            panel(g, left, 12, width, compact ? 96 : 88);
            g.setFont(GameText.font(new Font(Font.SANS_SERIF, Font.BOLD, compact ? 19 : 16)));
            g.setColor(state.pressure() >= 70 ? new Color(255, 157, 86) : new Color(173, 222, 163));
            GameText.draw(g, (compact ? "HEAP · Pressure " : "HEAP DISTRICT · Pressure ") + state.pressure() + "%", left + 13, 35);
            g.setFont(GameText.font(new Font(Font.SANS_SERIF, Font.PLAIN, compact ? 18 : 15)));
            g.setColor(new Color(231, 236, 211));
            List<String> lines = wrap(g.getFontMetrics(), state.objective(), width - 26);
            for (int i = 0; i < Math.min(compact ? 3 : 2, lines.size()); i++)
                GameText.draw(g, lines.get(i), left + 13, 59 + i * 20);
            if (labNotice) return;
            panel(g, 308, 491, 636, 69);
            g.setColor(new Color(128, 220, 195));
            g.setFont(GameText.font(new Font(Font.SANS_SERIF, Font.BOLD, compact ? 18 : 14)));
            GameText.draw(g, state.storyTicksRemaining() > 0 ? "MAINTENANCE RADIO / FIELD LOG"
                    : boss ? "CUT REFLUX → EXPOSE THE CORE" : "GC STATION / E Purge · F Risk salvage", 321, 513);
            g.setFont(GameText.font(new Font(Font.SANS_SERIF, Font.PLAIN, compact ? 18 : 15)));
            g.setColor(new Color(219, 223, 205));
            String text = state.storyTicksRemaining() > 0 ? state.story()
                    : boss ? "Shoot returning blocks or press E near a GC station. Hit the open core for bonus damage."
                    : "Stay near the station to purge; moving away cancels it. Upper platforms bypass leaks.";
            List<String> story = wrap(g.getFontMetrics(), text, 610);
            for (int i = 0; i < Math.min(2, story.size()); i++) GameText.draw(g, story.get(i), 321, 534 + i * 20);
        } finally { g.dispose(); }
    }

    static List<String> wrap(FontMetrics metrics, String text, int width) {
        return GameText.wrap(metrics, text, width);
    }

    private static void panel(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(new Color(8, 18, 20, 237)); g.fillRoundRect(x, y, w, h, 7, 7);
        g.setColor(new Color(107, 141, 108, 150)); g.drawRoundRect(x, y, w, h, 7, 7);
    }
}
