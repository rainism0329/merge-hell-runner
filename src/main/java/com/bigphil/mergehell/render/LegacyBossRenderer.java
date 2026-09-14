package com.bigphil.mergehell.render;

import com.bigphil.mergehell.i18n.GameText;

import com.bigphil.mergehell.boss.BossAction;
import com.bigphil.mergehell.boss.BossPhase;
import com.bigphil.mergehell.boss.BossSnapshot;
import java.awt.*;
import java.awt.geom.Path2D;

/** Draws immutable boss presentation data; the controller remains the sole source of danger geometry. */
public final class LegacyBossRenderer {
    public record Visual(BossSnapshot boss, double coreX, double coreY, int coreWidth, int coreHeight,
                         int warningTicks, double laserY, int laserHeight, int laserTicks,
                         double defeatProgress, double seconds, double cameraX, boolean compact,
                         boolean flashes) { }

    private static final Color AMBER = new Color(255, 184, 78);
    private static final Color CREAM = new Color(245, 234, 211);
    private static final Color RED = new Color(255, 78, 49);

    public void render(Graphics2D target, IndustrialArt art, Visual v, int width, int groundY) {
        render(target, art, v, width, groundY, true);
    }

    public void render(Graphics2D target, IndustrialArt art, Visual v, int width, int groundY, boolean showStatus) {
        render(target, art, v, width, groundY, showStatus, true);
    }

