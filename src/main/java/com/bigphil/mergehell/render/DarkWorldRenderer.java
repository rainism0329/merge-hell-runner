package com.bigphil.mergehell.render;

import com.bigphil.mergehell.world.DarkBiome;
import com.bigphil.mergehell.world.WorldScenery;

import java.awt.*;
import java.awt.geom.Arc2D;
import java.awt.geom.Path2D;
import java.util.List;

/** Paints the Dark mission as a layered arcade warzone using only Java2D. */
public final class DarkWorldRenderer {
    private static final Font LABEL_FONT = new Font("JetBrains Mono", Font.BOLD, 10);
    private static final Font SMALL_FONT = new Font("JetBrains Mono", Font.PLAIN, 8);
    private static final Font ZONE_FONT = new Font("JetBrains Mono", Font.BOLD, 22);

    public void drawBackground(Graphics2D target, int width, int groundY, double cameraX,
                               DarkBiome biome, boolean danger) {
        Graphics2D g = (Graphics2D) target.create();
        try {
            Color bottom = danger ? mix(biome.skyBottom, new Color(103, 16, 20), 0.42) : biome.skyBottom;
            g.setPaint(new GradientPaint(0, 0, biome.skyTop, 0, groundY, bottom));
            g.fillRect(0, 0, width, groundY);
            drawAtmosphere(g, width, groundY, cameraX, biome);
            drawFarLayer(g, width, groundY, cameraX, biome, 0.12, 118, 0.34f);
            drawFarLayer(g, width, groundY, cameraX, biome, 0.28, 176, 0.62f);
            g.setPaint(new GradientPaint(0, groundY - 170, new Color(0, 0, 0, 0),
                    0, groundY, new Color(biome.accent.getRed(), biome.accent.getGreen(),
                    biome.accent.getBlue(), 30)));
            g.fillRect(0, groundY - 170, width, 170);
        } finally {
            g.dispose();
        }
    }

    public void drawScenery(Graphics2D g, List<WorldScenery> scenery,
                            WorldScenery.Layer layer, double cameraX, int width,
                            DarkBiome biome) {
        double left = cameraX - 240;
        double right = cameraX + width + 240;
        for (WorldScenery item : scenery) {
            if (item.layer() != layer || item.x() + item.width() < left) continue;
            if (item.x() > right) break;
            drawItem(g, item, biome);
        }
    }

    public void drawGround(Graphics2D g, int width, int groundY, double cameraX, DarkBiome biome) {
        int left = (int) cameraX - 80;
        g.setColor(biome.ground);
        g.fillRect(left, groundY, width + 240, 16);
        g.setColor(withAlpha(biome.accent, 190));
        g.fillRect(left, groundY, width + 240, 2);
        g.setColor(new Color(5, 8, 12));
        g.fillRect(left, groundY + 16, width + 240, 18);
        int start = ((left / 72) - 1) * 72;
        for (int x = start; x < left + width + 240; x += 72) {
            g.setColor(withAlpha(biome.accent, 42));
            g.drawLine(x, groundY + 3, x + 34, groundY + 15);
            g.drawLine(x + 34, groundY + 15, x + 68, groundY + 3);
            g.setColor(new Color(255, 255, 255, 38));
            g.fillOval(x + 5, groundY + 7, 3, 3);
        }
    }

    public void drawZoneBanner(Graphics2D g, int width, DarkBiome biome, int ticks) {
        if (ticks <= 0) return;
        double phase = Math.min(1, ticks / 35.0);
        int alpha = (int) (225 * phase);
        int y = 188;
        int cardW = Math.min(520, width - 80);
        int x = (width - cardW) / 2;
        g.setColor(new Color(7, 11, 17, Math.min(210, alpha)));
        g.fillRoundRect(x, y, cardW, 70, 10, 10);
        g.setColor(withAlpha(biome.accent, alpha));
        g.fillRect(x, y, 5, 70);
        g.drawRoundRect(x, y, cardW, 70, 10, 10);
        g.setFont(ZONE_FONT);
        g.setColor(new Color(255, 255, 255, alpha));
        g.drawString(biome.displayName, x + 24, y + 31);
        g.setFont(SMALL_FONT);
        g.setColor(new Color(biome.accent.getRed(), biome.accent.getGreen(),
                biome.accent.getBlue(), alpha));
        g.drawString(biome.subtitle.toUpperCase(), x + 25, y + 52);
    }

