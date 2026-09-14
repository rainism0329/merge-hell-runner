package com.bigphil.mergehell.render;

import com.bigphil.mergehell.engine.GameLoop;
import com.bigphil.mergehell.i18n.GameText;
import com.bigphil.mergehell.world.SingularityEdgeController;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.Locale;
import java.util.function.Consumer;

/** A containment hall: background machinery is inert; only controller fields carry hazard markings. */
public final class SingularityWorldRenderer {
    private static final Color INK = new Color(9, 19, 27), STEEL = new Color(104, 125, 132);
    private static final Color COPPER = new Color(219, 166, 112), TEAL = new Color(146, 205, 201);
    private static final Color SIGNAL = new Color(209, 160, 211), LIVE = new Color(250, 155, 99);
    private static final int GROUND = 480;
    // Fixed images, initialized before the first encounter. No camera-dependent cache or live gradients.
    private static final BufferedImage BASE = texture(960, 600, true, g -> {
        g.setPaint(new GradientPaint(0, 0, new Color(12, 21, 33), 0, 480, new Color(31, 44, 52)));
        g.fillRect(0, 0, 960, 600);
        g.setColor(new Color(111, 142, 151, 15));
        for (int y = 24; y < 480; y += 48) g.drawLine(0, y, 960, y);
    });
    private static final BufferedImage FAR = texture(620, GROUND, false, SingularityWorldRenderer::farHall);
    private static final BufferedImage HALL = texture(780, GROUND, false, SingularityWorldRenderer::nearHall);
    private static final BufferedImage ROTOR = texture(138, 138, false, g -> {
        g.setColor(new Color(117, 152, 153, 100)); g.setStroke(new BasicStroke(4));
        for (int n = 0; n < 3; n++) {
            g.drawArc(8, 8, 122, 122, n * 120, 68);
            g.rotate(Math.PI * 2 / 3, 69, 69);
            g.fillPolygon(new int[]{63, 73, 82, 66}, new int[]{9, 9, 29, 28}, 4);
        }
    });
    private static final BufferedImage ANCHOR = texture(58, 76, false, SingularityWorldRenderer::anchorShell);

    public SingularityWorldRenderer() { }
    public static void preload() { }

    /** Standalone opaque backdrop: the caller need not paint another city underneath. */
    public void drawBackground(Graphics2D target, int width, int height, int groundY, double camera,
                               double seconds, boolean danger, boolean highContrast) {
        Graphics2D g = (Graphics2D) target.create();
        try {
            g.drawImage(BASE, 0, 0, width, height, null);
            int first = (int) Math.floor(camera * .14 / 620) - 1;
            for (int i = first; i <= first + width / 620 + 2; i++) {
                int x = (int) (i * 620 - camera * .14);
                if (x + 620 >= 0 && x < width) g.drawImage(FAR, x, 0, 620, groundY, null);
            }
            first = (int) Math.floor(camera * .39 / 780) - 1;
            for (int i = first; i <= first + width / 780 + 2; i++) {
                int x = (int) (i * 780 - camera * .39);
                if (x + 780 < 0 || x >= width) continue;
                g.drawImage(HALL, x, 0, 780, groundY, null);
                Graphics2D rotor = (Graphics2D) g.create();
                try {
                    rotor.translate(x + 390, 291 * groundY / (double) GROUND);
                    rotor.scale(1, groundY / (double) GROUND);
                    rotor.rotate(seconds * .09 + Math.floorMod(i, 3));
                    rotor.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    if (highContrast) opacity(rotor, .55f);
                    rotor.drawImage(ROTOR, -69, -69, null);
                } finally { rotor.dispose(); }
            }
            if (danger) {
                // Alarm lamps stay on the building, away from the authored attack trajectories.
                g.setColor(new Color(186, 126, 102, 100));
                for (int x = 76; x < width; x += 260) g.fillRect(x, 170, 22, 3);
            }
        } finally { g.dispose(); }
    }

