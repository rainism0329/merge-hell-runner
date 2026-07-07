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
                       List<Projectile> projectiles, List<Projectile> enemyBullets,
                       List<Particle> particles, List<FloatingText> floatingTexts,
                       List<CodeRain> bgLayer1, List<CodeRain> bgLayer2, List<CodeRain> bgLayer3,
                       List<Platform> platforms, List<LevelManager.Coin> coins,
                       LinkedList<String> logs, int score, int combo, int comboTimer,
                       int shakeTimer, int flashTimer, int level, double difficulty,
                       boolean isNewHighScore, double cameraX,
                       boolean inBattle, int currentWave, int totalWaves) {

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (shakeTimer > 0) {
            int dx = (int) (Math.random() * 10 - 5);
            int dy = (int) (Math.random() * 10 - 5);
            g.translate(dx, dy);
        }

        // Camera scroll offset (saved for UI later)
        g.translate(-cameraX, 0);

        // Background tint: boss fights get a darker red tint
        Color levelBg;
        if (state == GameState.BOSS_FIGHT || state == GameState.BOSS_WARNING) {
            levelBg = new Color(30, 18, 18);
        } else {
            levelBg = switch (level % 3) {
                case 0 -> GameColors.BG;
                case 1 -> new Color(20, 25, 35);
                default -> new Color(25, 20, 30);
            };
        }
        g.setColor(levelBg);
        g.fillRect((int) cameraX, 0, width + 200, groundY);

        // Background grid
        drawBackgroundGrid(g, width, groundY, cameraX);

        // Parallax code rain - deepest layer
        g.setFont(new Font("JetBrains Mono", Font.PLAIN, 7));
        for (CodeRain cr : bgLayer3) cr.draw(g);

        // Parallax code rain - far layer
        g.setFont(new Font("JetBrains Mono", Font.PLAIN, 9));
        for (CodeRain cr : bgLayer2) cr.draw(g);

        // Parallax code rain - near layer
        g.setFont(new Font("JetBrains Mono", Font.PLAIN, 11));
        for (CodeRain cr : bgLayer1) cr.draw(g);

        g.setColor(GameColors.GROUND);
        g.fillRect((int) cameraX, groundY, width + 200, 10);
        g.setColor(Color.GRAY);
        g.drawLine((int) cameraX, groundY, (int) cameraX + width + 200, groundY);

        // Foreground world elements (on top of background)
        drawPlatforms(g, platforms);
        drawCoins(g, coins);

        player.draw(g);
        enemyManager.draw(g);
        if (state == GameState.BOSS_FIGHT || state == GameState.BOSS_WARNING) boss.draw(g);
        for (Projectile p : projectiles) p.draw(g);
        for (Projectile b : enemyBullets) b.draw(g);
        for (Particle p : particles) p.draw(g);
        for (FloatingText t : floatingTexts) t.draw(g);

        drawScanlines(g, width, groundY);

        // Reset camera offset BEFORE drawing fixed HUD/UI
        g.translate(cameraX, 0);
        if (shakeTimer > 0) g.translate(0, 0);

        // Damage flash overlay (screen space)
        if (flashTimer > 0) {
            float alpha = (flashTimer / 15f) * 0.4f;
            g.setColor(new Color(1f, 0f, 0f, Math.min(alpha, 0.4f)));
            g.fillRect(0, 0, width, groundY);
        }

        // Low HP warning vignette
        if (player.getHp() > 0 && player.getHp() < 30) {
            float alpha = (30 - player.getHp()) / 50f * 0.35f;
            g.setColor(new Color(1f, 0f, 0f, alpha));
            g.fillRect(0, 0, width, groundY);
        }

        // Kill streak banner
        if (combo == 10 || combo == 20 || combo == 30 || combo == 50) {
            g.setFont(new Font("JetBrains Mono", Font.BOLD, 40));
            g.setColor(new Color(255, 200, 50, 200));
            String text = combo >= 50 ? "GODLIKE!" : combo >= 30 ? "UNSTOPPABLE!"
                    : combo >= 20 ? "RAMPAGE!" : "KILLING SPREE!";
            int tx = (width - g.getFontMetrics().stringWidth(text)) / 2;
            g.drawString(text, tx, groundY / 2);
        }

        drawHUD(g, width, player, boss, score, combo, comboTimer, state, level, difficulty,
                inBattle, currentWave, totalWaves);
        drawUI(g, width, height, groundY, state, score, isNewHighScore, level);

        boolean isGameplay = state == GameState.RUNNING || state == GameState.BOSS_WARNING
                || state == GameState.BOSS_FIGHT || state == GameState.LEVEL_CLEAR;
        if (isGameplay) {
            drawTerminal(g, width, groundY, logs);
        }
    }

    private void drawUI(Graphics2D g, int width, int height, int groundY,
                         GameState state, int score, boolean isNewHighScore, int level) {
        boolean fullscreen = state == GameState.MENU || state == GameState.PAUSED
                || state == GameState.GAME_OVER || state == GameState.VICTORY
                || state == GameState.MISSION_COMPLETE;
        int areaH = fullscreen ? height : groundY;
        int centerY = areaH / 2;

        if (state == GameState.MENU) {
            drawOverlay(g, width, areaH, "MERGE HELL", "PRESS SPACE TO DEPLOY", GameColors.PLAYER);
            drawControls(g, width, centerY);
        } else if (state == GameState.PAUSED) {
            drawOverlay(g, width, areaH, "SYSTEM PAUSED", "PRESS 'P' TO RESUME", Color.YELLOW);
            drawControls(g, width, centerY);
        } else if (state == GameState.GAME_OVER) {
            String sub = "SCORE: " + score + (isNewHighScore ? "  ⭐ NEW HIGH SCORE!" : "");
            drawOverlay(g, width, areaH, "BUILD FAILED", sub, GameColors.DANGER_RED);
            drawTopScores(g, width, centerY);
        } else if (state == GameState.MISSION_COMPLETE) {
            String sub = "SCORE: " + score + "  |  Level " + (level + 1) + " cleared";
            drawOverlay(g, width, areaH, "MISSION COMPLETE", sub, Color.GREEN);
        } else if (state == GameState.BOSS_WARNING) {
            drawCenteredString(g, width, "⚠ WARNING: HIGH CPU LOAD ⚠", groundY / 2, 36, Color.RED);
        } else if (state == GameState.VICTORY) {
            String sub = "SCORE: " + score + (isNewHighScore ? "  ⭐ NEW HIGH SCORE!" : "");
            drawOverlay(g, width, areaH, "PRODUCTION READY", sub, Color.GREEN);
            drawTopScores(g, width, centerY);
        }
    }

    private void drawTopScores(Graphics2D g, int width, int centerY) {
        java.util.List<Integer> scores = ScoreStore.load();
        if (scores.isEmpty()) return;

        g.setFont(new Font("JetBrains Mono", Font.PLAIN, 14));
        int y = centerY + 120;
        g.setColor(Color.LIGHT_GRAY);
        String header = "-- TOP SCORES --";
        int hx = (width - g.getFontMetrics().stringWidth(header)) / 2;
        g.drawString(header, hx, y);
        y += 24;
        for (int i = 0; i < Math.min(scores.size(), 5); i++) {
            g.setColor(i == 0 ? Color.YELLOW : Color.LIGHT_GRAY);
            String entry = (i + 1) + ". " + scores.get(i);
            int x = (width - g.getFontMetrics().stringWidth(entry)) / 2;
            g.drawString(entry, x, y);
            y += 20;
        }
    }

    private void drawPlatforms(Graphics2D g, List<Platform> platforms) {
        for (Platform p : platforms) {
            // Body: matches background but slightly lighter
            g.setColor(new Color(42, 46, 52));
            g.fillRect((int) p.x, (int) p.y, p.width, p.height);
            // Top edge: subtle green code-line highlight
            g.setColor(new Color(80, 180, 120, 180));
            g.fillRect((int) p.x, (int) p.y, p.width, 2);
            // Faint glow below top edge
            g.setColor(new Color(60, 140, 90, 60));
            g.fillRect((int) p.x, (int) p.y + 2, p.width, 6);
            // Subtle corner brackets
            g.setColor(new Color(100, 200, 140, 100));
            g.setFont(new Font("JetBrains Mono", Font.PLAIN, 10));
            g.drawString("[", (int) p.x + 2, (int) p.y + 14);
            g.drawString("]", (int) (p.x + p.width - 10), (int) p.y + 14);
        }
    }

    private void drawCoins(Graphics2D g, List<LevelManager.Coin> coins) {
        for (LevelManager.Coin c : coins) {
            if (c.collected) continue;
            g.setColor(new Color(255, 220, 50));
            g.setFont(new Font("SansSerif", Font.BOLD, 18));
            int pulse = (int) (Math.sin(System.currentTimeMillis() / 200.0) * 3);
            g.drawString("$", (int) c.x - 5, (int) c.y + 6 + pulse);
        }
    }

    private void drawBackgroundGrid(Graphics2D g, int width, int height, double cameraX) {
        g.setColor(new Color(35, 38, 42));
        int step = 40;
        int startX = ((int) cameraX / step) * step;
        for (int x = startX; x < cameraX + width + step; x += step) {
            g.drawLine(x, 0, x, height);
        }
        for (int y = step; y < height; y += step) {
            g.drawLine((int) cameraX, y, (int) cameraX + width + 200, y);
        }
    }

    private void drawScanlines(Graphics2D g, int width, int height) {
        g.setColor(new Color(0, 0, 0, 15));
        for (int i = 0; i < height; i += 4) {
            g.fillRect(0, i, width + 100, 1);
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
            } else if (log.contains("GRANTED") || log.contains("collected") || log.contains("killed")) {
                g.setColor(Color.YELLOW);
            } else {
                g.setColor(Color.GRAY);
            }
            g.drawString(log, 10, logY);
            logY += 16;
        }
    }

    private void drawHUD(Graphics2D g, int width, Player player, Boss boss,
                         int score, int combo, int comboTimer, GameState state, int level,
                         double difficulty, boolean inBattle, int currentWave, int totalWaves) {
        // Score + lives
        g.setFont(new Font("JetBrains Mono", Font.BOLD, 18));
        g.setColor(GameColors.PLAYER);
        g.drawString("Lines: " + score, 20, 30);
        // Lives
        g.setColor(Color.RED);
        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        g.drawString("❤".repeat(Math.max(0, player.getLives())), 20, 48);

        // Level + progress
        g.setFont(new Font("JetBrains Mono", Font.PLAIN, 11));
        g.setColor(Color.DARK_GRAY);
        double progress = level > 0 ? 0 : 0; // placeholder — actual progress needs cameraX
        g.drawString("Level " + (level + 1), 20, 60);

        // Combo with multiplier + timer bar
        if (combo > 1) {
            float comboScale = 1f + Math.min(combo * 0.03f, 0.4f);
            Font comboFont = new Font("JetBrains Mono", Font.BOLD, (int) (24 * comboScale));
            g.setFont(comboFont);
            double mult = combo >= 20 ? 3.0 : combo >= 10 ? 2.0 : combo >= 5 ? 1.5 : 1.0;
            Color multColor = mult >= 3 ? Color.ORANGE : mult >= 2 ? Color.YELLOW : Color.WHITE;
            g.setColor(multColor);
            String multStr = mult > 1.0 ? String.format(" (%.1fx)", mult) : "";
            g.drawString(combo + "x COMBO!" + multStr, 20, 78);
            // Combo timer bar
            int barW = 130;
            g.setColor(Color.DARK_GRAY);
            g.fillRect(20, 82, barW, 4);
            g.setColor(multColor);
            g.fillRect(20, 82, (int) (barW * (comboTimer / 100.0)), 4);
        }

        // HP bar
        int barWidth = 200;
        int barX = width - barWidth - 20;
        g.setColor(Color.DARK_GRAY);
        g.fillRect(barX, 20, barWidth, 12);
        double hpRatio = player.getHp() / (double) player.getMaxHp();
        Color hpColor = hpRatio > 0.5 ? GameColors.HP_BAR
                : hpRatio > 0.25 ? Color.YELLOW : GameColors.HP_LOW;
        g.setColor(hpColor);
        g.fillRect(barX, 20, (int) (hpRatio * barWidth), 12);
        g.setColor(Color.WHITE);
        g.setFont(new Font("JetBrains Mono", Font.BOLD, 10));
        g.drawString(player.getHp() + "/" + player.getMaxHp(), barX + 5, 30);

        // Weapon
        int buffY = 50;
        g.setFont(new Font("SansSerif", Font.BOLD, 12));

        WeaponType wp = player.getWeapon();
        if (wp != WeaponType.COMMIT) {
            g.setColor(wp == WeaponType.HEAVY ? GameColors.DANGER_RED
                    : wp == WeaponType.SPREAD ? GameColors.SUDO_YELLOW : GameColors.PLAYER);
            int ammo = player.getWeaponAmmo();
            g.drawString(wp.name() + " [" + ammo + "]", barX, buffY);
            buffY += 15;
        }

        if (player.getSudoTimer() > 0) {
            boolean expiring = player.isBuffExpiring(player.getSudoTimer());
            g.setColor(expiring && (System.currentTimeMillis() / 200 % 2 == 0)
                    ? Color.WHITE : GameColors.SUDO_YELLOW);
            g.drawString("SUDO " + (player.getSudoTimer() / 60) + "s", barX, buffY);
            buffY += 15;
        }
        if (player.getShieldTimer() > 0) {
            boolean expiring = player.isBuffExpiring(player.getShieldTimer());
            g.setColor(expiring && (System.currentTimeMillis() / 200 % 2 == 0)
                    ? Color.WHITE : GameColors.SHIELD_CYAN);
            g.drawString("SHIELD " + (player.getShieldTimer() / 60) + "s", barX, buffY);
            buffY += 15;
        }

        // Bombs
        g.setFont(new Font("JetBrains Mono", Font.BOLD, 11));
        g.setColor(player.getBombs() > 0 ? Color.ORANGE : Color.DARK_GRAY);
        g.drawString("BOMB x" + player.getBombs(), barX, buffY);
        buffY += 15;

        // Dash cooldown
        int dashCd = player.getDashCooldown();
        if (dashCd > 0) {
            g.setColor(new Color(100, 100, 100));
            g.setFont(new Font("JetBrains Mono", Font.PLAIN, 10));
            g.drawString("Dash: " + String.format("%.1f", dashCd / 60.0) + "s", barX, buffY);
        } else {
            g.setColor(GameColors.PLAYER);
            g.setFont(new Font("JetBrains Mono", Font.BOLD, 10));
            g.drawString("Dash: READY", barX, buffY);
        }
        buffY += 15;

        // Difficulty
        g.setFont(new Font("JetBrains Mono", Font.PLAIN, 10));
        g.setColor(Color.DARK_GRAY);
        String diffText = String.format("Threat: %.1fx", difficulty);
        g.drawString(diffText, barX, buffY + 5);

        // Battle zone wave indicator
        if (inBattle && totalWaves > 0) {
            g.setFont(new Font("JetBrains Mono", Font.BOLD, 16));
            g.setColor(Color.ORANGE);
            String waveText = "⚠  WAVE " + currentWave + " / " + totalWaves;
            int lx = (width - g.getFontMetrics().stringWidth(waveText)) / 2;
            g.drawString(waveText, lx, 35);
        }

        // Boss HP bar
        if (state == GameState.BOSS_FIGHT && boss != null && boss.isActive()) {
            int bw = 500;
            int bx = (width - bw) / 2;
            g.setColor(Color.DARK_GRAY);
            g.fillRect(bx, 64, bw, 14);
            g.setColor(GameColors.DANGER_RED);
            int bossHpWidth = (int) ((boss.getHp() / (double) boss.getMaxHp()) * bw);
            g.fillRect(bx, 64, bossHpWidth, 14);
            g.setColor(Color.WHITE);
            g.setFont(new Font("JetBrains Mono", Font.BOLD, 12));
            g.drawString(boss.getName(), bx + 5, 60);
        }
    }

    private void drawOverlay(Graphics2D g, int width, int areaH, String title, String sub, Color c) {
        g.setColor(GameColors.OVERLAY);
        g.fillRect(0, 0, width, areaH);
        drawCenteredString(g, width, title, areaH / 2 - 20, 40, c);
        drawCenteredString(g, width, sub, areaH / 2 + 30, 20, Color.WHITE);
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

        int startY = centerY + 50;
        int lineHeight = 25;

        String[] lines = {
                "[ ← / → ]   MOVE",
                "[ SHIFT ]   DASH",
                "[ SPACE ]   JUMP / DOUBLE JUMP",
                "[ C ]       SHOOT",
                "[ X ]       MELEE  [ Z ] WEAPON",
                "[ B ]       BOMB   [ SHIFT ] DASH",
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
