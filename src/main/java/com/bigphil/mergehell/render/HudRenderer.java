package com.bigphil.mergehell.render;

import com.bigphil.mergehell.engine.GameSession;
import com.bigphil.mergehell.mission.MissionSegment;
import com.bigphil.mergehell.mission.EncounterDirector;
import com.bigphil.mergehell.model.GameColors;
import com.bigphil.mergehell.model.Player;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;

public final class HudRenderer {
    private static final Font LABEL = new Font("JetBrains Mono", Font.BOLD, 12);
    private static final Font VALUE = new Font("JetBrains Mono", Font.BOLD, 16);
    private static final Color PANEL = new Color(8, 12, 20, 218);
    private static final Color TRACK = new Color(255, 255, 255, 28);

    private static final int TOP = 14;
    private static final int SIDE_WIDTH = 288;

    public void render(Graphics2D g, GameSession session, Player player,
                       int score, int combo, int comboTimer, boolean unranked) {
        drawPlayerCard(g, session, player);
        drawMissionCard(g, session);
        drawRunCard(g, session, player, score, combo, comboTimer);
        if (unranked) drawLabStrip(g, player.isDebugMode());

        if (session.isOverclocked()) {
            g.setColor(new Color(70, 230, 255, 45));
            for (int i = 0; i < 5; i++) g.drawRoundRect(i, i, 959 - i * 2, 599 - i * 2, 24, 24);
        }
    }

    private void drawLabStrip(Graphics2D g, boolean powered) {
        int x = 176, y = 438, width = 608;
        g.setColor(new Color(8, 12, 20, 225));
        g.fillRoundRect(x, y, width, 30, 10, 10);
        g.setColor(powered ? GameColors.SUDO_YELLOW : new Color(160, 170, 180));
        g.drawRoundRect(x, y, width, 30, 10, 10);
        g.setFont(LABEL);
        String text = powered
                ? "LAB // H SUPPLY  U LEVEL  J NEXT  K CLEAR  L BOSS  T EXIT  N RESET"
                : "LAB RUN // UNRANKED  •  T/F12 RESTORE POWERS  •  N NEW RANKED RUN";
        g.drawString(text, x + 12, y + 20);
    }

    private void drawPlayerCard(Graphics2D g, GameSession session, Player player) {
        g.setColor(PANEL);
        g.fillRoundRect(14, TOP, SIDE_WIDTH, 106, 14, 14);
        g.setFont(LABEL);
        g.setColor(Color.WHITE);
        g.drawString("HP " + player.getHp() + "/" + player.getMaxHp(), 28, 34);
        drawRight(g, "LIVES x" + player.getLives(), 288, 34);
        drawBar(g, 28, 41, 260, 10, player.getHp() / (double) player.getMaxHp(),
                GameColors.HEALTH_GREEN);

        g.setFont(LABEL);
        g.setColor(Color.WHITE);
        g.drawString("BUILD LV." + session.buildProgress().level(), 28, 67);
        g.setColor(new Color(190, 205, 220));
        drawRight(g, session.buildProgress().currentXp() + "/"
                + session.buildProgress().nextThreshold() + " XP", 288, 67);
        drawBar(g, 28, 74, 260, 8,
                session.buildProgress().currentXp() / (double) session.buildProgress().nextThreshold(),
                GameColors.SHIELD_CYAN);

        g.setColor(GameColors.SUDO_YELLOW);
        String weapon = player.isUsingTemporaryWeapon()
                ? "PICKUP " + player.getWeapon().name() + "  " + player.getWeaponAmmo()
                : session.runBuild().weapon().name().replace('_', ' ')
                    + "  L" + session.runBuild().weaponLevel();
        int weaponWidth = player.getShieldRebootsRemaining() > 0 ? 154 : 260;
        g.drawString(fit(g, weapon, weaponWidth), 28, 104);
        if (player.getShieldRebootsRemaining() > 0) {
            g.setColor(GameColors.SHIELD_CYAN);
            drawRight(g, "REBOOT x" + player.getShieldRebootsRemaining(), 288, 104);
        }
    }

