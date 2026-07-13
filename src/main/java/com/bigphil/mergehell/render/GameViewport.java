package com.bigphil.mergehell.render;

public final class GameViewport {
    public static final int LOGICAL_WIDTH = 960;
    public static final int LOGICAL_HEIGHT = 600;

    public static ViewportTransform fit(int panelWidth, int panelHeight) {
        if (panelWidth <= 0 || panelHeight <= 0) return new ViewportTransform(1, 0, 0, 0, 0);
        double scale = Math.min(panelWidth / (double) LOGICAL_WIDTH,
                panelHeight / (double) LOGICAL_HEIGHT);
        int drawWidth = (int) Math.round(LOGICAL_WIDTH * scale);
        int drawHeight = (int) Math.round(LOGICAL_HEIGHT * scale);
        return new ViewportTransform(scale, (panelWidth - drawWidth) / 2,
                (panelHeight - drawHeight) / 2, drawWidth, drawHeight);
    }

    private GameViewport() { }
}
