package com.bigphil.mergehell.render;

import com.bigphil.mergehell.i18n.GameText;
import com.bigphil.mergehell.world.TraversalEnvironment;
import java.awt.*;
import java.awt.geom.*;

/** Layered industrial scenery and water feedback; all motion uses the paused simulation clock. */
public final class TraversalRenderer {
    private static final Color AMBER = new Color(224, 171, 90);
    private static final Color WATER = new Color(106, 213, 221);

    /** Screen space, behind the combat lane. No texture allocations or wall-clock animation. */
    public void backdrop(Graphics2D target, int width, int groundY, double cameraX,
                         double seconds, int level, boolean highContrast) {
        Graphics2D g = (Graphics2D) target.create();
        try {
            g.clipRect(0, 0, width, groundY);
            int first = (int) Math.floor(cameraX * .32 / 690) - 1;
            for (int i = first; i <= first + width / 690 + 2; i++) {
                double x = i * 690 - cameraX * .32;
                // An upper maintenance gantry, braced towers and hanging cable runs.
                g.setColor(new Color(6, 14, 18, 160)); g.fill(new Rectangle2D.Double(x, 198, 530, 17));
                g.fill(new Rectangle2D.Double(x + 26, 65, 18, 280));
                g.setColor(new Color(128, 122, 91, highContrast ? 30 : 70));
                g.setStroke(new BasicStroke(2));
                g.draw(new Line2D.Double(x + 26, 65, x + 44, 345));
                for (int bay = 0; bay < 8; bay++) {
                    double bx = x + bay * 66;
                    g.draw(new Line2D.Double(bx, 178, bx, 198));
                    g.draw(new Line2D.Double(bx, 216, bx + 62, 238));
                    g.draw(new Line2D.Double(bx, 238, bx + 62, 216));
                }
                g.draw(new Line2D.Double(x, 178, x + 530, 178));
                g.setColor(new Color(4, 12, 17, 175)); g.setStroke(new BasicStroke(4));
                g.draw(new CubicCurve2D.Double(x + 310, 8, x + 300, 140, x + 400, 160, x + 504, 200));
                g.setStroke(new BasicStroke(1)); g.setColor(new Color(151, 121, 71, 100));
                g.draw(new CubicCurve2D.Double(x + 309, 8, x + 299, 140, x + 399, 160, x + 503, 200));
                // Slow work light sweeps are purely scenery, never an attack telegraph.
                double lampX = x + 465, reach = 110 + Math.sin(seconds * .22 + i) * 45;
                Path2D beam = new Path2D.Double(); beam.moveTo(lampX, 215);
                beam.lineTo(lampX + reach + 100, groundY); beam.lineTo(lampX + reach - 70, groundY); beam.closePath();
                g.setPaint(new GradientPaint((float) lampX, 215, new Color(206, 178, 114, highContrast ? 8 : 27),
                        (float) (lampX + reach), groundY, new Color(148, 185, 175, 0)));
                g.fill(beam);
                g.setColor(new Color(11, 17, 21)); g.fill(new RoundRectangle2D.Double(lampX - 12, 206, 25, 12, 4, 4));
                g.setColor(new Color(228, 197, 128, 155)); g.fill(new Rectangle2D.Double(lampX - 7, 215, 15, 3));
            }
            // Thin drifting haze stays behind actors and has no gameplay collision.
            for (int i = 0; i < 4; i++) {
                double x = Math.floorMod((int) (i * 311 - cameraX * .13 + seconds * 8), width + 380) - 190;
                g.setPaint(new GradientPaint(0, groundY - 112, new Color(136, 173, 162, 0),
                        0, groundY - 34, new Color(136, 173, 162, highContrast ? 7 : 16)));
                g.fill(new Ellipse2D.Double(x, groundY - 130 - i * 5, 340, 120));
            }
        } finally { g.dispose(); }
    }

