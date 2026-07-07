package com.bigphil.mergehell;

import com.bigphil.mergehell.model.*;

import java.awt.*;
import java.util.LinkedList;
import java.util.List;

public class GameRenderer {

    private static final int TERMINAL_HEIGHT = 120;

    public void render(Graphics2D g, int width, int height, int groundY,
                       GameState state, Player player, Boss boss,
                       ObstacleManager enemyManager,
                       List<Projectile> projectiles, List<Particle> particles,
                       List<FloatingText> floatingTexts, List<CodeRain> backgroundCodes,
                       LinkedList<String> logs, int score, int combo, int shakeTimer) {

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (shakeTimer > 0) {
            int dx = (int) (Math.random() * 10 - 5);
            int dy = (int) (Math.random() * 10 - 5);
            g.translate(dx, dy);
        }

        g.setColor(GameColors.BG);
        g.fillRect(0, 0, width, groundY);

        g.setFont(new Font("JetBrains Mono", Font.PLAIN, 12));
        for (CodeRain cr : backgroundCodes) cr.draw(g);

        g.setColor(GameColors.GROUND);
        g.fillRect(0, groundY, width, 10);
        g.setColor(Color.GRAY);
        g.drawLine(0, groundY, width, groundY);

        player.draw(g);
        enemyManager.draw(g);
        if (state == GameState.BOSS_FIGHT || state == GameState.BOSS_WARNING) boss.draw(g);
        for (Projectile p : projectiles) p.draw(g);
        for (Particle p : particles) p.draw(g);
        for (FloatingText t : floatingTexts) t.draw(g);

        drawScanlines(g, width, groundY);
        drawHUD(g, width, player, boss, score, combo, state);
        drawUI(g, width, height, groundY, state);

        if (shakeTimer > 0) g.translate(0, 0);
        drawTerminal(g, width, groundY, logs);
    }

    private void drawUI(Graphics2D g, int width, int height, int groundY, GameState state) {
        if (state == GameState.MENU) {
            drawOverlay(g, width, height, "MERGE HELL 2.0", "PRESS SPACE TO DEPLOY", GameColors.PLAYER);
            drawControls(g, width, height / 2);
        } else if (state == GameState.PAUSED) {
            drawOverlay(g, width, height, "SYSTEM PAUSED", "PRESS 'P' TO RESUME", Color.YELLOW);
            drawControls(g, width, height / 2);
        } else if (state == GameState.GAME_OVER) {
            drawOverlay(g, width, height, "BUILD FAILED", "See terminal for logs", GameColors.DANGER_RED);
        } else if (state == GameState.BOSS_WARNING) {
            drawCenteredString(g, width, "WARNING: HIGH LOAD", groundY / 2, 40, Color.RED);
        } else if (state == GameState.VICTORY) {
            drawOverlay(g, width, height, "PRODUCTION READY", "All systems operational.", Color.GREEN);
        }
    }

    private void drawScanlines(Graphics2D g, int width, int height) {
        g.setColor(GameColors.SCANLINE);
        for (int i = 0; i < height; i += 4) {
            g.fillRect(0, i, width, 2);
        }
    }

    private void drawTerminal(Graphics2D g, int width, int yStart, LinkedList<String> logs) {
        g.setColor(GameColors.TERMINAL_BG);
        g.fillRect(0, yStart, width, TERMINAL_HEIGHT);
        g.setColor(GameColors.TERMINAL_BORDER);
        g.drawLine(0, yStart, width, yStart);

        g.setFont(new Font("JetBrains Mono", Font.PLAIN, 11));
        g.setColor(Color.LIGHT_GRAY);
        g.drawString("Terminal: Local", 10, yStart + 16);

        g.setFont(new Font("JetBrains Mono", Font.PLAIN, 12));
        int logY = yStart + 35;
        for (String log : logs) {
            if (log.contains("ERROR") || log.contains("WARNING") || log.contains("ALERT") || log.contains("CRITICAL")) {
                g.setColor(GameColors.DANGER_RED);
            } else if (log.contains("Sudo") || log.contains("Victory") || log.contains("GRANTED")) {
                g.setColor(Color.YELLOW);
            } else {
                g.setColor(Color.GRAY);
            }
            g.drawString(log, 10, logY);
            logY += 16;
        }
    }

