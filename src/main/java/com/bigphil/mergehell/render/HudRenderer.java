package com.bigphil.mergehell.render;

import com.bigphil.mergehell.i18n.GameText;

import com.bigphil.mergehell.engine.GameSession;
import com.bigphil.mergehell.combat.WeaponCatalog;
import com.bigphil.mergehell.model.Player;
import com.bigphil.mergehell.mission.MissionRouteProgress;
import java.awt.*;

/** Compact combat instruments keep the central arena visible. */
public final class HudRenderer {
    private boolean highContrast;
    private boolean compact;
    public void setHighContrast(boolean enabled) { highContrast = enabled; }
    /** Called on the simulation thread before publishing the resized presentation frame. */
    public void setCompact(boolean enabled) { compact = enabled; }
    private static final Font SMALL = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
    private static final Font LABEL = new Font(Font.SANS_SERIF, Font.BOLD, 12);
    private static final Font COMPACT_LABEL = new Font(Font.SANS_SERIF, Font.BOLD, 18);
    private static final Font COMPACT_BODY = new Font(Font.SANS_SERIF, Font.PLAIN, 18);
    private static final Color PANEL = new Color(8, 12, 16, 200);
    private static final Color GREEN = new Color(154, 235, 147), CYAN = new Color(76, 221, 232);
    private static final Color AMBER = new Color(246, 187, 97), TEXT = new Color(222, 227, 217);

    public void render(Graphics2D original, GameSession session, Player player, int score, int combo,
                       int comboTimer, boolean unranked, boolean showMissionCard) {
        Graphics2D g = (Graphics2D) original.create();
        try {
            if (compact) {
                renderCompact(g, session, player, unranked, showMissionCard);
                return;
            }
            Color panel = highContrast ? new Color(3, 6, 10, 246) : PANEL;
            g.setColor(panel); g.fillRoundRect(14, 12, 276, 80, 6, 6);
            g.setFont(GameText.font(LABEL)); g.setColor(GREEN); GameText.draw(g, "HP", 26, 31);
            bar(g, 64, 22, 158, 10, player.getHp() / (double) player.getMaxHp(), GREEN);
            g.setFont(GameText.font(SMALL)); g.setColor(TEXT); right(g, player.getHp() + "/" + player.getMaxHp(), 278, 31);
            g.setColor(CYAN); GameText.draw(g, "SUDO", 26, 52);
            bar(g, 64, 43, 158, 8, session.overclockRatio(), session.isOverclocked() ? AMBER : CYAN);
            right(g, session.isOverclocked() ? seconds(session.overclockActiveTicks()) : "CHARGE", 278, 52);
            String weapon = player.isUsingTemporaryWeapon() ? player.getWeapon().name() + "  " + player.getWeaponAmmo()
                    : WeaponCatalog.definition(session.runBuild().weapon()).displayName() + " L" + session.runBuild().weaponLevel();
            g.setFont(GameText.font(LABEL)); g.setColor(AMBER); GameText.draw(g, fit(g, weapon.toUpperCase(), 218), 26, 75);
            right(g, "LV " + session.buildProgress().level(), 278, 75);
            bar(g, 26, 83, 252, 3, session.buildProgress().currentXp() / (double) session.buildProgress().nextThreshold(), CYAN);
            if (session.runBuild().evolutionReady()) {
                g.setFont(GameText.font(SMALL)); g.setColor(AMBER); GameText.draw(g, "EVOLUTION READY // NEXT SAFE NODE", 26, 108);
            } else if (session.runBuild().evolved()) {
                g.setFont(GameText.font(SMALL)); g.setColor(AMBER); GameText.draw(g, WeaponCatalog.definition(session.runBuild().weapon()).evolutionName(), 26, 108);
            }
            g.setColor(panel); g.fillRoundRect(717, 12, 229, 80, 6, 6);
            g.setFont(GameText.font(new Font(Font.SANS_SERIF, Font.BOLD, 23))); g.setColor(TEXT);
            String scoreText = String.format(java.util.Locale.ROOT, "%07d", score);
            GameText.fitFont(g, scoreText, 119, 14);
            right(g, scoreText, 932, 38);
            g.setFont(GameText.font(SMALL)); g.setColor(combo >= 10 ? AMBER : CYAN);
            GameText.draw(g, fit(g, combo > 0 ? "COMBO x" + combo : "COMBO --", 74), 730, 31);
            drawBombInstrument(g, player, false, 730, 54, 864);
            g.setFont(GameText.font(SMALL)); g.setColor(TEXT);
            right(g, GameText.message("hud.lives.only", player.getLives()), 932, 54);
            bar(g, 730, 61, 202, 3, combo > 0 ? comboTimer / (double) session.comboWindowTicks() : 0, AMBER);
            g.setColor(player.getDashCooldown() == 0 ? CYAN : TEXT);
            GameText.draw(g, player.getDashCacheTicks() > 0 ? "NEXT SHOT BOOST" : player.getDashCooldown() == 0
                    ? "DASH READY" : "DASH " + seconds(player.getDashCooldown()), 730, 80);
            right(g, player.getShieldTimer() > 0 ? "SHIELD " + seconds(player.getShieldTimer())
                    : "JUMPS " + player.getJumpsRemaining() + "/2", 932, 80);
            drawMissionCard(g, session.routeProgress(), showMissionCard, false, panel);
            drawRunStatus(g, unranked, player.isDebugMode(), false, panel);
            if (session.isOverclocked()) {
                g.setColor(new Color(246, 187, 97, 100)); g.setStroke(new BasicStroke(2)); g.drawRect(1, 1, 957, 568);
            }
        } finally { g.dispose(); }
    }

