package com.bigphil.mergehell.render;

import com.bigphil.mergehell.i18n.GameText;

import com.bigphil.mergehell.engine.GameSession;
import com.bigphil.mergehell.engine.GameLoop;
import com.bigphil.mergehell.combat.DroneController;
import com.bigphil.mergehell.model.GameColors;
import com.bigphil.mergehell.progression.UpgradeDefinition;
import com.bigphil.mergehell.progression.BuildStats;
import com.bigphil.mergehell.progression.UpgradeCatalog;
import com.bigphil.mergehell.progression.UpgradeId;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.List;
import java.util.Locale;

public final class UpgradeOverlayRenderer {
    private static final Font EYEBROW = new Font(Font.SANS_SERIF, Font.BOLD, 14);
    private static final Font TITLE = new Font("JetBrains Mono", Font.BOLD, 20);
    private static final Font BODY = new Font(Font.SANS_SERIF, Font.PLAIN, 16);
    private static final int[] X = {75, 355, 635};

    public Rectangle cardBounds(int index) {
        if (index < 0 || index >= 3) throw new IndexOutOfBoundsException(index);
        return new Rectangle(X[index], 165, 250, 300);
    }

    public void render(Graphics2D original, GameSession session) {
        Graphics2D g = (Graphics2D) original.create();
        try { drawOverlay(g, session); } finally { g.dispose(); }
    }

    private void drawOverlay(Graphics2D g, GameSession session) {
        g.setColor(new Color(5, 8, 14, 225));
        g.fillRect(0, 0, GameViewport.LOGICAL_WIDTH, GameViewport.LOGICAL_HEIGHT);
        g.setFont(GameText.font(TITLE));
        g.setColor(Color.WHITE);
        GameText.draw(g, "BUILD SUCCESSFUL // SELECT PATCH", 238, 112);
        g.setFont(GameText.font(BODY));
        g.setColor(new Color(180, 195, 215));
        GameText.draw(g, "BUILD XP FULL • WORLD PAUSED • CHOOSE ONE UPGRADE", 252, 139);

        List<UpgradeDefinition> cards = session.upgradeChoices();
        for (int i = 0; i < Math.min(3, cards.size()); i++) drawCard(g, session, cards.get(i), i);
        g.setFont(GameText.font(EYEBROW));
        g.setColor(session.rerollAvailable() ? GameColors.SHIELD_CYAN : new Color(100, 110, 125));
        centered(g, session.rerollAvailable() ? "[ R ] REROLL AVAILABLE" : "REROLL CONSUMED", 495);
        g.setColor(new Color(165, 180, 200));
        centered(g, "PRESS 1 / 2 / 3  OR CLICK A CARD", 522);
    }

    private void drawCard(Graphics2D g, GameSession session, UpgradeDefinition card, int index) {
        Rectangle r = cardBounds(index);
        boolean relevant = card.isWeaponRelevant(session.runBuild().weapon());
        g.setColor(new Color(18, 24, 36, 245));
        g.fillRoundRect(r.x, r.y, r.width, r.height, 22, 22);
        g.setColor(relevant ? GameColors.SUDO_YELLOW : new Color(80, 160, 220));
        g.drawRoundRect(r.x, r.y, r.width, r.height, 22, 22);
        g.drawRoundRect(r.x + 3, r.y + 3, r.width - 6, r.height - 6, 19, 19);
        g.setFont(GameText.font(EYEBROW));
        GameText.draw(g, "[ " + (index + 1) + " ]  " + card.tag(), r.x + 18, r.y + 31);
        g.setFont(GameText.font(TITLE));
        g.setColor(Color.WHITE);
        drawWrapped(g, card.title(), r.x + 18, r.y + 70, 210, 23);
        if (card.id() == UpgradeId.DRONE_COPILOT) {
            drawDroneDetails(g, session, card, r);
            return;
        }
        g.setFont(GameText.font(BODY));
        g.setColor(new Color(190, 202, 218));
        fitLines(g, card.description(), 210, 4, 14);
        drawWrapped(g, card.description(), r.x + 18, r.y + 120, 210, 20);
        g.setFont(GameText.font(EYEBROW));
        g.setColor(new Color(125, 145, 170));
        GameText.draw(g, "ACTUAL EFFECT", r.x + 18, r.y + 207);
        g.setFont(GameText.font(BODY));
        g.setColor(relevant ? GameColors.SUDO_YELLOW : GameColors.SHIELD_CYAN);
        fitLines(g, impactText(session, card), 210, 3, 14);
        drawWrapped(g, impactText(session, card), r.x + 18, r.y + 230, 210, 18);
        g.setColor(relevant ? GameColors.SUDO_YELLOW : GameColors.SHIELD_CYAN);
        GameText.draw(g, card.id().name().startsWith("SUPPLY_") ? "APPLIES IMMEDIATELY" : "RANK " + session.runBuild().rank(card.id()) + " → "
                + (session.runBuild().rank(card.id()) + 1) + " / " + card.maxRank(),
                r.x + 18, r.y + 285);
    }

