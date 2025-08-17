package com.bigphil.mergehell;

import java.awt.*;

public class Obstacle {
    private int x;
    private int y;
    private final int width = 30;
    private final int height = 40;

    public Obstacle(int startX) {
        this.x = startX;
    }

    public void moveLeft() {
        x -= 5;
    }

    public void draw(Graphics g) {
        // 设置动态地面（底部减去障碍高度）
        y = g.getClipBounds().height - height - 10;

        g.setColor(Color.RED);
        g.fillRect(x, y, width, height);
        g.setColor(Color.WHITE);
        g.drawString("MERGE", x + 2, y + 20);
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
}