    /** World space, before actors. Thin puddles sit on the deck; its load-bearing face stays visible. */
    public void ground(Graphics2D target, TraversalEnvironment.Snapshot scene,
                       double cameraX, int width, int groundY, int level, double seconds) {
        Graphics2D g = (Graphics2D) target.create();
        try {
            quality(g);
            for (var platform : scene.platforms()) {
                if (!visible(platform.x, platform.width, cameraX, width)) continue;
                int x = (int) platform.x, y = (int) platform.y;
                int depth = groundY - y;
                g.setPaint(new GradientPaint(x, y, new Color(54, 63, 65), x + platform.width, groundY, new Color(11, 24, 29)));
                g.fillRect(x, y + 10, platform.width, Math.max(1, depth - 10));
                g.setColor(new Color(112, 109, 86)); g.drawRect(x + 4, y + 13, platform.width - 8, Math.max(1, depth - 17));
                g.setColor(new Color(5, 15, 21)); g.setStroke(new BasicStroke(4));
                g.drawLine(x + 8, y + 17, x + platform.width - 10, groundY - 5);
                g.setStroke(new BasicStroke(1)); g.setColor(new Color(85, 103, 105));
                g.drawLine(x + 8, y + 16, x + platform.width - 10, groundY - 6);
                if (depth >= 70) {
                    // Open lower passage and rail silhouette make the vertical route readable.
                    g.setColor(new Color(5, 17, 23)); g.fillRoundRect(x + 18, y + 26, platform.width - 36, depth - 26, 15, 15);
                    g.setColor(new Color(115, 155, 156, 100)); g.drawArc(x + 18, y + 24, platform.width - 36, 36, 0, 180);
                    g.setColor(new Color(142, 126, 91));
                    for (int rail = 8; rail < platform.width; rail += 34) g.drawLine(x + rail, y - 20, x + rail, y);
                    g.drawLine(x + 8, y - 20, x + platform.width - 8, y - 20);
                }
            }
            for (var water : scene.water()) {
                if (!visible(water.x(), water.width(), cameraX, width)) continue;
                drawPuddle(g, water, seconds);
            }
            for (var prop : scene.props()) {
                if (visible(prop.x(), prop.width(), cameraX, width)) prop(g, prop, seconds);
            }
        } finally { g.dispose(); }
    }

    private static double puddleDepth(TraversalEnvironment.WaterView water) {
        return Math.max(8, Math.min(12, water.depth() * .44));
    }

    /** Broad, uneven wet edges taper to the dry floor without a rim or a disconnected outlet. */
    private static Shape puddleShape(TraversalEnvironment.WaterView water, double margin) {
        double x = water.x() - margin, w = water.width() + margin * 2;
        double y = water.surfaceY() - 2, d = puddleDepth(water) + margin * .04;
        double offset = Math.floorMod(water.id() * 13, 7) / 7.0;
        Path2D shape = new Path2D.Double();
        shape.moveTo(x, y + .8);
        shape.curveTo(x + w * .08, y - 1.1, x + w * .16, y + 1, x + w * .31, y - .3);
        shape.curveTo(x + w * .5, y - 1.8, x + w * .74, y + .7, x + w, y + .5);
        shape.curveTo(x + w * .96, y + d * .55, x + w * .87, y + d * .45, x + w * .77, y + d * .8);
        shape.curveTo(x + w * (.62 + offset * .04), y + d * 1.15, x + w * .51, y + d * .6, x + w * .4, y + d);
        shape.curveTo(x + w * .2, y + d * .9, x + w * .09, y + d * .4, x, y + .8);
        shape.closePath();
        return shape;
    }

    private static void drawPuddle(Graphics2D target, TraversalEnvironment.WaterView water, double seconds) {
        Graphics2D g = (Graphics2D) target.create();
        try {
            double x = water.x(), w = water.width(), y = water.surfaceY() - 2, d = puddleDepth(water);
            g.setColor(new Color(9, 21, 23, 72)); g.fill(puddleShape(water, 12));
            Shape pool = puddleShape(water, 0);
            g.setPaint(new GradientPaint(0, (float) y, new Color(54, 84, 82, 235),
                    0, (float) (y + d), new Color(17, 34, 36, 175)));
            g.fill(pool); g.clip(pool);
            // Reflections are sparse horizontal fragments, not luminous vertical bars.
            for (int i = 0; i < 9; i++) {
                double phase = seconds * .65 + i * 2.4 + Math.floorMod(water.id(), 23);
                double sx = x + w * (.06 + i * .105) + Math.sin(phase) * 1.2;
                double sy = y + 1.5 + Math.floorMod(i * 7, 5);
                g.setColor(i % 3 == 0 ? new Color(199, 166, 107, 105) : new Color(149, 181, 177, 110));
                g.setStroke(new BasicStroke(i % 3 == 0 ? .8f : .6f));
                g.draw(new Line2D.Double(sx, sy, sx + 9 + Math.floorMod(i * 17, 23), sy + .25 * Math.sin(phase)));
            }
            g.setColor(new Color(173, 192, 181, 82)); g.setStroke(new BasicStroke(.7f));
            g.draw(new Line2D.Double(x + w * .12, y + .5, x + w * .34, y + .2));
            g.draw(new Line2D.Double(x + w * .62, y + .4, x + w * .9, y + .6));
        } finally { g.dispose(); }
    }

