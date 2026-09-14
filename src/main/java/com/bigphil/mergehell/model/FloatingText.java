package com.bigphil.mergehell.model;

import com.bigphil.mergehell.i18n.GameText;

import java.awt.*;

public class FloatingText {
    private double x, y;
    private final String text;
    private final Color color;
    private double life = 1.0;
    private final double vy;

    public FloatingText(double x, double y, String text, Color color) {
        this.x = x;
        this.y = y;
        this.text = text;
        this.color = color;
        this.vy = -1.0 - Math.random(); // 向上飘
    }

    public boolean update() {
        y += vy;
        life -= 0.02;
        return life > 0;
    }

    public void draw(Graphics2D g) {
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.max(0, life)));
        g.setColor(color);
        g.setFont(GameText.font(new Font("JetBrains Mono", Font.BOLD, 14)));
        GameText.draw(g, text, (int) x, (int) y);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
    }
}