    private static void farHall(Graphics2D g) {
        g.setColor(new Color(6, 17, 27, 175)); g.fillRect(14, 47, 548, 433);
        g.setColor(new Color(83, 116, 130, 70)); g.drawRect(14, 47, 548, 433);
        for (int bay = 0; bay < 4; bay++) {
            int x = 38 + bay * 132;
            g.setPaint(new GradientPaint(x, 89, new Color(39, 66, 79, 150), x + 88, 350, new Color(11, 25, 38, 190)));
            g.fillRoundRect(x, 87, 92, 321, 30, 30);
            g.setColor(new Color(119, 153, 158, 65)); g.drawRoundRect(x, 87, 92, 321, 30, 30);
            g.setStroke(new BasicStroke(7)); g.setColor(new Color(33, 57, 69, 230));
            g.drawLine(x + 44, 408, x + 44, GROUND);
            g.setStroke(new BasicStroke(1));
            for (int ring = 0; ring < 8; ring++) {
                int y = 117 + ring * 36;
                g.setColor(new Color(108, 138, 146, 50)); g.drawArc(x + 4, y, 84, 24, 180, 180);
            }
            g.setColor(new Color(137, 180, 182, 43)); g.fillRect(x + 36, 142, 9, 156);
            g.setColor(new Color(186, 144, 176, 38)); g.fillRect(x + 53, 191, 4, 85);
        }
        g.setColor(new Color(51, 74, 86, 160)); g.fillRect(0, 54, 620, 6); g.fillRect(0, 451, 620, 12);
    }