    private void drawRunCard(Graphics2D g, GameSession session, Player player,
                             int score, int combo, int comboTimer) {
        g.setColor(PANEL);
        g.fillRoundRect(658, TOP, SIDE_WIDTH, 106, 14, 14);
        g.setFont(VALUE);
        g.setColor(Color.WHITE);
        g.drawString(String.format("%07d", score), 672, 38);
        g.setFont(LABEL);
        g.setColor(combo >= 10 ? GameColors.SUDO_YELLOW : Color.WHITE);
        drawRight(g, combo > 0 ? "COMBO x" + combo : "COMBO --", 932, 38);
        drawBar(g, 672, 46, 260, 5,
                combo > 0 ? comboTimer / (double) session.comboWindowTicks() : 0,
                combo >= 10 ? GameColors.SUDO_YELLOW : GameColors.SHIELD_CYAN);

        g.setColor(Color.WHITE);
        g.drawString(session.isOverclocked()
                ? "SUDO // " + String.format("%.1fs", session.overclockActiveTicks() / 60.0)
                : "SUDO CHARGE", 672, 72);
        int dash = player.getDashCooldown();
        g.setColor(dash > 0 ? new Color(160, 174, 188) : GameColors.SHIELD_CYAN);
        drawRight(g, dash > 0 ? "DASH " + String.format("%.1fs", dash / 60.0) : "DASH READY",
                932, 72);
        drawBar(g, 672, 79, 260, 9, session.overclockRatio(),
                session.isOverclocked() ? GameColors.SUDO_YELLOW : GameColors.SHIELD_CYAN);

        g.setColor(new Color(190, 205, 220));
        g.drawString("BOMB x" + player.getBombs(), 672, 105);
        g.setColor(player.getShieldTimer() > 0 ? GameColors.SHIELD_CYAN : new Color(150, 164, 178));
        String defense = player.getShieldTimer() > 0
                ? "SHIELD " + Math.max(1, player.getShieldTimer() / 60) + "s"
                : "JUMPS " + player.getJumpsRemaining() + "/2";
        drawRight(g, defense, 932, 105);
    }

    private void drawMissionCard(Graphics2D g, GameSession session) {
        MissionSegment segment = session.currentMissionSegment();
        if (segment == null) return;
        int x = 314, width = 332;
        g.setColor(PANEL);
        g.fillRoundRect(x, TOP, width, 80, 14, 14);
        g.setFont(LABEL);
        g.setColor(GameColors.SHIELD_CYAN);
        g.drawString("MISSION // " + segment.kind(), x + 14, 35);
        String timer = String.format("%02d:%02d", session.missionSegmentTicksRemaining() / 3600,
                (session.missionSegmentTicksRemaining() / 60) % 60);
        drawRight(g, timer, x + width - 14, 35);
        g.setColor(Color.WHITE);
        g.drawString(fit(g, segment.objective(), width - 28), x + 14, 57);
        if (segment.objective().startsWith("CLEAR")) {
            g.setColor(GameColors.SUDO_YELLOW);
            g.drawString("KILLS " + Math.min(EncounterDirector.BOSS_KILL_TARGET, session.missionObjectiveKills())
                    + "/" + EncounterDirector.BOSS_KILL_TARGET, x + 14, 78);
        }
    }

    private static void drawBar(Graphics2D g, int x, int y, int w, int h, double ratio, Color color) {
        g.setColor(TRACK);
        g.fillRoundRect(x, y, w, h, h, h);
        g.setColor(color);
        g.fillRoundRect(x, y, (int) Math.round(w * Math.max(0, Math.min(1, ratio))), h, h, h);
    }

    private static void drawRight(Graphics2D g, String text, int right, int baseline) {
        g.drawString(text, right - g.getFontMetrics().stringWidth(text), baseline);
    }

    private static String fit(Graphics2D g, String text, int maxWidth) {
        FontMetrics metrics = g.getFontMetrics();
        if (metrics.stringWidth(text) <= maxWidth) return text;
        String suffix = "...";
        int length = text.length();
        while (length > 0 && metrics.stringWidth(text.substring(0, length) + suffix) > maxWidth) length--;
        return text.substring(0, length) + suffix;
    }
}
