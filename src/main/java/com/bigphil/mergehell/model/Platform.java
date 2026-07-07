package com.bigphil.mergehell.model;

public class Platform {
    public final double x, y;
    public final int width, height;

    public Platform(double x, double y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public boolean canStandOn(double playerLeft, double playerBottom, double playerRight) {
        return playerRight > x && playerLeft < x + width
                && playerBottom >= y && playerBottom <= y + height + 12;
    }
}
