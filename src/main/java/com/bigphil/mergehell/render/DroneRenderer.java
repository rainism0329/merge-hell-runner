package com.bigphil.mergehell.render;

import com.bigphil.mergehell.combat.DroneController;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/** Friendly mechanical companions. Every moving visual value comes from the simulation snapshot. */
public final class DroneRenderer {
    private static final int CACHE_EDGE = 48;
    private static final int CACHE_SCALE = 4;
    private static final Color INK = new Color(13, 19, 22);
    private static final Color CYAN = new Color(91, 237, 235);
    private static final Color PALE = new Color(225, 255, 242);
    private static final BufferedImage STANDARD_BODY = buildBody(false);
    private static final BufferedImage ARMORED_BODY = buildBody(true);

    /** Caller supplies world coordinates, including any camera transform. No clock or file access. */
    public void render(Graphics2D target, DroneController.Snapshot snapshot) {
        for (DroneController.Pose pose : snapshot.drones()) {
            Graphics2D g = (Graphics2D) target.create();
            try {
                g.translate(pose.x(), pose.y());
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                thrusters(g, pose.thrust());
                if (pose.rank() >= 3) armorRing(g);
                Graphics2D body = (Graphics2D) g.create();
                try {
                    if (Math.cos(pose.aimRadians()) < 0) body.scale(-1, 1);
                    body.drawImage(pose.rank() >= 3 ? ARMORED_BODY : STANDARD_BODY,
                            -CACHE_EDGE / 2, -CACHE_EDGE / 2, CACHE_EDGE, CACHE_EDGE, null);
                } finally { body.dispose(); }
                cannon(g, pose.aimRadians(), pose.recoil());
                muzzleFlash(g, pose);
            } finally { g.dispose(); }
        }
    }

    private static void thrusters(Graphics2D g, double thrust) {
        double power = Math.max(0, Math.min(1, thrust));
        double length = 3 + power * 7;
        for (int x : new int[]{-11, 11}) {
            g.setColor(new Color(48, 218, 225, (int) (22 + 28 * power)));
            g.fill(new Ellipse2D.Double(x - 5, 8, 10, length + 5));
            Shape flame = polygon(x - 3, 9, x - 2, 9 + length * .6, x, 10 + length,
                    x + 2, 9 + length * .6, x + 3, 9);
            g.setPaint(new GradientPaint(x, 9, new Color(115, 255, 244, 220),
                    x, (float) (10 + length), new Color(26, 153, 197, 0)));
            g.fill(flame);
            g.setColor(new Color(222, 255, 240, 220));
            g.fill(new RoundRectangle2D.Double(x - .9, 9, 1.8, 2 + power * 3.5, 1, 1));
        }
    }

    private static void armorRing(Graphics2D g) {
        g.setColor(new Color(60, 223, 220, 28));
        g.setStroke(new BasicStroke(3));
        g.drawOval(-18, -14, 36, 30);
        g.setColor(new Color(124, 250, 232, 155));
        g.setStroke(new BasicStroke(1));
        g.drawArc(-18, -14, 36, 30, 22, 122);
        g.drawArc(-18, -14, 36, 30, 202, 122);
    }

