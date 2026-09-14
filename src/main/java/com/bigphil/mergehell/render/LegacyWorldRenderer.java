package com.bigphil.mergehell.render;

import com.bigphil.mergehell.i18n.GameText;

import com.bigphil.mergehell.LevelTheme;

import java.awt.*;
import java.awt.geom.AffineTransform;

/** Distinct visual ecosystems for campaign levels two through five. */
public final class LegacyWorldRenderer {
    private static final Font MICRO = new Font("JetBrains Mono", Font.PLAIN, 9);
    private static final Font LABEL = new Font("JetBrains Mono", Font.BOLD, 11);

    public void drawBackground(Graphics2D g, int level, int width, int height,
                               double cameraX, boolean danger) {
        Paint paint = g.getPaint();
        Stroke stroke = g.getStroke();
        Font font = g.getFont();
        Composite composite = g.getComposite();

        switch (level) {
            case 1 -> drawHeapDistrict(g, width, height, cameraX);
            case 2 -> drawBlueprintCitadel(g, width, height, cameraX);
            case 3 -> drawKernelCore(g, width, height, cameraX);
            default -> drawSingularityEdge(g, width, height, cameraX);
        }
        if (danger) {
            g.setColor(new Color(190, 20, 35, 34));
            g.fillRect(0, 0, width, height);
        }

        g.setPaint(paint);
        g.setStroke(stroke);
        g.setFont(GameText.font(font));
        g.setComposite(composite);
    }

    public void drawGround(Graphics2D g, int level, int width, int groundY,
                           double cameraX, LevelTheme theme) {
        int left = (int) cameraX;
        g.setColor(theme.ground);
        g.fillRect(left, groundY, width + 220, 12);
        switch (level) {
            case 1 -> drawHeapGround(g, left, width, groundY);
            case 2 -> drawBlueprintGround(g, left, width, groundY);
            case 3 -> drawKernelGround(g, left, width, groundY);
            default -> drawSingularityGround(g, left, width, groundY);
        }
    }

    public String districtName(int level) {
        return switch (level) {
            case 1 -> "HEAP DISTRICT // GC OFFLINE";
            case 2 -> "BLUEPRINT CITADEL // DESIGN IS LAW";
            case 3 -> "KERNEL CORE // RING-0 UNSTABLE";
            default -> "SINGULARITY EDGE // ALL PATTERNS CONVERGE";
        };
    }

    private void drawHeapDistrict(Graphics2D g, int width, int height, double cameraX) {
        g.setPaint(new GradientPaint(0, 0, new Color(24, 28, 20),
                0, height, new Color(45, 33, 42)));
        g.fillRect(0, 0, width, height);

        int spacing = 126;
        int offset = Math.floorMod((int) (-cameraX * 0.17), spacing);
        g.setFont(GameText.font(MICRO));
        for (int x = offset - spacing, column = 0; x < width + spacing; x += spacing, column++) {
            int hash = hash(column + (int) cameraX / 700);
            int towerH = 130 + Math.floorMod(hash, 190);
            int y = height - towerH;
            g.setColor(new Color(68, 82, 54, 105));
            g.fillRoundRect(x, y, 92, towerH, 12, 12);
            for (int blockY = y + 14; blockY < height - 10; blockY += 25) {
                boolean retained = ((hash + blockY) & 3) != 0;
                g.setColor(retained ? new Color(166, 226, 46, 88)
                        : new Color(249, 38, 114, 72));
                g.fillRoundRect(x + 9, blockY, 74, 15, 5, 5);
            }
            g.setColor(new Color(220, 235, 190, 100));
            GameText.draw(g, String.format(java.util.Locale.ROOT, "0x%04X", hash & 0xFFFF), x + 9, y + 11);
        }

        for (int i = 0; i < 13; i++) {
            int bx = Math.floorMod(hash(i * 17) - (int) (cameraX * 0.31), width + 100) - 50;
            int by = 55 + Math.floorMod(hash(i * 31), Math.max(80, height - 130));
            int size = 10 + Math.floorMod(hash(i * 43), 24);
            g.setColor(new Color(249, 38, 114, 42 + i % 3 * 18));
            g.fillOval(bx, by, size, size);
            g.setColor(new Color(166, 226, 46, 75));
            g.drawOval(bx - 4, by - 4, size + 8, size + 8);
        }
        int scanY = 70 + Math.floorMod((int) (cameraX * 0.23), Math.max(1, height - 100));
        g.setColor(new Color(166, 226, 46, 35));
        g.fillRect(0, scanY, width, 5);
        drawDistrictLabel(g, districtName(1), new Color(166, 226, 46));
    }