    public void render(Graphics2D target, IndustrialArt art, Visual v, int width, int groundY,
                       boolean showStatus, boolean showCombatHints) {
        Graphics2D g = (Graphics2D) target.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int cx = (int) (v.coreX() - v.cameraX()), cy = (int) v.coreY();
            if (showCombatHints) drawAttack(g, v, width, groundY, cx, cy);
            drawTethers(g, v, cx, cy);
            drawCore(g, art, v, cx, cy);
            drawNodes(g, art, v, showCombatHints);
            if (showStatus) drawStatus(g, v, width);
        } finally { g.dispose(); }
    }

    private void drawAttack(Graphics2D g, Visual v, int width, int groundY, int cx, int cy) {
        if (v.laserTicks() > 0) {
            int y = (int) v.laserY() - v.laserHeight() / 2;
            int alpha = v.flashes() ? Math.min(235, 80 + v.laserTicks() * 16) : 180;
            g.setColor(new Color(255, 50, 35, alpha)); g.fillRect(0, y, width, v.laserHeight());
            g.setColor(new Color(255, 239, 201, alpha));
            g.fillRect(0, y + v.laserHeight() / 2 - 3, width, 6);
        }
        BossAction action = v.boss().telegraph();
        if (action == null) return;
        int alpha = v.flashes() ? Math.min(205, 135 + v.warningTicks() % 8 * 10) : 205;
        g.setFont(GameText.font(new Font(Font.SANS_SERIF, Font.BOLD, v.compact() ? 18 : 14)));
        g.setStroke(new BasicStroke(2));
        if (action instanceof BossAction.Laser laser) {
            int y = (int) laser.laneY() - laser.height() / 2;
            g.setColor(new Color(255, 65, 45, 45)); g.fillRect(0, y, width, laser.height());
            g.setColor(new Color(255, 123, 76, alpha));
            g.drawLine(0, y, width, y); g.drawLine(0, y + laser.height(), width, y + laser.height());
            for (int x = 6; x < width; x += 40) {
                g.drawLine(x, y + 4, x + 8, y + 12);
                g.drawLine(x, y + laser.height() - 4, x + 8, y + laser.height() - 12);
            }
            caption(g, "LASER LOCK / CHANGE LANE", 32, Math.max(188, y - 10));
        } else if (action instanceof BossAction.Volley volley) {
            int x = (int) (volley.targetX() - v.cameraX()), y = (int) volley.targetY() + 15;
            g.setColor(new Color(255, 102, 76, alpha));
            Graphics2D lanes = (Graphics2D) g.create();
            try {
                lanes.clipRect(0, 170, width, groundY - 170);
                lanes.setStroke(new BasicStroke(1.25f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                        10, new float[]{9, 9}, 0));
                for (int i = 0; i < volley.count(); i++) {
                    double angle = volley.angleFrom(v.coreX() + 10, v.coreY() + 92, i);
                    lanes.drawLine(cx + 10, cy + 92, (int) (cx + 10 + Math.cos(angle) * width * 2),
                            (int) (cy + 92 + Math.sin(angle) * width * 2));
                }
            } finally { lanes.dispose(); }
            g.drawOval(x - 22, y - 22, 44, 44);
            g.drawLine(x - 30, y, x - 16, y); g.drawLine(x + 16, y, x + 30, y);
            caption(g, "PACKET BARRAGE / MOVE", Math.max(24, Math.min(width - 300, x - 100)), Math.max(190, y - 35));
        } else if (action instanceof BossAction.Shockwave) {
            g.setColor(new Color(255, 110, 45, 50)); g.fillRect(0, groundY - 42, width, 42);
            g.setColor(new Color(255, 155, 65, alpha));
            for (int x = 0; x < width; x += 38) g.drawLine(x, groundY, x + 19, groundY - 24);
            caption(g, "GROUND PANIC / JUMP", 36, groundY - 52);
        }
    }

    private void drawTethers(Graphics2D g, Visual v, int cx, int cy) {
        for (var node : v.boss().nodes()) {
            int x = (int) (node.x() - v.cameraX()) + 27, y = (int) node.y() + 27;
            double endX = cx + 18, endY = cy + v.coreHeight() / 2.0;
            Path2D cable = new Path2D.Double(); cable.moveTo(x, y);
            cable.curveTo(x + 30, y, endX - 25, endY, endX, endY);
            g.setStroke(new BasicStroke(7, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(7, 10, 12, 190)); g.draw(cable);
            g.setStroke(new BasicStroke(node.alive() ? 2.4f : 1.5f));
            g.setColor(node.alive() ? new Color(235, 154, 49, 175) : new Color(83, 72, 56, 130));
            g.draw(cable);
            if (!node.alive()) {
                g.setColor(new Color(217, 161, 88));
                g.drawLine(x + 4, y - 5, x + 10, y - 11); g.drawLine(x + 7, y + 2, x + 14, y + 5);
                continue;
            }
            double t = (v.seconds() * 0.8 + node.id() * 0.31) % 1, u = 1 - t;
            double px = u*u*u*x + 3*u*u*t*(x+30) + 3*u*t*t*(endX-25) + t*t*t*endX;
            double py = u*u*u*y + 3*u*u*t*y + 3*u*t*t*endY + t*t*t*endY;
            g.setColor(CREAM); g.fillOval((int) px - 2, (int) py - 2, 4, 4);
        }
    }

    private void drawCore(Graphics2D target, IndustrialArt art, Visual v, int x, int y) {
        Graphics2D g = (Graphics2D) target.create();
        try {
            boolean dead = v.boss().phase() == BossPhase.DEFEATED;
            double collapse = dead ? Math.max(0, Math.min(1, v.defeatProgress())) : 0;
            g.translate(0, collapse * 20);
            opacity(g, (float) (1 - collapse * 0.7));
            int w = v.coreWidth(), h = v.coreHeight();
            // The reactor image is nearly square. Structural legs and rails complete the tall hurtbox.
            g.setPaint(new GradientPaint(x, y, new Color(89, 73, 49), x + w, y, new Color(18, 23, 27)));
            g.fillRoundRect(x + 12, y, w - 24, h, 14, 14);
            for (int edge : new int[]{x + 8, x + w - 22}) {
                g.setColor(new Color(20, 24, 28)); g.fillRoundRect(edge, y + 8, 16, h - 13, 5, 5);
                g.setColor(new Color(116, 96, 63)); g.drawLine(edge + 3, y + 10, edge + 3, y + h - 10);
                for (int rivet = y + 16; rivet < y + h - 10; rivet += 24) {
                    g.setColor(new Color(162, 132, 82)); g.fillOval(edge + 7, rivet, 3, 3);
                }
            }
            for (int i = 0; i < 3; i++) {
                g.setColor(new Color(14, 18, 22)); g.fillRect(x + 32, y + 12 + i * 9, w - 64, 5);
                g.setColor(dead ? new Color(96, 78, 55) : AMBER); g.fillRect(x + 35, y + 13 + i * 9, 4, 2);
            }
            double artW = w + 26;
            var frame = art.frame("hostiles", "legacy-core");
            double artH = frame.map(f -> artW * f.height() / f.width()).orElse(artW);
            double artY = y + (h - artH) * 0.53;
            if (!art.part(g, "hostiles", "legacy-core", x - 13, artY, artW, artH)) {
                g.setColor(new Color(36, 41, 45)); g.fillRoundRect(x + 18, y + 45, w - 36, h - 95, 16, 16);
                g.setColor(AMBER); g.fillOval(x + w / 2 - 27, y + h / 2 - 27, 54, 54);
            }
            int centerX = x + w / 2, centerY = (int) (artY + artH * 0.49);
            Color energy = v.boss().phase() == BossPhase.ENRAGED ? RED : AMBER;
            if (!dead) {
                int glow = v.flashes() ? 45 + (int) (Math.sin(v.seconds() * 5) * 14) : 45;
                g.setColor(new Color(energy.getRed(), energy.getGreen(), energy.getBlue(), glow));
                g.fillOval(centerX - 30, centerY - 30, 60, 60);
            }
            if (v.boss().phase() == BossPhase.DEPENDENCIES) {
                g.setColor(new Color(125, 210, 218, 120)); g.setStroke(new BasicStroke(2));
                g.drawRoundRect(x - 8, y - 5, w + 16, h + 9, 24, 24);
                for (int i = -1; i <= 1; i++) g.drawArc(centerX - 38, centerY - 38, 76, 76, i * 120 + 8, 72);
            } else if (!dead) {
                g.setColor(energy); g.setStroke(new BasicStroke(v.boss().phase() == BossPhase.ENRAGED ? 3 : 2));
                g.drawArc(centerX - 37, centerY - 37, 74, 74, 25, 120);
                g.drawArc(centerX - 37, centerY - 37, 74, 74, 205, 120);
            }
        } finally { g.dispose(); }
    }

    private void drawNodes(Graphics2D g, IndustrialArt art, Visual v, boolean showCombatHints) {
        for (var node : v.boss().nodes()) {
            int x = (int) (node.x() - v.cameraX()), y = (int) node.y();
            if (node.alive()) {
                g.setColor(new Color(252, 163, 48, 28)); g.fillOval(x - 8, y - 10, 70, 72);
            }
            Graphics2D body = (Graphics2D) g.create();
            try {
                if (!node.alive()) opacity(body, 0.28f);
                double h = 68;
                double w = art.frame("hostiles", "node").map(f -> h * f.width() / f.height()).orElse(48.0);
                if (!art.part(body, "hostiles", "node", x + (54 - w) / 2, y - 7, w, h)) {
                    body.setColor(node.alive() ? AMBER : new Color(48, 52, 60)); body.fillRoundRect(x, y, 54, 54, 12, 12);
                }
            } finally { body.dispose(); }
            if (!showCombatHints) continue;
            g.setFont(GameText.font(new Font(Font.SANS_SERIF, Font.BOLD, v.compact() ? 16 : 11)));
            g.setColor(node.alive() ? CREAM : new Color(148, 137, 116));
            String label = node.alive() ? "LINK " + (node.id() + 1) : "OFFLINE";
            if (node.id() == 2) {
                GameText.draw(g, label, x - g.getFontMetrics().stringWidth(GameText.text(label)) - 14, y + 49);
            } else {
                GameText.draw(g, label, x + 27 - g.getFontMetrics().stringWidth(GameText.text(label)) / 2, y + 77);
            }
            if (!node.alive()) continue;
            g.setColor(new Color(12, 17, 22)); g.fillRoundRect(x - 3, y - 14, 60, 5, 3, 3);
            g.setColor(AMBER); g.fillRoundRect(x - 3, y - 14, (int) (60.0 * node.hp() / node.maxHp()), 5, 3, 3);
            g.setColor(CREAM); g.setStroke(new BasicStroke(1.5f));
            int labelWidth = g.getFontMetrics().stringWidth(GameText.text("SHOOT"));
            GameText.draw(g, "SHOOT", x - labelWidth - 14, y + 29);
            g.drawLine(x - 10, y + 26, x - 2, y + 26);
        }
    }

    private void drawStatus(Graphics2D g, Visual v, int width) {
        long alive = v.boss().nodes().stream().filter(BossSnapshot.NodeView::alive).count();
        String action = switch (v.boss().phase()) {
            case DEPENDENCIES -> "BREAK LINKS " + (3 - alive) + "/3  /  CORE IMMUNE";
            case CORE_EXPOSED -> "CORE EXPOSED / ATTACK";
            case ENRAGED -> "CORE PANIC / FINISH IT";
            case DEFEATED -> "DEPENDENCIES CLEARED";
        };
        int w = v.compact() ? 700 : 560, x = (width - w) / 2, y = v.compact() ? 123 : 113;
        g.setColor(new Color(6, 11, 16, 235)); g.fillRoundRect(x, y, w, 39, 6, 6);
        g.setFont(GameText.font(new Font(Font.SANS_SERIF, Font.BOLD, v.compact() ? 17 : 12))); g.setColor(CREAM);
        GameText.draw(g, "LEGACY / " + action, x + 13, y + 21);
        g.setColor(new Color(101, 81, 52)); g.fillRect(x + 12, y + 29, w - 24, 4);
        g.setColor(v.boss().phase() == BossPhase.ENRAGED ? RED : AMBER);
        g.fillRect(x + 12, y + 29, (int) ((w - 24.0) * v.boss().coreHp() / v.boss().maxCoreHp()), 4);
    }

    private static void caption(Graphics2D g, String text, int x, int y) {
        int w = g.getFontMetrics().stringWidth(GameText.text(text));
        g.setColor(new Color(8, 12, 15, 220)); g.fillRoundRect(x - 7, y - g.getFontMetrics().getAscent() - 4, w + 14, g.getFontMetrics().getHeight() + 5, 4, 4);
        g.setColor(CREAM); GameText.draw(g, text, x, y);
    }

    private static void opacity(Graphics2D g, float amount) {
        if (g.getComposite() instanceof AlphaComposite composite) g.setComposite(composite.derive(composite.getAlpha() * amount));
    }
}
