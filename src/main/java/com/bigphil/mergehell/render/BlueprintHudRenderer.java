package com.bigphil.mergehell.render;

import com.bigphil.mergehell.i18n.GameText;
import com.bigphil.mergehell.world.BlueprintCitadelController;
import java.awt.*;

/** Structural objective in the reserved center region; dialogue stays below the combat floor. */
public final class BlueprintHudRenderer {
    public void render(Graphics2D target, BlueprintCitadelController.Snapshot state,
                       boolean compact, boolean boss, boolean labNotice) {
        Graphics2D g = (Graphics2D) target.create();
        try {
            int left = compact ? 354 : 308, width = compact ? 326 : 390;
            panel(g, left, 12, width, 96);
            g.setFont(GameText.font(new Font(Font.SANS_SERIF, Font.BOLD, compact ? 18 : 16)));
            g.setColor(new Color(150, 215, 226)); GameText.draw(g, GameText.message("blueprint.title"), left + 12, 34);
            g.setFont(GameText.font(new Font(Font.SANS_SERIF, Font.PLAIN, compact ? 17 : 14)));
            g.setColor(new Color(224, 231, 222));
            var lines = GameText.wrap(g.getFontMetrics(), state.objective(), width - 24);
            for (int i = 0; i < Math.min(3, lines.size()); i++) GameText.draw(g, lines.get(i), left + 12, 56 + i * 20);
            if (labNotice) return;
            panel(g, 308, 491, 636, 69);
            g.setFont(GameText.font(new Font(Font.SANS_SERIF, Font.BOLD, compact ? 18 : 14)));
            g.setColor(new Color(150, 215, 226)); GameText.draw(g, GameText.message("blueprint.radio"), 321, 512);
            g.setFont(GameText.font(new Font(Font.SANS_SERIF, Font.PLAIN, compact ? 17 : 14)));
            g.setColor(new Color(219, 229, 224));
            String text = state.storyTicksRemaining() > 0 ? state.story() : GameText.message(boss ? "blueprint.boss.tip" : "blueprint.route.tip");
            var story = GameText.wrap(g.getFontMetrics(), text, 610);
            while (story.size() > 2 && g.getFont().getSize() > 13) {
                g.setFont(g.getFont().deriveFont((float) g.getFont().getSize() - 1));
                story = GameText.wrap(g.getFontMetrics(), text, 610);
            }
            for (int i = 0; i < Math.min(2, story.size()); i++) GameText.draw(g, story.get(i), 321, 533 + i * 20);
        } finally { g.dispose(); }
    }
    private static void panel(Graphics2D g, int x, int y, int width, int height) {
        g.setColor(new Color(9, 23, 32, 235)); g.fillRoundRect(x, y, width, height, 6, 6);
        g.setColor(new Color(115, 157, 170, 135)); g.drawRoundRect(x, y, width, height, 6, 6);
    }
}
