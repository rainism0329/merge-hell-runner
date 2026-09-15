package com.bigphil.mergehell.render;

import com.bigphil.mergehell.model.EntityType;

import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;

/** Electromechanical actors; warnings and exposure always consume the simulation's real state. */
public final class KernelActorRenderer {
    public record BossVisual(double x, double y, double width, double height, int hp, int maxHp,
                             int stage, int hitTicks, int vulnerableTicks, double seconds,
                             boolean flashes, boolean highContrast, int warningTicks, boolean dashing) { }

    private static final Color INK = new Color(10, 17, 23);
    private static final Color LIGHT = new Color(213, 218, 202);
    private static final Color COPPER = new Color(207, 147, 86);
    private static final Color POWER = new Color(148, 194, 226);
    private static final Color DANGER = new Color(251, 125, 77);
    private static final BufferedImage INTERRUPT = cache(36, 48, KernelActorRenderer::interruptShell);
    private static final BufferedImage EXECUTIONER = cache(120, 150, KernelActorRenderer::executionerShell);

    private KernelActorRenderer() { }
    public static void preload() { /* Initialize static material layers before the first encounter. */ }

    public static boolean renderInterrupt(Graphics2D target, ActorVisuals.Hostile pose) {
        if (pose.type() != EntityType.INTERRUPT) return false;
        Graphics2D g = (Graphics2D) target.create();
        try {
            quality(g);
            g.translate(pose.x() + pose.width() / 2, pose.y() + pose.height() / 2);
            g.scale(pose.facing() * pose.width() / 36.0, pose.height() / 48.0);
            if (pose.death() > 0) {
                opacity(g, (float) Math.max(0, 1 - pose.death()));
                g.translate(pose.death() * 7, pose.death() * 12);
                g.rotate(pose.death() * 1.3);
            } else {
                g.rotate(Math.sin(pose.phase()) * .04);
                g.translate(pose.hit() * -1.8, 0);
            }
            g.translate(-18, -24);
            g.drawImage(INTERRUPT, 0, 0, 36, 48, null);
            Color energy = pose.warning() > 0 ? DANGER : POWER;
            // Twin side coils surround a small armored optical head, instead of a floating glyph.
            for (int x : new int[]{3, 28}) {
                g.setColor(new Color(energy.getRed(), energy.getGreen(), energy.getBlue(), 100));
                g.fillRoundRect(x, 14, 5, 20, 3, 3);
                g.setColor(energy);
                int segment = (int) Math.floorMod((long) (pose.phase() * 2), 4);
                g.fillRect(x + 1, 17 + segment * 4, 3, 2);
            }
            g.setColor(new Color(5, 20, 26)); g.fillRoundRect(12, 12, 15, 8, 3, 3);
            g.setColor(energy); g.fillRect(19, 14, 7, 3);
            g.setColor(LIGHT); g.fillRect(23, 14, 2, 1);
            g.setColor(INK); g.fillOval(12, 25, 13, 13);
            g.setPaint(new RadialGradientPaint(18, 31, 6, new float[]{0, .35f, 1},
                    new Color[]{LIGHT, energy, new Color(24, 40, 49)}));
            g.fillOval(14, 27, 9, 9);
            g.setColor(new Color(55, 74, 81)); g.setStroke(new BasicStroke(1.5f));
            g.drawArc(12, 25, 13, 13, (int) (pose.phase() * 35), 205);
            // No free lightning: emission stays between the attached terminal contacts.
            if (pose.warning() > 0 && pose.death() == 0) {
                g.setColor(DANGER); g.setStroke(new BasicStroke(1.2f));
                g.draw(polygon(12, 6, 17, 9, 20, 5, 24, 8));
            }
            if (pose.hit() > 0) {
                g.setColor(new Color(255, 232, 183, Math.min(205, (int) (pose.hit() * 205))));
                g.setStroke(new BasicStroke(1.6f));
                g.draw(polygon(10, 10, 27, 10, 29, 38, 24, 44, 12, 44, 8, 38));
            }
        } finally { g.dispose(); }
        EnemyWarningRenderer.render(target, pose, DANGER);
        if (EnemyWarningRenderer.showHealth(pose)) {
            Graphics2D status = (Graphics2D) target.create();
            try {
                status.setColor(new Color(5, 16, 23, 225));
                status.fillRoundRect((int) pose.x(), (int) pose.y() - 8, (int) pose.width(), 4, 3, 3);
                status.setColor(COPPER); status.fillRect((int) pose.x(), (int) pose.y() - 8,
                        (int) (pose.width() * Math.max(0, pose.hp()) / Math.max(1, pose.maxHp())), 3);
            } finally { status.dispose(); }
        }
        return true;
    }

