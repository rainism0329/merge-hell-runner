package com.bigphil.mergehell.render;

import com.bigphil.mergehell.model.EntityType;

import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;

/** Faceted optical scouts and an orbital containment engine, with bounded, pre-rendered material layers. */
public final class SingularityActorRenderer {
    public record BossVisual(double x, double y, double width, double height, int hp, int maxHp,
                             int stage, int hitTicks, int vulnerableTicks, double seconds,
                             boolean flashes, boolean highContrast, int warningTicks, int rebootTicks) { }
    private static final Color INK = new Color(9, 18, 27), LIGHT = new Color(224, 232, 218);
    private static final Color COPPER = new Color(215, 164, 112), TEAL = new Color(156, 216, 212);
    private static final Color SIGNAL = new Color(211, 166, 218), WARNING = new Color(250, 157, 103);
    private static final BufferedImage MIRROR = cache(54, 54, SingularityActorRenderer::mirrorShell);
    private static final BufferedImage BODY = cache(120, 150, SingularityActorRenderer::bossShell);
    private static final BufferedImage CLAMP = cache(26, 58, SingularityActorRenderer::clampShell);
    private static final BufferedImage CORE = core(SIGNAL);
    private static final BufferedImage OPEN_CORE = core(TEAL);
    private static final BufferedImage ALERT_CORE = core(WARNING);

    private SingularityActorRenderer() { }
    public static void preload() { }

    public static boolean renderMirror(Graphics2D target, ActorVisuals.Hostile pose) {
        if (pose.type() != EntityType.MIRROR) return false;
        Graphics2D g = (Graphics2D) target.create();
        try {
            quality(g); g.translate(pose.x() + pose.width() / 2, pose.y() + pose.height() / 2);
            g.scale(pose.facing() * pose.width() / 54.0, pose.height() / 54.0);
            if (pose.death() > 0) {
                opacity(g, (float) Math.max(0, 1 - pose.death()));
                g.translate(pose.death() * 6, pose.death() * 14); g.rotate(pose.death() * .9);
            } else {
                g.rotate(Math.sin(pose.phase()) * .045); g.translate(-pose.hit() * 1.5, 0);
            }
            g.translate(-27, -27); g.drawImage(MIRROR, 0, 0, 54, 54, null);
            Color energy = pose.warning() > 0 ? WARNING : TEAL;
            // A mirrored lens has steel facets and an asymmetric moving iris, rather than a COPY glyph.
            g.setColor(INK); g.fillOval(18, 18, 19, 19);
            g.setColor(new Color(47, 74, 86)); g.fillOval(21, 21, 13, 13);
            g.setColor(energy); g.fillOval(26, 22, 7, 9);
            g.setColor(LIGHT); g.fillRect(29, 23, 2, 2);
            g.setStroke(new BasicStroke(1.4f)); g.setColor(SIGNAL);
            g.drawArc(16, 16, 23, 23, (int) (pose.phase() * 25), 115);
            for (int x : new int[]{5, 43}) {
                g.setColor(energy); g.fillRect(x, 24, 5, 3);
                g.setColor(new Color(103, 143, 151)); g.fillRect(x + 1, 29, 3, 2);
            }
            if (pose.warning() > 0 && pose.death() == 0) {
                // The visible iris narrows while the simulation counts down its real shot telegraph.
                g.setColor(WARNING); g.setStroke(new BasicStroke(2));
                g.drawLine(20, 16, 34, 16); g.drawLine(20, 39, 34, 39);
            }
            if (pose.hit() > 0) {
                g.setColor(new Color(249, 228, 190, Math.min(180, (int) (pose.hit() * 180))));
                g.setStroke(new BasicStroke(1.6f)); g.draw(polygon(12, 10, 39, 9, 48, 25, 40, 43, 14, 44, 7, 27));
            }
        } finally { g.dispose(); }
        EnemyWarningRenderer.render(target, pose, WARNING);
        if (EnemyWarningRenderer.showHealth(pose)) {
            Graphics2D status = (Graphics2D) target.create();
            try {
                int x = (int) pose.x(), y = (int) pose.y();
                status.setColor(new Color(6, 17, 26, 225)); status.fillRoundRect(x + 4, y - 8, (int) pose.width() - 8, 4, 3, 3);
                status.setColor(COPPER); status.fillRect(x + 4, y - 8, (int) ((pose.width() - 8) * Math.max(0, pose.hp()) / Math.max(1, pose.maxHp())), 3);
            } finally { status.dispose(); }
        }
        return true;
    }