    private void prop(Graphics2D g, TraversalEnvironment.PropView prop, double seconds) {
        int x = (int) prop.x(), y = (int) prop.y(), w = prop.width(), h = prop.height();
        boolean capacitor = prop.kind() == TraversalEnvironment.PropKind.CAPACITOR;
        Color accent = capacitor ? WATER : AMBER;
        g.setColor(new Color(2, 9, 12, 170)); g.fillOval(x - 7, y + h - 4, w + 14, 11);
        g.setPaint(new GradientPaint(x, y, new Color(119, 132, 120), x + w, y, new Color(22, 40, 45)));
        g.fillRoundRect(x, y, w, h, capacitor ? 13 : 4, capacitor ? 8 : 4);
        g.setColor(new Color(4, 16, 22)); g.fillRoundRect(x + 5, y + 7, w - 10, h - 14, 4, 4);
        g.setColor(accent); g.drawRoundRect(x + 5, y + 7, w - 10, h - 14, 4, 4);
        if (capacitor) {
            int cx = x + w / 2;
            g.fillPolygon(new int[]{cx + 2, cx - 6, cx, cx - 2, cx + 7, cx + 1},
                    new int[]{y + 10, y + 24, y + 24, y + 34, y + 20, y + 20}, 6);
            g.setColor(new Color(120, 214, 221, 60 + (int) (Math.sin(seconds * 2) * 15)));
            g.fillOval(x - 6, y + 6, w + 12, h - 2);
        } else {
            g.fillRect(x + w / 2 - 3, y + 11, 6, 14); g.fillRect(x + w / 2 - 7, y + 15, 14, 6);
        }
        g.setColor(new Color(153, 158, 133)); g.fillRect(x + 3, y + 3, w - 6, 3);
        g.fillRect(x + 3, y + h - 6, w - 6, 3);
        g.setColor(new Color(226, 187, 103));
        for (int mark = 3; mark < w - 5; mark += 8) g.drawLine(x + mark, y + h - 2, x + mark + 4, y + h - 5);
    }

    /** World space, after actors: water reflection, contact rings and droplets can overlap the boots. */
    public void foreground(Graphics2D target, TraversalEnvironment.Snapshot scene,
                           IndustrialArt art, ActorVisuals.Hero hero, double cameraX, int width,
                           boolean compact, int particlePercent) {
        Graphics2D g = (Graphics2D) target.create();
        try {
            quality(g);
            if (hero != null && art.has("repair")) for (var pool : scene.water()) {
                if (hero.x() < pool.x() - 30 || hero.x() > pool.x() + pool.width() + 30) continue;
                Graphics2D reflection = (Graphics2D) g.create();
                try {
                    reflection.clip(puddleShape(pool, 0));
                    reflection.translate(0, pool.surfaceY() * 1.34); reflection.scale(1, -.34);
                    reflection.setComposite(AlphaComposite.SrcOver.derive(.15f));
                    ActorVisuals.hero(reflection, art, hero);
                } finally { reflection.dispose(); }
            }
            for (var ring : scene.ripples()) {
                g.setColor(new Color(.65f, .79f, .78f, Math.min(1, ring.alpha() * .72f)));
                g.setStroke(new BasicStroke(ring.strength() > 1.5 ? 1.2f : .8f));
                g.draw(new Ellipse2D.Double(ring.x() - ring.radius(), ring.y() - ring.radius() * .17,
                        ring.radius() * 2, ring.radius() * .34));
            }
            for (int i = 0; i < scene.droplets().size(); i++) {
                if ((i * 37) % 100 >= particlePercent) continue;
                var drop = scene.droplets().get(i);
                g.setColor(new Color(.7f, .84f, .84f, Math.min(1, drop.alpha() * .8f)));
                g.fill(new Ellipse2D.Double(drop.x() - drop.radius() * .7, drop.y() - drop.radius(), drop.radius() * 1.4, drop.radius() * 1.9));
            }
            if (hero != null && !scene.bossArena()) {
                boolean shown = false;
                for (var prop : scene.props()) {
                    if (Math.abs(prop.centerX() - hero.x()) > 165) continue;
                    String label = GameText.message(prop.kind() == TraversalEnvironment.PropKind.CAPACITOR
                            ? "environment.capacitor" : "environment.supply");
                    hint(g, label + " · " + GameText.message("environment.shoot"),
                            prop.centerX(), prop.y() - 15, compact, cameraX, width);
                    shown = true; break;
                }
                if (!shown && !scene.water().isEmpty()) for (var platform : scene.platforms()) {
                    int floor = scene.water().get(0).surfaceY();
                    if (floor - platform.y != 24 || Math.abs(hero.x() - platform.x) > 70) continue;
                    hint(g, GameText.message("environment.route"), platform.x + platform.width,
                            floor - 125, compact, cameraX, width);
                    break;
                }
            }
        } finally { g.dispose(); }
    }

    private static void hint(Graphics2D g, String text, double center, double y, boolean compact, double camera, int width) {
        g.setFont(GameText.font(new Font(Font.SANS_SERIF, Font.BOLD, compact ? 17 : 12)));
        int w = g.getFontMetrics().stringWidth(text) + 16;
        int x = (int) Math.max(camera + 10, Math.min(camera + width - w - 10, center - w / 2.0));
        g.setColor(new Color(6, 17, 22, 220)); g.fillRoundRect(x, (int) y - 18, w, 26, 5, 5);
        g.setColor(AMBER); GameText.draw(g, text, x + 8, (int) y);
    }
    private static boolean visible(double x, int w, double camera, int width) { return x + w >= camera - 50 && x <= camera + width + 50; }
    private static void quality(Graphics2D g) { g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); }
}
