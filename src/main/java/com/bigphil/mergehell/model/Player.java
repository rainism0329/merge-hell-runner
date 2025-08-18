package com.bigphil.mergehell.model;

import java.awt.*;

public class Player {

    private final int width = 40;
    private final int height = 60;

    private int x;
    private int y;
    private int velocityY;
    private final int gravity = 1;

    private final int startX;
    private final int groundY;

    private boolean jumpPressed;

    public Player(int startX, int groundY) {
        this.startX = startX;
        this.groundY = groundY;
        this.x = startX;
        this.y = groundY - height;
        this.velocityY = 0;
        this.jumpPressed = false;
    }

    public void reset() {
        this.x = startX;
        this.y = groundY - height;
        this.velocityY = 0;
        this.jumpPressed = false;
    }

    public void update(int panelWidth, int panelHeight, boolean moveLeft, boolean moveRight) {
        // Apply gravity
        velocityY += gravity;
        y += velocityY;

        // Floor collision
        int groundLevel = panelHeight - height;
        if (y > groundLevel) {
            y = groundLevel;
            velocityY = 0;
        }

        // Horizontal movement
        if (moveLeft) {
            x -= 5;
        }
        if (moveRight) {
            x += 5;
        }

        // Prevent going off screen (left)
        if (x < 0) {
            x = 0;
        }

        // Prevent going off screen (right)
        if (x + width > panelWidth) {
            x = panelWidth - width;
        }
    }

    public void jump() {
        if (velocityY == 0 && !jumpPressed) {
            velocityY = -15;
            jumpPressed = true;
        }
    }

    public void releaseJump() {
        jumpPressed = false;
    }

    public void draw(Graphics g) {
        g.setColor(Color.CYAN);
        g.fillRect(x, y, width, height);
    }

    public Rectangle getBounds() {
        return new Rectangle(x, y, width, height);
    }

    // Getters
    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}