    private static String impactText(GameSession session, UpgradeDefinition card) {
        BuildStats before = session.runBuild().buildStats();
        BuildStats after = card.effect().apply(before);
        return switch (card.id()) {
            case COMMIT_RICOCHET, BEAM_REFRACTION -> "RICOCHET " + before.combat().ricochets() + " → " + after.combat().ricochets();
            case COMMIT_CRITICAL, RAPID_SIGNED_BUILDS -> "CRIT " + percent(before.combat().criticalChance())
                    + " → " + percent(after.combat().criticalChance());
            case FORCE_EXTRA_PELLETS, FIREWALL_DENSITY -> "PELLETS " + before.combat().pellets() + " → " + after.combat().pellets();
            case FORCE_KNOCKBACK -> "KNOCKBACK " + number(before.combat().knockback())
                    + " → " + number(after.combat().knockback());
            case DASH_CACHE -> "DASH COOLDOWN " + frames(before.dashCooldownMultiplier())
                    + "f → " + frames(after.dashCooldownMultiplier()) + "f";
            case SHIELD_REBOOT -> "LETHAL REBOOTS " + before.shieldReboots() + " → " + after.shieldReboots();
            case DRONE_COPILOT -> String.join(" / ", droneImpactLines(session));
            case COMBO_WINDOW -> "COMBO GRACE " + before.comboGraceTicks() + " → " + after.comboGraceTicks() + " TICKS";
            case DEPENDENCY_CORE_COMMIT, DEPENDENCY_CORE_FORCE, DEPENDENCY_CORE_RAPID,
                    DEPENDENCY_CORE_GC, DEPENDENCY_CORE_FIREWALL, DEPENDENCY_CORE_BEAM -> "ENABLES EVOLUTION AT LEVEL 5 / SAFE NODE";
            case RAPID_PIPELINE -> "FIRE INTERVAL " + before.combat().cooldownFrames() + " → " + after.combat().cooldownFrames() + " TICKS";
            case GC_COMPACTION, FIREWALL_PRESSURE, BEAM_FOCUS -> "DAMAGE " + before.combat().damage() + " → " + after.combat().damage();
            case GC_GENERATIONS -> "PIERCE " + before.combat().pierces() + " → " + after.combat().pierces();
            case SUPPLY_REPAIR -> "RESTORE 25 HP";
            case SUPPLY_BOMB -> "+1 BOMB / MAX 5";
            case SUPPLY_SHIELD -> "SHIELD FOR AT LEAST 2.9 SECONDS";
        };
    }

    private static void drawDroneDetails(Graphics2D g, GameSession session,
                                         UpgradeDefinition card, Rectangle bounds) {
        int rank = session.runBuild().rank(card.id());
        g.setFont(GameText.font(BODY));
        g.setColor(new Color(190, 202, 218));
        drawLines(g, UpgradeCatalog.droneDescriptionLines(rank + 1),
                bounds.x + 18, bounds.y + 120, 20);
        g.setFont(GameText.font(EYEBROW));
        g.setColor(new Color(125, 145, 170));
        GameText.draw(g, "ACTUAL EFFECT", bounds.x + 18, bounds.y + 207);
        g.setFont(GameText.font(BODY));
        g.setColor(GameColors.SHIELD_CYAN);
        drawLines(g, droneImpactLines(session), bounds.x + 18, bounds.y + 230, 20);
        GameText.draw(g, "RANK " + rank + " → " + (rank + 1) + " / " + card.maxRank(),
                bounds.x + 18, bounds.y + 285);
    }

    static List<String> droneImpactLines(GameSession session) {
        int level = session.runBuild().buildStats().droneLevel();
        var before = DroneController.profile(level);
        var after = DroneController.profile(level + 1);
        int weaponDamage = session.runBuild().effectiveStats().damage();
        if (after.count() > before.count()) {
            return List.of("DRONES " + before.count() + " → " + after.count(),
                    after.damage(weaponDamage) + " DMG / " + seconds(after.fireIntervalTicks()) + " EACH",
                    (before.count() == 0 ? "AUTO TARGET" : "SPLIT TARGETS")
                            + " · " + (int) after.acquisitionRange() + "px");
        }
        return List.of("EACH SHOT " + seconds(before.fireIntervalTicks()) + " → "
                        + seconds(after.fireIntervalTicks()),
                "DAMAGE " + before.damage(weaponDamage) + " → " + after.damage(weaponDamage) + " / SHOT",
                "PIERCE " + before.pierces() + " → " + after.pierces() + " ENEMY");
    }

    private static String seconds(int ticks) {
        return String.format(Locale.ROOT, "%.2fs", ticks * GameLoop.LEGACY_STEP_NANOS / 1e9);
    }

    private static void drawLines(Graphics2D g, List<String> lines, int x, int y, int lineHeight) {
        Font original = g.getFont();
        for (String line : lines) {
            g.setFont(original); GameText.fitFont(g, line, 210, 14);
            GameText.draw(g, line, x, y);
            y += lineHeight;
        }
        g.setFont(original);
    }

    private static void fitLines(Graphics2D g, String text, int width, int maxLines, int minimum) {
        while (g.getFont().getSize() > minimum && GameText.wrap(g.getFontMetrics(), text, width).size() > maxLines)
            g.setFont(g.getFont().deriveFont((float) g.getFont().getSize() - 1));
    }
    private static void centered(Graphics2D g, String text, int y) {
        GameText.draw(g, text, (GameViewport.LOGICAL_WIDTH - g.getFontMetrics().stringWidth(GameText.text(text))) / 2, y);
    }

    private static String percent(double value) { return Math.round(value * 100) + "%"; }
    private static int frames(double multiplier) { return Math.max(12, (int) Math.round(45 * multiplier)); }
    private static String number(double value) {
        return value == Math.rint(value) ? Integer.toString((int) value) : String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private static void drawWrapped(Graphics2D g, String text, int x, int y, int maxWidth, int lineHeight) {
        for (String line : GameText.wrap(g.getFontMetrics(), text, maxWidth)) {
            GameText.draw(g, line, x, y);
            y += lineHeight;
        }
    }
}