    private static void nearHall(Graphics2D g) {
        // A suspended containment vessel is supported by a cradle and a continuous floor plinth.
        g.setPaint(new GradientPaint(0, 0, new Color(91, 107, 111, 225), 30, 0, new Color(22, 40, 51, 230)));
        g.fillRect(13, 0, 28, GROUND); g.fillRect(739, 0, 28, GROUND);
        g.setColor(new Color(106, 121, 121, 175)); g.fillRect(8, 173, 764, 8);
        g.setColor(new Color(32, 49, 60, 210)); g.fillRect(8, 202, 764, 7);
        g.setStroke(new BasicStroke(2));
        for (int i = 0; i < 12; i++) {
            int x = 23 + i * 61;
            g.drawLine(x, 182, x + 52, 202); g.drawLine(x + 52, 182, x, 202);
        }
        g.setColor(new Color(159, 122, 89, 130)); g.drawLine(22, 165, 755, 165);
        g.setColor(new Color(17, 30, 41, 245)); g.fillRoundRect(272, 207, 236, 177, 30, 30);
        g.setPaint(new GradientPaint(285, 225, new Color(103, 128, 132, 165), 497, 367, new Color(25, 43, 53, 235)));
        g.fillOval(291, 195, 198, 198);
        g.setStroke(new BasicStroke(10)); g.setColor(new Color(115, 133, 131, 185)); g.drawOval(299, 203, 182, 182);
        g.setStroke(new BasicStroke(3)); g.setColor(new Color(197, 172, 136, 125)); g.drawArc(299, 203, 182, 182, 43, 115);
        g.setColor(new Color(11, 27, 39, 240)); g.fillOval(319, 220, 142, 142);
        g.setPaint(new RadialGradientPaint(390, 291, 57, new float[]{0, .25f, 1},
                new Color[]{new Color(134, 176, 183, 115), new Color(56, 89, 110, 155), new Color(17, 33, 47, 220)}));
        g.fillOval(333, 234, 114, 114);
        g.setColor(new Color(181, 145, 187, 70)); g.fillOval(380, 281, 20, 20);
        g.setStroke(new BasicStroke(16)); g.setColor(new Color(23, 41, 52, 230));
        g.drawLine(304, 351, 287, 461); g.drawLine(476, 351, 493, 461);
        g.setStroke(new BasicStroke(3)); g.setColor(new Color(111, 132, 129, 170));
        g.drawLine(301, 352, 284, 459); g.drawLine(473, 352, 490, 459);
        g.setColor(new Color(80, 103, 109, 200)); g.fillRoundRect(263, 464, 254, 16, 4, 4);
        // Coolant trunks physically connect the vessel, utility cabinets and floor return.
        g.setStroke(new BasicStroke(15, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(41, 62, 71, 215));
        g.draw(new Line2D.Double(285, 319, 212, 319)); g.drawLine(212, 319, 212, 480);
        g.drawLine(491, 275, 569, 275); g.drawLine(569, 275, 569, 480);
        g.setStroke(new BasicStroke(2)); g.setColor(new Color(149, 132, 104, 160));
        g.drawLine(277, 312, 205, 312); g.drawLine(205, 312, 205, 478);
        for (int x : new int[]{78, 613}) {
            g.setColor(new Color(13, 30, 41, 230)); g.fillRect(x, 309, 81, 171);
            g.setColor(new Color(105, 125, 130, 145)); g.drawRect(x, 309, 81, 171);
            g.setColor(new Color(123, 168, 173, 120)); g.fillRoundRect(x + 11, 324, 58, 34, 3, 3);
            g.setColor(new Color(24, 48, 62, 230));
            for (int line = 0; line < 3; line++) g.fillRect(x + 18, 332 + line * 7, 38 - line * 6, 2);
            g.setColor(new Color(116, 134, 133, 105));
            for (int slot = 0; slot < 8; slot++) g.fillRect(x + 12, 393 + slot * 8, 57, 2);
        }
    }

    private static void anchorShell(Graphics2D g) {
        g.setColor(INK); g.fillRoundRect(0, 68, 58, 8, 4, 4);
        g.setPaint(new GradientPaint(7, 3, new Color(156, 167, 159), 49, 70, new Color(32, 53, 65)));
        g.fillPolygon(new int[]{7, 16, 42, 51, 51, 7}, new int[]{9, 3, 3, 9, 69, 69}, 6);
        g.setColor(new Color(222, 216, 183)); g.drawLine(16, 4, 41, 4);
        g.setColor(INK); g.fillRoundRect(15, 15, 28, 24, 4, 4);
        g.setColor(new Color(96, 119, 129)); g.drawRoundRect(15, 15, 28, 24, 4, 4);
        g.setColor(COPPER); g.fillRect(12, 58, 34, 5);
        g.setColor(new Color(24, 37, 44)); for (int i = 0; i < 5; i++) g.fillRect(13 + i * 7, 58, 3, 5);
        g.setColor(new Color(22, 36, 45)); g.fillRect(13, 46, 32, 5);
        g.setColor(new Color(185, 197, 190)); for (int x : new int[]{11, 45}) for (int y : new int[]{12, 66}) g.fillRect(x, y, 2, 2);
    }

    /** Drawn under the actor camera transform, using only authoritative controller geometry. */
    public void drawWorld(Graphics2D target, SingularityEdgeController.Snapshot state, double camera,
                          int width, boolean compact, boolean flashes, boolean highContrast) {
        Graphics2D g = (Graphics2D) target.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            for (var anchor : state.anchors()) {
                var b = anchor.bounds(); var deck = anchor.safetyPlatform();
                if (Math.max(deck.x + deck.width, b.x() + b.width()) < camera - 70 || Math.min(deck.x, b.x()) > camera + width + 70) continue;
                int cx = (int) b.centerX(), floor = (int) (b.y() + b.height());
                boolean armed = anchor.state() == SingularityEdgeController.AnchorState.ARMED;
                Color accent = armed ? SIGNAL : anchor.state() == SingularityEdgeController.AnchorState.STABILIZED ? TEAL
                        : anchor.state() == SingularityEdgeController.AnchorState.COOLDOWN ? STEEL : COPPER;
                // Safety platforms keep permanent struts; suppression never changes their collision.
                g.setStroke(new BasicStroke(9)); g.setColor(INK);
                for (int dx : new int[]{12, (int) deck.width - 12}) g.drawLine((int) deck.x + dx, (int) deck.y + 11, (int) deck.x + dx, floor);
                g.setStroke(new BasicStroke(2)); g.setColor(STEEL);
                for (int dx : new int[]{10, (int) deck.width - 14}) g.drawLine((int) deck.x + dx, (int) deck.y + 11, (int) deck.x + dx, floor);
                g.drawLine((int) deck.x + 12, floor - 7, (int) (deck.x + deck.width - 12), (int) deck.y + 17);
                g.setColor(new Color(181, 206, 193, highContrast ? 230 : 150)); g.drawLine((int) deck.x + 3, (int) deck.y + 1, (int) (deck.x + deck.width - 3), (int) deck.y + 1);
                g.setStroke(new BasicStroke(4)); g.setColor(new Color(40, 56, 64));
                g.drawLine(cx, floor - 2, (int) (deck.x + deck.width / 2), floor - 2);
                g.setStroke(new BasicStroke(1)); g.setColor(new Color(128, 122, 100));
                g.drawLine(cx, floor, (int) (deck.x + deck.width / 2), floor);
                // Suppression removes energy, never the floor sump, scanner steelwork or cable rail.
                if (anchor.fieldBounds() != null) hazardHousing(g, anchor.echo(), anchor.fieldBounds());
                g.drawImage(ANCHOR, cx - 29, (int) b.y() - 2, null);
                g.setColor(accent); g.setStroke(new BasicStroke(2));
                if (anchor.echo() == SingularityEdgeController.Echo.MEMORY) {
                    g.drawOval(cx - 6, (int) b.y() + 18, 12, 12); g.drawLine(cx - 8, (int) b.y() + 26, cx + 8, (int) b.y() + 26);
                } else if (anchor.echo() == SingularityEdgeController.Echo.BLUEPRINT) {
                    g.drawRect(cx - 6, (int) b.y() + 18, 12, 12); g.drawLine(cx, (int) b.y() + 16, cx, (int) b.y() + 33);
                } else {
                    Path2D bolt = new Path2D.Double(); bolt.moveTo(cx + 4, b.y() + 17); bolt.lineTo(cx - 3, b.y() + 25); bolt.lineTo(cx + 3, b.y() + 25); bolt.lineTo(cx - 4, b.y() + 33); g.draw(bolt);
                }
                if (armed) { g.setColor(SIGNAL); g.fillRect(cx - 15, floor - 22, 30, 3); }
                if (anchor.inReach() || anchor.state() != SingularityEdgeController.AnchorState.READY) {
                    String key = switch (anchor.state()) {
                        case ARMED -> "singularity.armed";
                        case STABILIZED -> "singularity.stabilized";
                        case COOLDOWN -> "singularity.cooldown";
                        case READY -> anchor.bossNode() ? "singularity.arm" : "singularity.interact";
                    };
                    label(g, GameText.message(key, seconds(anchor.ticksRemaining())), cx, (int) b.y() - 18, compact, camera, width, accent);
                }
            }
            for (var hazard : state.hazards()) {
                var b = hazard.bounds();
                if (b.x() + b.width() < camera - 40 || b.x() > camera + width + 40) continue;
                boolean warning = hazard.phase() == SingularityEdgeController.HazardPhase.WARNING;
                boolean active = hazard.phase() == SingularityEdgeController.HazardPhase.ACTIVE;
                Color accent = active ? LIVE : warning ? COPPER : TEAL;
                Graphics2D field = (Graphics2D) g.create();
                try {
                    field.clip(new Rectangle2D.Double(b.x(), b.y(), b.width(), b.height()));
                    field.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), active ? 64 : warning ? 25 : 9));
                    field.fill(new Rectangle2D.Double(b.x(), b.y(), b.width(), b.height()));
                    field.setColor(accent); field.setStroke(new BasicStroke(active ? 1.5f : 1));
                    if (hazard.echo() == SingularityEdgeController.Echo.MEMORY) {
                        // The memory echo fills a real floor sump. Its waves cannot leave the hurtbox.
                        int phase = flashes && active ? (int) (state.tick() % 12) : 0;
                        for (int n = 0; n < b.width(); n += 34) {
                            field.draw(new Arc2D.Double(b.x() + n - phase, b.y() + 4, 28, 9, 180, 180, Arc2D.OPEN));
                            if (active) field.draw(new Line2D.Double(b.x() + n + 4, b.y() + 13, b.x() + n + 20, b.y() + 13));
                        }
                    } else if (hazard.echo() == SingularityEdgeController.Echo.BLUEPRINT) {
                        for (int y = 8; y < b.height(); y += 21) field.draw(new Line2D.Double(b.x() + 4, b.y() + y, b.x() + b.width() - 4, b.y() + y));
                        if (active) {
                            double scanY = b.y() + (flashes ? state.tick() * 2 % Math.max(1, b.height()) : b.height() / 2.0);
                            field.setStroke(new BasicStroke(3)); field.draw(new Line2D.Double(b.x(), scanY, b.x() + b.width(), scanY));
                        }
                    } else if (active) {
                        int phase = flashes ? (int) (state.tick() % 8) : 0;
                        Path2D arc = new Path2D.Double(); arc.moveTo(b.x(), b.centerY());
                        for (int x = 5; x < b.width(); x += 9) arc.lineTo(b.x() + x, b.centerY() + (((x + phase) / 9 % 2 == 0) ? -3 : 3));
                        field.draw(arc);
                    } else {
                        for (int x = 8; x < b.width(); x += 30) field.draw(new Line2D.Double(b.x() + x, b.y() + b.height() - 3, b.x() + x + 8, b.y() + 3));
                    }
                } finally { field.dispose(); }
                g.setColor(accent);
                g.setStroke(warning ? new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{7, 5}, 0) : new BasicStroke(active ? 2 : 1));
                g.draw(new Rectangle2D.Double(b.x(), b.y(), b.width(), b.height()));
                String echo = GameText.message("singularity.echo." + hazard.echo().name().toLowerCase(Locale.ROOT));
                String phase = GameText.message(active ? "singularity.active" : warning ? "singularity.warning" : "singularity.safe", seconds(hazard.ticksRemaining()));
                label(g, echo + " · " + phase, (int) b.centerX(), (int) b.y() - 12, compact, camera, width, accent);
            }
        } finally { g.dispose(); }
    }

    private static void hazardHousing(Graphics2D g, SingularityEdgeController.Echo echo, SingularityEdgeController.Bounds b) {
        int floor = (int) (b.y() + b.height());
        g.setColor(INK); g.fill(new Rectangle2D.Double(b.x() - 4, floor, b.width() + 8, 6));
        g.setColor(STEEL); g.setStroke(new BasicStroke(1));
        if (echo == SingularityEdgeController.Echo.BLUEPRINT) {
            // Scanner head and ground return share a visible steel upright outside the attack area.
            g.setStroke(new BasicStroke(8)); g.setColor(INK); g.drawLine((int) b.x() - 9, floor, (int) b.x() - 9, (int) b.y() - 8);
            g.setStroke(new BasicStroke(2)); g.setColor(STEEL); g.drawLine((int) b.x() - 11, floor, (int) b.x() - 11, (int) b.y() - 8);
            g.setColor(new Color(53, 74, 84)); g.fillRect((int) b.x() - 13, (int) b.y() - 10, b.width() + 18, 8);
            g.setColor(new Color(152, 191, 193)); g.fillRect((int) b.x() + 3, (int) b.y() - 3, b.width() - 6, 2);
        } else {
            g.setColor(new Color(105, 116, 115));
            for (int x = 6; x < b.width(); x += 26) g.fill(new Rectangle2D.Double(b.x() + x, floor, 8, 3));
            if (echo == SingularityEdgeController.Echo.MEMORY) {
                g.setColor(new Color(108, 138, 143)); g.drawLine((int) b.x() - 4, floor + 3, (int) (b.x() + b.width() + 4), floor + 3);
            }
        }
    }

    private static String seconds(int ticks) {
        double tenths = Math.ceil(Math.max(0, ticks) * (double) GameLoop.LEGACY_STEP_NANOS / 100_000_000.0);
        return String.format(Locale.ROOT, "%.1f", tenths / 10);
    }
    private static void label(Graphics2D g, String text, int center, int y, boolean compact, double camera, int width, Color accent) {
        g.setFont(GameText.font(new Font(Font.SANS_SERIF, Font.BOLD, compact ? 17 : 12)));
        GameText.fitFont(g, text, width - 32, compact ? 15 : 11);
        int w = g.getFontMetrics().stringWidth(text) + 16;
        int x = (int) Math.max(camera + 12, Math.min(camera + width - w - 12, center - w / 2));
        g.setColor(new Color(7, 19, 27, 235)); g.fillRoundRect(x, y - 17, w, 25, 4, 4);
        g.setColor(accent); GameText.draw(g, text, x + 8, y);
    }
    private static BufferedImage texture(int width, int height, boolean opaque, Consumer<Graphics2D> draw) {
        BufferedImage image = new BufferedImage(width, height, opaque ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = image.createGraphics();
        try { g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); draw.accept(g); }
        finally { g.dispose(); }
        return image;
    }
    private static void opacity(Graphics2D g, float alpha) {
        if (g.getComposite() instanceof AlphaComposite composite) g.setComposite(composite.derive(composite.getAlpha() * alpha));
    }
}
