package com.bigphil.mergehell.render;

import com.bigphil.mergehell.i18n.GameText;

import com.bigphil.mergehell.world.HeapDistrictController;
import com.bigphil.mergehell.assets.AssetCatalog;
import com.bigphil.mergehell.assets.AssetStore;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.Map;

/** Dedicated pipeworks district and exact interaction geometry. Draw methods never advance state. */
public final class HeapWorldRenderer {
    private static final int TILE_WIDTH = 1280;
    private static final Color INK = new Color(8, 17, 19);
    private static final Color AMBER = new Color(217, 161, 77);
    private static final Color CREAM = new Color(241, 223, 183);
    private static final Color CYAN = new Color(111, 229, 216);
    private static final Color ACID = new Color(196, 208, 86);
    private static final Color RED = new Color(239, 105, 62);
    private static final AssetStore BACKGROUND_ART = AssetStore.preload(new AssetCatalog(Map.of(
            "heap-background", "game/art/heap-background.properties")), HeapWorldRenderer.class.getClassLoader());
    private static final BufferedImage PANORAMA = panorama(false);
    private static final BufferedImage PANORAMA_MIRROR = panorama(true);
    private static final BufferedImage FAR = makeBackground(false);
    private static final BufferedImage NEAR = makeBackground(true);
    private static final BufferedImage STATION = makeStation();

    /** Constructed with GameRenderer, so image decoding and actor mip caches precede gameplay painting. */
    public HeapWorldRenderer() { HeapActorRenderer.preload(); }

    /** Screen coordinates: replaces the city backdrop for Heap, preserving the shared industrial deck. */
    public void drawBackground(Graphics2D target, int width, int height, int groundY, double cameraX,
                               double seconds, boolean danger, boolean highContrast) {
        Graphics2D g = (Graphics2D) target.create();
        try {
            quality(g);
            if (PANORAMA != null) {
                double travel = cameraX * .18;
                int index = (int) Math.floor(travel / PANORAMA.getWidth());
                double offset = travel - index * (double) PANORAMA.getWidth();
                for (int i = -1; i <= width / PANORAMA.getWidth() + 1; i++) {
                    BufferedImage tile = Math.floorMod(index + i, 2) == 0 ? PANORAMA : PANORAMA_MIRROR;
                    g.drawImage(tile, (int) Math.floor(i * PANORAMA.getWidth() - offset), groundY - 480, null);
                }
                g.setColor(new Color(8, 20, 21, 55)); g.fillRect(0, 0, width, height);
                g.setPaint(new GradientPaint(0, Math.max(0, groundY - 190), new Color(3, 13, 15, 10),
                        0, groundY, new Color(3, 13, 15, 110)));
                g.fillRect(0, Math.max(0, groundY - 190), width, 190);
                if (danger) { g.setColor(new Color(102, 31, 13, 22)); g.fillRect(0, 0, width, groundY); }
                if (highContrast) { g.setColor(new Color(2, 9, 12, 105)); g.fillRect(0, 0, width, height); }
                return;
            }
            g.setPaint(new GradientPaint(0, 0, new Color(10, 22, 23), 0, groundY, new Color(37, 44, 35)));
            g.fillRect(0, 0, width, height);
            tile(g, FAR, cameraX * .13, width, groundY - 480);
            g.setPaint(new GradientPaint(0, 100, new Color(17, 32, 30, 15), 0, groundY, new Color(49, 64, 50, 80)));
            g.fillRect(0, 80, width, Math.max(0, groundY - 80));
            tile(g, NEAR, cameraX * .34, width, groundY - 480);
            // Slow translucent exhaust is presentation-only and consumes the caller's paused clock.
            int first = (int) Math.floor(cameraX * .34 / 320) - 1;
            for (int i = first; i < first + width / 320 + 3; i++) {
                double x = i * 320 + 202 - cameraX * .34;
                for (int puff = 0; puff < 3; puff++) {
                    double rise = (seconds * 10 + puff * 23 + Math.floorMod(i, 3) * 7) % 70;
                    int alpha = (int) (19 * (1 - rise / 90));
                    g.setColor(new Color(156, 174, 143, alpha));
                    g.fill(new Ellipse2D.Double(x - rise * .13, groundY - 201 - rise,
                            29 + rise * .45, 13 + rise * .16));
                }
            }
            // Keep the action lane darker than the high, clearly articulated pipework.
            g.setPaint(new GradientPaint(0, groundY - 200, new Color(3, 11, 14, 8),
                    0, groundY, new Color(3, 11, 14, 145)));
            g.fillRect(0, Math.max(0, groundY - 200), width, 200);
            if (danger) { g.setColor(new Color(90, 29, 14, 31)); g.fillRect(0, 0, width, groundY); }
            if (highContrast) { g.setColor(new Color(2, 9, 12, 95)); g.fillRect(0, 0, width, height); }
        } finally { g.dispose(); }
    }

