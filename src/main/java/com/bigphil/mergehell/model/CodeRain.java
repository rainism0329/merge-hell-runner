package com.bigphil.mergehell.model;

import java.awt.*;

public class CodeRain {
    private String text;
    private double x, y;
    private double speed;
    private float alpha;

    private static final String[] SNIPPETS = {
            "public void fix() {", "return null;", "throw new Exception();",
            "// TODO: Remove this", "git merge master", "Segmentation fault",
            "System.exit(0);", "if (bug) panic();", "while(true) {", ">> HEAD"
    };

    public CodeRain(double startWorldX, int maxY) {
        reset(startWorldX, maxY, 0);
    }

    public void reset(double screenRight, int maxY, double cameraX) {
        this.text = SNIPPETS[(int) (Math.random() * SNIPPETS.length)];
        this.x = cameraX + Math.random() * (screenRight - cameraX + 300);
        this.y = 50 + Math.random() * (maxY - 100);
        this.speed = 2 + Math.random() * 3;
        this.alpha = 0.04f + (float) Math.random() * 0.06f;
    }

    public void update(double screenWidth, int maxY, double speedMult, double cameraX) {
        x -= speed * speedMult;
        if (x < cameraX - 200) reset(cameraX + screenWidth, maxY, cameraX);
    }

    public void draw(Graphics2D g) {
        g.setColor(new Color(1f, 1f, 1f, alpha));
        g.drawString(text, (int) x, (int) y);
    }
}
