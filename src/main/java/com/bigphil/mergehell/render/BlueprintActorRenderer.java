package com.bigphil.mergehell.render;

import com.bigphil.mergehell.model.EntityType;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;

/** Mechanical construction actors. Attack geometry remains owned by the encounter logic. */
public final class BlueprintActorRenderer {
    public record BossVisual(double x, double y, double width, double height, int hp, int maxHp,
                             int stage, int hitTicks, int vulnerableTicks, double seconds,
                             boolean flashes, boolean highContrast) { }

    private static final Color INK = new Color(9, 20, 29);
    private static final Color LIGHT = new Color(206, 220, 218);
    private static final Color CYAN = new Color(121, 215, 237);
    private static final Color AMBER = new Color(225, 175, 100);
    private static final Color DANGER = new Color(246, 131, 78);
    private static final BufferedImage SENTINEL = cache(52, 52, BlueprintActorRenderer::sentinelShell);
    private static final BufferedImage ARCHITECT = cache(120, 150, BlueprintActorRenderer::architectShell);

    private BlueprintActorRenderer() { }
    public static void preload() { /* Eager static raster preparation, outside gameplay rendering. */ }

    public static boolean renderSentinel(Graphics2D target, ActorVisuals.Hostile pose) {
        if (pose.type() != EntityType.SENTINEL) return false;
        Graphics2D g = (Graphics2D) target.create();
        try {
            quality(g);
            g.translate(pose.x() + pose.width() / 2, pose.y() + pose.height() / 2);
            g.scale(pose.facing() * pose.width() / 52.0, pose.height() / 52.0);
            if (pose.death() > 0) {
                opacity(g, (float) Math.max(0, 1 - pose.death()));
                g.translate(pose.death() * 8, pose.death() * 10);
                g.rotate(pose.death() * .9);
            } else g.rotate(Math.sin(pose.phase()) * .025);
            g.translate(-26, -26);
            g.drawImage(SENTINEL, 0, 0, 52, 52, null);
            Color energy = pose.warning() > 0 ? DANGER : CYAN;
            g.setColor(new Color(7, 25, 35)); g.fillOval(15, 15, 24, 24);
            g.setPaint(new RadialGradientPaint(27, 27, 12, new float[]{0, .3f, 1},
                    new Color[]{LIGHT, energy, new Color(13, 49, 62)}));
            g.fillOval(18, 18, 18, 18);
            g.setColor(new Color(17, 29, 38)); g.setStroke(new BasicStroke(2));
            int angle = (int) (pose.phase() * 18);
            g.drawArc(17, 17, 20, 20, angle, 125); g.drawArc(17, 17, 20, 20, angle + 180, 125);
            g.setColor(energy); g.fillRect(45, 23, 4, 5);
            g.setColor(new Color(99, 166, 188, 90));
            g.fillOval(10, 45, 10, 4); g.fillOval(33, 45, 10, 4);
            if (pose.hit() > 0) {
                g.setColor(new Color(249, 233, 191, (int) (Math.min(1, pose.hit()) * 205)));
                g.setStroke(new BasicStroke(1.8f)); g.drawPolygon(new Polygon(
                        new int[]{12, 35, 46, 37, 15, 4}, new int[]{4, 4, 18, 41, 43, 29}, 6));
            }
        } finally { g.dispose(); }
        if (pose.death() == 0) {
            Graphics2D status = (Graphics2D) target.create();
            try {
                status.setColor(new Color(5, 17, 24, 220));
                status.fillRoundRect((int) pose.x(), (int) pose.y() - 8, (int) pose.width(), 4, 3, 3);
                status.setColor(AMBER); status.fillRect((int) pose.x(), (int) pose.y() - 8,
                        (int) (pose.width() * Math.max(0, pose.hp()) / Math.max(1, pose.maxHp())), 3);
                if (pose.warning() > 0) {
                    int x = (int) (pose.x() + pose.width() / 2), y = (int) pose.y() - 24;
                    status.setColor(DANGER);
                    status.fillPolygon(new int[]{x, x - 7, x + 7}, new int[]{y - 4, y + 9, y + 9}, 3);
                    status.setColor(INK); status.fillRect(x - 1, y, 2, 4); status.fillRect(x - 1, y + 6, 2, 1);
                }
            } finally { status.dispose(); }
        }
        return true;
    }