    private void drawBlueprintCitadel(Graphics2D g, int width, int height, double cameraX) {
        g.setPaint(new GradientPaint(0, 0, new Color(0, 43, 54),
                0, height, new Color(7, 54, 66)));
        g.fillRect(0, 0, width, height);
        g.setStroke(new BasicStroke(1));
        g.setColor(new Color(38, 139, 210, 58));
        int grid = 32;
        int offset = Math.floorMod((int) (-cameraX * 0.34), grid);
        for (int x = offset; x < width; x += grid) g.drawLine(x, 0, x, height);
        for (int y = 24; y < height; y += grid) g.drawLine(0, y, width, y);

        int horizon = height - 78;
        g.setColor(new Color(42, 161, 152, 90));
        g.setStroke(new BasicStroke(2));
        int span = 210;
        int buildingOffset = Math.floorMod((int) (-cameraX * 0.22), span);
        for (int x = buildingOffset - span, index = 0; x < width + span; x += span, index++) {
            int h = 120 + Math.floorMod(hash(index + (int) cameraX / 900), 170);
            Polygon tower = new Polygon(
                    new int[]{x, x + 72, x + 138, x + 176},
                    new int[]{horizon, horizon - h, horizon - h / 2, horizon}, 4);
            g.drawPolygon(tower);
            g.drawLine(x + 72, horizon - h, x + 72, horizon);
            g.drawLine(x + 30, horizon - h / 3, x + 145, horizon - h / 3);
            g.fillOval(x + 67, horizon - h - 5, 10, 10);
        }
        g.setColor(new Color(181, 137, 0, 80));
        g.setStroke(new BasicStroke(3));
        g.drawArc(width - 290, 55, 240, 240, 20, 300);
        g.drawLine(width - 170, 75, width - 170, 275);
        g.drawLine(width - 270, 175, width - 70, 175);
        drawDistrictLabel(g, districtName(2), new Color(42, 161, 152));
    }

    private void drawKernelCore(Graphics2D g, int width, int height, double cameraX) {
        g.setPaint(new GradientPaint(0, 0, new Color(30, 35, 46),
                0, height, new Color(46, 52, 64)));
        g.fillRect(0, 0, width, height);
        int rackWidth = 118;
        int offset = Math.floorMod((int) (-cameraX * 0.25), rackWidth);
        g.setFont(GameText.font(MICRO));
        for (int x = offset - rackWidth, index = 0; x < width + rackWidth; x += rackWidth, index++) {
            int rackTop = 80 + Math.floorMod(hash(index + (int) cameraX / 600), 70);
            g.setColor(new Color(20, 24, 34, 170));
            g.fillRoundRect(x + 8, rackTop, 94, height - rackTop, 9, 9);
            g.setColor(new Color(136, 192, 208, 90));
            g.drawRoundRect(x + 8, rackTop, 94, height - rackTop, 9, 9);
            for (int y = rackTop + 18; y < height - 12; y += 24) {
                g.setColor(new Color(67, 76, 94));
                g.fillRect(x + 17, y, 76, 14);
                g.setColor((y / 24 + index) % 4 == 0
                        ? new Color(255, 90, 90) : new Color(143, 188, 187));
                g.fillOval(x + 79, y + 4, 5, 5);
            }
        }

        g.setColor(new Color(180, 142, 173, 115));
        g.setStroke(new BasicStroke(2));
        int previousY = height / 2;
        for (int x = 0; x < width; x += 14) {
            int wave = height / 2 + (int) (Math.sin((x + cameraX * 0.8) * 0.035) * 35);
            if ((x / 14) % 9 == 0) wave -= 55;
            g.drawLine(Math.max(0, x - 14), previousY, x, wave);
            previousY = wave;
        }
        drawDistrictLabel(g, districtName(3), new Color(136, 192, 208));
    }

