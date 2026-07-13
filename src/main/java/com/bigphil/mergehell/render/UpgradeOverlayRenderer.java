package com.bigphil.mergehell.render;

import com.bigphil.mergehell.engine.GameSession;
import com.bigphil.mergehell.model.GameColors;
import com.bigphil.mergehell.progression.UpgradeDefinition;
import com.bigphil.mergehell.progression.BuildStats;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.List;

public final class UpgradeOverlayRenderer {
    private static final Font EYEBROW = new Font("JetBrains Mono", Font.BOLD, 12);
    private static final Font TITLE = new Font("JetBrains Mono", Font.BOLD, 20);
    private static final Font BODY = new Font("JetBrains Mono", Font.PLAIN, 13);
    private static final int[] X = {75, 355, 635};

    public Rectangle cardBounds(int index) {
        if (index < 0 || index >= 3) throw new IndexOutOfBoundsException(index);
        return new Rectangle(X[index], 165, 250, 260);
    }

    public void render(Graphics2D g, GameSession session) {
        g.setColor(new Color(5, 8, 14, 225));
        g.fillRect(0, 0, GameViewport.LOGICAL_WIDTH, GameViewport.LOGICAL_HEIGHT);
        g.setFont(TITLE);
        g.setColor(Color.WHITE);
        g.drawString("BUILD SUCCESSFUL // SELECT PATCH", 238, 112);
        g.setFont(BODY);
        g.setColor(new Color(180, 195, 215));
        g.drawString("BUILD XP FULL • WORLD PAUSED • CHOOSE ONE UPGRADE", 252, 139);

        List<UpgradeDefinition> cards = session.upgradeChoices();
        for (int i = 0; i < Math.min(3, cards.size()); i++) drawCard(g, session, cards.get(i), i);
        g.setFont(EYEBROW);
        g.setColor(session.rerollAvailable() ? GameColors.SHIELD_CYAN : new Color(100, 110, 125));
        g.drawString(session.rerollAvailable() ? "[ R ] REROLL AVAILABLE" : "REROLL CONSUMED", 385, 468);
        g.setColor(new Color(165, 180, 200));
        g.drawString("PRESS 1 / 2 / 3  OR CLICK A CARD", 356, 495);
    }

    private void drawCard(Graphics2D g, GameSession session, UpgradeDefinition card, int index) {
        Rectangle r = cardBounds(index);
        boolean relevant = card.isWeaponRelevant(session.runBuild().weapon());
        g.setColor(new Color(18, 24, 36, 245));
        g.fillRoundRect(r.x, r.y, r.width, r.height, 22, 22);
        g.setColor(relevant ? GameColors.SUDO_YELLOW : new Color(80, 160, 220));
        g.drawRoundRect(r.x, r.y, r.width, r.height, 22, 22);
        g.drawRoundRect(r.x + 3, r.y + 3, r.width - 6, r.height - 6, 19, 19);
        g.setFont(EYEBROW);
        g.drawString("[ " + (index + 1) + " ]  " + card.tag(), r.x + 18, r.y + 31);
        g.setFont(TITLE);
        g.setColor(Color.WHITE);
        drawWrapped(g, card.title(), r.x + 18, r.y + 70, 210, 23);
        g.setFont(BODY);
        g.setColor(new Color(190, 202, 218));
        drawWrapped(g, card.description(), r.x + 18, r.y + 120, 210, 18);
        g.setFont(EYEBROW);
        g.setColor(new Color(125, 145, 170));
        g.drawString("ACTUAL EFFECT", r.x + 18, r.y + 181);
        g.setFont(BODY);
        g.setColor(relevant ? GameColors.SUDO_YELLOW : GameColors.SHIELD_CYAN);
        drawWrapped(g, impactText(session, card), r.x + 18, r.y + 202, 210, 17);
        g.setColor(relevant ? GameColors.SUDO_YELLOW : GameColors.SHIELD_CYAN);
        g.drawString("RANK " + session.runBuild().rank(card.id()) + " → "
                + (session.runBuild().rank(card.id()) + 1) + " / " + card.maxRank(),
                r.x + 18, r.y + 244);
    }

    private static String impactText(GameSession session, UpgradeDefinition card) {
        BuildStats before = session.runBuild().buildStats();
        BuildStats after = card.effect().apply(before);
        return switch (card.id()) {
            case COMMIT_RICOCHET -> "RICOCHET " + before.combat().ricochets() + " → " + after.combat().ricochets();
            case COMMIT_CRITICAL -> "CRIT " + percent(before.combat().criticalChance())
                    + " → " + percent(after.combat().criticalChance());
            case FORCE_EXTRA_PELLETS -> "PELLETS " + before.combat().pellets() + " → " + after.combat().pellets();
            case FORCE_KNOCKBACK -> "KNOCKBACK " + number(before.combat().knockback())
                    + " → " + number(after.combat().knockback());
            case DASH_CACHE -> "DASH COOLDOWN " + frames(before.dashCooldownMultiplier())
                    + "f → " + frames(after.dashCooldownMultiplier()) + "f";
            case SHIELD_REBOOT -> "LETHAL REBOOTS " + before.shieldReboots() + " → " + after.shieldReboots();
            case DRONE_COPILOT -> "AUTO DRONES " + before.droneLevel() + " → " + after.droneLevel();
            case COMBO_WINDOW -> "COMBO GRACE " + before.comboGraceTicks() + " → " + after.comboGraceTicks() + " TICKS";
            case DEPENDENCY_CORE_COMMIT, DEPENDENCY_CORE_FORCE -> "UNLOCKS WEAPON EVOLUTION AT LEVEL 5";
        };
    }

    private static String percent(double value) { return Math.round(value * 100) + "%"; }
    private static int frames(double multiplier) { return Math.max(12, (int) Math.round(45 * multiplier)); }
    private static String number(double value) {
        return value == Math.rint(value) ? Integer.toString((int) value) : String.format("%.1f", value);
    }

    private static void drawWrapped(Graphics2D g, String text, int x, int y, int maxWidth, int lineHeight) {
        StringBuilder line = new StringBuilder();
        int yy = y;
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && g.getFontMetrics().stringWidth(candidate) > maxWidth) {
                g.drawString(line.toString(), x, yy);
                line.setLength(0);
                line.append(word);
                yy += lineHeight;
            } else {
                line.setLength(0);
                line.append(candidate);
            }
        }
        if (!line.isEmpty()) g.drawString(line.toString(), x, yy);
    }
}