    /** The boss model draws the authoritative volley lanes and charge paths separately. */
    public static void renderBoss(Graphics2D target, BossVisual pose) {
        Graphics2D g = (Graphics2D) target.create();
        try {
            quality(g); g.translate(pose.x(), pose.y()); g.scale(pose.width() / 120, pose.height() / 150);
            if (pose.hp() <= 0) opacity(g, .45f);
            boolean exposed = pose.vulnerableTicks() > 0, warning = pose.warningTicks() > 0;
            boolean reboot = pose.rebootTicks() > 0;
            Color energy = warning ? WARNING : exposed ? TEAL : SIGNAL;
            g.drawImage(BODY, 0, 0, 120, 150, null);
            g.drawImage(warning ? ALERT_CORE : exposed ? OPEN_CORE : CORE, 28, 42, 64, 64, null);
            Graphics2D orbit = (Graphics2D) g.create();
            try {
                orbit.translate(60, 74);
                // Warn/reboot states mechanically lock the containment ring; exposed clamps retract.
                double angle = warning || reboot ? -.12 : pose.seconds() * (exposed ? .25 : .085);
                orbit.rotate(angle);
                for (int i = 0; i < 3; i++) {
                    Graphics2D jaw = (Graphics2D) orbit.create();
                    try {
                        jaw.rotate(i * Math.PI * 2 / 3);
                        jaw.translate(-13, exposed ? -58 : warning ? -46 : -51);
                        jaw.drawImage(CLAMP, 0, 0, 26, 50, null);
                        jaw.setColor(energy); jaw.fillRect(8, 37, 10, 3);
                        jaw.setColor(LIGHT); jaw.fillRect(9, 37, 3, 1);
                    } finally { jaw.dispose(); }
                }
            } finally { orbit.dispose(); }
            // Split armor shutters expose the same core whose actual hit multiplier is active.
            int gap = exposed ? 19 : 0;
            for (int side = 0; side < 2; side++) {
                int x = side == 0 ? 33 - gap : 65 + gap;
                g.setColor(new Color(16, 31, 42)); g.fillPolygon(new int[]{x, x + 22, x + 22, x}, new int[]{53, 59, 91, 98}, 4);
                g.setColor(new Color(116, 139, 144)); g.fillPolygon(new int[]{x + 2, x + 19, x + 19, x + 2}, new int[]{56, 61, 88, 94}, 4);
                g.setColor(new Color(200, 203, 184)); g.drawLine(x + 2, 56, x + 19, 61);
                g.setColor(new Color(33, 53, 65));
                for (int vent = 0; vent < 3; vent++) g.fillRect(x + 5, 67 + vent * 7, 11, 2);
                g.setColor(energy); g.fillRect(x + (side == 0 ? 19 : 1), 64, 2, 23);
            }
            for (int phase = 0; phase < 3; phase++) {
                g.setColor(phase < pose.stage() ? COPPER : new Color(46, 64, 74));
                g.fillRoundRect(42 + phase * 13, 127, 9, 4, 2, 2);
            }
            if (reboot && !exposed) {
                g.setColor(new Color(134, 174, 181)); g.setStroke(new BasicStroke(2));
                g.drawLine(41, 116, 79, 116); g.drawLine(46, 120, 74, 120);
            }
            if (warning) {
                g.setColor(WARNING); g.setStroke(new BasicStroke(2));
                g.drawLine(7, 54, 17, 54); g.drawLine(103, 54, 113, 54);
                g.drawLine(48, 20, 72, 20);
            }
            if (pose.flashes() && pose.hitTicks() > 0) {
                g.setColor(new Color(251, 233, 199, Math.min(190, pose.hitTicks() * 25)));
                g.setStroke(new BasicStroke(2)); g.drawOval(15, 28, 90, 94);
            }
            if (pose.highContrast()) {
                g.setColor(new Color(225, 232, 218, 205)); g.setStroke(new BasicStroke(1));
                g.draw(polygon(43, 3, 76, 3, 96, 25, 116, 57, 111, 105, 78, 147, 42, 147, 9, 108, 4, 59, 23, 28));
            }
        } finally { g.dispose(); }
    }