    private void renderCompact(Graphics2D g, GameSession session, Player player,
                               boolean unranked, boolean showMissionCard) {
        // At 600 × 400 the 960-wide world scales to 0.625: 18 logical pixels remain 11.25 pixels.
        // Keep immediate decisions here; score/combo/XP explanations belong in the full-size HUD.
        Color panel = highContrast ? new Color(3, 6, 10, 246) : PANEL;
        g.setColor(panel); g.fillRoundRect(14, 12, 326, 96, 6, 6);
        g.setFont(GameText.font(COMPACT_LABEL)); g.setColor(GREEN); GameText.draw(g, "HP", 26, 36);
        bar(g, 66, 23, 166, 13, player.getHp() / (double) player.getMaxHp(), GREEN);
        g.setFont(GameText.font(COMPACT_BODY)); g.setColor(TEXT);
        right(g, player.getHp() + "/" + player.getMaxHp(), 328, 36);
        String weapon = player.isUsingTemporaryWeapon() ? player.getWeapon().name() + "  " + player.getWeaponAmmo()
                : WeaponCatalog.definition(session.runBuild().weapon()).displayName() + " L" + session.runBuild().weaponLevel();
        g.setFont(GameText.font(COMPACT_LABEL)); g.setColor(AMBER);
        GameText.draw(g, fit(g, weapon.toUpperCase(), 302), 26, 65);
        g.setFont(GameText.font(COMPACT_BODY)); g.setColor(TEXT);
        GameText.draw(g, GameText.message("hud.lives.only", player.getLives()), 26, 94);
        drawBombInstrument(g, player, true, 138, 94, 328);

        drawMissionCard(g, session.routeProgress(), showMissionCard, true, panel);

        g.setColor(panel); g.fillRoundRect(694, 12, 252, 96, 6, 6);
        g.setFont(GameText.font(COMPACT_LABEL));
        g.setColor(player.getDashCacheTicks() > 0 ? AMBER : player.getDashCooldown() == 0 ? CYAN : TEXT);
        GameText.draw(g, player.getDashCacheTicks() > 0 ? "NEXT SHOT BOOST" : player.getDashCooldown() == 0
                ? "DASH READY" : "DASH " + seconds(player.getDashCooldown()), 708, 36);
        g.setFont(GameText.font(COMPACT_BODY)); g.setColor(TEXT);
        GameText.draw(g, player.getShieldTimer() > 0 ? "SHIELD " + seconds(player.getShieldTimer())
                : "JUMPS " + player.getJumpsRemaining() + "/2", 708, 65);
        g.setColor(session.isOverclocked() ? AMBER : CYAN);
        GameText.draw(g, session.isOverclocked() ? "SUDO " + seconds(session.overclockActiveTicks())
                : "SUDO " + Math.round(session.overclockRatio() * 100) + "%", 708, 94);

        drawRunStatus(g, unranked, player.isDebugMode(), true, panel);
        if (session.isOverclocked()) {
            g.setColor(new Color(246, 187, 97, 100)); g.setStroke(new BasicStroke(2)); g.drawRect(1, 1, 957, 568);
        }
    }

    /** One resource row inside the existing HUD footprint, leaving scenery and other overlays clear. */
    private static void drawBombInstrument(Graphics2D g, Player player, boolean compact,
                                           int x, int baseline, int right) {
        boolean unlimited = player.isDebugMode();
        boolean empty = !unlimited && player.getBombs() == 0;
        Color accent = unlimited ? CYAN : empty ? new Color(203, 166, 151) : AMBER;
        int iconWidth = compact ? 11 : 8, iconHeight = compact ? 12 : 9;
        g.setColor(accent);
        g.fillRoundRect(x, baseline - iconHeight + 1, iconWidth, iconHeight, 3, 3);
        g.fillRect(x + 2, baseline - iconHeight - 2, iconWidth - 4, 3);
        g.drawLine(x + iconWidth - 3, baseline - iconHeight - 2, x + iconWidth, baseline - iconHeight - 5);
        int keyX = x + iconWidth + (compact ? 6 : 4);
        g.setFont(GameText.font(compact ? COMPACT_BODY : LABEL));
        GameText.draw(g, "[B]", keyX, baseline);
        int labelX = keyX + g.getFontMetrics().stringWidth("[B]") + (compact ? 8 : 6);
        g.setFont(GameText.font(new Font(Font.SANS_SERIF, Font.BOLD, compact ? 20 : 14)));
        String quantity = unlimited ? "∞" : Integer.toString(player.getBombs());
        int quantityX = right - g.getFontMetrics().stringWidth(quantity);
        GameText.draw(g, quantity, quantityX, baseline);
        g.setFont(GameText.font(compact ? COMPACT_BODY : LABEL));
        String label = GameText.message(unlimited ? "hud.bombs.unlimited" : empty ? "hud.bombs.empty" : "hud.bombs.label");
        GameText.draw(g, fit(g, label, quantityX - labelX - 6), labelX, baseline);
    }