    private static void cannon(Graphics2D target, double aim, double recoil) {
        Graphics2D g = (Graphics2D) target.create();
        try {
            g.rotate(aim);
            double kick = Math.max(0, Math.min(1, recoil)) * 2.1;
            double muzzle = DroneController.MUZZLE_LENGTH;
            // The receiver slides back on a telescoping rail. The muzzle collar stays at the
            // authoritative 18px emission point, including on the very frame a shot is spawned.
            Shape rail = polygon(1, -2, muzzle - 3, -2, muzzle - 3, 2, 1, 2);
            g.setColor(INK); g.setStroke(new BasicStroke(1.4f)); g.draw(rail);
            g.setPaint(new GradientPaint(0, -2, new Color(159, 163, 150), 0, 2, new Color(40, 50, 53)));
            g.fill(rail);
            Shape housing = polygon(-4 - kick, -3.4, 6 - kick, -3.4, 10 - kick, -1.8,
                    10 - kick, 2.4, -3 - kick, 3.4, -5 - kick, 1);
            g.setColor(INK); g.setStroke(new BasicStroke(1.4f)); g.draw(housing);
            g.setPaint(new GradientPaint(0, -3, new Color(194, 132, 60), 0, 3, new Color(75, 55, 39)));
            g.fill(housing);
            g.setColor(new Color(242, 200, 126)); g.setStroke(new BasicStroke(.7f));
            g.drawLine((int) (-3 - kick), -3, (int) (5 - kick), -3);
            g.setColor(new Color(23, 36, 41));
            for (int i = 0; i < 3; i++) g.drawLine((int) (2 + i * 2 - kick), -1, (int) (2 + i * 2 - kick), 2);
            g.setPaint(new GradientPaint(0, -3, new Color(193, 203, 186), 0, 3, new Color(38, 48, 52)));
            g.fill(new RoundRectangle2D.Double(muzzle - 4, -3, 4, 6, 1.3, 1.3));
            g.setColor(INK); g.setStroke(new BasicStroke(1));
            g.drawLine((int) muzzle, -2, (int) muzzle, 2);
            g.setColor(CYAN); g.drawLine((int) muzzle - 3, -1, (int) muzzle - 3, 1);
            g.setPaint(new GradientPaint(-3, -3, new Color(176, 181, 161), 3, 3, new Color(35, 46, 50)));
            g.fill(new Ellipse2D.Double(-4, -4, 8, 8));
            g.setColor(INK); g.draw(new Ellipse2D.Double(-4, -4, 8, 8));
            g.setColor(new Color(104, 242, 226)); g.fill(new Ellipse2D.Double(-1.3, -1.3, 2.6, 2.6));
        } finally { g.dispose(); }
    }

    private static void muzzleFlash(Graphics2D target, DroneController.Pose pose) {
        if (pose.recoil() < .72) return;
        Graphics2D g = (Graphics2D) target.create();
        try {
            // Use the provided world-space emission point; never infer it from an image edge.
            g.translate(pose.muzzleX() - pose.x(), pose.muzzleY() - pose.y());
            g.rotate(pose.aimRadians());
            double strength = Math.max(0, Math.min(1, (pose.recoil() - .72) / .28));
            g.setColor(new Color(80, 232, 242, (int) (110 * strength)));
            g.fill(new Ellipse2D.Double(-3, -5, 15, 10));
            g.setColor(new Color(106, 248, 246, (int) (235 * strength)));
            g.fill(polygon(-1, -2, 3, -3, 8, 0, 3, 3, -1, 2, 1, 0));
            g.setColor(PALE);
            g.fill(polygon(-.7, -1, 4, -1.3, 6, 0, 4, 1.3, -.7, 1));
            g.fill(new Ellipse2D.Double(-1, -1, 2, 2));
        } finally { g.dispose(); }
    }

