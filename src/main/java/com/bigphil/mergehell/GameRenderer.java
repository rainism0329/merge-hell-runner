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
                       boolean inBattle, int currentWave, int totalWaves,
                       int transitionTimer, double runProgress) {

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (shakeTimer > 0) {
            int dx = (int) (Math.random() * 10 - 5);
            int dy = (int) (Math.random() * 10 - 5);
            g.translate(dx, dy);
        }

        // Camera scroll offset (saved for UI later)
        g.translate(-cameraX, 0);

        // Background tint: boss fights get a darker red tint
        // Level-specific theme
        LevelTheme theme = LevelTheme.forLevel(level);
        Color levelBg = (state == GameState.BOSS_FIGHT || state == GameState.BOSS_WARNING)
                ? new Color(30, 18, 18) : theme.bg;
        g.setColor(levelBg);
        g.fillRect((int) cameraX, 0, width + 200, groundY);

        // Background grid (themed)
        drawBackgroundGrid(g, width, groundY, cameraX, theme.grid);

        // Parallax code rain - themed code rain color
        Color crColor = new Color(theme.codeRain.getRed(), theme.codeRain.getGreen(),
                theme.codeRain.getBlue(), 100);
        g.setColor(crColor);
        g.setFont(new Font("JetBrains Mono", Font.PLAIN, 7));
        for (CodeRain cr : bgLayer3) cr.draw(g);
        g.setFont(new Font("JetBrains Mono", Font.PLAIN, 9));
        for (CodeRain cr : bgLayer2) cr.draw(g);
        g.setFont(new Font("JetBrains Mono", Font.PLAIN, 11));
        for (CodeRain cr : bgLayer1) cr.draw(g);

        g.setColor(theme.ground);
        g.fillRect((int) cameraX, groundY, width + 200, 10);
        g.setColor(Color.GRAY);
        g.drawLine((int) cameraX, groundY, (int) cameraX + width + 200, groundY);

        // Foreground world elements (on top of background)
        drawPlatforms(g, platforms, theme.accent);
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
                inBattle, currentWave, totalWaves, runProgress);
        drawUI(g, width, height, groundY, state, score, isNewHighScore, level);

        boolean isGameplay = state == GameState.RUNNING || state == GameState.BOSS_WARNING
                || state == GameState.BOSS_FIGHT || state == GameState.LEVEL_CLEAR;
        if (isGameplay) {
            drawTerminal(g, width, groundY, logs);
        }

        // Screen transition overlay
        if (transitionTimer > 0) {
            g.setColor(new Color(0, 0, 0, Math.min(transitionTimer, 255)));
            g.fillRect(0, 0, width, height);
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
            drawMenu(g, width, areaH);
        } else if (state == GameState.PAUSED) {
            drawPause(g, width, areaH);
        } else if (state == GameState.GAME_OVER) {
            String sub = "SCORE: " + score + (isNewHighScore ? "  ⭐ NEW HIGH SCORE!" : "");
            drawOverlay(g, width, areaH, "BUILD FAILED", sub, GameColors.DANGER_RED);
            drawCenteredString(g, width, "[ SPACE / ENTER ]  NEW RUN", centerY + 72, 12, Color.WHITE);
            drawTopScores(g, width, centerY);
        } else if (state == GameState.MISSION_COMPLETE) {
            String sub = "SCORE: " + score + "  |  Level " + (level + 1) + " cleared";
            drawOverlay(g, width, areaH, "MISSION COMPLETE", sub, Color.GREEN);
        } else if (state == GameState.BOSS_WARNING) {
            drawCenteredString(g, width, "⚠ WARNING: HIGH CPU LOAD ⚠", groundY / 2, 36, Color.RED);
        } else if (state == GameState.VICTORY) {
            String sub = "SCORE: " + score + (isNewHighScore ? "  ⭐ NEW HIGH SCORE!" : "");
            drawOverlay(g, width, areaH, "PRODUCTION READY", sub, Color.GREEN);
            drawCenteredString(g, width, "[ SPACE / ENTER ]  NEW RUN", centerY + 72, 12, Color.WHITE);
            drawTopScores(g, width, centerY);
        }
    }

    private void drawMenu(Graphics2D g, int width, int height) {
        g.setColor(new Color(8, 11, 16, 218));
        g.fillRect(0, 0, width, height);

        int margin = Math.max(18, width / 24);
        g.setFont(new Font("JetBrains Mono", Font.BOLD, 11));
        g.setColor(new Color(130, 210, 255));
        g.drawString("IDE ARCADE  //  RUN 01", margin, 32);

        int heroY = Math.max(118, height / 2 - 115);
        drawCenteredString(g, width, "MERGE HELL", heroY, Math.min(48, Math.max(29, width / 16)), Color.WHITE);
        drawCenteredString(g, width, "RUN THE BUILD. OUTRUN THE FAILURE.", heroY + 30, 13,
                new Color(160, 177, 195));

        int cardW = Math.min(520, width - margin * 2);
        int cardX = (width - cardW) / 2;
        int cardY = heroY + 55;
        drawPanel(g, cardX, cardY, cardW, 82, new Color(53, 117, 243));
        g.setFont(new Font("JetBrains Mono", Font.BOLD, 10));
        g.setColor(new Color(130, 210, 255));
        g.drawString("PRIMARY OBJECTIVE", cardX + 18, cardY + 23);
        g.setFont(new Font("JetBrains Mono", Font.PLAIN, 12));
        g.setColor(Color.WHITE);
        g.drawString("Clear four arenas. Reach the boss gate.", cardX + 18, cardY + 45);
        g.setColor(new Color(175, 188, 201));
        g.drawString("Every kill extends your combo window.", cardX + 18, cardY + 65);

        int ctaY = cardY + 104;
        int ctaW = Math.min(330, cardW);
        int ctaX = (width - ctaW) / 2;
        g.setColor(GameColors.PLAYER);
        g.fillRoundRect(ctaX, ctaY, ctaW, 38, 10, 10);
        drawCenteredString(g, width, "[ SPACE / ENTER ]  START RUN", ctaY + 25, 13, Color.WHITE);

        drawControlStrip(g, width, Math.min(height - 35, ctaY + 74));
    }

    private void drawPause(Graphics2D g, int width, int height) {
        g.setColor(new Color(8, 11, 16, 205));
        g.fillRect(0, 0, width, height);
        drawCenteredString(g, width, "RUN PAUSED", height / 2 - 35, 34, Color.YELLOW);
        drawCenteredString(g, width, "THE BUILD CAN WAIT.", height / 2 - 7, 13, new Color(190, 198, 207));
        int cardW = Math.min(350, width - 40);
        int cardX = (width - cardW) / 2;
        int cardY = height / 2 + 20;
        drawPanel(g, cardX, cardY, cardW, 52, Color.YELLOW);
        drawCenteredString(g, width, "[ P / ESC ]  RESUME RUN", cardY + 32, 13, Color.WHITE);
        drawControlStrip(g, width, Math.min(height - 35, cardY + 90));
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

    private void drawPlatforms(Graphics2D g, List<Platform> platforms, Color accent) {
        for (Platform p : platforms) {
            g.setColor(new Color(36, 40, 46));
            g.fillRect((int) p.x, (int) p.y, p.width, p.height);
            g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 180));
            g.fillRect((int) p.x, (int) p.y, p.width, 2);
            g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 60));
            g.fillRect((int) p.x, (int) p.y + 2, p.width, 6);
            g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 100));
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

    private void drawBackgroundGrid(Graphics2D g, int width, int height, double cameraX, Color gridColor) {
        g.setColor(new Color(gridColor.getRed(), gridColor.getGreen(), gridColor.getBlue(), 80));
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
        g.setColor(new Color(17, 22, 29));
        g.fillRect(0, yStart, width, TERMINAL_HEIGHT);
        g.setColor(new Color(86, 107, 128));
        g.drawLine(0, yStart, width, yStart);

        g.setFont(new Font("JetBrains Mono", Font.BOLD, 10));
        g.setColor(GameColors.HP_BAR);
        g.fillOval(12, yStart + 12, 7, 7);
        g.setColor(new Color(202, 213, 224));
        g.drawString("LIVE EVENT STREAM", 26, yStart + 19);
        g.setFont(new Font("JetBrains Mono", Font.PLAIN, 9));
        String status = "LOCAL / CONNECTED";
        g.setColor(new Color(130, 152, 173));
        g.drawString(status, width - g.getFontMetrics().stringWidth(status) - 12, yStart + 19);
        g.setColor(new Color(255, 255, 255, 24));
        g.drawLine(10, yStart + 28, width - 10, yStart + 28);

        g.setFont(new Font("JetBrains Mono", Font.PLAIN, 11));
        int logY = yStart + 46;
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
                         double difficulty, boolean inBattle, int currentWave, int totalWaves,
                         double runProgress) {
        LevelTheme theme = LevelTheme.forLevel(level);
        int margin = Math.max(12, width / 60);
        int leftW = Math.min(245, Math.max(165, width / 3));
        int rightW = Math.min(220, Math.max(145, width / 4));
        int rightX = width - rightW - margin;

        // The left card answers the question a player asks most often: "what am I doing
        // and how far am I from it?" Score remains prominent, without becoming the HUD's only signal.
        drawPanel(g, margin, 14, leftW, combo > 1 ? 112 : 90, theme.hud);
        g.setFont(new Font("JetBrains Mono", Font.BOLD, 10));
        g.setColor(new Color(178, 194, 210));
        g.drawString("RUN " + String.format("%02d", level + 1) + "  /  BOSS GATE", margin + 12, 31);
        g.setFont(new Font("JetBrains Mono", Font.BOLD, 20));
        g.setColor(Color.WHITE);
        g.drawString(String.format("%,d", score), margin + 12, 55);
        g.setFont(new Font("JetBrains Mono", Font.PLAIN, 10));
        g.setColor(new Color(185, 194, 203));
        g.drawString("LINES SHIPPED    " + (int) (runProgress * 100) + "%", margin + 12, 72);
        drawMeter(g, margin + 12, 78, leftW - 24, 4, runProgress, theme.hud);

        if (combo > 1) {
            double mult = combo >= 20 ? 3.0 : combo >= 10 ? 2.0 : combo >= 5 ? 1.5 : 1.0;
            Color multColor = mult >= 3 ? Color.ORANGE : mult >= 2 ? Color.YELLOW : Color.WHITE;
            g.setFont(new Font("JetBrains Mono", Font.BOLD, 13));
            g.setColor(multColor);
            g.drawString(combo + "x COMBO  " + String.format("%.1fx", mult), margin + 12, 99);
            drawMeter(g, margin + 12, 104, leftW - 24, 4, comboTimer / 100.0, multColor);
        }

        drawPanel(g, rightX, 14, rightW, 128, theme.hud);
        g.setFont(new Font("JetBrains Mono", Font.BOLD, 10));
        g.setColor(new Color(178, 194, 210));
        g.drawString("VITALS", rightX + 12, 31);
        g.setFont(new Font("JetBrains Mono", Font.BOLD, 11));
        g.setColor(Color.WHITE);
        g.drawString("HP  " + player.getHp() + " / " + player.getMaxHp(), rightX + 12, 49);
        double hpRatio = player.getHp() / (double) player.getMaxHp();
        Color hpColor = hpRatio > 0.5 ? GameColors.HP_BAR
                : hpRatio > 0.25 ? Color.YELLOW : GameColors.HP_LOW;
        drawMeter(g, rightX + 12, 55, rightW - 24, 7, hpRatio, hpColor);

        int buffY = 80;
        g.setFont(new Font("JetBrains Mono", Font.PLAIN, 10));
        WeaponType wp = player.getWeapon();
        if (wp != WeaponType.COMMIT) {
            g.setColor(wp == WeaponType.HEAVY ? GameColors.DANGER_RED
                    : wp == WeaponType.SPREAD ? GameColors.SUDO_YELLOW : GameColors.PLAYER);
            int ammo = player.getWeaponAmmo();
            g.drawString(wp.name() + "  " + ammo + " RDS", rightX + 12, buffY);
        } else {
            g.setColor(new Color(184, 194, 203));
            g.drawString("COMMIT CANNON", rightX + 12, buffY);
        }
        buffY += 16;
        if (player.getSudoTimer() > 0) {
            boolean expiring = player.isBuffExpiring(player.getSudoTimer());
            g.setColor(expiring && (System.currentTimeMillis() / 200 % 2 == 0)
                    ? Color.WHITE : GameColors.SUDO_YELLOW);
            g.drawString("SUDO " + (player.getSudoTimer() / 60) + "s", rightX + 12, buffY);
        }
        else if (player.getShieldTimer() > 0) {
            boolean expiring = player.isBuffExpiring(player.getShieldTimer());
            g.setColor(expiring && (System.currentTimeMillis() / 200 % 2 == 0)
                    ? Color.WHITE : GameColors.SHIELD_CYAN);
            g.drawString("SHIELD " + (player.getShieldTimer() / 60) + "s", rightX + 12, buffY);
        } else {
            g.setColor(new Color(184, 194, 203));
            g.drawString("JUMP  " + player.getJumpsRemaining() + " / 2", rightX + 12, buffY);
        }
        buffY += 16;
        g.setFont(new Font("JetBrains Mono", Font.PLAIN, 10));
        g.setColor(player.getBombs() > 0 ? Color.ORANGE : Color.DARK_GRAY);
        g.drawString("BOMB x" + player.getBombs(), rightX + 12, buffY);
        int dashCd = player.getDashCooldown();
        g.setColor(dashCd > 0 ? new Color(143, 153, 163) : GameColors.PLAYER);
        String dashText = dashCd > 0 ? "DASH " + String.format("%.1fs", dashCd / 60.0) : "DASH READY";
        g.drawString(dashText, rightX + rightW / 2, buffY);

        if (inBattle && totalWaves > 0) {
            int displayWave = Math.max(1, Math.min(currentWave, totalWaves));
            g.setFont(new Font("JetBrains Mono", Font.BOLD, 12));
            g.setColor(Color.ORANGE);
            String waveText = "ARENA LOCKED  //  WAVE " + displayWave + " / " + totalWaves;
            int lx = (width - g.getFontMetrics().stringWidth(waveText)) / 2;
            drawPanel(g, lx - 12, 16, g.getFontMetrics().stringWidth(waveText) + 24, 26, Color.ORANGE);
            g.drawString(waveText, lx, 34);
        }

        if (state == GameState.BOSS_FIGHT && boss != null && boss.isActive()) {
            int bw = Math.min(500, width - 80);
            int bx = (width - bw) / 2;
            drawPanel(g, bx - 10, 48, bw + 20, 38, GameColors.DANGER_RED);
            g.setColor(Color.WHITE);
            g.setFont(new Font("JetBrains Mono", Font.BOLD, 10));
            g.drawString(boss.getName(), bx, 63);
            drawMeter(g, bx, 69, bw, 7, boss.getHp() / (double) boss.getMaxHp(), GameColors.DANGER_RED);
        }
    }

    private void drawPanel(Graphics2D g, int x, int y, int width, int height, Color accent) {
        Composite original = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.88f));
        g.setColor(new Color(13, 18, 25));
        g.fillRoundRect(x, y, width, height, 9, 9);
        g.setComposite(original);
        g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 165));
        g.drawRoundRect(x, y, width, height, 9, 9);
    }

    private void drawMeter(Graphics2D g, int x, int y, int width, int height,
                           double ratio, Color fill) {
        double safeRatio = Math.max(0, Math.min(1, ratio));
        g.setColor(new Color(255, 255, 255, 32));
        g.fillRoundRect(x, y, width, height, height, height);
        g.setColor(fill);
        g.fillRoundRect(x, y, (int) (width * safeRatio), height, height, height);
    }

    private void drawControlStrip(Graphics2D g, int width, int y) {
        String controls = width < 620
                ? "[←/→] MOVE   [SPACE] JUMP   [C] FIRE   [P] PAUSE"
                : "[←/→] MOVE   [SPACE] DOUBLE JUMP   [C] FIRE   [X] MELEE   [SHIFT] DASH   [B] BOMB";
        g.setFont(new Font("JetBrains Mono", Font.PLAIN, 10));
        g.setColor(new Color(167, 181, 196));
        int x = (width - g.getFontMetrics().stringWidth(controls)) / 2;
        g.drawString(controls, Math.max(12, x), y);
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
