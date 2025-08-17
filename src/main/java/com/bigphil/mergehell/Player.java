package com.bigphil.mergehell;

import java.awt.*;

public class Player {
    private int x = 50, y;
    private final int width = 40;
    private final int height = 40;
    private int velocityY = 0;
    private boolean isJumping = false;
    private boolean firstDraw = true;

    private int groundY = 0; // 动态地面高度

    public void update() {
        if (isJumping) {
            y += velocityY;
            velocityY += 1;
            if (y >= groundY) {
                y = groundY;
                isJumping = false;
            }
        }
    }

    public void jump() {
        if (!isJumping) {
            velocityY = -15;
            isJumping = true;
        }
    }

    public void moveLeft() {
        x -= 5;
        if (x < 0) x = 0;
    }

    public void moveRight() {
        x += 5;
        if (x > 800 - width) x = 800 - width;
    }

    public void draw(Graphics g) {
        groundY = g.getClipBounds().height - height - 10;

        if (firstDraw) {
            y = groundY;
            firstDraw = false;
        }

        g.setColor(Color.BLUE);
        g.fillRect(x, y, width, height);
    }


    public boolean collidesWith(Obstacle o) {
        Rectangle r1 = new Rectangle(x, y, width, height);
        Rectangle r2 = new Rectangle(o.getX(), o.getY(), o.getWidth(), o.getHeight());
        return r1.intersects(r2);
    }

    public void reset() {
        velocityY = 0;
        isJumping = false;
        firstDraw = true;
    }
}
