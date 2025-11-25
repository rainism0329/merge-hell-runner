package com.bigphil.mergehell.model;

import java.awt.*;

public class Particle {
    public double x, y, vx, vy;
    public float life = 1.0f;
    public float decay;
    public Color color;

    public Particle(double x, double y, Color color, double vx, double vy, float decay) {
        this.x = x;
        this.y = y;
        this.color = color;
        this.vx = vx;
        this.vy = vy;
        this.decay = decay;
    }

    public void update() {
        x += vx;
        y += vy;
        vy += 0.2; // 简单的重力模拟
        life -= decay;
    }

    public void draw(Graphics2D g) {
        if (life <= 0) return;
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0, life)));
        g.setColor(color);
        g.fillRect((int)x, (int)y, 4, 4);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
    }
}