    /** The boss model separately draws the authoritative attack trajectory. */
    public static void renderBoss(Graphics2D target, BossVisual pose) {
        Graphics2D g = (Graphics2D) target.create();
        try {
            quality(g); g.translate(pose.x(), pose.y());
            g.scale(pose.width() / 120.0, pose.height() / 150.0);
            if (pose.hp() <= 0) opacity(g, .5f);
            boolean exposed = pose.vulnerableTicks() > 0;
            boolean warning = pose.warningTicks() > 0;
            Color energy = warning || pose.dashing() ? DANGER : exposed ? new Color(171, 225, 229)
                    : pose.stage() >= 3 ? new Color(221, 152, 107) : POWER;
            // Contact treads remain on the collision floor. Warning compresses the upper suspension.
            for (int side = 0; side < 2; side++) {
                int x = side == 0 ? 9 : 78;
                g.setColor(INK); g.fillRoundRect(x, 122, 34, 28, 6, 6);
                g.setPaint(new GradientPaint(x, 122, new Color(121, 132, 131), x, 148, new Color(24, 34, 41)));
                g.fillRoundRect(x + 3, 125, 28, 20, 5, 5);
                g.setColor(new Color(13, 24, 32)); g.fillRoundRect(x + 5, 129, 24, 12, 4, 4);
                g.setColor(new Color(145, 157, 148)); g.setStroke(new BasicStroke(1));
                double cycle = pose.dashing() ? pose.seconds() * 14 : pose.seconds() * .6;
                for (int wheel = 0; wheel < 3; wheel++) {
                    int wx = x + 10 + wheel * 8;
                    g.drawOval(wx - 4, 131, 7, 7);
                    g.drawLine(wx, 134, wx + (int) (Math.cos(cycle) * 3), 134 + (int) (Math.sin(cycle) * 3));
                }
            }
            Graphics2D chassis = (Graphics2D) g.create();
            try {
                chassis.translate(0, warning ? 3 : pose.dashing() ? 1 : Math.sin(pose.seconds() * 3) * .65);
                chassis.drawImage(EXECUTIONER, 0, 0, 120, 150, null);
                // Coiled hydraulic arms brace during a charge and relax when a fault exposes the core.
                for (int side = 0; side < 2; side++) {
                    int x = side == 0 ? 3 : 98;
                    int piston = exposed ? 7 : warning ? -3 : 0;
                    chassis.setColor(new Color(193, 198, 184)); chassis.fillRect(x + 7, 67, 4, 38 + piston);
                    chassis.setColor(INK); chassis.fillRoundRect(x, 92 + piston, 21, 24, 3, 3);
                    chassis.setPaint(new GradientPaint(x, 94, new Color(152, 160, 145), x + 19, 114, new Color(37, 51, 59)));
                    chassis.fillRoundRect(x + 2, 94 + piston, 17, 18, 3, 3);
                    chassis.setColor(COPPER); chassis.fillRect(x + 4, 99 + piston, 13, 3);
                }
                chassis.setColor(INK); chassis.fillRoundRect(33, 46, 54, 66, 10, 10);
                chassis.setPaint(new RadialGradientPaint(60, 79, 29, new float[]{0, .28f, 1},
                        new Color[]{new Color(230, 247, 229), energy, new Color(13, 34, 47)}));
                chassis.fillOval(39, 57, 43, 45);
                chassis.setColor(new Color(22, 41, 48)); chassis.setStroke(new BasicStroke(3));
                int rotor = (int) (pose.seconds() * (exposed ? 30 : 9));
                for (int i = 0; i < 3; i++) chassis.drawArc(39, 57, 43, 45, rotor + i * 120, 62);
                int opening = exposed ? 15 : 0;
                for (int side = 0; side < 2; side++) {
                    int x = side == 0 ? 33 - opening : 61 + opening;
                    chassis.setPaint(new GradientPaint(x, 47, new Color(165, 173, 151), x + 24, 109, new Color(48, 65, 70)));
                    chassis.fill(polygon(x, 47, x + 25, 53, x + 25, 106, x, 112));
                    chassis.setColor(new Color(213, 203, 159)); chassis.drawLine(x, 47, x + 24, 53);
                    chassis.setColor(new Color(24, 40, 47));
                    for (int slot = 0; slot < 4; slot++) chassis.fillRect(x + 5, 64 + slot * 9, 16, 3);
                    chassis.setColor(exposed ? energy : COPPER); chassis.fillRect(x + (side == 0 ? 22 : 1), 57, 2, 40);
                }
                chassis.setColor(energy); chassis.fillRoundRect(43, 26, 34, 6, 2, 2);
                chassis.setColor(LIGHT); chassis.fillRect(48, 26, 10, 1);
                for (int i = 0; i < 3; i++) {
                    chassis.setColor(i < pose.stage() ? COPPER : new Color(46, 58, 61));
                    chassis.fillRect(48 + i * 9, 119, 5, 3);
                }
                if (warning) {
                    chassis.setColor(DANGER); chassis.setStroke(new BasicStroke(2));
                    chassis.drawLine(9, 47, 22, 47); chassis.drawLine(98, 47, 111, 47);
                }
                if (pose.flashes() && pose.hitTicks() > 0) {
                    chassis.setColor(new Color(255, 238, 203, Math.min(190, pose.hitTicks() * 25)));
                    chassis.setStroke(new BasicStroke(2)); chassis.draw(polygon(25, 19, 40, 7, 80, 7, 96, 20, 99, 116, 83, 130, 35, 130, 22, 116));
                }
                if (pose.highContrast()) {
                    chassis.setColor(new Color(221, 228, 215, 190)); chassis.setStroke(new BasicStroke(1));
                    chassis.draw(polygon(24, 20, 39, 6, 81, 6, 97, 20, 115, 40, 115, 112, 96, 124, 23, 124, 3, 112, 3, 40));
                }
            } finally { chassis.dispose(); }
        } finally { g.dispose(); }
    }