    private void drawAtmosphere(Graphics2D g, int width, int groundY,
                                double cameraX, DarkBiome biome) {
        int drift = (int) (cameraX * 0.04);
        switch (biome) {
            case REPOSITORY_CITY -> {
                g.setColor(withAlpha(biome.accent, 45));
                for (int i = -2; i < 22; i++) {
                    int x = Math.floorMod(i * 97 - drift, width + 140) - 70;
                    int y = Math.floorMod(i * 43, groundY - 80) + 25;
                    g.drawLine(x, y, x - 18, y + 42);
                }
            }
            case CI_FOUNDRY -> {
                for (int i = 0; i < 8; i++) {
                    int x = Math.floorMod(i * 173 - drift, width + 180) - 90;
                    g.setColor(new Color(231, 126, 70, 12 + i * 2));
                    g.fillOval(x, 95 + (i % 3) * 34, 150, 55);
                }
            }
            case DATA_CENTER -> {
                int pulseY = 70 + Math.floorMod((int) (cameraX * 0.22), 260);
                g.setColor(withAlpha(biome.accent, 38));
                g.fillRect(0, pulseY, width, 2);
                for (int x = -Math.floorMod(drift, 90); x < width; x += 90) {
                    g.drawLine(x, 0, x + 55, groundY);
                }
            }
            case DEPENDENCY_DUMP -> {
                g.setColor(new Color(221, 190, 225, 30));
                for (int i = 0; i < 18; i++) {
                    int x = Math.floorMod(i * 127 - drift, width + 100) - 50;
                    int y = 40 + Math.floorMod(i * 71, groundY - 90);
                    g.fillRect(x, y, 2 + i % 3, 2 + (i + 1) % 4);
                }
            }
            case BOSS_GATE -> {
                g.setColor(new Color(255, 46, 46, 20));
                for (int x = -Math.floorMod(drift * 3, 140); x < width + 140; x += 140) {
                    Polygon beam = new Polygon(new int[]{x, x + 28, x + 150, x + 110},
                            new int[]{0, 0, groundY, groundY}, 4);
                    g.fillPolygon(beam);
                }
            }
        }
    }

    private void drawFarLayer(Graphics2D g, int width, int groundY, double cameraX,
                              DarkBiome biome, double factor, int spacing, float opacity) {
        int offset = Math.floorMod((int) (cameraX * factor), spacing);
        Color base = withAlpha(biome.silhouette, (int) (255 * opacity));
        for (int i = -2; i <= width / spacing + 2; i++) {
            int x = i * spacing - offset;
            int hash = hash(i + (int) (cameraX * factor / spacing) + biome.ordinal() * 97);
            int w = spacing + 18 + Math.floorMod(hash, 64);
            int h = 86 + Math.floorMod(hash >> 5, factor < 0.2 ? 125 : 185);
            int y = groundY - h;
            g.setColor(base);
            switch (biome) {
                case REPOSITORY_CITY -> drawSkyline(g, x, y, w, h, biome, hash);
                case CI_FOUNDRY -> drawFactory(g, x, y, w, h, biome, hash);
                case DATA_CENTER -> drawServerCanyon(g, x, y, w, h, biome, hash);
                case DEPENDENCY_DUMP -> drawRuins(g, x, y, w, h, biome, hash);
                case BOSS_GATE -> drawFortress(g, x, y, w, h, biome, hash);
            }
        }
    }

    private void drawSkyline(Graphics2D g, int x, int y, int w, int h,
                             DarkBiome biome, int hash) {
        g.fillRect(x, y, w, h);
        g.fillRect(x + w / 4, y - 24 - Math.floorMod(hash, 32), 3, 30);
        g.setColor(withAlpha(biome.accent, 42));
        for (int wx = x + 12; wx < x + w - 8; wx += 22)
            for (int wy = y + 18; wy < y + h - 10; wy += 26)
                if (((wx + wy + hash) & 3) != 0) g.fillRect(wx, wy, 5, 9);
    }

    private void drawFactory(Graphics2D g, int x, int y, int w, int h,
                             DarkBiome biome, int hash) {
        g.fillRect(x, y + h / 3, w, h - h / 3);
        int stackW = Math.max(12, w / 7);
        g.fillRect(x + w / 5, y - 20, stackW, h / 2 + 22);
        g.fillRect(x + w * 3 / 5, y + 12, stackW + 4, h / 2);
        g.setColor(withAlpha(biome.accent, 38));
        g.fillOval(x + 6, y + h / 2, w / 2, w / 2);
    }

    private void drawServerCanyon(Graphics2D g, int x, int y, int w, int h,
                                  DarkBiome biome, int hash) {
        g.fillRoundRect(x, y, w, h, 5, 5);
        g.setColor(withAlpha(biome.accent, 46));
        for (int row = y + 14; row < y + h - 8; row += 18) {
            g.drawLine(x + 8, row, x + w - 8, row);
            g.fillRect(x + 12 + Math.floorMod(hash + row, Math.max(1, w - 34)), row - 3, 7, 3);
        }
    }

