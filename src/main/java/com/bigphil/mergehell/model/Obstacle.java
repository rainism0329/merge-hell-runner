package com.bigphil.mergehell.model;

import java.awt.*;

public class Obstacle {

    protected int x, y, width, height;
    protected Color color = Color.RED;

    public Obstacle(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void update() {
        x -= 5; // Default scrolling speed
    }

    public boolean isOffScreen() {
        return x + width < 0;
    }

    public boolean checkCollision(Rectangle playerBounds) {
        return getBounds().intersects(playerBounds);
    }

    public void draw(Graphics g) {
        g.setColor(color);
        g.fillRect(x, y, width, height);
        g.setColor(Color.WHITE);
        g.drawString("MERGE", x + 2, y + 30);
    }

    public Rectangle getBounds() {
        return new Rectangle(x, y, width, height);
    }

    public int getRightEdge() {
        return x + width;
    }
}