    /** World coordinates, before actors/projectiles. Bounds and phase come directly from the controller. */
    public void drawWorld(Graphics2D target, HeapDistrictController.Snapshot snapshot,
                          double cameraX, int width, boolean compact, boolean flashes, boolean highContrast) {
        Graphics2D g = (Graphics2D) target.create();
        try {
            quality(g);
            for (var pool : snapshot.pools()) if (visible(pool.bounds(), cameraX, width))
                drawPool(g, pool, snapshot.tick(), flashes, highContrast);
            for (var node : snapshot.nodes()) if (visible(node.bounds(), cameraX, width))
                drawNode(g, node, snapshot.tick(), compact, flashes, highContrast);
            for (var block : snapshot.blocks()) if (visible(block.bounds(), cameraX, width))
                drawBlock(g, block, snapshot.tick(), flashes);
        } finally { g.dispose(); }
    }

    private static void drawPool(Graphics2D target, HeapDistrictController.PoolView pool,
                                 long tick, boolean flashes, boolean highContrast) {
        var b = pool.bounds(); int x = (int) b.x(), y = (int) b.y(), w = b.width(), h = b.height();
        Graphics2D g = (Graphics2D) target.create();
        try {
            boolean warning = pool.phase() == HeapDistrictController.PoolPhase.WARNING;
            g.setColor(new Color(7, 14, 15, 210)); g.fillRect(x, y, w, h);
            if (warning) {
                g.setColor(new Color(239, 181, 77, 75)); g.fillRect(x, y, w, h);
                Graphics2D stripes = (Graphics2D) g.create();
                try {
                    stripes.clipRect(x, y, w, h); stripes.setColor(AMBER); stripes.setStroke(new BasicStroke(2));
                    for (int xx = x - h; xx < x + w; xx += 15) stripes.drawLine(xx, y + h, xx + h, y);
                } finally { stripes.dispose(); }
                g.setColor(AMBER); g.setStroke(new BasicStroke(1.5f)); g.drawRect(x, y, w, h);
                int center = x + w / 2;
                g.fillPolygon(new int[]{center, center - 7, center + 7}, new int[]{y - 18, y - 5, y - 5}, 3);
                g.setColor(INK); g.fillRect(center - 1, y - 14, 2, 5); g.fillRect(center - 1, y - 7, 2, 1);
            } else {
                g.setPaint(new GradientPaint(x, y, new Color(217, 221, 117, 230), x, y + h, new Color(99, 123, 47, 230)));
                g.fillRect(x, y, w, h);
                g.setColor(new Color(242, 246, 172, 185)); g.fillRect(x, y, w, 2);
                Graphics2D surface = (Graphics2D) g.create();
                try {
                    surface.clipRect(x, y, w, h);
                    for (int i = 0; i < w / 15; i++) {
                        double px = x + 6 + i * 15, py = y + 3 + Math.floorMod(tick + i * 7, Math.max(1, h - 3));
                        surface.setColor(new Color(62, 88, 34, 135)); surface.drawOval((int) px, (int) py, 5, 2);
                    }
                } finally { surface.dispose(); }
                g.setColor(new Color(255, 116, 55, highContrast ? 255 : 230));
                g.setStroke(new BasicStroke(highContrast ? 2.5f : 2)); g.drawRect(x, y, w, h);
                int alpha = flashes ? 100 + (int) (Math.sin(tick * .19) * 25) : 105;
                g.setColor(new Color(235, 137, 57, alpha)); g.drawLine(x, y - 3, x + w, y - 3);
            }
        } finally { g.dispose(); }
    }

