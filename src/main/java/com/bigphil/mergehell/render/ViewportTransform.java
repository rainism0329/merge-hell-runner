package com.bigphil.mergehell.render;

public record ViewportTransform(
        double scale, int offsetX, int offsetY, int drawWidth, int drawHeight) {
    public boolean containsPhysical(int x, int y) {
        return x >= offsetX && y >= offsetY && x < offsetX + drawWidth && y < offsetY + drawHeight;
    }
    public int logicalX(int x) { return (int) Math.floor((x - offsetX) / scale); }
    public int logicalY(int y) { return (int) Math.floor((y - offsetY) / scale); }
}
