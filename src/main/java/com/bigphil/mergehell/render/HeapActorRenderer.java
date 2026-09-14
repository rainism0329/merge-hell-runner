package com.bigphil.mergehell.render;

import com.bigphil.mergehell.model.EntityType;
import com.bigphil.mergehell.assets.AnimationClip;
import com.bigphil.mergehell.assets.AssetCatalog;
import com.bigphil.mergehell.assets.AssetStore;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Map;

/** Heap machines. Cached materials are static; all motion consumes simulation presentation values. */
public final class HeapActorRenderer {
    public record BossVisual(double x, double y, int width, int height, int hp, int maxHp,
                             int stage, int hitTicks, int vulnerableTicks, double seconds,
                             boolean flashes, boolean highContrast) { }

    private static final Color INK = new Color(10, 17, 19);
    private static final Color STEEL = new Color(109, 121, 112);
    private static final Color CREAM = new Color(230, 213, 166);
    private static final Color RUST = new Color(157, 96, 45);
    private static final Color ACID = new Color(187, 211, 84);
    private static final Color RED = new Color(248, 95, 57);
    private static final AssetStore MATERIALS = AssetStore.preload(new AssetCatalog(Map.of(
            "heap-actors", "game/art/heap-actors.properties")), HeapActorRenderer.class.getClassLoader());
    private static final BufferedImage LEAK = cache(44, 44, HeapActorRenderer::leakShell);
    private static final BufferedImage BOSS = cache(120, 150, HeapActorRenderer::bossShell);

    private HeapActorRenderer() { }

    /** Explicit eager-load hook called by HeapWorldRenderer construction, before gameplay painting. */
    public static void preload() { /* Class initialization owns image decoding and mip preparation. */ }

