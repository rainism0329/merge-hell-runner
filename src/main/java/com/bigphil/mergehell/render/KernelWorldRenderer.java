package com.bigphil.mergehell.render;

import com.bigphil.mergehell.engine.GameLoop;
import com.bigphil.mergehell.i18n.GameText;
import com.bigphil.mergehell.world.KernelCoreController;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;
import java.util.Locale;

/** A powered workshop. Conduits connect the real switches, tracks and raised refuge platforms. */
public final class KernelWorldRenderer {
    private static final Color INK = new Color(10, 20, 27), STEEL = new Color(99, 119, 127);
    private static final Color COPPER = new Color(214, 160, 100), POWER = new Color(148, 200, 222);
    private static final Color LIVE = new Color(252, 148, 85), SAFE = new Color(158, 201, 182);

    private static final int TILE_GROUND = 480;
    private static final BufferedImage WASH = texture(960, 600, g -> {
        g.setPaint(new GradientPaint(0, 0, new Color(27, 31, 43, 105), 0, TILE_GROUND, new Color(53, 54, 58, 45)));
        g.fillRect(0, 0, 960, 600);
    });
    private static final BufferedImage FAR_TILE = texture(390, TILE_GROUND, KernelWorldRenderer::farTile);
    private static final BufferedImage HALL_TILE = texture(660, TILE_GROUND, KernelWorldRenderer::hallTile);
    private static final BufferedImage ROTOR = texture(160, 160, g -> {
        g.setColor(new Color(89, 115, 126, 105));
        for (int blade = 0; blade < 8; blade++) {
            g.rotate(Math.PI / 4, 80, 80);
            g.fillPolygon(new int[]{81, 94, 137, 122}, new int[]{64, 21, 37, 78}, 4);
        }
    });

    /** Class initialization happens when GameRenderer creates this renderer, outside paint. */
    public KernelWorldRenderer() { }
    public static void preload() { }

    /** Bounded reusable textures preserve the two independent parallax speeds at any camera X. */
    public void drawBackground(Graphics2D target, int width, int height, int groundY, double camera,
                               double seconds, boolean danger, boolean highContrast) {
        Graphics2D g = (Graphics2D) target.create();
        try {
            g.drawImage(WASH, 0, 0, width, height, null);
            int first = (int) Math.floor(camera * .16 / 390) - 1;
            for (int i = first; i <= first + width / 390 + 2; i++) {
                int x = (int) (i * 390 - camera * .16);
                if (x + 390 >= 0 && x < width) g.drawImage(FAR_TILE, x, 0, 390, groundY, null);
            }
            first = (int) Math.floor(camera * .43 / 660) - 1;
            for (int i = first; i <= first + width / 660 + 2; i++) {
                int x = (int) (i * 660 - camera * .43);
                if (x + 660 < 0 || x >= width) continue;
                g.drawImage(HALL_TILE, x, 0, 660, groundY, null);
                Graphics2D rotor = (Graphics2D) g.create();
                try {
                    rotor.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    rotor.translate(x + 363, (TILE_GROUND - 107) * groundY / (double) TILE_GROUND);
                    rotor.scale(1, groundY / (double) TILE_GROUND);
                    rotor.rotate(seconds * .13 + Math.floorMod(i, 4));
                    if (highContrast && rotor.getComposite() instanceof AlphaComposite composite)
                        rotor.setComposite(composite.derive(composite.getAlpha() * (70f / 105f)));
                    rotor.drawImage(ROTOR, -80, -80, null);
                } finally { rotor.dispose(); }
                if (highContrast) {
                    g.setColor(new Color(11, 24, 32, 48));
                    g.fillRect(x + 279, (int) (156 * groundY / (double) TILE_GROUND), 100, 4);
                }
            }
            if (danger) { g.setColor(new Color(72, 23, 12, 20)); g.fillRect(0, 0, width, groundY); }
        } finally { g.dispose(); }
    }

