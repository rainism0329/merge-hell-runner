package com.bigphil.mergehell.render;

import com.bigphil.mergehell.i18n.GameText;
import com.bigphil.mergehell.world.BlueprintCitadelController;
import java.awt.*;
import java.awt.geom.*;

/** Industrial drafting hall with structural links from each switchbox to its walkway and scanner. */
public final class BlueprintWorldRenderer {
    private static final Color STEEL = new Color(88, 112, 120), CYAN = new Color(127, 208, 222);
    private static final Color AMBER = new Color(236, 175, 101), INK = new Color(10, 22, 29);

    public void drawBackground(Graphics2D target, int width, int height, int groundY, double camera,
                               double seconds, boolean danger, boolean highContrast) {
        Graphics2D g = (Graphics2D) target.create();
        try {
            g.setPaint(new GradientPaint(0, 0, new Color(13, 27, 36, 95), 0, groundY, new Color(41, 58, 63, 55)));
            g.fillRect(0, 0, width, height);
            // Distant storeys have solid piers and recessed windows, with restrained drafting light.
            int first = (int) Math.floor(camera * .18 / 310) - 1;
            for (int i = first; i <= first + width / 310 + 2; i++) {
                int x = (int) (i * 310 - camera * .18), top = 35 + Math.floorMod(i * 37, 80);
                g.setColor(new Color(15, 32, 40, 65)); g.fillRect(x, top, 235, groundY - top);
                g.setColor(new Color(70, 92, 100, 65)); g.drawRect(x, top, 235, groundY - top);
                for (int floor = top + 22; floor < groundY; floor += 52) {
                    g.setColor(new Color(9, 24, 33, 40)); g.fillRect(x + 14, floor, 206, 30);
                    for (int bay = 0; bay < 5; bay++) {
                        g.setColor(new Color(95, 139, 148, 28 + Math.floorMod(i * 13 + bay * 29 + floor, 28)));
                        g.fillRect(x + 23 + bay * 38, floor + 5, 18, 16);
                    }
                }
            }
            // Main hall modules: equipment cabinets, drafting tables and supported overhead gantries.
            first = (int) Math.floor(camera * .48 / 540) - 1;
            for (int i = first; i <= first + width / 540 + 2; i++) {
                int x = (int) (i * 540 - camera * .48);
                g.setPaint(new GradientPaint(x, 0, new Color(61, 77, 83), x + 30, 0, new Color(17, 31, 39)));
                g.fillRect(x + 12, 0, 28, groundY); g.fillRect(x + 426, 0, 24, groundY);
                g.setColor(new Color(74, 91, 90)); g.fillRect(x + 8, 245, 447, 8);
                g.setStroke(new BasicStroke(3));
                for (int n = 0; n < 7; n++) {
                    int bx = x + 22 + n * 60;
                    g.drawLine(bx, 254, bx + 54, 284); g.drawLine(bx + 54, 254, bx, 284);
                }
                g.setColor(new Color(22, 40, 49, 150)); g.fillRect(x + 86, 304, 250, 136);
                g.setColor(new Color(88, 112, 120, 115)); g.drawRect(x + 86, 304, 250, 136);
                for (int cabinet = 0; cabinet < 4; cabinet++) {
                    int bx = x + 94 + cabinet * 59;
                    g.setColor(new Color(10, 27, 37, 95)); g.fillRect(bx, 314, 48, 114);
                    g.setColor(new Color(72, 110, 122, 100)); g.drawRect(bx, 314, 48, 114);
                    g.setColor(CYAN); g.fillRect(bx + 6, 323, 18, 2);
                    g.setColor(new Color(82, 102, 103));
                    for (int vent = 0; vent < 5; vent++) g.fillRect(bx + 7, 378 + vent * 7, 32, 2);
                }
                g.setColor(new Color(8, 20, 27, 185)); g.fillRoundRect(x + 120, 124, 214, 91, 6, 6);
                g.setColor(new Color(89, 136, 151, 100)); g.drawRoundRect(x + 120, 124, 214, 91, 6, 6);
                g.setStroke(new BasicStroke(1));
                for (int n = 0; n < 3; n++) {
                    int bx = x + 140 + n * 61;
                    g.drawRect(bx, 143 + n * 4, 42, 45 - n * 7);
                    g.drawLine(bx + 21, 133, bx + 21, 205);
                    g.drawLine(bx - 8, 195, bx + 48, 195);
                }
                // Floor-mounted task lamp: its housing and beam share a visible post.
                int lx = x + 385;
                g.setColor(new Color(24, 39, 43)); g.fillRect(lx, 280, 7, 180);
                g.setColor(AMBER); g.fillRect(lx - 12, 277, 31, 4);
                g.setPaint(new GradientPaint(lx, 282, new Color(223, 181, 114, highContrast ? 12 : 25),
                        lx, groundY, new Color(223, 181, 114, 0)));
                g.fillPolygon(new int[]{lx - 12, lx + 19, lx + 90, lx - 60}, new int[]{281, 281, groundY, groundY}, 4);
            }
            if (danger) { g.setColor(new Color(40, 15, 8, 24)); g.fillRect(0, 0, width, groundY); }
        } finally { g.dispose(); }
    }