    private void drawRuins(Graphics2D g, int x, int y, int w, int h,
                           DarkBiome biome, int hash) {
        Path2D ruin = new Path2D.Double();
        ruin.moveTo(x, y + h); ruin.lineTo(x, y + h / 3);
        ruin.lineTo(x + w / 4, y + h / 5); ruin.lineTo(x + w / 2, y + h / 2);
        ruin.lineTo(x + w * 3 / 4, y); ruin.lineTo(x + w, y + h / 4);
        ruin.lineTo(x + w, y + h); ruin.closePath(); g.fill(ruin);
        g.setColor(withAlpha(biome.accent, 30));
        g.drawLine(x + 8, y + h / 2, x + w - 4, y + h / 3);
    }

    private void drawFortress(Graphics2D g, int x, int y, int w, int h,
                              DarkBiome biome, int hash) {
        g.fillRect(x, y, w, h);
        g.fillRect(x - 8, y - 18, w / 3, 22);
        g.fillRect(x + w * 2 / 3, y - 18, w / 3 + 8, 22);
        g.setColor(withAlpha(biome.accent, 54));
        g.fillRect(x + 12, y + 28, w - 24, 4);
        g.fillRect(x + w / 2 - 3, y + 45, 6, h - 45);
    }

    private void drawItem(Graphics2D target, WorldScenery s, DarkBiome biome) {
        Graphics2D g = (Graphics2D) target.create();
        try {
            int x = (int) s.x(), y = s.y(), w = s.width(), h = s.height();
            Color body = s.layer() == WorldScenery.Layer.BACK
                    ? withAlpha(biome.silhouette, 190) : new Color(31, 38, 47, 230);
            g.setStroke(new BasicStroke(2f));
            g.setFont(SMALL_FONT);
            switch (s.kind()) {
                case CITY_TOWER -> {
                    g.setColor(body); g.fillRect(x, y, w, h);
                    g.setColor(withAlpha(biome.accent, 90));
                    for (int wx = x + 12; wx < x + w - 8; wx += 24)
                        for (int wy = y + 18; wy < y + h - 10; wy += 28) g.fillRect(wx, wy, 7, 11);
                }
                case NEON_SIGN -> {
                    g.setColor(new Color(13, 18, 24, 225)); g.fillRoundRect(x, y, w, h, 5, 5);
                    g.setColor(withAlpha(biome.accent, 220)); g.drawRoundRect(x, y, w, h, 5, 5);
                    g.setFont(LABEL_FONT); g.drawString((s.variant() & 1) == 0 ? "PUSH // NIGHT" : "MERGE DISTRICT", x + 8, y + h / 2 + 4);
                }
                case WATER_TANK -> {
                    g.setColor(body); g.fillOval(x, y, w, h / 2); g.fillRect(x, y + h / 4, w, h / 3);
                    g.drawLine(x + 12, y + h / 2, x + 5, y + h); g.drawLine(x + w - 12, y + h / 2, x + w - 5, y + h);
                }
                case CRANE -> {
                    g.setColor(withAlpha(biome.accent, 145)); g.fillRect(x, y + 12, w, 7); g.fillRect(x + 22, y, 8, h);
                    g.drawLine(x + 30, y + 19, x + w - 24, y + h); g.drawLine(x + w - 36, y + 18, x + w - 36, y + h - 22);
                    g.fillRect(x + w - 43, y + h - 25, 14, 11);
                }
                case FURNACE -> {
                    g.setColor(body); g.fillRoundRect(x, y, w, h, 7, 7);
                    g.setColor(new Color(255, 101, 35, 210)); g.fillRoundRect(x + 22, y + 30, w - 44, h - 40, 10, 10);
                    g.setColor(Color.YELLOW); g.drawString("BUILD", x + w / 2 - 16, y + 18);
                }
                case SMOKE_STACK -> {
                    g.setColor(body); g.fillRect(x + w / 4, y + h / 3, w / 2, h * 2 / 3);
                    for (int i = 0; i < 4; i++) { g.setColor(new Color(160, 129, 116, 35 + i * 12)); g.fillOval(x - i * 9, y + i * 18, w + i * 14, h / 3); }
                }
                case PIPE_CLUSTER -> {
                    g.setColor(new Color(16, 21, 26, 210)); g.fillRect(x, y, w, h);
                    g.setColor(withAlpha(biome.accent, 92));
                    for (int py = y + 10; py < y + h; py += 14) { g.drawLine(x, py, x + w, py); g.fillOval(x + 40 + (py & 31), py - 4, 8, 8); }
                }
                case SERVER_RACK -> {
                    g.setColor(body); g.fillRoundRect(x, y, w, h, 4, 4);
                    for (int row = y + 12; row < y + h - 7; row += 17) { g.setColor(new Color(6, 11, 15)); g.fillRect(x + 7, row, w - 14, 11); g.setColor(withAlpha(biome.accent, 190)); g.fillRect(x + 14, row + 4, 5, 3); }
                }
                case COOLING_FAN -> {
                    g.setColor(new Color(14, 20, 24, 220)); g.fillOval(x, y, w, h);
                    g.setColor(withAlpha(biome.accent, 120)); g.drawOval(x + 6, y + 6, w - 12, h - 12);
                    for (int i = 0; i < 4; i++) g.fill(new Arc2D.Double(x + 15, y + 15, w - 30, h - 30, i * 90 + s.variant() * 11, 42, Arc2D.PIE));
                }
                case CABLE_BRIDGE -> {
                    g.setColor(withAlpha(biome.accent, 90)); g.drawArc(x, y, w, h * 2, 0, 180); g.drawArc(x, y + 14, w, h * 2, 0, 180);
                }
                case BROKEN_PACKAGE -> {
                    g.rotate((s.variant() % 5 - 2) * 0.05, x + w / 2.0, y + h / 2.0);
                    g.setColor(body); g.fillRect(x, y, w, h); g.setColor(withAlpha(biome.accent, 170)); g.drawRect(x, y, w, h);
                    g.setFont(LABEL_FONT); g.drawString("404.jar", x + 18, y + h / 2);
                    g.drawLine(x + w / 2, y, x + w / 3, y + h);
                }
                case SCRAP_HEAP -> {
                    g.setColor(body); Polygon heap = new Polygon(new int[]{x, x + w / 5, x + w / 2, x + w * 4 / 5, x + w}, new int[]{y + h, y + h / 3, y, y + h / 4, y + h}, 5); g.fillPolygon(heap);
                    g.setColor(withAlpha(biome.accent, 80)); g.drawPolyline(heap.xpoints, heap.ypoints, heap.npoints);
                }
                case DEAD_TREE -> {
                    g.setColor(body); g.setStroke(new BasicStroke(7f)); g.drawLine(x + w / 2, y + h, x + w / 2, y + 22); g.setStroke(new BasicStroke(4f)); g.drawLine(x + w / 2, y + 48, x + 8, y + 12); g.drawLine(x + w / 2, y + 65, x + w - 5, y + 30);
                }
                case WARNING_PYLON -> {
                    g.setColor(body); g.fillRect(x, y, w, h); g.setColor(withAlpha(biome.accent, 220));
                    for (int py = y + 8; py < y + h; py += 20) g.fillPolygon(new int[]{x, x + 9, x + w, x + w - 9}, new int[]{py, py, py + 11, py + 11}, 4);
                }
                case BLAST_DOOR -> {
                    g.setColor(body); g.fillRoundRect(x, y, w, h, 18, 18); g.setColor(withAlpha(biome.accent, 115)); g.setStroke(new BasicStroke(5f)); g.drawRoundRect(x + 12, y + 12, w - 24, h - 12, 12, 12); g.drawLine(x + w / 2, y + 16, x + w / 2, y + h); g.setFont(LABEL_FONT); g.drawString("MAIN // PROTECTED", x + w / 2 - 55, y + 42);
                }
                case SEARCHLIGHT -> {
                    g.setColor(new Color(255, 68, 58, 24)); g.fillPolygon(new int[]{x + w / 2, x - w, x + w * 2}, new int[]{y, y + h, y + h}, 3);
                    g.setColor(body); g.fillRect(x + w / 2 - 8, y + 20, 16, h - 20); g.setColor(withAlpha(biome.accent, 210)); g.fillOval(x + w / 2 - 18, y, 36, 26);
                }
            }
        } finally {
            g.dispose();
        }
    }

    private static int hash(int value) {
        int x = value * 0x45d9f3b;
        x = (x ^ (x >>> 16)) * 0x45d9f3b;
        return x ^ (x >>> 16);
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    private static Color mix(Color a, Color b, double amount) {
        double t = Math.max(0, Math.min(1, amount));
        return new Color((int) (a.getRed() * (1 - t) + b.getRed() * t),
                (int) (a.getGreen() * (1 - t) + b.getGreen() * t),
                (int) (a.getBlue() * (1 - t) + b.getBlue() * t));
    }
}
