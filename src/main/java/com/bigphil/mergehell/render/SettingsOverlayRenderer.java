package com.bigphil.mergehell.render;

import com.bigphil.mergehell.i18n.GameText;

import com.bigphil.mergehell.settings.SettingsEditor;
import com.bigphil.mergehell.settings.SettingsEditor.Option;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Objects;

/** 960 × 600 settings overlay. Its snapshot contains no live settings bean. */
public final class SettingsOverlayRenderer {
    private boolean compact;
    /** Called on the simulation thread before publishing the resized presentation frame. */
    public void setCompact(boolean enabled) { compact = enabled; }
    private static final Font TITLE = new Font(Font.SANS_SERIF, Font.BOLD, 24);
    private static final Font LABEL = new Font(Font.SANS_SERIF, Font.BOLD, 14);
    private static final Font BODY = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    private static final Font MONO = new Font(Font.MONOSPACED, Font.BOLD, 11);
    private static final Font COMPACT_LABEL = new Font(Font.SANS_SERIF, Font.BOLD, 18);
    private static final Font COMPACT_BODY = new Font(Font.SANS_SERIF, Font.PLAIN, 16);
    private static final Font COMPACT_CONTROLS = new Font(Font.SANS_SERIF, Font.BOLD, 16);
    private static final Color AMBER = new Color(241, 178, 71);
    private static final Color CYAN = new Color(83, 210, 236);
    private static final Color CREAM = new Color(241, 237, 225);
    private static final int ROW_X = 136, ROW_Y = 132, ROW_WIDTH = 688, ROW_HEIGHT = 31, ROW_PITCH = 34;