    public void drawWorld(Graphics2D target, BlueprintCitadelController.Snapshot state, double camera,
                          int width, boolean compact, boolean flashes, boolean highContrast) {
        Graphics2D g = (Graphics2D) target.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            for (var support : state.supports()) {
                var b = support.bounds(); var deck = support.linkedPlatform();
                if (deck.x + deck.width < camera - 80 || b.x() > camera + width + 80) continue;
                int cx = (int) b.centerX(), floor = (int) (b.y() + b.height());
                boolean online = support.hp() > 0;
                boolean collapsing = support.state() == BlueprintCitadelController.SupportState.COLLAPSING;
                boolean carries = online || collapsing;
                Color accent = collapsing ? AMBER : online ? CYAN : STEEL;
                if (carries) {
                    // Box -> steel upright -> diagonal transfer arm -> full deck girder.
                    g.setStroke(new BasicStroke(12, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_BEVEL));
                    g.setColor(new Color(15, 28, 36));
                    g.drawLine(cx, floor - 12, cx, (int) deck.y + 14);
                    g.drawLine(cx, floor - 30, (int) deck.x + 35, (int) deck.y + 12);
                    g.drawLine(cx, (int) deck.y + 14, (int) (deck.x + deck.width), (int) deck.y + 14);
                    g.setStroke(new BasicStroke(3)); g.setColor(STEEL);
                    g.drawLine(cx - 3, floor - 12, cx - 3, (int) deck.y + 10);
                    g.drawLine(cx, floor - 31, (int) deck.x + 35, (int) deck.y + 11);
                    g.drawLine(cx, (int) deck.y + 10, (int) (deck.x + deck.width), (int) deck.y + 10);
                    g.setStroke(new BasicStroke(1)); g.setColor(new Color(96, 143, 156));
                    for (int bay = 8; bay < deck.width - 8; bay += 28)
                        g.drawLine((int) deck.x + bay, (int) deck.y + 7, (int) deck.x + bay + 20, (int) deck.y + 21);
                }
                g.setColor(new Color(8, 19, 26)); g.fillRoundRect(cx - 31, floor - 10, 62, 10, 4, 4);
                g.setPaint(new GradientPaint(cx - 22, 0, new Color(94, 110, 113), cx + 22, 0, new Color(23, 42, 51)));
                g.fillRoundRect(cx - 22, (int) b.y(), 44, 72, 6, 6);
                g.setColor(INK); g.fillRoundRect(cx - 15, floor - 58, 30, 43, 5, 5);
                g.setColor(accent); g.drawRoundRect(cx - 15, floor - 58, 30, 43, 5, 5);
                g.setStroke(new BasicStroke(2));
                g.drawLine(cx - 7, floor - 47, cx + 7, floor - 28);
                g.drawLine(cx + 7, floor - 47, cx - 7, floor - 28);
                if (online) {
                    g.setColor(new Color(6, 19, 26)); g.fillRect(cx - 20, floor - 82, 40, 4);
                    g.setColor(accent); g.fillRect(cx - 20, floor - 82, 40 * support.hp() / support.maxHp(), 4);
                }
                if (carries) {
                    var scan = state.scans().stream().filter(s -> s.supportId() == support.id()).findFirst().orElse(null);
                    int sx = (int) (deck.x + deck.width - 16);
                    int sy = scan == null ? (int) deck.y - 14 : (int) scan.originY();
                    g.setColor(STEEL); g.setStroke(new BasicStroke(5)); g.drawLine(sx, (int) deck.y + 5, sx, sy);
                    g.setColor(INK); g.fillRoundRect(sx - 15, sy - 8, 26, 16, 5, 5);
                    g.setColor(scan == null ? CYAN : AMBER); g.fillRect(sx - 18, sy - 4, 5, 8);
                    if (support.inReach() || collapsing) {
                        g.setColor(accent); g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_BUTT,
                                BasicStroke.JOIN_MITER, 10, new float[]{7, 5}, 0));
                        g.drawRect((int) deck.x - 3, (int) deck.y - 3, deck.width + 6, deck.height + 7);
                    }
                }
                String key = collapsing ? "blueprint.collapse" : support.state() == BlueprintCitadelController.SupportState.OVERLOADING
                        ? "blueprint.overloading" : support.state() == BlueprintCitadelController.SupportState.COOLDOWN
                        ? "blueprint.rebuilding" : online ? "blueprint.interact" : "blueprint.disconnected";
                if (support.inReach() || collapsing) label(g, GameText.message(key), cx, (int) b.y() - 18, compact, camera, width);
            }
            for (var scan : state.scans()) {
                var b = scan.bounds(); boolean warning = scan.phase() == BlueprintCitadelController.ScanPhase.WARNING;
                g.setColor(new Color(246, 163, 92, warning ? 24 : 76)); g.fill(b.rectangle());
                g.setColor(warning ? AMBER : new Color(255, 207, 145));
                g.setStroke(warning ? new BasicStroke(1.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{8, 5}, 0)
                        : new BasicStroke(2.2f));
                g.draw(b.rectangle());
                if (!warning) g.draw(new Line2D.Double(b.x(), b.centerY(), b.x() + b.width(), b.centerY()));
                if (warning) for (int mark = 8; mark < b.width(); mark += 32)
                    g.draw(new Line2D.Double(b.x() + mark, b.y() + b.height() - 3, b.x() + mark + 9, b.y() + 3));
            }
        } finally { g.dispose(); }
    }

    private static void label(Graphics2D g, String text, int center, int y, boolean compact, double camera, int width) {
        g.setFont(GameText.font(new Font(Font.SANS_SERIF, Font.BOLD, compact ? 17 : 12)));
        int w = g.getFontMetrics().stringWidth(text) + 16;
        int x = (int) Math.max(camera + 12, Math.min(camera + width - w - 12, center - w / 2));
        g.setColor(new Color(7, 20, 28, 232)); g.fillRoundRect(x, y - 17, w, 25, 4, 4);
        g.setColor(AMBER); GameText.draw(g, text, x + 8, y);
    }
}