    private static void interruptShell(Graphics2D g) {
        g.setColor(INK); g.fill(polygon(12, 8, 27, 8, 30, 19, 29, 39, 24, 46, 11, 46, 7, 37, 7, 17));
        g.setPaint(new GradientPaint(8, 10, new Color(175, 185, 175), 28, 43, new Color(34, 51, 61)));
        g.fill(polygon(12, 10, 25, 10, 27, 20, 26, 37, 22, 42, 13, 42, 10, 36, 10, 18));
        g.setColor(COPPER); g.fillRect(13, 39, 10, 3);
        g.setColor(new Color(44, 61, 69)); g.setStroke(new BasicStroke(3));
        g.drawLine(5, 21, 13, 24); g.drawLine(24, 23, 31, 21);
        for (int x : new int[]{1, 27}) {
            g.setColor(INK); g.fillRoundRect(x, 10, 8, 28, 3, 3);
            g.setColor(new Color(128, 141, 139)); g.fillRect(x + 1, 11, 6, 3); g.fillRect(x + 1, 34, 6, 3);
            g.setColor(COPPER); for (int y = 16; y <= 31; y += 4) g.fillRect(x + 1, y, 6, 2);
        }
        g.setColor(new Color(146, 164, 164)); g.setStroke(new BasicStroke(1.5f));
        g.drawLine(12, 11, 12, 4); g.drawLine(24, 11, 24, 3);
        g.setColor(COPPER); g.fillOval(10, 2, 4, 4); g.fillOval(22, 1, 4, 4);
        bolts(g, 12, 21, 25, 21, 13, 37, 24, 37);
    }

    private static void executionerShell(Graphics2D g) {
        g.setColor(INK); g.fill(polygon(23, 19, 38, 4, 82, 4, 97, 19, 101, 117, 85, 135, 32, 135, 19, 117));
        g.setPaint(new GradientPaint(22, 10, new Color(154, 166, 155), 96, 132, new Color(36, 53, 64)));
        g.fill(polygon(27, 22, 40, 8, 79, 8, 93, 22, 96, 115, 82, 129, 35, 129, 24, 114));
        g.setColor(new Color(210, 211, 181)); g.setStroke(new BasicStroke(2));
        g.drawLine(28, 21, 40, 9); g.drawLine(41, 9, 77, 9);
        g.setColor(INK); g.fillRoundRect(36, 19, 49, 21, 5, 5);
        g.setColor(new Color(108, 129, 134)); g.drawRoundRect(37, 20, 47, 19, 5, 5);
        g.setColor(new Color(36, 51, 57)); g.fillRoundRect(31, 113, 59, 14, 4, 4);
        for (int side = 0; side < 2; side++) {
            int x = side == 0 ? 2 : 96;
            g.setColor(INK); g.fill(polygon(x, 40, x + 9, 31, x + 22, 34, x + 22, 76, x, 76));
            g.setPaint(new GradientPaint(x, 34, new Color(153, 163, 146), x + 22, 73, new Color(43, 61, 70)));
            g.fill(polygon(x + 3, 42, x + 10, 35, x + 19, 37, x + 19, 71, x + 3, 71));
            g.setColor(COPPER); g.fillRect(x + 4, 42, 15, 10);
            g.setColor(new Color(40, 45, 44));
            for (int k = 0; k < 3; k++) g.fillPolygon(new int[]{x + 5 + k * 5, x + 8 + k * 5, x + 4 + k * 5, x + 2 + k * 5}, new int[]{42, 42, 52, 52}, 4);
            g.setColor(INK); g.fillOval(x + 5, 56, 12, 12);
            g.setColor(new Color(152, 167, 159)); g.drawOval(x + 7, 58, 8, 8);
            g.setColor(INK); g.fillRect(x + 2, 76, 20, 14);
        }
        bolts(g, 30, 25, 91, 25, 30, 110, 91, 110, 38, 124, 83, 124);
    }

    private static void bolts(Graphics2D g, int... points) {
        for (int i = 0; i < points.length; i += 2) {
            g.setColor(INK); g.fillOval(points[i] - 2, points[i + 1] - 2, 4, 4);
            g.setColor(LIGHT); g.fillRect(points[i] - 1, points[i + 1] - 1, 2, 1);
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
    private static void opacity(Graphics2D g, float factor) {
        if (g.getComposite() instanceof AlphaComposite old) g.setComposite(old.derive(old.getAlpha() * factor));
    }
}
