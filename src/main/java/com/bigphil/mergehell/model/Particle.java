package com.bigphil.mergehell.model;

import java.awt.*;

public class Particle {
    private double x, y, vx, vy;
    private float life = 1.0f;
    private final float decay;
    private final Color color;

    public Particle(double x, double y, Color color, double vx, double vy, float decay) {
        this.x = x;
        this.y = y;
        this.color = color;
        this.vx = vx;
        this.vy = vy;
        this.decay = decay;
    }

    public float getLife() {
        return life;
    }

    public void update() {
        x += vx;
        y += vy;
        vy += 0.2;
        life -= decay;
    }

    public void draw(Graphics2D g) {
        if (life <= 0) return;
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0, life)));
        g.setColor(color);
        g.fillRect((int) x, (int) y, 4, 4);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
    }
}