    private static void drawNode(Graphics2D target, HeapDistrictController.NodeView node,
                                 long tick, boolean compact, boolean flashes, boolean highContrast) {
        var b = node.bounds(); double x = b.x(), y = b.y();
        Graphics2D g = (Graphics2D) target.create();
        try {
            g.translate(x, y); g.scale(b.width() / 48.0, b.height() / 76.0);
            g.drawImage(STATION, 0, 0, 48, 76, null);
            boolean ready = node.state() == HeapDistrictController.NodeState.READY;
            boolean channel = node.state() == HeapDistrictController.NodeState.CHANNELING;
            boolean used = node.state() == HeapDistrictController.NodeState.USED;
            Color lamp = ready || channel ? CYAN : used ? new Color(97, 112, 95) : AMBER;
            g.setColor(new Color(9, 25, 27)); g.fillRoundRect(10, 22, 28, 23, 4, 4);
            g.setColor(lamp); g.setStroke(new BasicStroke(1.5f));
            g.drawRoundRect(13, 25, 22, 17, 3, 3);
            g.drawLine(17, 33, 21, 37); g.drawLine(21, 37, 29, 29);
            if (node.bossStation()) { g.drawLine(16, 28, 16, 38); g.drawLine(31, 28, 31, 38); }
            if (used) { g.setColor(new Color(70, 78, 68)); g.fillRect(16, 32, 16, 3); }
            g.setColor(lamp); g.fillRect(11, 15, 8, 2); g.fillRect(29, 15, 8, 2);
            double rotation = channel ? tick * .19 : -.3;
            Graphics2D wheel = (Graphics2D) g.create();
            try {
                wheel.rotate(rotation, 36, 57); wheel.setColor(used ? new Color(96, 104, 84) : AMBER);
                wheel.setStroke(new BasicStroke(2)); wheel.drawOval(29, 50, 14, 14);
                wheel.drawLine(29, 57, 43, 57); wheel.drawLine(36, 50, 36, 64);
            } finally { wheel.dispose(); }
            if (channel) {
                double progress = Math.max(0, Math.min(1, 1 - node.ticksRemaining() / (double) HeapDistrictController.CHANNEL_TICKS));
                g.setColor(new Color(4, 22, 24)); g.fillRect(9, 68, 30, 3);
                g.setColor(CYAN); g.fillRect(9, 68, (int) (30 * progress), 3);
            }
            if ((ready && node.inReach()) || channel) {
                int a = flashes ? 80 + (int) (Math.sin(tick * .15) * 25) : 90;
                g.setColor(new Color(119, 234, 215, a)); g.setStroke(new BasicStroke(2));
                g.drawRoundRect(1, 3, 46, 72, 7, 7);
            } else if (highContrast) {
                g.setColor(new Color(231, 214, 168, 150)); g.drawRoundRect(1, 3, 46, 72, 7, 7);
            }
        } finally { g.dispose(); }
        String text = switch (node.state()) {
            case READY -> node.inReach() ? node.bossStation() ? "E  PURGE / EXPOSE" : "E  PURGE   F  SALVAGE" : "GC " + (node.bossStation() ? "BREAKER" : "STATION");
            case CHANNELING -> "PURGING " + Math.max(1, (int) Math.ceil(node.ticksRemaining() * .016)) + "s";
            case COOLDOWN -> "COOLING " + Math.max(1, (int) Math.ceil(node.ticksRemaining() * .016)) + "s";
            case USED -> "GC / USED";
        };
        int size = compact ? 17 : node.inReach() ? 13 : 11;
        label(target, text, x + b.width() / 2.0, y - 10, size,
                node.state() == HeapDistrictController.NodeState.USED ? new Color(139, 151, 130) : CREAM);
    }

