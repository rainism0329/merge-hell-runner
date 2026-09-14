package com.bigphil.mergehell.boss;

import com.bigphil.mergehell.model.Projectile;
import com.bigphil.mergehell.model.ProjectileType;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;

/** A real, budgeted mortar shell travelling along the announced 55-step ballistic arc. */
public final class MagmaMortarProjectile extends Projectile {
    public static final int FLIGHT_TICKS = 55;
    private final double originX, originY, destinationX, destinationY;
    private double x, y, previousX, previousY;
    private int age;

    public MagmaMortarProjectile(double x, double y, double destinationX, double destinationY) {
        super(x, y, 0, 0, ProjectileType.ENEMY);
        originX = this.x = previousX = x;
        originY = this.y = previousY = y;
        this.destinationX = destinationX;
        this.destinationY = destinationY;
    }

    @Override public double getX() { return x; }
    @Override public double getY() { return y; }
    @Override public double getVx() { return x - previousX; }
    @Override public double getVy() { return y - previousY; }
    @Override public Rectangle getBounds() { return new Rectangle((int) x, (int) y, 18, 18); }
    @Override public void update() {
        if (isDead()) return;
        if (age >= FLIGHT_TICKS + 1) { setDead(true); return; }
        previousX = x; previousY = y;
        double t = Math.min(1, ++age / (double) FLIGHT_TICKS);
        x = originX + (destinationX - originX) * t;
        y = originY + (destinationY - originY) * t - 160 * 4 * t * (1 - t);
    }
    @Override public double hitFraction(Rectangle target) {
        // These shots travel at < 20 pixels/step. Exact swept slabs also support interception.
        double entry = 0, exit = 1;
        double[] from = {previousX, previousY}, delta = {x - previousX, y - previousY};
        double[] low = {target.getMinX() - 18 + 1e-9, target.getMinY() - 18 + 1e-9};
        double[] high = {target.getMaxX() - 1e-9, target.getMaxY() - 1e-9};
        for (int i = 0; i < 2; i++) {
            if (Math.abs(delta[i]) < 1e-12) {
                if (from[i] < low[i] || from[i] > high[i]) return Double.POSITIVE_INFINITY;
            } else {
                double a = (low[i] - from[i]) / delta[i], b = (high[i] - from[i]) / delta[i];
                entry = Math.max(entry, Math.min(a, b)); exit = Math.min(exit, Math.max(a, b));
            }
        }
        return entry <= exit ? entry : Double.POSITIVE_INFINITY;
    }
    @Override public void draw(Graphics2D target) {
        Graphics2D g = (Graphics2D) target.create();
        try {
            g.setColor(new Color(254, 121, 41, 90));
            g.setStroke(new BasicStroke(5));
            g.drawLine((int) x + 9, (int) y + 9, (int) (x - getVx() * 2) + 9, (int) (y - getVy() * 2) + 9);
            g.setColor(new Color(74, 48, 32)); g.fillOval((int) x, (int) y, 18, 18);
            g.setColor(new Color(255, 170, 58)); g.setStroke(new BasicStroke(2));
            g.drawOval((int) x + 1, (int) y + 1, 16, 16);
            g.setColor(new Color(255, 239, 173)); g.fillOval((int) x + 6, (int) y + 5, 6, 6);
        } finally { g.dispose(); }
    }
}