    private void drawSingularityEdge(Graphics2D g, int width, int height, double cameraX) {
        int cx = width * 3 / 4 + (int) Math.sin(cameraX * 0.0008) * 45;
        int cy = height / 2;
        g.setPaint(new RadialGradientPaint(cx, cy, Math.max(width, height) * 0.82f,
                new float[]{0f, 0.22f, 0.58f, 1f},
                new Color[]{new Color(5, 4, 12), new Color(48, 22, 62),
                        new Color(40, 42, 54), new Color(15, 12, 28)}));
        g.fillRect(0, 0, width, height);

        for (int i = 0; i < 64; i++) {
            int sx = Math.floorMod(hash(i * 71) - (int) (cameraX * (0.04 + i % 4 * 0.03)), width);
            int sy = Math.floorMod(hash(i * 97), Math.max(1, height - 25));
            int size = 1 + Math.floorMod(hash(i * 13), 3);
            g.setColor(i % 3 == 0 ? new Color(255, 121, 198, 175)
                    : new Color(139, 233, 253, 150));
            g.fillOval(sx, sy, size, size);
        }
        g.setStroke(new BasicStroke(2));
        for (int i = 0; i < 7; i++) {
            int radius = 45 + i * 30;
            g.setColor(i % 2 == 0 ? new Color(255, 121, 198, 80)
                    : new Color(139, 233, 253, 65));
            g.drawArc(cx - radius, cy - radius, radius * 2, radius * 2,
                    (int) (cameraX * 0.08) + i * 37, 205);
        }
        g.setColor(Color.BLACK);
        g.fillOval(cx - 42, cy - 42, 84, 84);
        g.setColor(new Color(255, 255, 255, 40));
        AffineTransform transform = g.getTransform();
        for (int i = 0; i < 8; i++) {
            int fx = Math.floorMod(hash(i * 53) - (int) (cameraX * 0.42), width + 100) - 50;
            int fy = 90 + Math.floorMod(hash(i * 29), Math.max(1, height - 180));
            g.setTransform(transform);
            g.rotate((i - 3) * 0.08, fx, fy);
            g.drawRect(fx, fy, 42 + i * 3, 18);
        }
        g.setTransform(transform);
        drawDistrictLabel(g, districtName(4), new Color(255, 121, 198));
    }

    private void drawHeapGround(Graphics2D g, int left, int width, int groundY) {
        for (int x = left; x < left + width + 200; x += 48) {
            g.setColor((x / 48 & 1) == 0 ? new Color(166, 226, 46, 105)
                    : new Color(249, 38, 114, 80));
            g.fillRoundRect(x + 3, groundY + 2, 41, 7, 4, 4);
        }
    }

    private void drawBlueprintGround(Graphics2D g, int left, int width, int groundY) {
        g.setColor(new Color(42, 161, 152, 190));
        g.drawLine(left, groundY, left + width + 200, groundY);
        for (int x = left; x < left + width + 200; x += 64) {
            g.drawLine(x, groundY, x + 28, groundY + 10);
            g.drawLine(x + 28, groundY + 10, x + 56, groundY);
        }
    }

    private void drawKernelGround(Graphics2D g, int left, int width, int groundY) {
        for (int x = left; x < left + width + 220; x += 34) {
            g.setColor((x / 34 & 1) == 0 ? new Color(255, 90, 90, 170)
                    : new Color(20, 24, 34, 200));
            Polygon stripe = new Polygon(new int[]{x, x + 18, x + 34, x + 16},
                    new int[]{groundY, groundY, groundY + 10, groundY + 10}, 4);
            g.fillPolygon(stripe);
        }
    }

    private void drawSingularityGround(Graphics2D g, int left, int width, int groundY) {
        g.setColor(new Color(255, 121, 198, 150));
        for (int x = left; x < left + width + 220; x += 92) {
            int lift = Math.floorMod(hash(x / 92), 7);
            g.fillRoundRect(x, groundY + lift, 58, 5, 5, 5);
            g.setColor(new Color(139, 233, 253, 100));
            g.drawLine(x + 58, groundY + lift + 2, x + 87, groundY + 8 - lift);
            g.setColor(new Color(255, 121, 198, 150));
        }
    }

    private void drawDistrictLabel(Graphics2D g, String text, Color color) {
        g.setFont(GameText.font(LABEL));
        g.setColor(new Color(0, 0, 0, 130));
        g.fillRoundRect(18, 188, g.getFontMetrics().stringWidth(GameText.text(text)) + 22, 25, 8, 8);
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 170));
        GameText.draw(g, text, 29, 205);
    }

    private static int hash(int value) {
        int x = value * 0x45d9f3b;
        x = (x ^ (x >>> 16)) * 0x45d9f3b;
        return x ^ (x >>> 16);
    }

    public LegacyWorldRenderer() { }
}
