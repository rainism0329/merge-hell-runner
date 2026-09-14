package com.bigphil.mergehell.render;

import com.bigphil.mergehell.boss.BossArrivalController;
import com.bigphil.mergehell.i18n.GameText;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** Stable, legible anticipation without a full-screen flash or hiding the ground. */
public final class BossArrivalRenderer {
    private static final Color AMBER = new Color(247, 187, 96);
    private static final Color CREAM = new Color(244, 232, 207);
    private static final Color QUIET = new Color(175, 200, 200);

    public void renderOverlay(Graphics2D target, BossArrivalController.Snapshot snapshot,
                              int width, int groundY, boolean compact) {
        if (!snapshot.active()) return;
        Graphics2D g = (Graphics2D) target.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int cardWidth = Math.min(688, width - 48);
            int left = (width - cardWidth) / 2;
            int top = Math.max(185, Math.min(188, groundY - 240));
            int height = 178;
            int counterWidth = compact ? 116 : 108;
            int counterLeft = left + cardWidth - counterWidth - 20;
            int textLeft = left + 22;
            int textWidth = counterLeft - textLeft - 24;

            g.setColor(new Color(5, 14, 20, 238));
            g.fillRoundRect(left, top, cardWidth, height, 12, 12);
            g.setColor(new Color(247, 187, 96, 115));
            g.setStroke(new BasicStroke(1.2f));
            g.drawRoundRect(left, top, cardWidth, height, 12, 12);
            g.setColor(AMBER);
            g.fillRect(left, top + 18, 4, height - 36);

            drawFitted(g, GameText.message("boss.arrival.title"), textLeft, top + 29,
                    textWidth, compact ? 19 : 15, 15, AMBER, true);
            drawFitted(g, GameText.text(snapshot.bossName()), textLeft, top + 68,
                    textWidth, compact ? 28 : 26, 18, CREAM, true);
            boolean right = snapshot.direction() == BossArrivalController.Direction.RIGHT;
            String direction = GameText.message(right ? "boss.arrival.right" : "boss.arrival.left");
            drawFitted(g, direction, textLeft + 29, top + 101,
                    textWidth - 29, compact ? 18 : 15, 14, AMBER, false);
            drawArrow(g, textLeft + 8, top + 96, right);
            drawFitted(g, GameText.message("boss.arrival.safe"), textLeft, top + 135,
                    textWidth, compact ? 18 : 15, 14, QUIET, false);

            g.setColor(new Color(247, 187, 96, 12));
            g.fillRoundRect(counterLeft, top + 18, counterWidth, 126, 8, 8);
            g.setColor(new Color(247, 187, 96, 66));
            g.drawRoundRect(counterLeft, top + 18, counterWidth, 126, 8, 8);
            String number = Integer.toString(snapshot.secondsRemaining());
            g.setFont(GameText.font(new Font(Font.SANS_SERIF, Font.BOLD, 58)));
            g.setColor(CREAM);
            GameText.draw(g, number, counterLeft + (counterWidth - g.getFontMetrics().stringWidth(number)) / 2,
                    top + 86);
            String countdown = GameText.message("boss.arrival.countdown", snapshot.secondsRemaining());
            g.setFont(GameText.font(new Font(Font.SANS_SERIF, Font.PLAIN, compact ? 16 : 13)));
            GameText.fitFont(g, countdown, counterWidth - 12, 12);
            countdown = fit(g.getFontMetrics(), countdown, counterWidth - 12);
            g.setColor(AMBER);
            GameText.draw(g, countdown, counterLeft + (counterWidth - g.getFontMetrics().stringWidth(countdown)) / 2,
                    top + 120);

            int trackWidth = cardWidth - 44;
            g.setColor(new Color(52, 63, 66));
            g.fillRoundRect(textLeft, top + height - 20, trackWidth, 4, 4, 4);
            g.setColor(AMBER);
            g.fillRoundRect(textLeft, top + height - 20,
                    (int) Math.round(trackWidth * snapshot.progress()), 4, 4, 4);
        } finally {
            g.dispose();
        }
    }

    private static void drawArrow(Graphics2D g, int x, int y, boolean right) {
        int sign = right ? 1 : -1;
        g.setColor(AMBER);
        g.setStroke(new BasicStroke(2));
        g.drawLine(x - 7 * sign, y, x + 7 * sign, y);
        g.drawLine(x + 1 * sign, y - 5, x + 7 * sign, y);
        g.drawLine(x + 1 * sign, y + 5, x + 7 * sign, y);
    }

    private static void drawFitted(Graphics2D g, String text, int x, int baseline, int width,
                                   int preferredSize, int minimumSize, Color color, boolean bold) {
        g.setFont(GameText.font(new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, preferredSize)));
        GameText.fitFont(g, text, width, minimumSize);
        g.setColor(color);
        GameText.draw(g, fit(g.getFontMetrics(), text, width), x, baseline);
    }

    private static String fit(FontMetrics metrics, String text, int width) {
        if (metrics.stringWidth(text) <= width) return text;
        while (!text.isEmpty() && metrics.stringWidth(text + "…") > width)
            text = text.substring(0, text.offsetByCodePoints(text.length(), -1));
        return metrics.stringWidth("…") <= width ? text + "…" : "";
    }
}
