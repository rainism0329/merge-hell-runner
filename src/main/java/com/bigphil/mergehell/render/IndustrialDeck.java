package com.bigphil.mergehell.render;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Random;

/** Small code-authored material tiles, rasterized once rather than rebuilding wear every frame. */
public final class IndustrialDeck {
    private static final int TILE = 112;
    private final BufferedImage[] tiles = {buildTile(0), buildTile(1), buildTile(2)};

    public void draw(Graphics2D g, int viewportWidth, int groundY, double cameraX) {
        int first = Math.floorDiv((int) cameraX, TILE) * TILE - TILE;
        for (int x = first; x < cameraX + viewportWidth + TILE; x += TILE)
            g.drawImage(tiles[Math.floorMod(x / TILE, tiles.length)], x, groundY, null);
    }

    private static BufferedImage buildTile(int variant) {
        BufferedImage image = new BufferedImage(TILE, 100, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setPaint(new GradientPaint(0, 0, new Color(85, 74, 58), 0, 70, new Color(22, 28, 31)));
            g.fillRect(0, 0, TILE, 70);
            g.setColor(new Color(8, 12, 15)); g.fillRect(0, 5, 4, 64);
            g.setColor(new Color(129, 112, 79)); g.drawRect(5, 9, 101, 49);
            g.setPaint(new GradientPaint(8, 14, new Color(44, 49, 48), 99, 54, new Color(16, 23, 28)));
            g.fillRect(8, 13, 94, 42);
            g.setColor(new Color(111, 90, 61, 120)); g.drawLine(8, 13, 101, 13);
            Random wear = new Random(0xDEC0DE + variant * 9181L);
            for (int i = 0; i < 135; i++) {
                int x = 5 + wear.nextInt(102), y = 7 + wear.nextInt(56);
                g.setColor(i % 3 == 0 ? new Color(146, 88, 43, 58) : new Color(8, 12, 15, 65));
                g.fillRect(x, y, 1 + wear.nextInt(4), 1 + wear.nextInt(2));
            }
            g.setColor(new Color(7, 13, 17, 205)); g.fillRect(21, 24, 62, 18);
            g.setColor(new Color(74, 78, 70));
            for (int x = 25; x < 81; x += 7) g.drawLine(x, 25, x - 3, 41);
            for (int x : new int[]{11, 98}) for (int y : new int[]{16, 51}) {
                g.setColor(new Color(5, 11, 14)); g.fillOval(x - 2, y - 1, 5, 5);
                g.setColor(new Color(145, 136, 110)); g.fillOval(x - 1, y - 2, 3, 3);
            }
            if (variant == 0) {
                g.setColor(new Color(255, 158, 46, 42)); g.fillRect(29, 4, 44, 12);
                g.setColor(new Color(15, 19, 22)); g.fillRoundRect(31, 5, 41, 7, 3, 3);
                g.setColor(new Color(255, 192, 92)); g.fillRect(34, 7, 34, 3);
            } else if (variant == 2) {
                g.setColor(new Color(198, 135, 55, 145));
                for (int x = 12; x < 100; x += 14) g.drawLine(x, 61, x + 7, 66);
            }
            g.setColor(new Color(4, 10, 14)); g.fillRect(0, 69, TILE, 3);
            g.setStroke(new BasicStroke(4)); g.setColor(new Color(6, 12, 16));
            g.drawArc(12, 53, 82, 39, 180, 180);
            g.setStroke(new BasicStroke(1)); g.setColor(new Color(111, 92, 65));
            g.drawArc(12, 51, 82, 39, 180, 180);
            g.setColor(new Color(65, 64, 53)); g.fillRect(14, 66, 8, 10); g.fillRect(88, 66, 7, 10);
            g.setColor(new Color(238, 184, 99)); g.fillRect(0, 0, TILE, 2);
            g.setColor(new Color(31, 28, 22)); g.fillRect(0, 2, TILE, 3);
            g.setColor(new Color(99, 82, 54)); g.fillRect(0, 5, TILE, 1);
        } finally { g.dispose(); }
        return image;
    }
}
