package com.bigphil.mergehell.render;

import com.bigphil.mergehell.model.EntityType;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;

/** Enemy tells describe intent, not a complete solution to dodging the future projectile. */
public final class EnemyWarningRenderer {
    private EnemyWarningRenderer() { }

    public static boolean showHealth(ActorVisuals.Hostile pose) {
        return pose.death() <= 0 && pose.hp() < pose.maxHp() && pose.hp() > 0;
    }

    /** Presentation only: all positions and timing come from the existing committed simulation pose. */
    public static void render(Graphics2D target, ActorVisuals.Hostile pose, Color color) {
        if (pose.warning() <= 0 || pose.death() > 0) return;
        Graphics2D g = (Graphics2D) target.create();
        try {
            var t = pose.tactics();
            double progress = 1 - Math.min(1, pose.warning() / (double) Math.max(1,
                    t.totalTicks() > 0 ? t.totalTicks() : 30));
            double x = pose.x() + pose.width() * (pose.facing() < 0 ? .12 : .88);
            double y = pose.y() + pose.height() * .43;
            // Ordinary shooters reveal charge at their attached optic. Split volleys do not
            // acquire floating reticles at every future projectile origin.
            if (pose.type() != EntityType.DRILLER) charge(g, x, y, color, progress);
            switch (pose.type()) {
                case SENTINEL -> {
                    // A fast, piercing sniper shot merits a short direction cue. It does not
                    // extend to the player and never predicts the entire flight path.
                    if (!t.lanes().isEmpty()) {
                        var lane = t.lanes().get(0);
                        double length = Math.hypot(lane.velocityX(), lane.velocityY());
                        if (length > .001) {
                            double dx = lane.velocityX() / length, dy = lane.velocityY() / length;
                            double ox = lane.x() + lane.projectileType().width / 2.0;
                            double oy = lane.y() + lane.projectileType().height / 2.0;
                            g.setColor(alpha(color, 125 + (int) (progress * 90)));
                            g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                            for (int start : new int[]{17, 35})
                                g.draw(new Line2D.Double(ox + dx * start, oy + dy * start,
                                        ox + dx * (start + 9), oy + dy * (start + 9)));
                        }
                    }
                }
                case WARDEN -> {
                    if (t.totalTicks() > 0) {
                        double left = Math.min(t.originX(), t.targetX());
                        double right = Math.max(t.originX(), t.targetX()) + pose.width();
                        groundBand(g, left, right, pose.y() + pose.height() - 2, color, progress);
                    }
                }
                case INTERRUPT, LURKER -> {
                    if (t.totalTicks() > 0)
                        groundBand(g, t.targetX(), t.targetX() + pose.width(),
                                t.targetY() + pose.height() - 2, color, progress);
                }
                case DRILLER -> {
                    double floor = pose.y() + pose.height() - 2;
                    groundBand(g, pose.x() - 4, pose.x() + pose.width() + 4, floor, color, progress);
                    // Dust fissures stay on the surface; the body remains hidden until emergence.
                    g.setColor(alpha(color, 130 + (int) (progress * 100)));
                    for (int i = 0; i < 3; i++) {
                        double cx = pose.x() + pose.width() * (.2 + i * .3);
                        Path2D crack = new Path2D.Double(); crack.moveTo(cx - 4, floor);
                        crack.lineTo(cx, floor - 3); crack.lineTo(cx - 2, floor - 7 - progress * 4);
                        g.draw(crack);
                    }
                }
                default -> { /* Small direct and ballistic shots use the attached charge only. */ }
            }
        } finally { g.dispose(); }
    }

    private static void charge(Graphics2D g, double x, double y, Color color, double progress) {
        double r = 3.2 + progress * 2.3;
        g.setColor(alpha(color, 45 + (int) (progress * 30)));
        g.fill(new Ellipse2D.Double(x - r * 1.5, y - r * 1.5, r * 3, r * 3));
        g.setColor(alpha(color, 170 + (int) (progress * 65)));
        g.fill(new Ellipse2D.Double(x - r / 2, y - r / 2, r, r));
        g.setColor(new Color(255, 246, 210, 195));
        g.fill(new Ellipse2D.Double(x - .8, y - .8, 1.6, 1.6));
    }

    private static void groundBand(Graphics2D g, double left, double right, double y,
                                   Color color, double progress) {
        if (!Double.isFinite(left) || !Double.isFinite(right) || right <= left) return;
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(alpha(color, 30)); g.fill(new Ellipse2D.Double(left, y - 4, right - left, 7));
        g.setColor(alpha(color, 155 + (int) (progress * 80)));
        g.draw(new Line2D.Double(left, y, right, y));
        for (double end : new double[]{left, right})
            g.draw(new Line2D.Double(end, y - 5, end, y + 1));
    }

    private static Color alpha(Color color, int opacity) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), opacity);
    }
}