    private static void farTile(Graphics2D g) {
        int x = 0, groundY = TILE_GROUND;
        // Rear transformer bays are subdued, solidly mounted behind the active route.
        g.setColor(new Color(14, 26, 37, 80)); g.fillRect(x + 10, 50, 310, groundY - 50);
        g.setColor(new Color(119, 144, 153, 48)); g.drawRect(x + 10, 50, 310, groundY - 50);
        for (int n = 0; n < 3; n++) {
            int bx = x + 38 + n * 88;
            g.setColor(new Color(18, 30, 42, 100)); g.fillRoundRect(bx, 148, 58, groundY - 148, 13, 13);
            g.setColor(new Color(109, 129, 139, 50));
            for (int k = 0; k < 10; k++) g.drawLine(bx + 5, 166 + k * 22, bx + 53, 166 + k * 22);
            g.setStroke(new BasicStroke(5)); g.drawLine(bx + 29, 100, bx + 29, 149);
            g.setStroke(new BasicStroke(1));
            for (int k = 0; k < 4; k++) g.drawOval(bx + 19, 109 + k * 9, 20, 5);
        }
    }

    private static void hallTile(Graphics2D g) {
        int x = 0, groundY = TILE_GROUND;
        // Load-bearing columns and a continuous cable bridge establish the hall's structure.
        g.setPaint(new GradientPaint(x, 0, new Color(75, 88, 96, 210), x + 24, 0, new Color(19, 31, 41, 220)));
        g.fillRect(x + 8, 0, 26, groundY); g.fillRect(x + 600, 0, 26, groundY);
        g.setColor(new Color(99, 113, 117, 155)); g.fillRect(x + 5, 178, 624, 7);
        g.setColor(new Color(42, 56, 64, 180)); g.fillRect(x + 5, 207, 624, 6);
        g.setStroke(new BasicStroke(2));
        for (int n = 0; n < 10; n++) {
            int bx = x + 20 + n * 59;
            g.drawLine(bx, 186, bx + 50, 207); g.drawLine(bx + 50, 186, bx, 207);
        }
        g.setColor(new Color(132, 102, 72, 105));
        g.drawLine(x + 12, 173, x + 621, 173); g.drawLine(x + 12, 169, x + 621, 169);

        int cx = x + 356, cy = groundY - 107;
        // Turbine housing, axle bearings, outlet duct and plinth form one connected machine.
        g.setColor(new Color(14, 27, 36, 205)); g.fillRoundRect(cx - 128, cy - 67, 206, 143, 22, 22);
        g.setPaint(new GradientPaint(cx - 110, cy - 63, new Color(75, 92, 97, 150), cx - 60, cy + 67, new Color(24, 41, 51, 180)));
        g.fillRoundRect(cx - 119, cy - 63, 151, 130, 17, 17);
        g.setColor(new Color(17, 31, 41, 225)); g.fillOval(cx - 75, cy - 82, 165, 165);
        g.setStroke(new BasicStroke(8)); g.setColor(new Color(106, 124, 125, 170)); g.drawOval(cx - 70, cy - 77, 155, 155);
        g.setStroke(new BasicStroke(2)); g.setColor(new Color(162, 166, 146, 90)); g.drawArc(cx - 75, cy - 82, 165, 165, 50, 170);
        g.setColor(new Color(24, 43, 55, 230)); g.fillOval(cx - 9, cy - 16, 32, 32);
        g.setColor(new Color(132, 153, 156, 155)); g.drawOval(cx - 8, cy - 15, 30, 30);
        g.setColor(new Color(37, 52, 59, 235)); g.fillRect(cx - 92, cy + 63, 23, 39); g.fillRect(cx + 45, cy + 68, 23, 34);
        g.setColor(new Color(89, 103, 105, 180)); g.fillRoundRect(cx - 125, groundY - 9, 232, 9, 3, 3);
        g.setStroke(new BasicStroke(23, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(34, 50, 60, 180)); g.draw(new Line2D.Double(cx + 85, cy + 9, x + 565, cy + 9));
        g.drawLine(x + 565, cy + 9, x + 565, groundY);
        g.setStroke(new BasicStroke(2)); g.setColor(new Color(118, 134, 129, 120));
        g.drawLine(cx + 89, cy, x + 552, cy); g.drawLine(x + 556, cy + 16, x + 556, groundY);

        g.setColor(new Color(17, 32, 42, 205)); g.fillRect(x + 65, groundY - 144, 98, 144);
        g.setColor(new Color(99, 119, 127, 130)); g.drawRect(x + 65, groundY - 144, 98, 144);
        g.setColor(new Color(121, 168, 185, 95)); g.fillRoundRect(x + 80, groundY - 126, 68, 31, 3, 3);
        g.setColor(new Color(21, 45, 57, 200));
        for (int line = 0; line < 3; line++) g.fillRect(x + 85, groundY - 119 + line * 7, 44 - line * 8, 2);
        g.setColor(new Color(108, 119, 117, 100));
        for (int vent = 0; vent < 7; vent++) g.fillRect(x + 78, groundY - 67 + vent * 7, 70, 2);
        g.setColor(new Color(208, 178, 122, 95)); g.fillRect(x + 279, 156, 100, 4);
    }

    private static BufferedImage texture(int width, int height, Consumer<Graphics2D> draw) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            draw.accept(g);
        } finally { g.dispose(); }
        return image;
    }

    /** World coordinates, called under the same camera transform as the actors and collision floor. */
    public void drawWorld(Graphics2D target, KernelCoreController.Snapshot state, double camera,
                          int width, boolean compact, boolean flashes, boolean highContrast) {
        Graphics2D g = (Graphics2D) target.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            for (var station : state.stations()) {
                var b = station.bounds(); var deck = station.safetyPlatform();
                if (deck.x + deck.width < camera - 70 || b.x() > camera + width + 70) continue;
                int cx = (int) b.centerX(), floor = (int) (b.y() + b.height());
                boolean armed = station.state() == KernelCoreController.NodeState.ARMED;
                Color accent = armed ? COPPER : station.state() == KernelCoreController.NodeState.DISABLED ? SAFE
                        : station.state() == KernelCoreController.NodeState.COOLDOWN ? STEEL : POWER;
                // The raised refuge is bolted to the floor on both ends. It never loses collision.
                g.setColor(INK); g.setStroke(new BasicStroke(9));
                g.drawLine((int) deck.x + 15, (int) deck.y + 12, (int) deck.x + 15, floor);
                g.drawLine((int) (deck.x + deck.width - 15), (int) deck.y + 12, (int) (deck.x + deck.width - 15), floor);
                g.setColor(STEEL); g.setStroke(new BasicStroke(2));
                g.drawLine((int) deck.x + 13, (int) deck.y + 12, (int) deck.x + 13, floor);
                g.drawLine((int) (deck.x + deck.width - 17), (int) deck.y + 12, (int) (deck.x + deck.width - 17), floor);
                g.drawLine((int) deck.x + 15, floor - 8, (int) (deck.x + deck.width - 15), (int) deck.y + 20);
                g.setColor(new Color(164, 189, 182, highContrast ? 210 : 120));
                g.drawLine((int) deck.x + 4, (int) deck.y + 1, (int) (deck.x + deck.width - 4), (int) deck.y + 1);
                g.setColor(new Color(44, 59, 65)); g.fillRect(cx, floor - 3, (int) (deck.x + deck.width - cx), 5);
                g.setColor(new Color(116, 103, 77)); g.drawLine(cx, floor, (int) (deck.x + deck.width), floor);

                g.setColor(INK); g.fillRoundRect(cx - 29, floor - 8, 58, 8, 4, 4);
                g.setPaint(new GradientPaint(cx - 22, (int) b.y(), new Color(125, 142, 138), cx + 22, floor, new Color(26, 44, 57)));
                g.fillRoundRect(cx - 22, (int) b.y(), 44, (int) b.height() - 5, 5, 5);
                g.setColor(new Color(193, 199, 176)); g.drawLine(cx - 18, (int) b.y() + 1, cx + 17, (int) b.y() + 1);
                g.setColor(INK); g.fillRoundRect(cx - 15, (int) b.y() + 10, 30, 21, 3, 3);
                g.setColor(accent); g.fillRoundRect(cx - 12, (int) b.y() + 13, 24, 4, 2, 2);
                g.setColor(new Color(116, 142, 149)); g.fillRect(cx - 12, (int) b.y() + 22, 12, 2);
                g.setStroke(new BasicStroke(3)); g.setColor(INK); g.drawLine(cx, floor - 28, cx + (armed ? 8 : -7), floor - 40);
                g.setStroke(new BasicStroke(2)); g.setColor(accent); g.drawLine(cx, floor - 29, cx + (armed ? 8 : -7), floor - 40);
                g.setColor(COPPER); g.fillRect(cx - 16, floor - 16, 32, 4);
                g.setColor(INK); for (int n = 0; n < 4; n++) g.fillRect(cx - 14 + n * 8, floor - 16, 3, 4);
                if (armed) {
                    // Arming physically exposes a fault contact on the box the boss must actually hit.
                    g.setColor(INK); g.fillRect(cx - 24, (int) b.y() + 32, 48, 8);
                    g.setColor(COPPER); g.fillRect(cx - 23, (int) b.y() + 34, 46, 3);
                    g.setStroke(new BasicStroke(1.4f)); g.draw(b.rectangle());
                }
                if (station.inReach() || armed || station.state() == KernelCoreController.NodeState.COOLDOWN) {
                    String key = switch (station.state()) {
                        case ARMED -> "kernel.armed";
                        case DISABLED -> "kernel.disabled";
                        case COOLDOWN -> "kernel.cooldown";
                        case READY -> station.bossNode() ? "kernel.arm" : "kernel.interact";
                    };
                    label(g, GameText.message(key, seconds(station.ticksRemaining())), cx, (int) b.y() - 18,
                            compact, camera, width, accent);
                }
            }
            for (var rail : state.rails()) {
                var b = rail.bounds();
                if (b.x() + b.width() < camera - 30 || b.x() > camera + width + 30) continue;
                boolean warning = rail.phase() == KernelCoreController.RailPhase.WARNING;
                boolean active = rail.phase() == KernelCoreController.RailPhase.ACTIVE;
                Color accent = active ? LIVE : warning ? COPPER : SAFE;
                // Decorative sleepers are below the hurtbox; every energized stroke is clipped inside it.
                g.setColor(new Color(16, 29, 36));
                g.fill(new Rectangle2D.Double(b.x() - 4, b.y() + b.height(), b.width() + 8, 7));
                g.setColor(new Color(108, 115, 105));
                for (int mark = 8; mark < b.width(); mark += 26)
                    g.fill(new Rectangle2D.Double(b.x() + mark, b.y() + b.height(), 9, 4));
                g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), active ? 74 : warning ? 24 : 12));
                g.fill(b.rectangle());
                g.setColor(accent);
                g.setStroke(warning ? new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{7, 5}, 0)
                        : new BasicStroke(active ? 2 : 1));
                g.draw(b.rectangle());
                Graphics2D current = (Graphics2D) g.create();
                try {
                    current.clip(b.rectangle()); current.setStroke(new BasicStroke(active ? 1.5f : 1));
                    if (active) {
                        int phase = flashes ? (int) (state.tick() % 8) : 0;
                        Path2D arc = new Path2D.Double(); arc.moveTo(b.x(), b.centerY());
                        for (int x = 5; x < b.width(); x += 9)
                            arc.lineTo(b.x() + x, b.centerY() + (((x + phase) / 9 % 2 == 0) ? -3 : 3));
                        current.draw(arc);
                    } else if (warning) {
                        for (int x = 8; x < b.width(); x += 32)
                            current.draw(new Line2D.Double(b.x() + x, b.y() + b.height() - 3, b.x() + x + 8, b.y() + 3));
                    } else {
                        current.setColor(new Color(121, 151, 146, 90));
                        current.draw(new Line2D.Double(b.x() + 3, b.centerY(), b.x() + b.width() - 3, b.centerY()));
                    }
                } finally { current.dispose(); }
                String key = active ? "kernel.active" : warning ? "kernel.warning" : "kernel.safe";
                label(g, GameText.message(key, seconds(rail.ticksRemaining())), (int) b.centerX(), (int) b.y() - 12,
                        compact, camera, width, accent);
            }
        } finally { g.dispose(); }
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
}
