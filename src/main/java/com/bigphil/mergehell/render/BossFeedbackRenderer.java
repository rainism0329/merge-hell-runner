package com.bigphil.mergehell.render;

import com.bigphil.mergehell.i18n.GameText;

import com.bigphil.mergehell.boss.BossFeedbackController;
import com.bigphil.mergehell.combat.CombatEvent;

import java.awt.*;

/** Draw-only feedback. Paused frames, alpha rules, and camera transforms are owned by the caller. */
public final class BossFeedbackRenderer {
    public void renderWorld(Graphics2D target, BossFeedbackController.Snapshot snapshot, boolean flashes) {
        Graphics2D g = (Graphics2D) target.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            for (var impact : snapshot.impacts()) {
                double progress = impact.progress();
                int x = (int) Math.round(impact.x()), y = (int) Math.round(impact.y());
                boolean drone = impact.source() == CombatEvent.DamageKind.DRONE;
                Color color = impact.blocked() ? new Color(107, 179, 212)
                        : drone ? new Color(156, 237, 244) : new Color(255, 224, 158);
                int alpha = (int) (220 * (1 - progress));
                g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
                int radius = (int) ((drone ? 4 : impact.critical() ? 10 : 7) + progress * (drone ? 11 : 24));
                g.setStroke(new BasicStroke(impact.critical() ? 2.4f : 1.3f));
                if (impact.blocked()) {
                    g.drawArc(x - radius, y - radius, radius * 2, radius * 2, 55, 250);
                    g.drawLine(x - 4, y - 4, x + 4, y + 4);
                } else {
                    g.drawOval(x - radius, y - radius, radius * 2, radius * 2);
                    for (int ray = 0; ray < (drone ? 3 : 6); ray++) {
                        double angle = ray * Math.PI / 3 + impact.partId() * .7;
                        int end = radius + (int) (7 * (1 - progress));
                        g.drawLine(x + (int) (Math.cos(angle) * radius), y + (int) (Math.sin(angle) * radius),
                                x + (int) (Math.cos(angle) * end), y + (int) (Math.sin(angle) * end));
                    }
                    if (flashes && progress < .20) {
                        g.setColor(new Color(255, 246, 218, (int) (180 * (1 - progress / .20))));
                        g.fillOval(x - 4, y - 4, 8, 8);
                    }
                }
                g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
                g.setFont(GameText.font(new Font(Font.SANS_SERIF, impact.critical() ? Font.BOLD : Font.PLAIN, drone ? 11 : 14)));
                GameText.draw(g, impact.blocked() ? "BLOCKED" : "-" + impact.damage(), x + 7, y - 10 - (int) (progress * 18));
            }
        } finally { g.dispose(); }
    }

    /** Unified boss HUD occupies y=108..180; the parent pause/upgrade layer is drawn afterwards. */
    public void renderOverlay(Graphics2D target, BossFeedbackController.Snapshot snapshot, int width, boolean compact) {
        renderOverlay(target, snapshot, width, compact, null, null, null);
    }

    /** Chapter mechanics supply their real exposure rule instead of the legacy +50% caption. */
    public void renderOverlay(Graphics2D target, BossFeedbackController.Snapshot snapshot, int width, boolean compact,
                              String exposureCaption, String move, String hint) {
        Graphics2D g = (Graphics2D) target.create();
        try {
            int barWidth = Math.min(600, Math.max(180, width - 96)), left = (width - barWidth) / 2;
            g.setColor(new Color(7, 15, 20, 235)); g.fillRoundRect(left - 12, 108, barWidth + 24, 72, 8, 8);
            Color accent = snapshot.vulnerable() ? new Color(102, 239, 210)
                    : snapshot.shielded() ? new Color(116, 183, 215) : new Color(244, 176, 84);
            g.setFont(GameText.font(new Font(Font.SANS_SERIF, Font.BOLD, compact ? 18 : 14))); g.setColor(accent);
            String state = snapshot.shielded() ? "SHIELDED // BREAK LINKS" : snapshot.vulnerable()
                    ? exposureCaption == null ? "CORE WINDOW // DAMAGE +50%" : exposureCaption : snapshot.stageLabel();
            String health = snapshot.hp() + " / " + snapshot.maxHp();
            int stateWidth = barWidth - g.getFontMetrics().stringWidth(GameText.text(health)) - 24;
            GameText.draw(g, fitText(g.getFontMetrics(), state, stateWidth), left, 124);
            GameText.draw(g, health, left + barWidth - g.getFontMetrics().stringWidth(GameText.text(health)), 124);
            g.setColor(new Color(39, 46, 48)); g.fillRoundRect(left, 131, barWidth, 13, 4, 4);
            g.setColor(new Color(247, 213, 139));
            g.fillRect(left, 131, (int) Math.round(barWidth * Math.min(1, snapshot.trailingHp() / snapshot.maxHp())), 13);
            g.setColor(accent); g.fillRect(left, 131, (int) Math.round(barWidth * (snapshot.hp() / (double) snapshot.maxHp())), 13);
            if (snapshot.banner() != null) {
                g.setFont(GameText.font(new Font(Font.SANS_SERIF, Font.BOLD, compact ? 18 : 14)));
                String text = fitText(g.getFontMetrics(), snapshot.banner().text(), barWidth);
                g.setColor(new Color(255, 239, 197));
                GameText.draw(g, text, (width - g.getFontMetrics().stringWidth(GameText.text(text))) / 2, hint == null ? 168 : 159);
            } else if (move != null) {
                g.setFont(GameText.font(new Font(Font.SANS_SERIF, Font.BOLD, compact ? 14 : 11)));
                g.setColor(new Color(236,203,147));
                GameText.draw(g, fitText(g.getFontMetrics(), move, barWidth), left, 157);
            }
            if (hint != null) {
                g.setFont(GameText.font(new Font(Font.SANS_SERIF, Font.PLAIN, compact ? 13 : 10)));
                g.setColor(new Color(204,220,217));
                GameText.draw(g, fitText(g.getFontMetrics(), hint, barWidth), left, 174);
            }
        } finally { g.dispose(); }
    }

    private static String fitText(FontMetrics metrics, String text, int width) {
        text = GameText.text(text);
        if (metrics.stringWidth(GameText.text(text)) <= width) return text;
        String suffix = "…";
        while (!text.isEmpty() && metrics.stringWidth(GameText.text(text + suffix)) > width)
            text = text.substring(0, text.offsetByCodePoints(text.length(), -1));
        return metrics.stringWidth(GameText.text(suffix)) > width ? "" : text + suffix;
    }
}