    private void drawHUD(Graphics2D g, int width, Player player, Boss boss, int score, int combo, GameState state) {
        g.setFont(new Font("JetBrains Mono", Font.BOLD, 18));
        g.setColor(GameColors.PLAYER);
        g.drawString("Lines: " + score, 20, 30);

        if (combo > 1) {
            g.setFont(new Font("JetBrains Mono", Font.BOLD, 24));
            g.setColor(Color.YELLOW);
            g.drawString(combo + "x COMBO!", 20, 60);
        }

        int barWidth = 200;
        int barX = width - barWidth - 20;
        g.setColor(Color.DARK_GRAY);
        g.fillRect(barX, 20, barWidth, 10);
        g.setColor(player.getHp() < 30 ? GameColors.HP_LOW : GameColors.HP_BAR);
        int hpWidth = (int) ((player.getHp() / (double) player.getMaxHp()) * barWidth);
        g.fillRect(barX, 20, hpWidth, 10);

        int buffY = 50;
        g.setFont(new Font("SansSerif", Font.BOLD, 12));

        if (player.getSudoTimer() > 0) {
            g.setColor(GameColors.SUDO_YELLOW);
            g.drawString("SUDO MODE " + (player.getSudoTimer() / 60) + "s", barX, buffY);
            buffY += 15;
        }
        if (player.getShieldTimer() > 0) {
            g.setColor(GameColors.SHIELD_CYAN);
            g.drawString("SHIELD ACTIVE " + (player.getShieldTimer() / 60) + "s", barX, buffY);
        }

        if (state == GameState.BOSS_FIGHT && boss.isActive()) {
            int bw = 600;
            int bx = (width - bw) / 2;
            g.setColor(Color.DARK_GRAY);
            g.fillRect(bx, 60, bw, 15);
            g.setColor(GameColors.DANGER_RED);
            int bossHpWidth = (int) ((boss.getHp() / (double) boss.getMaxHp()) * bw);
            g.fillRect(bx, 60, bossHpWidth, 15);
            g.setColor(GameColors.DANGER_RED);
            g.setFont(new Font("JetBrains Mono", Font.BOLD, 16));
            g.drawString(boss.getName(), bx, 55);
        }
    }

    private void drawOverlay(Graphics2D g, int width, int height, String title, String sub, Color c) {
        g.setColor(GameColors.OVERLAY);
        g.fillRect(0, 0, width, height - TERMINAL_HEIGHT);
        drawCenteredString(g, width, title, height / 2 - 20, 40, c);
        drawCenteredString(g, width, sub, height / 2 + 30, 20, Color.WHITE);
    }

    private void drawCenteredString(Graphics2D g, int width, String text, int y, int size, Color c) {
        g.setColor(c);
        g.setFont(new Font("JetBrains Mono", Font.BOLD, size));
        FontMetrics fm = g.getFontMetrics();
        int x = (width - fm.stringWidth(text)) / 2;
        g.drawString(text, x, y);
    }

    private void drawControls(Graphics2D g, int width, int centerY) {
        g.setColor(GameColors.CONTROLS_TEXT);
        g.setFont(new Font("JetBrains Mono", Font.PLAIN, 16));

        int startY = centerY + 80;
        int lineHeight = 25;

        String[] lines = {
                "[ ← / → ]   MOVE",
                "[ SPACE ]   JUMP / DOUBLE JUMP",
                "[ C ]       COMMIT (SHOOT)",
                "[ P / ESC]  PAUSE"
        };

        FontMetrics fm = g.getFontMetrics();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int x = (width - fm.stringWidth(line)) / 2;
            g.drawString(line, x, startY + i * lineHeight);
        }
    }
}