    /** World coordinates; no synthetic targeting reticle or damage warning is inferred here. */
    public static void renderBoss(Graphics2D target, BossVisual pose) {
        Graphics2D g = (Graphics2D) target.create();
        try {
            quality(g); g.translate(pose.x(), pose.y());
            g.scale(pose.width() / 120.0, pose.height() / 150.0);
            if (pose.hp() <= 0) opacity(g, .55f);
            double motion = Math.sin(pose.seconds() * (pose.stage() >= 3 ? 7 : 3)) * 1.6;
            Color energy = pose.stage() >= 3 ? DANGER : CYAN;
            // Paired engine pods are fastened to the lower chassis, never floating beside it.
            for (int side : new int[]{0, 1}) {
                int x = side == 0 ? 16 : 89;
                g.setColor(INK); g.fillRoundRect(x - 3, 120, 22, 30, 5, 5);
                g.setPaint(new GradientPaint(x, 125, new Color(124, 139, 139), x + 15, 146, new Color(27, 47, 57)));
                g.fillRoundRect(x, 124, 15, 21, 4, 4);
                g.setColor(AMBER); g.fillRect(x + 2, 126, 11, 3);
                g.setColor(new Color(energy.getRed(), energy.getGreen(), energy.getBlue(), 125));
                g.fill(new Ellipse2D.Double(x + 2, 145, 11, 3 + motion));
            }
            g.drawImage(ARCHITECT, 0, 0, 120, 150, null);
            // A physical iris opens only when the actual vulnerability window is active.
            boolean exposed = pose.vulnerableTicks() > 0;
            g.setColor(new Color(4, 20, 30)); g.fillOval(29, 45, 62, 65);
            g.setPaint(new RadialGradientPaint(60, 76, 30, new float[]{0, .3f, 1},
                    new Color[]{new Color(238, 252, 243), energy, new Color(9, 29, 41)}));
            g.fillOval(35, 51, 50, 53);
            Graphics2D rotor = (Graphics2D) g.create();
            try {
                rotor.rotate(pose.seconds() * (exposed ? .65 : .18), 60, 78);
                rotor.setStroke(new BasicStroke(2)); rotor.setColor(new Color(215, 224, 207));
                rotor.draw(polygon(60, 54, 80, 90, 40, 90));
                rotor.setColor(new Color(16, 42, 52)); rotor.drawOval(46, 63, 28, 28);
            } finally { rotor.dispose(); }
            int opening = exposed ? 17 : 1;
            for (int side : new int[]{-1, 1}) {
                int x = side < 0 ? 36 - opening : 61 + opening;
                g.setPaint(new GradientPaint(x, 56, new Color(153, 167, 159), x + 22, 104, new Color(35, 56, 65)));
                g.fill(polygon(x, 56, x + 22, 62, x + 22, 96, x, 104));
                g.setColor(AMBER); g.setStroke(new BasicStroke(1.6f));
                g.drawLine(x + (side < 0 ? 21 : 1), 62, x + (side < 0 ? 21 : 1), 96);
                g.setColor(new Color(12, 34, 44));
                for (int slot = 0; slot < 4; slot++) g.fillRect(x + 5, 65 + slot * 8, 11, 3);
            }
            g.setColor(energy); g.fillRoundRect(43, 26, 34, 5, 2, 2);
            g.setColor(LIGHT); g.fillRect(49, 26, 12, 1);
            for (int i = 0; i < 3; i++) {
                g.setColor(i < pose.stage() ? AMBER : new Color(48, 62, 66));
                g.fillRect(45 + i * 11, 125, 7, 4);
            }
            if (pose.flashes() && pose.hitTicks() > 0) {
                g.setColor(new Color(255, 239, 207, Math.min(180, pose.hitTicks() * 24)));
                g.setStroke(new BasicStroke(2)); g.drawRoundRect(17, 36, 87, 83, 9, 9);
            }
            if (pose.highContrast()) {
                g.setColor(new Color(209, 225, 229, 185)); g.setStroke(new BasicStroke(1));
                g.drawPolygon(new Polygon(new int[]{20, 42, 77, 104, 111, 100, 19, 8},
                        new int[]{13, 6, 6, 20, 116, 136, 136, 115}, 8));
            }
        } finally { g.dispose(); }
    }

