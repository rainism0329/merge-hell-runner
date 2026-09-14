package com.bigphil.mergehell.render;

import com.bigphil.mergehell.i18n.GameText;
import com.bigphil.mergehell.world.SingularityEdgeController;
import java.awt.*;

/** The containment objective and radio occupy the same reserved columns as the previous world. */
public final class SingularityHudRenderer {
    public void render(Graphics2D target, SingularityEdgeController.Snapshot state,
                       boolean compact, boolean boss, boolean labNotice) {
        if (state.objective().isEmpty()) return;
        Graphics2D g = (Graphics2D) target.create();
        try {
            int left = compact ? 354 : 308, width = compact ? 326 : 390;
            panel(g, left, 12, width, 96);
            g.setFont(GameText.font(new Font(Font.SANS_SERIF, Font.BOLD, compact ? 18 : 16)));
            g.setColor(new Color(217, 179, 210)); GameText.draw(g, GameText.message("singularity.title"), left + 12, 34);
            g.setFont(GameText.font(new Font(Font.SANS_SERIF, Font.PLAIN, compact ? 17 : 14)));
            g.setColor(new Color(226, 234, 227));
            var lines = GameText.wrap(g.getFontMetrics(), GameText.message(state.objective()), width - 24);
            while (lines.size() > 3 && g.getFont().getSize() > 13) {
                g.setFont(g.getFont().deriveFont((float) g.getFont().getSize() - 1));
                lines = GameText.wrap(g.getFontMetrics(), GameText.message(state.objective()), width - 24);
            }
            for (int i = 0; i < Math.min(3, lines.size()); i++) GameText.draw(g, lines.get(i), left + 12, 56 + i * 20);
            if (labNotice) return;
            panel(g, 308, 491, 636, 69);
            g.setFont(GameText.font(new Font(Font.SANS_SERIF, Font.BOLD, compact ? 18 : 14)));
            g.setColor(new Color(217, 179, 210)); GameText.draw(g, GameText.message("singularity.radio"), 321, 512);
            g.setFont(GameText.font(new Font(Font.SANS_SERIF, Font.PLAIN, compact ? 17 : 14)));
            g.setColor(new Color(222, 231, 228));
            String text = GameText.message(state.storyTicksRemaining() > 0 ? state.story() : boss ? "singularity.boss.tip" : "singularity.route.tip");
            var story = GameText.wrap(g.getFontMetrics(), text, 610);
            while (story.size() > 2 && g.getFont().getSize() > 13) {
                g.setFont(g.getFont().deriveFont((float) g.getFont().getSize() - 1));
                story = GameText.wrap(g.getFontMetrics(), text, 610);
            }
            for (int i = 0; i < Math.min(2, story.size()); i++) GameText.draw(g, story.get(i), 321, 533 + i * 20);
        } finally { g.dispose(); }
    }
    private static void panel(Graphics2D g, int x, int y, int width, int height) {
        g.setColor(new Color(12, 24, 34, 235)); g.fillRoundRect(x, y, width, height, 6, 6);
        g.setColor(new Color(151, 145, 157, 130)); g.drawRoundRect(x, y, width, height, 6, 6);
    }
}