    record MissionCard(String route, String objective, String condition, String next, double progress) { }

    static MissionCard missionCard(MissionRouteProgress progress) {
        String route = "ROUTE " + progress.stageNumber() + "/" + progress.stageCount()
                + " · " + progress.stagesRemaining() + " LEFT";
        if (progress.atBossGate()) return new MissionCard(route, "BOSS GATE",
                "CLEAR AREA: " + progress.activeHostiles() + " HOSTILES", "BOSS AFTER AREA IS CLEAR", progress.routeFraction());
        if (progress.killTarget() > 0) return new MissionCard(route, progress.objective(),
                "KILLS " + progress.kills() + "/" + progress.killTarget()
                        + " · STAGE " + stageClock(progress.stageTicksRemaining()),
                progress.waitingForKills() ? "TIME DONE · FINISH KILLS"
                        : progress.kills() >= progress.killTarget() ? "TARGET DONE · FINISH TIMER"
                        : "TIMER + KILLS, THEN CLEAR", progress.routeFraction());
        return new MissionCard(route, progress.objective(), "THIS STAGE " + stageClock(progress.stageTicksRemaining()),
                "BOSS: ROUTE + CLEAR AREA", progress.routeFraction());
    }

    private static String stageClock(int ticks) {
        int seconds = (int) Math.ceil(ticks * 0.016);
        return String.format(java.util.Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }

    private static void drawMissionCard(Graphics2D g, MissionRouteProgress progress,
                                        boolean visible, boolean compact, Color panel) {
        // The boss owns the upper-center warning region once it has spawned.
        if (!visible || progress.kind() == null || progress.bossSpawned()) return;
        MissionCard card = missionCard(progress);
        int left = compact ? 354 : 308, width = compact ? 326 : 391;
        int textX = left + 14, textWidth = width - 28;
        g.setColor(panel); g.fillRoundRect(left, 12, width, 96, 6, 6);
        g.setFont(GameText.font(compact ? COMPACT_BODY : LABEL));
        g.setColor(AMBER); GameText.draw(g, fit(g, card.route(), textWidth), textX, 30);
        g.setColor(TEXT); GameText.draw(g, fit(g, card.objective(), textWidth), textX, compact ? 52 : 50);
        g.setFont(GameText.font(compact ? COMPACT_BODY : SMALL));
        g.setColor(CYAN); GameText.draw(g, fit(g, card.condition(), textWidth), textX, compact ? 74 : 70);
        g.setColor(TEXT); GameText.draw(g, fit(g, card.next(), textWidth), textX, compact ? 96 : 90);
        bar(g, textX, 102, textWidth, 3, card.progress(), AMBER);
    }

    static String labStatus(boolean invincible) { return invincible ? "Invincible · [ T ] Disable" : "Invincibility disabled"; }

    private static void drawRunStatus(Graphics2D g, boolean unranked, boolean invincible,
                                      boolean compact, Color panel) {
        // The combat badge reports the current power. Past use belongs to the result explanation.
        if (!invincible) return;
        g.setColor(panel); g.fillRoundRect(14, 519, compact ? 276 : 230, 36, 5, 5);
        g.setFont(GameText.font(compact ? COMPACT_LABEL : LABEL));
        g.setColor(AMBER);
        GameText.draw(g, labStatus(true), 26, 543);
    }

    private static String seconds(int ticks) { return String.format(java.util.Locale.ROOT, "%.1fs", ticks * 0.016); }
    private static void bar(Graphics2D g, int x, int y, int width, int height, double ratio, Color color) {
        g.setColor(new Color(205, 224, 220, 33)); g.fillRect(x, y, width, height);
        g.setColor(color); g.fillRect(x, y, (int) Math.round(width * Math.max(0, Math.min(1, ratio))), height);
    }
    private static void right(Graphics2D g, String text, int x, int y) { GameText.draw(g, text, x - g.getFontMetrics().stringWidth(GameText.text(text)), y); }
    private static String fit(Graphics2D g, String text, int width) {
        text = GameText.text(text);
        if (g.getFontMetrics().stringWidth(GameText.text(text)) <= width) return text;
        int end = text.length();
        while (end > 0 && g.getFontMetrics().stringWidth(GameText.text(text.substring(0, end) + "...")) > width) end--;
        return text.substring(0, end) + "...";
    }
}