    public void render(Graphics2D target, SettingsEditor.Snapshot snapshot) {
        Objects.requireNonNull(target);
        Objects.requireNonNull(snapshot);
        Graphics2D g = (Graphics2D) target.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(new Color(3, 7, 11, 218));
            g.fillRect(0, 0, GameViewport.LOGICAL_WIDTH, GameViewport.LOGICAL_HEIGHT);
            g.setPaint(new GradientPaint(112, 42, new Color(28, 34, 39), 848, 558, new Color(10, 17, 23)));
            g.fillRoundRect(112, 42, 736, 516, 12, 12);
            g.setColor(snapshot.values().highContrast() ? CREAM : new Color(118, 107, 88));
            g.setStroke(new BasicStroke(1));
            g.drawRoundRect(112, 42, 736, 516, 12, 12);
            g.setColor(AMBER);
            g.fillRect(136, 42, 104, 3);
            g.setFont(GameText.font(TITLE));
            g.setColor(CREAM);
            GameText.draw(g, "SYSTEM SETTINGS", 142, 84);
            g.setFont(GameText.font(compact ? COMPACT_BODY : MONO));
            g.setColor(AMBER);
            rightAligned(g, "LOCAL PROFILE", 818, 82);
            g.setFont(GameText.font(compact ? COMPACT_BODY : BODY));
            g.setColor(new Color(191, 199, 205));
            GameText.draw(g, compact ? "Changes save automatically. Esc returns."
                    : "Changes apply immediately. Return to your game with Esc.", 142, 108);
            g.setColor(new Color(69, 79, 86));
            g.drawLine(136, 124, 824, 124);

            for (Option option : Option.values()) drawRow(g, snapshot, option);

            g.setColor(new Color(69, 79, 86));
            g.drawLine(136, 477, 824, 477);
            g.setFont(GameText.font(compact ? COMPACT_BODY : BODY));
            g.setColor(snapshot.values().highContrast() ? Color.WHITE : new Color(198, 204, 209));
            GameText.draw(g, compact ? compactDescription(snapshot.selected()) : snapshot.selected().description(), 142, 500);
            g.setFont(GameText.font(compact ? COMPACT_CONTROLS : MONO));
            g.setColor(AMBER);
            GameText.draw(g, compact ? "UP/DOWN Select   LEFT/RIGHT Adjust   ENTER Toggle   ESC Back"
                    : "UP/DOWN SELECT   LEFT/RIGHT ADJUST   ENTER TOGGLE   ESC BACK", 142, 534);
        } finally { g.dispose(); }
    }

    private void drawRow(Graphics2D g, SettingsEditor.Snapshot snapshot, Option option) {
        int y = ROW_Y + option.ordinal() * ROW_PITCH;
        boolean selected = snapshot.selected() == option;
        boolean highContrast = snapshot.values().highContrast();
        g.setColor(selected ? new Color(43, 47, 46) : new Color(18, 25, 31));
        g.fillRoundRect(ROW_X, y, ROW_WIDTH, ROW_HEIGHT, 6, 6);
        if (selected) {
            g.setColor(AMBER);
            g.drawRoundRect(ROW_X, y, ROW_WIDTH, ROW_HEIGHT, 6, 6);
            g.fillRect(ROW_X, y + 7, 3, ROW_HEIGHT - 14);
        }
        g.setFont(GameText.font(compact ? COMPACT_BODY : MONO));
        g.setColor(selected ? AMBER : highContrast ? CREAM : new Color(147, 160, 170));
        GameText.draw(g, String.format(java.util.Locale.ROOT, "%02d", option.ordinal() + 1), ROW_X + 15, y + 22);
        g.setFont(GameText.font(compact ? COMPACT_LABEL : LABEL));
        g.setColor(selected || highContrast ? CREAM : new Color(207, 214, 219));
        GameText.draw(g, option.label(), ROW_X + 48, y + 24);
        int value = snapshot.values().value(option);
        if (option.percentage()) {
            int sliderX = 559, sliderWidth = 156;
            g.setColor(new Color(66, 78, 87));
            g.fillRoundRect(sliderX, y + 17, sliderWidth, 5, 4, 4);
            g.setColor(selected ? AMBER : CYAN);
            g.fillRoundRect(sliderX, y + 17, sliderWidth * value / 100, 5, 4, 4);
            g.setColor(CREAM);
            g.fillRect(sliderX + sliderWidth * value / 100 - 2, y + 13, 4, 13);
            g.setFont(GameText.font(compact ? COMPACT_LABEL : LABEL));
            rightAligned(g, value + "%", 795, y + 24);
        } else if (option == Option.LANGUAGE) {
            g.setFont(GameText.font(new Font(Font.DIALOG, Font.BOLD, compact ? 18 : 14)));
            g.setColor(selected ? AMBER : CYAN);
            rightAligned(g, "‹ " + snapshot.values().language().nativeName() + " ›", 795, y + 24);
        } else {
            g.setColor(value == 0 ? new Color(39, 48, 54) : new Color(33, 67, 72));
            g.fillRoundRect(729, y + 5, 68, 23, 5, 5);
            g.setColor(selected ? AMBER : value == 0 ? new Color(173, 183, 191) : CYAN);
            g.drawRoundRect(729, y + 5, 68, 23, 5, 5);
            g.setFont(GameText.font(compact ? COMPACT_LABEL : MONO));
            String label = value == 0 ? "OFF" : "ON";
            GameText.draw(g, label, 763 - g.getFontMetrics().stringWidth(GameText.text(label)) / 2, y + 24);
        }
    }

    private static String compactDescription(Option option) {
        return switch (option) {
            case SHAKE -> "Reduce camera movement during combat.";
            case CRT -> "Show or hide CRT scanlines.";
            case FLASHES -> "Enable full-screen combat flashes.";
            case PARTICLES -> "Reduce decorative particle effects.";
            case VOLUME -> "Sound volume. Mute keeps this level.";
            case BACKGROUND -> "Background volume. Set to 0 for effects only.";
            case MUTED -> "Turn off sound without changing volume.";
            case AUTO_FIRE -> "Fire automatically while gameplay is active.";
            case HIGH_CONTRAST -> "Increase HUD and indicator contrast.";
            case LANGUAGE -> "One language across menus, combat and story.";
        };
    }

    private static void rightAligned(Graphics2D g, String text, int right, int y) {
        GameText.draw(g, text, right - g.getFontMetrics().stringWidth(GameText.text(text)), y);
    }
}