    /** Returns false for other actors, allowing the existing actor renderer to keep its normal path. */
    public static boolean renderLeak(Graphics2D target, ActorVisuals.Hostile p) {
        if (p.type() != EntityType.LEAK) return false;
        if (frame("leak-body") != null) {
            texturedLeak(target, p);
            leakStatus(target, p);
            return true;
        }
        Graphics2D g = (Graphics2D) target.create();
        try {
            quality(g); g.translate(p.x() + p.width() / 2, p.y() + p.height() / 2);
            g.scale(-p.facing() * p.width() / 44, p.height() / 44);
            if (p.death() > 0) {
                opacity(g, (float) Math.max(0, 1 - p.death()));
                g.translate(p.death() * 7, p.death() * 5); g.rotate(p.death() * .8);
            } else g.rotate(Math.sin(p.phase()) * .035);
            g.translate(-22, -22);
            // Suspension hoses move inside the actual airborne body; there is no fake ground shadow.
            for (int i = 0; i < 3; i++) {
                double x = 13 + i * 9, sway = Math.sin(p.phase() + i * 1.7) * 2;
                Path2D hose = new Path2D.Double(); hose.moveTo(x, 28);
                hose.curveTo(x - 3, 35, x + sway + 3, 36, x + sway, 42);
                g.setColor(INK); g.setStroke(new BasicStroke(4, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g.draw(hose);
                g.setColor(new Color(125, 107, 64)); g.setStroke(new BasicStroke(1.2f)); g.draw(hose);
                g.setColor(ACID); g.fill(new Ellipse2D.Double(x + sway - 1.4, 39, 2.8, 3.2));
            }
            g.drawImage(LEAK, 0, 0, 44, 44, null);
            g.setColor(new Color(14, 36, 35)); g.fillRoundRect(24, 12, 9, 17, 3, 3);
            int fill = 9 + (int) (Math.sin(p.phase() * .6) * 2);
            g.setPaint(new GradientPaint(24, 15, new Color(213, 227, 125), 33, 28, new Color(73, 117, 57)));
            g.fillRoundRect(25, 28 - fill, 7, fill, 2, 2);
            g.setColor(new Color(235, 248, 188, 170)); g.drawLine(26, 14, 26, 25);
            g.setColor(new Color(255, 82, 57)); g.fillRoundRect(5, 15, 11, 4, 2, 2);
            g.setColor(CREAM); g.fillRect(6, 15, 5, 1);
            if (p.hit() > 0) {
                g.setColor(new Color(255, 236, 196, (int) (210 * Math.min(1, p.hit()))));
                g.setStroke(new BasicStroke(1.7f)); g.drawRoundRect(7, 5, 31, 28, 8, 8);
            }
        } finally { g.dispose(); }
        leakStatus(target, p);
        return true;
    }

    private static void leakStatus(Graphics2D target, ActorVisuals.Hostile p) {
        if (p.death() > 0) return;
        Graphics2D status = (Graphics2D) target.create();
        try {
            status.setColor(new Color(9, 16, 18, 225));
            status.fillRoundRect((int) p.x(), (int) p.y() - 9, (int) p.width(), 4, 3, 3);
            status.setColor(RED); status.fillRoundRect((int) p.x(), (int) p.y() - 9,
                    (int) (p.width() * Math.max(0, p.hp()) / Math.max(1, p.maxHp())), 4, 3, 3);
            // Leak currently has no projectile attack. A warning is shown only if the logic adds one.
            if (p.warning() > 0) {
                status.setColor(RED); status.setStroke(new BasicStroke(2));
                status.drawOval((int) (p.x() + p.width() / 2 - 6), (int) p.y() - 26, 12, 12);
            }
        } finally { status.dispose(); }
    }

    /** Uses world coordinates. It paints no labels or danger geometry inferred from attack names. */
    public static void renderBoss(Graphics2D target, BossVisual p) {
        if (frame("boss-shell") != null && frame("shutter") != null) {
            texturedBoss(target, p);
            return;
        }
        Graphics2D g = (Graphics2D) target.create();
        try {
            quality(g); g.translate(p.x(), p.y()); g.scale(p.width() / 120.0, p.height() / 150.0);
            boolean exposed = p.vulnerableTicks() > 0;
            double t = p.seconds(), hit = Math.min(1, Math.max(0, p.hitTicks()) / 6.0);
            Color energy = p.stage() >= 3 ? RED : ACID;
            if (p.hp() <= 0) { opacity(g, .55f); energy = new Color(104, 107, 74); }
            // Low-opacity exhaust stays inside/near the hull; it does not imply a damaging floor pool.
            for (int side : new int[]{-1, 1}) {
                int cx = side < 0 ? 21 : 99;
                double stroke = Math.sin(t * (p.stage() >= 3 ? 10 : 6) + side) * 2;
                g.setColor(new Color(28, 37, 35)); g.fillRoundRect(cx - 8, 124, 16, 23, 6, 6);
                g.setColor(new Color(207, 162, 88)); g.fillRect(cx - 6, (int) (130 + stroke), 12, 4);
                g.setColor(new Color(175, 202, 143, 38)); g.fillOval(cx - 11, 138, 22, 17);
                g.setColor(new Color(172, 208, 123, 145)); g.fillOval(cx - 5, 141, 10, 4);
            }
            g.drawImage(BOSS, 0, 0, 120, 150, null);
            Graphics2D rotor = (Graphics2D) g.create();
            try {
                rotor.rotate(t * (1.6 + p.stage() * .35), 60, 23);
                rotor.setColor(new Color(168, 159, 119));
                for (int i = 0; i < 4; i++) {
                    rotor.rotate(Math.PI / 2, 60, 23);
                    rotor.fill(polygon(59, 21, 64, 12, 70, 17, 64, 24));
                }
            } finally { rotor.dispose(); }
            g.setColor(INK); g.fillOval(56, 19, 8, 8);
            g.setColor(CREAM); g.fillOval(58, 21, 3, 3);

            // Central reservoir and compressor jaws use the real vulnerable timer, not HP color alone.
            g.setColor(new Color(9, 28, 28)); g.fillRoundRect(30, 42, 60, 72, 15, 15);
            int fluidHeight = 31 + p.stage() * 8;
            g.setPaint(new GradientPaint(33, 51, new Color(166, 188, 95), 86, 113, new Color(45, 79, 61)));
            g.fillRoundRect(33, 113 - fluidHeight, 54, fluidHeight, 12, 12);
            for (int i = 0; i < 6; i++) {
                double by = 105 - Math.floorMod((long) (t * 17 + i * 19), 48);
                g.setColor(new Color(205, 226, 136, 85)); g.drawOval(40 + (i * 17) % 36, (int) by, 3 + i % 3, 3 + i % 3);
            }
            g.setColor(new Color(228, 238, 193, 100)); g.fillRoundRect(36, 48, 4, 53, 2, 2);
            g.setColor(new Color(208, 162, 81)); g.setStroke(new BasicStroke(2));
            g.drawRoundRect(30, 42, 60, 72, 15, 15);
            int gap = exposed ? 23 : 5;
            for (int side : new int[]{-1, 1}) {
                int x = side < 0 ? 60 - gap - 23 : 60 + gap;
                g.setColor(INK); g.fillRoundRect(x - 1, 60, 25, 44, 4, 4);
                g.setPaint(new GradientPaint(x, 60, new Color(190, 147, 77), x, 103, new Color(71, 61, 46)));
                g.fillRoundRect(x, 61, 23, 42, 3, 3);
                g.setColor(new Color(226, 199, 139)); g.drawLine(x + 2, 62, x + 20, 62);
                g.setColor(new Color(31, 41, 38));
                for (int y = 67; y < 100; y += 8) g.drawLine(x + 4, y, x + 19, y);
                g.setColor(exposed ? CREAM : RED); g.fillRect(side < 0 ? x + 19 : x + 1, 75, 3, 16);
            }
            if (exposed) {
                g.setColor(new Color(255, 205, 102, 40)); g.fillOval(33, 55, 54, 54);
                g.setPaint(new RadialGradientPaint(60, 82, 19,
                        new float[]{0, .48f, 1}, new Color[]{new Color(255, 246, 206), new Color(245, 175, 66), new Color(102, 65, 37)}));
                g.fillOval(43, 65, 34, 34);
                g.setColor(INK); g.setStroke(new BasicStroke(3)); g.drawOval(43, 65, 34, 34);
                g.setColor(CREAM); g.setStroke(new BasicStroke(1.5f)); g.drawArc(39, 61, 42, 42, 25, 130);
            }
            g.setColor(energy);
            for (int i = 0; i < p.stage(); i++) g.fillRoundRect(44 + i * 12, 124, 7, 3, 2, 2);
            if (p.stage() >= 2) {
                g.setStroke(new BasicStroke(1)); g.setColor(new Color(30, 31, 27));
                g.drawPolyline(new int[]{99, 92, 97, 90}, new int[]{38, 46, 53, 64}, 4);
                if (p.stage() >= 3) { g.setColor(RED); g.drawLine(11, 80, 19, 92); g.drawLine(19, 92, 12, 108); }
            }
            if (hit > 0) {
                g.setColor(new Color(255, 233, 188, p.flashes() ? (int) (210 * hit) : 115));
                g.setStroke(new BasicStroke(2.5f)); g.drawRoundRect(10, 7, 101, 131, 19, 19);
                g.drawArc(34, 55, 52, 52, 20, 135);
            }
            if (p.highContrast()) {
                g.setColor(new Color(232, 207, 145, 165)); g.setStroke(new BasicStroke(1));
                g.drawRoundRect(8, 4, 104, 137, 20, 20);
            }
        } finally { g.dispose(); }
    }

    private static void texturedLeak(Graphics2D target, ActorVisuals.Hostile p) {
        AnimationClip.Frame f = frame("leak-body");
        Graphics2D g = (Graphics2D) target.create();
        try {
            quality(g); g.translate(p.x() + p.width() / 2, p.y() + p.height());
            g.scale(-p.facing(), 1);
            if (p.death() > 0) {
                opacity(g, (float) Math.max(0, 1 - p.death()));
                g.translate(p.death() * 6, p.death() * 4); g.rotate(p.death() * .7, 0, -p.height() / 2);
            } else g.rotate(Math.sin(p.phase()) * .04, 0, -p.height() / 2);
            double scale = Math.min(p.width() / f.width(), p.height() / f.height());
            double w = f.width() * scale, h = f.height() * scale;
            paintPart(g, "leak-body", -w / 2, -h, w, h, false);
            if (p.hit() > 0) {
                g.setColor(new Color(255, 238, 196, (int) (185 * Math.min(1, p.hit()))));
                g.setStroke(new BasicStroke(1.3f)); g.drawArc((int) (-w / 2), (int) -h, (int) w, (int) (h * .86), 60, 145);
            }
        } finally { g.dispose(); }
    }

    private static void texturedBoss(Graphics2D target, BossVisual p) {
        AnimationClip.Frame shell = frame("boss-shell"), shutter = frame("shutter");
        Graphics2D g = (Graphics2D) target.create();
        try {
            quality(g); g.translate(p.x(), p.y());
            if (p.hp() <= 0) opacity(g, .55f);
            double scale = Math.min(p.width() / (double) shell.width(), p.height() / (double) shell.height());
            double w = shell.width() * scale, h = shell.height() * scale;
            double x = (p.width() - w) / 2, y = p.height() - h;
            paintPart(g, "boss-shell", x, y, w, h, false);
            var core = shell.sockets().getOrDefault("core", new AnimationClip.Point(shell.width() / 2.0, shell.height() * .49));
            var coreLeft = shell.sockets().getOrDefault("core-left", new AnimationClip.Point(core.x() - shell.width() * .13, core.y()));
            double cx = x + core.x() * scale, cy = y + core.y() * scale;
            double radius = Math.abs(core.x() - coreLeft.x()) * scale;
            boolean exposed = p.vulnerableTicks() > 0;
            double inner = Math.max(7, radius - 1.2);
            g.setColor(new Color(6, 17, 16)); g.fill(new Ellipse2D.Double(cx - inner, cy - inner, inner * 2, inner * 2));
            Color energy = exposed ? new Color(255, 235, 181) : p.stage() >= 3 ? new Color(211, 118, 53) : new Color(165, 189, 89);
            g.setPaint(new RadialGradientPaint((float) cx, (float) cy, (float) inner,
                    new float[]{0, .36f, .73f, 1}, new Color[]{energy,
                    exposed ? new Color(235, 167, 70) : new Color(115, 137, 62),
                    new Color(70, 79, 42), new Color(7, 23, 20)}));
            g.fill(new Ellipse2D.Double(cx - inner, cy - inner, inner * 2, inner * 2));
            if (exposed) {
                g.setColor(new Color(250, 220, 158, 140)); g.setStroke(new BasicStroke(1.2f));
                g.drawArc((int) (cx - inner + 3), (int) (cy - inner + 3), (int) (inner * 2 - 6), (int) (inner * 2 - 6),
                        (int) (p.seconds() * 65), 215);
            }
            double doorH = radius * 2 + 11, doorW = doorH * shutter.width() / shutter.height();
            double gap = exposed ? radius + 1 : 0;
            paintPart(g, "shutter", cx - doorW - gap, cy - doorH / 2, doorW, doorH, true);
            paintPart(g, "shutter", cx + gap, cy - doorH / 2, doorW, doorH, false);
            if (p.stage() >= 2) {
                int alpha = p.flashes() ? 55 + (int) (Math.sin(p.seconds() * 5) * 13) : 55;
                g.setColor(new Color(255, 99, 42, alpha));
                g.fillOval((int) (x + w * .17), (int) (y + h * .67), 9, 13);
                if (p.stage() >= 3) g.fillOval((int) (x + w * .76), (int) (y + h * .67), 9, 13);
            }
            if (p.hitTicks() > 0) {
                g.setColor(new Color(255, 235, 193, p.flashes() ? Math.min(220, p.hitTicks() * 35) : 100));
                g.setStroke(new BasicStroke(2)); g.drawArc((int) (cx - radius - 5), (int) (cy - radius - 5),
                        (int) (radius * 2 + 10), (int) (radius * 2 + 10), 45, 160);
            }
            if (p.highContrast()) {
                g.setColor(new Color(234, 220, 177, 160)); g.setStroke(new BasicStroke(1.2f));
                g.drawArc((int) (cx - radius - 3), (int) (cy - radius - 3), (int) (radius * 2 + 6), (int) (radius * 2 + 6), 20, 140);
            }
        } finally { g.dispose(); }
    }

    private static AnimationClip.Frame frame(String name) {
        var sprite = MATERIALS.find("heap-actors").orElse(null);
        if (sprite == null) return null;
        var clip = sprite.definition().animations().get(name);
        return clip == null ? null : clip.frames().get(0);
    }

    private static void paintPart(Graphics2D target, String name, double x, double y, double w, double h, boolean mirror) {
        var sprite = MATERIALS.find("heap-actors").orElseThrow();
        var f = sprite.definition().animations().get(name).frames().get(0);
        Graphics2D g = (Graphics2D) target.create();
        try {
            g.translate(x, y);
            if (mirror) { g.translate(w, 0); g.scale(-1, 1); }
            g.scale(w / f.width(), h / f.height()); sprite.paintFrame(g, f);
        } finally { g.dispose(); }
    }

    private static void leakShell(Graphics2D g) {
        g.setColor(INK); g.setStroke(new BasicStroke(3));
        Path2D hose = new Path2D.Double(); hose.moveTo(31, 7); hose.curveTo(43, 0, 44, 28, 34, 30); g.draw(hose);
        g.setColor(STEEL); g.setStroke(new BasicStroke(1)); g.draw(hose);
        Shape tank = new RoundRectangle2D.Double(8, 5, 30, 29, 11, 11);
        g.setColor(INK); g.setStroke(new BasicStroke(2)); g.draw(tank);
        g.setPaint(new GradientPaint(9, 4, new Color(220, 191, 128), 36, 31, new Color(86, 73, 49))); g.fill(tank);
        g.setColor(RUST); g.fillRoundRect(6, 12, 15, 16, 4, 4);
        g.setColor(INK); g.fillRoundRect(2, 18, 8, 10, 3, 3);
        g.setColor(STEEL); g.fillRect(2, 20, 3, 6);
        g.setColor(CREAM); g.drawLine(12, 7, 30, 7);
        g.setColor(new Color(39, 43, 35)); g.fillRoundRect(18, 3, 10, 4, 2, 2);
        for (int y : new int[]{11, 29}) { g.setColor(new Color(44, 49, 42)); g.fillRect(10, y, 26, 3); }
        g.setColor(new Color(228, 211, 157)); g.fillOval(12, 30, 2, 2); g.fillOval(33, 9, 2, 2);
        g.setColor(new Color(47, 46, 35)); g.drawLine(13, 7, 16, 9); g.drawLine(31, 32, 34, 31);
    }

    private static void bossShell(Graphics2D g) {
        Shape hull = polygon(29, 6, 89, 6, 111, 22, 110, 116, 95, 140, 27, 140, 9, 119, 8, 25);
        g.setColor(INK); g.setStroke(new BasicStroke(4)); g.draw(hull);
        g.setPaint(new GradientPaint(11, 10, new Color(190, 152, 88), 109, 137, new Color(54, 60, 53))); g.fill(hull);
        for (int x : new int[]{10, 98}) {
            g.setColor(new Color(19, 27, 26)); g.fillRoundRect(x, 29, 12, 92, 5, 5);
            g.setPaint(new GradientPaint(x, 0, new Color(150, 155, 135), x + 12, 0, new Color(31, 43, 40)));
            g.fillRoundRect(x + 3, 35, 6, 79, 3, 3);
            for (int y : new int[]{37, 101}) { g.setColor(RUST); g.fillRect(x - 2, y, 16, 9); g.setColor(CREAM); g.drawLine(x, y, x + 12, y); }
        }
        g.setColor(new Color(30, 40, 36)); g.fillRoundRect(35, 5, 50, 34, 9, 9);
        g.setColor(new Color(182, 150, 93)); g.setStroke(new BasicStroke(3)); g.drawOval(44, 7, 32, 32);
        g.setColor(new Color(9, 22, 22)); g.fillOval(47, 10, 26, 26);
        for (int side : new int[]{-1, 1}) {
            int x = side < 0 ? 2 : 101;
            g.setColor(INK); g.fillRoundRect(x, 53, 18, 50, 8, 8);
            g.setPaint(new GradientPaint(x, 0, new Color(189, 149, 79), x + 18, 0, new Color(50, 58, 48)));
            g.fillRoundRect(x + 2, 55, 14, 46, 6, 6);
            g.setColor(new Color(44, 50, 42));
            for (int y = 61; y < 95; y += 9) g.drawLine(x + 2, y, x + 15, y);
            g.setColor(CREAM); g.fillOval(x + 5, 57, 3, 3);
        }
        g.setColor(new Color(178, 120, 52)); g.fill(polygon(26, 115, 91, 115, 99, 127, 89, 139, 30, 139, 21, 127));
        g.setColor(INK); g.fillRoundRect(36, 120, 49, 13, 4, 4);
        g.setColor(new Color(200, 178, 122));
        for (int x = 42; x < 81; x += 8) g.drawLine(x, 133, x + 4, 137);
        g.setStroke(new BasicStroke(1)); g.setColor(CREAM); g.drawLine(28, 8, 88, 8);
        for (int x : new int[]{25, 92}) for (int y : new int[]{23, 49, 108, 129}) {
            g.setColor(INK); g.fillOval(x - 2, y - 2, 5, 5);
            g.setColor(new Color(204, 191, 145)); g.fillOval(x - 1, y - 1, 2, 2);
        }
        g.setColor(new Color(37, 43, 36));
        for (int[] line : new int[][]{{31,13,34,15},{83,11,89,13},{29,109,33,106},{84,136,88,134},{90,33,95,35}})
            g.drawLine(line[0], line[1], line[2], line[3]);
    }

    private static BufferedImage cache(int width, int height, java.util.function.Consumer<Graphics2D> painter) {
        BufferedImage image = new BufferedImage(width * 3, height * 3, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = image.createGraphics();
        try { g.scale(3, 3); quality(g); painter.accept(g); } finally { g.dispose(); }
        return image;
    }
    private static void quality(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    }
    private static void opacity(Graphics2D g, float amount) {
        if (g.getComposite() instanceof AlphaComposite c) g.setComposite(c.derive(c.getAlpha() * amount));
    }
    private static Path2D polygon(double... values) {
        Path2D p = new Path2D.Double(); p.moveTo(values[0], values[1]);
        for (int i = 2; i < values.length; i += 2) p.lineTo(values[i], values[i + 1]);
        p.closePath(); return p;
    }
}