    private static void mirrorShell(Graphics2D g) {
        g.setColor(INK); g.fill(polygon(13, 7, 40, 7, 51, 25, 42, 47, 12, 47, 3, 27));
        g.setPaint(new GradientPaint(8, 9, new Color(198, 213, 211), 41, 47, new Color(36, 59, 76)));
        g.fill(polygon(15, 10, 37, 10, 46, 25, 38, 43, 15, 43, 8, 27));
        g.setColor(new Color(70, 99, 117)); g.fill(polygon(15, 10, 27, 19, 19, 29, 8, 27));
        g.setColor(new Color(150, 182, 190)); g.fill(polygon(37, 10, 46, 25, 34, 24, 27, 19));
        g.setColor(new Color(34, 61, 82)); g.fill(polygon(19, 29, 34, 24, 38, 43, 15, 43));
        g.setColor(new Color(223, 229, 211)); g.drawLine(15, 10, 36, 10); g.drawLine(9, 26, 15, 12);
        g.setColor(COPPER); g.fillRect(19, 5, 16, 3); g.fillRect(19, 45, 16, 3);
        g.setColor(INK); g.fillRoundRect(2, 20, 10, 16, 3, 3); g.fillRoundRect(42, 20, 10, 16, 3, 3);
        bolts(g, 15, 15, 37, 15, 15, 39, 37, 39);
    }
    private static void bossShell(Graphics2D g) {
        g.setColor(INK); g.fill(polygon(43, 2, 77, 2, 96, 25, 114, 58, 110, 106, 79, 148, 41, 148, 10, 107, 5, 59, 23, 27));
        g.setPaint(new GradientPaint(21, 9, new Color(130, 155, 159), 102, 142, new Color(29, 49, 65)));
        g.fill(polygon(46, 6, 73, 6, 92, 28, 109, 60, 106, 104, 76, 143, 44, 143, 14, 105, 10, 61, 27, 30));
        g.setColor(new Color(217, 213, 185)); g.drawLine(46, 7, 72, 7); g.drawLine(27, 29, 46, 7);
        g.setColor(new Color(14, 31, 44)); g.fillOval(13, 26, 94, 98);
        g.setStroke(new BasicStroke(6)); g.setColor(new Color(113, 140, 150)); g.drawOval(18, 31, 84, 87);
        g.setStroke(new BasicStroke(2)); g.setColor(COPPER); g.drawArc(16, 29, 88, 91, 58, 106);
        g.setColor(new Color(43, 67, 80)); g.drawArc(16, 29, 88, 91, 205, 95);
        g.setColor(new Color(9, 26, 39)); g.fillRoundRect(34, 122, 52, 14, 4, 4);
        for (int side = 0; side < 2; side++) {
            int x = side == 0 ? 2 : 101;
            g.setColor(INK); g.fillRoundRect(x, 52, 17, 49, 5, 5);
            g.setColor(new Color(104, 130, 143)); g.fillRoundRect(x + 3, 55, 11, 42, 3, 3);
            g.setColor(COPPER); g.fillRect(x + 4, 62, 9, 6);
            g.setColor(new Color(23, 43, 58)); for (int slot = 0; slot < 4; slot++) g.fillRect(x + 5, 74 + slot * 5, 7, 2);
        }
        bolts(g, 46, 14, 74, 14, 31, 38, 88, 38, 26, 110, 93, 110, 47, 139, 73, 139);
    }
    private static void clampShell(Graphics2D g) {
        g.setColor(INK); g.fill(polygon(3, 0, 23, 0, 26, 36, 18, 58, 8, 58, 0, 36));
        g.setPaint(new GradientPaint(3, 2, new Color(184, 194, 177), 23, 54, new Color(47, 71, 85)));
        g.fill(polygon(5, 3, 20, 3, 22, 34, 16, 53, 10, 53, 4, 34));
        g.setColor(new Color(221, 218, 188)); g.drawLine(6, 4, 19, 4);
        g.setColor(COPPER); g.fillRect(7, 11, 12, 6);
        g.setColor(new Color(21, 43, 57)); g.fillRect(8, 23, 10, 9); g.fillRect(10, 45, 6, 5);
        bolts(g, 7, 7, 19, 7, 8, 34, 18, 34);
    }
    private static BufferedImage core(Color energy) {
        return cache(64, 64, g -> {
            g.setPaint(new RadialGradientPaint(32, 32, 30, new float[]{0, .18f, .48f, 1},
                    new Color[]{LIGHT, energy, new Color(61, 91, 112), new Color(13, 31, 44)}));
            g.fillOval(2, 2, 60, 60);
            g.setColor(new Color(215, 232, 221, 180)); g.setStroke(new BasicStroke(1)); g.drawOval(21, 21, 22, 22);
        });
    }
    private static void bolts(Graphics2D g, int... points) {
        for (int i = 0; i < points.length; i += 2) {
            g.setColor(INK); g.fillOval(points[i] - 2, points[i + 1] - 2, 4, 4);
            g.setColor(LIGHT); g.fillRect(points[i] - 1, points[i + 1] - 1, 2, 1);
        }
    }
    private static Path2D polygon(double... points) {
        Path2D p = new Path2D.Double(); p.moveTo(points[0], points[1]);
        for (int i = 2; i < points.length; i += 2) p.lineTo(points[i], points[i + 1]); p.closePath(); return p;
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
        if (g.getComposite() instanceof AlphaComposite composite) g.setComposite(composite.derive(composite.getAlpha() * alpha));
    }
}