    private static BufferedImage buildBody(boolean armored) {
        BufferedImage image = new BufferedImage(CACHE_EDGE * CACHE_SCALE, CACHE_EDGE * CACHE_SCALE,
                BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = image.createGraphics();
        try {
            g.scale(CACHE_SCALE, CACHE_SCALE); g.translate(CACHE_EDGE / 2.0, CACHE_EDGE / 2.0);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            for (int x : new int[]{-11, 11}) {
                g.setColor(INK); g.fillRoundRect(x - 4, 2, 8, 11, 3, 3);
                g.setPaint(new GradientPaint(x - 4, 4, new Color(158, 161, 142), x + 3, 10, new Color(31, 43, 48)));
                g.fillRoundRect(x - 3, 3, 6, 8, 2, 2);
                g.setColor(new Color(234, 166, 66)); g.fillRect(x - 3, 5, 6, 2);
                g.setColor(new Color(9, 25, 30)); g.fillOval(x - 3, 8, 6, 4);
                g.setColor(CYAN); g.fillRect(x - 2, 10, 4, 1);
            }
            Shape hull = polygon(-15, -2, -10, -8, 8, -8, 14, -3, 13, 4, 8, 7, -10, 6, -15, 2);
            g.setColor(INK); g.setStroke(new BasicStroke(2)); g.draw(hull);
            g.setPaint(new GradientPaint(-4, -8, new Color(237, 173, 76), 3, 7, new Color(120, 73, 34)));
            g.fill(hull);
            g.setColor(new Color(73, 56, 38)); g.fill(polygon(-14, 1, -9, 3, 11, 3, 8, 7, -10, 6));
            g.setColor(new Color(255, 218, 143)); g.setStroke(new BasicStroke(.8f));
            g.draw(polygon(-13, -2, -9, -7, 7, -7, 12, -3));
            g.setColor(new Color(43, 49, 45)); g.fillRoundRect(-10, -4, 7, 5, 1, 1);
            g.setColor(new Color(141, 139, 110));
            for (int x = -9; x < -3; x += 2) g.drawLine(x, -3, x, 0);
            // Friendly glass eye is high on the front shell, clear of the rotating center turret.
            g.setColor(new Color(9, 31, 37)); g.fillRoundRect(2, -7, 9, 5, 2, 2);
            g.setColor(new Color(59, 187, 202)); g.fillRoundRect(3, -6, 7, 3, 1, 1);
            g.setColor(new Color(154, 255, 242)); g.fillRect(4, -6, 5, 2);
            g.setColor(new Color(237, 255, 239)); g.fillRect(4, -6, 2, 1);
            for (int x : new int[]{-12, 10}) {
                g.setColor(new Color(25, 33, 32)); g.fill(new Ellipse2D.Double(x - 1, 1, 2.2, 2.2));
                g.setColor(new Color(206, 189, 139)); g.fill(new Ellipse2D.Double(x - .7, 1, 1, .7));
            }
            // Fixed wear marks are rasterized once; neither drawing nor cache creation uses RNG.
            g.setColor(new Color(53, 46, 37, 210)); g.setStroke(new BasicStroke(.6f));
            for (int[] mark : new int[][]{{-7,-7,-5,-6},{-2,-6,0,-6},{10,-2,12,-1},{-13,3,-11,4},{6,4,8,4}})
                g.drawLine(mark[0], mark[1], mark[2], mark[3]);
            if (armored) {
                for (int side : new int[]{-1, 1}) {
                    Shape plate = polygon(side * 11, -6, side * 17, -3, side * 18, 3,
                            side * 14, 6, side * 11, 3);
                    g.setColor(INK); g.setStroke(new BasicStroke(1.3f)); g.draw(plate);
                    g.setPaint(new GradientPaint(0, -6, new Color(247, 187, 96), 0, 6, new Color(130, 85, 46)));
                    g.fill(plate);
                    g.setColor(new Color(209, 218, 188)); g.drawLine(side * 14, -3, side * 16, 2);
                    g.setColor(CYAN); g.fillRect(side < 0 ? -16 : 14, 1, 2, 3);
                }
                g.setColor(new Color(23, 43, 47)); g.fillRoundRect(-3, -10, 7, 3, 1, 1);
                g.setColor(CYAN); g.fillRect(-1, -10, 3, 1);
            }
        } finally { g.dispose(); }
        return image;
    }

    private static Path2D polygon(double... points) {
        Path2D path = new Path2D.Double(); path.moveTo(points[0], points[1]);
        for (int i = 2; i < points.length; i += 2) path.lineTo(points[i], points[i + 1]);
        path.closePath();
        return path;
    }
}