    private static void drawBlock(Graphics2D target, HeapDistrictController.BlockView block, long tick, boolean flashes) {
        var b = block.bounds(); double x = b.x(), y = b.y(); int w = b.width(), h = b.height();
        Graphics2D g = (Graphics2D) target.create();
        try {
            g.translate(x, y);
            if (block.warningTicksRemaining() > 0) {
                g.setColor(new Color(211, 184, 93, 70)); g.fillRect(0, 0, w, h);
                g.setColor(AMBER); g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                        10, new float[]{4, 3}, 0)); g.drawRect(0, 0, w, h);
                g.setColor(CREAM); g.drawLine(w / 2, 5, w / 2, h - 8); g.fillRect(w / 2 - 1, h - 5, 2, 2);
                return;
            }
            // The packet keeps its exact hittable box; only its internal reel rotates.
            g.setColor(new Color(39, 47, 36)); g.fillRoundRect(0, 0, w, h, 5, 5);
            g.setPaint(new GradientPaint(0, 0, new Color(201, 167, 89), w, h, new Color(88, 93, 50)));
            g.fillRoundRect(2, 2, w - 4, h - 4, 4, 4);
            g.setColor(INK); g.fillRoundRect(5, 5, w - 10, h - 10, 3, 3);
            g.setColor(ACID); g.setStroke(new BasicStroke(2));
            int phase = (int) (tick * 5 % 360);
            g.drawArc(7, 7, w - 14, h - 14, phase, 230);
            g.setColor(CREAM); g.fillRect(w / 2 - 2, h / 2 - 2, 4, 4);
            g.setColor(flashes ? new Color(241, 205, 114, 130 + (int) (Math.sin(tick * .18) * 30)) : AMBER);
            g.setStroke(new BasicStroke(1)); g.drawRoundRect(0, 0, w, h, 5, 5);
            g.setColor(new Color(250, 221, 155));
            g.drawLine(-4, 3, -4, h - 3); g.drawLine(w + 4, 3, w + 4, h - 3);
        } finally { g.dispose(); }
    }

    private static BufferedImage makeBackground(boolean near) {
        BufferedImage image = new BufferedImage(TILE_WIDTH, 600, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = image.createGraphics();
        try {
            quality(g);
            if (!near) {
                for (int i = 0; i < 9; i++) {
                    int x = i * 155 - 60, y = 130 + i % 3 * 45, w = 118 + i % 2 * 17;
                    g.setPaint(new GradientPaint(x, 0, new Color(41, 57, 48), x + w, 0, new Color(17, 33, 31)));
                    g.fillRoundRect(x, y, w, 380, 37, 37);
                    g.setColor(new Color(103, 124, 93, 65)); g.drawOval(x, y, w, 31);
                    for (int yy = y + 47; yy < 480; yy += 55) { g.setColor(new Color(4, 18, 19, 170)); g.fillRect(x, yy, w, 5); }
                    g.setColor(new Color(167, 150, 85, 70)); g.fillRect(x + 28, y + 20, 4, 30);
                    pipe(g, x + w / 2.0, y, x + w / 2.0, 44 + i % 3 * 20, 12, new Color(65, 77, 58));
                }
                for (int yy : new int[]{58, 88}) pipe(g, 0, yy, TILE_WIDTH, yy, 17, new Color(70, 84, 64));
            } else {
                // Foreground pipework is still behind every player, enemy, hazard and interaction station.
                for (int i = 0; i < 4; i++) {
                    int x = i * 320 + 34;
                    g.setColor(new Color(7, 19, 20)); g.fillRect(x + 20, 115, 10, 365);
                    g.setColor(new Color(94, 97, 70, 100)); g.fillRect(x + 22, 118, 2, 362);
                    int top = 238 + i % 2 * 36;
                    g.setPaint(new GradientPaint(x + 72, 0, new Color(84, 89, 65), x + 225, 0, new Color(27, 44, 38)));
                    g.fillRoundRect(x + 72, top, 152, 263, 37, 37);
                    g.setColor(new Color(140, 130, 80, 135)); g.drawOval(x + 74, top + 2, 148, 30);
                    g.setColor(new Color(14, 29, 27)); g.fillRect(x + 73, top + 43, 150, 9);
                    g.setColor(new Color(105, 104, 71, 150)); g.fillRect(x + 74, top + 43, 148, 2);
                    g.setColor(new Color(16, 30, 28)); g.fillRoundRect(x + 131, top + 63, 35, 109, 7, 7);
                    g.setPaint(new GradientPaint(x + 135, top + 64, new Color(139, 151, 76, 150), x + 159, top + 172, new Color(67, 94, 60, 160)));
                    g.fillRoundRect(x + 135, top + 93, 27, 74, 5, 5);
                    g.setColor(new Color(162, 172, 112, 100)); g.fillRect(x + 138, top + 70, 3, 91);
                    pipe(g, x + 220, top + 58, x + 250, top + 58, 13, new Color(101, 104, 71));
                    pipe(g, x + 250, top + 58, x + 250, 175, 13, new Color(101, 104, 71));
                    pipe(g, x + 250, 175, x + 284, 175, 13, new Color(101, 104, 71));
                    g.setColor(new Color(163, 125, 61, 150)); g.fillRoundRect(x + 267, 168, 12, 14, 3, 3);
                    for (int boltY = top + 49; boltY < 478; boltY += 48) {
                        g.setColor(new Color(162, 151, 94, 100)); g.fillOval(x + 82, boltY, 3, 3); g.fillOval(x + 212, boltY, 3, 3);
                    }
                    // Catwalk above the combat lane, with depth and an exposed cable bundle.
                    g.setColor(new Color(15, 29, 29)); g.fillRect(x - 34, 190, 320, 12);
                    g.setColor(new Color(152, 127, 70, 130)); g.fillRect(x - 34, 190, 320, 2);
                    g.setStroke(new BasicStroke(2));
                    for (int rail = x - 30; rail < x + 286; rail += 48) g.drawLine(rail, 190, rail, 159);
                    g.drawLine(x - 34, 159, x + 286, 159);
                }
                pipe(g, 0, 108, TILE_WIDTH, 108, 27, new Color(112, 109, 73));
                pipe(g, 0, 138, TILE_WIDTH, 138, 12, new Color(90, 106, 81));
                for (int x = 45; x < TILE_WIDTH; x += 125) {
                    g.setColor(new Color(19, 33, 29)); g.fillRoundRect(x, 90, 12, 36, 3, 3);
                    g.setColor(new Color(186, 159, 92, 125)); g.fillRect(x + 1, 91, 2, 34);
                    g.setColor(new Color(149, 132, 77, 105)); g.fillOval(x + 4, 93, 4, 4); g.fillOval(x + 4, 117, 4, 4);
                }
            }
        } finally { g.dispose(); }
        return image;
    }

    private static BufferedImage panorama(boolean mirror) {
        var sprite = BACKGROUND_ART.find("heap-background").orElse(null);
        if (sprite == null || !sprite.definition().animations().containsKey("backdrop")) return null;
        var frame = sprite.definition().animations().get("backdrop").frames().get(0);
        BufferedImage image = new BufferedImage(1200, 600, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            quality(g);
            if (mirror) { g.translate(1200, 0); g.scale(-1, 1); }
            g.scale(1200.0 / frame.width(), 600.0 / frame.height());
            sprite.paintFrame(g, frame);
        } finally { g.dispose(); }
        return image;
    }

    private static BufferedImage makeStation() {
        BufferedImage image = new BufferedImage(144, 228, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = image.createGraphics();
        try {
            g.scale(3, 3); quality(g);
            g.setColor(INK); g.fillRoundRect(3, 3, 42, 72, 7, 7);
            g.setPaint(new GradientPaint(5, 0, new Color(176, 134, 70), 43, 0, new Color(71, 80, 60)));
            g.fillRoundRect(5, 5, 38, 66, 5, 5);
            g.setColor(new Color(225, 189, 117)); g.fillRect(7, 6, 32, 2);
            g.setColor(new Color(14, 30, 29)); g.fillRect(7, 46, 34, 21);
            g.setPaint(new GradientPaint(7, 0, new Color(141, 150, 119), 22, 0, new Color(30, 50, 46)));
            g.fillRoundRect(10, 48, 12, 18, 3, 3);
            g.setColor(new Color(32, 46, 35)); g.fillRect(9, 10, 30, 9);
            g.setColor(new Color(185, 174, 115)); g.fillRect(8, 70, 32, 3);
            g.setColor(INK); g.fillRect(0, 73, 48, 3);
            for (int x : new int[]{8, 37}) for (int y : new int[]{9, 43, 69}) {
                g.setColor(INK); g.fillOval(x - 1, y - 1, 3, 3);
                g.setColor(CREAM); g.fillRect(x, y, 1, 1);
            }
            g.setColor(new Color(59, 56, 41)); g.drawLine(12, 6, 17, 8); g.drawLine(31, 67, 36, 65);
        } finally { g.dispose(); }
        return image;
    }

    private static void pipe(Graphics2D target, double x1, double y1, double x2, double y2, float width, Color metal) {
        target.setStroke(new BasicStroke(width + 3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        target.setColor(new Color(8, 21, 21)); target.drawLine((int) x1, (int) y1, (int) x2, (int) y2);
        target.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        target.setColor(metal); target.drawLine((int) x1, (int) y1, (int) x2, (int) y2);
        target.setStroke(new BasicStroke(Math.max(1, width / 5), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        target.setColor(new Color(192, 184, 122, 82)); target.drawLine((int) x1, (int) (y1 - width * .25), (int) x2, (int) (y2 - width * .25));
    }
    private static void tile(Graphics2D g, BufferedImage tile, double camera, int width, int y) {
        int start = (int) Math.floor(camera / TILE_WIDTH);
        double offset = camera - start * (double) TILE_WIDTH;
        for (int i = -1; i <= width / TILE_WIDTH + 1; i++) g.drawImage(tile, (int) Math.floor(i * TILE_WIDTH - offset), y, null);
    }
    private static boolean visible(HeapDistrictController.Bounds b, double camera, int width) {
        return b.x() + b.width() >= camera - 160 && b.x() <= camera + width + 160;
    }
    private static void label(Graphics2D target, String text, double centerX, double bottom, int size, Color color) {
        Graphics2D g = (Graphics2D) target.create();
        try {
            g.setFont(GameText.font(new Font(Font.SANS_SERIF, Font.BOLD, size))); FontMetrics metrics = g.getFontMetrics();
            int w = metrics.stringWidth(GameText.text(text)), x = (int) (centerX - w / 2.0), y = (int) bottom;
            g.setColor(new Color(5, 16, 19, 225)); g.fillRoundRect(x - 6, y - metrics.getAscent() - 3, w + 12, metrics.getHeight() + 4, 5, 5);
            g.setColor(color); GameText.draw(g, text, x, y);
        } finally { g.dispose(); }
    }
    private static void quality(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    }
}
