package com.bigphil.mergehell.model;

public class Platform {
    public final double x, y;
    public final int width, height;
    public final Style style;

    public enum Style {
        BASIC, ROOFTOP, CATWALK, PIPE, SERVER_BANK, CABLE, RUBBLE, FORTIFICATION
    }

    public Platform(double x, double y, int width, int height) {
        this(x, y, width, height, Style.BASIC);
    }

    public Platform(double x, double y, int width, int height, Style style) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.style = style == null ? Style.BASIC : style;
    }

    public boolean canStandOn(double playerLeft, double playerBottom, double playerRight) {
        return playerRight > x && playerLeft < x + width
                && playerBottom >= y && playerBottom <= y + height + 12;
    }
}