    private static void sentinelShell(Graphics2D g) {
        g.setColor(INK); g.fill(polygon(13, 2, 36, 2, 49, 17, 47, 36, 37, 46, 12, 46, 2, 31, 3, 15));
        g.setPaint(new GradientPaint(7, 5, new Color(179, 191, 185), 45, 44, new Color(38, 66, 80)));
        g.fill(polygon(14, 5, 34, 5, 44, 17, 42, 34, 34, 42, 15, 42, 6, 29, 7, 17));
        g.setColor(new Color(221, 220, 191)); g.setStroke(new BasicStroke(1));
        g.drawLine(14, 5, 34, 5); g.drawLine(7, 17, 14, 6);
        g.setColor(new Color(43, 64, 76)); g.fillRoundRect(9, 8, 29, 9, 4, 4);
        g.setColor(AMBER); g.fillRect(13, 10, 13, 3);
        g.setColor(INK); g.fillRoundRect(34, 20, 17, 12, 3, 3);
        g.setColor(new Color(133, 154, 155)); g.fillRect(40, 21, 7, 10);
        g.setColor(new Color(32, 54, 66)); g.fillRect(12, 40, 9, 6); g.fillRect(32, 40, 9, 6);
        bolts(g, 8, 20, 40, 11, 16, 37, 39, 35);
    }

    private static void architectShell(Graphics2D g) {
        g.setColor(INK); g.fill(polygon(17, 13, 40, 3, 80, 3, 105, 20, 115, 115, 101, 140, 18, 140, 4, 115));
        g.setPaint(new GradientPaint(10, 17, new Color(165, 177, 167), 113, 133, new Color(32, 51, 65)));
        g.fill(polygon(20, 16, 41, 7, 78, 7, 101, 23, 110, 114, 97, 134, 22, 134, 10, 114));
        g.setColor(new Color(208, 211, 188)); g.setStroke(new BasicStroke(2));
        g.drawLine(21, 17, 41, 8); g.drawLine(42, 8, 77, 8); g.drawLine(13, 46, 20, 19);
        g.setColor(INK); g.fillRoundRect(35, 19, 51, 20, 4, 4); g.fillRoundRect(20, 40, 83, 78, 9, 9);
        g.setColor(new Color(120, 142, 150)); g.drawRoundRect(21, 41, 80, 76, 9, 9);
        for (int side : new int[]{0, 1}) {
            int x = side == 0 ? 5 : 100;
            g.setPaint(new GradientPaint(x, 34, new Color(111, 133, 139), x + 15, 107, new Color(20, 37, 46)));
            g.fillRoundRect(x, 35, 15, 79, 5, 5);
            g.setColor(AMBER); g.fillRect(x + 3, 40, 9, 5);
            g.setColor(new Color(171, 183, 168)); g.fillRect(x + 4, 53, 3, 43);
            g.setColor(INK); for (int y = 58; y < 98; y += 10) g.fillRect(x + 8, y, 5, 5);
        }
        g.setColor(new Color(82, 105, 116)); g.fillRoundRect(25, 117, 70, 16, 3, 3);
        g.setColor(INK); for (int x = 29; x <= 87; x += 6) g.fillRect(x, 120, 3, 6);
        bolts(g, 24, 24, 93, 27, 23, 110, 96, 109, 28, 130, 88, 130);
    }

    private static void bolts(Graphics2D g, int... points) {
        for (int i = 0; i < points.length; i += 2) {
            g.setColor(INK); g.fillOval(points[i] - 2, points[i + 1] - 2, 5, 5);
            g.setColor(LIGHT); g.fillOval(points[i] - 1, points[i + 1] - 1, 2, 2);
        }
    }
    private static Path2D polygon(double... points) {
        Path2D p = new Path2D.Double(); p.moveTo(points[0], points[1]);
        for (int i = 2; i < points.length; i += 2) p.lineTo(points[i], points[i + 1]);
        p.closePath(); return p;
    }
    private static BufferedImage cache(int width, int height, Consumer<Graphics2D> draw) {
        BufferedImage image = new BufferedImage(width * 2, height * 2, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = image.createGraphics();
        try { g.scale(2, 2); quality(g); draw.accept(g); } finally { g.dispose(); }
        return image;
    }
    private static void quality(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    }
    private static void opacity(Graphics2D g, float alpha) {
        if (g.getComposite() instanceof AlphaComposite old) g.setComposite(old.derive(old.getAlpha() * alpha));
    }
}
