package com.bigphil.mergehell.model;

import java.awt.*;

public class CodeRain {
    private String text;
    private double x, y;
    private double speed;
    private float alpha;

    public CodeRain(int startX, int maxY) {
        reset(startX, maxY);
    }

    private static final String[] SNIPPETS = {
            "public void fix() {", "return null;", "throw new Exception();",
            "// TODO: Remove this", "git merge master", "Segmentation fault",
            "System.exit(0);", "if (bug) panic();", "while(true) {", ">> HEAD"
    };

    public void reset(int startX, int maxY) {
        this.text = SNIPPETS[(int) (Math.random() * SNIPPETS.length)];
        this.x = startX + Math.random() * 300;
        this.y = 50 + Math.random() * (maxY - 100);
        this.speed = 2 + Math.random() * 3;
        this.alpha = 0.1f + (float) Math.random() * 0.2f;
    }

    public void update(int width, int maxY, double speedMult) {
        x -= speed * speedMult;
        if (x < -200) reset(width, maxY);
    }

    public void draw(Graphics2D g) {
        g.setColor(new Color(1f, 1f, 1f, alpha));
        g.drawString(text, (int) x, (int) y);
    }
}
