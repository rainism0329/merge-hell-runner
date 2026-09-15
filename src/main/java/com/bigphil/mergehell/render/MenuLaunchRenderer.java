package com.bigphil.mergehell.render;

import com.bigphil.mergehell.i18n.GameText;

import java.awt.*;

/** The menu's visible actions and their logical hit areas share the same layout. */
public final class MenuLaunchRenderer {
    public enum Choice { START, PRACTICE, BOSS, CONTINUE, SETTINGS, MUTE }
    public record Model(boolean godArmed, int savedWorld, boolean otherWindow, boolean muted, boolean savedSegment) {
        public Model(boolean godArmed, int savedWorld, boolean otherWindow, boolean muted) {
            this(godArmed, savedWorld, otherWindow, muted, false);
        }
    }

    private static Rectangle bounds(Choice choice) {
        return switch (choice) {
            case START -> new Rectangle(64, 426, 152, 68);
            case PRACTICE -> new Rectangle(228, 426, 152, 68);
            case BOSS -> new Rectangle(392, 426, 152, 68);
            case CONTINUE -> new Rectangle(64, 504, 480, 32);
            case SETTINGS -> new Rectangle(642, 504, 106, 32);
            case MUTE -> new Rectangle(756, 504, 162, 32);
        };
    }

    public static Choice at(int x, int y, Model model) {
        for (Choice choice : Choice.values()) {
            if (choice == Choice.CONTINUE && (model.savedWorld() < 1 || model.otherWindow())) continue;
            if (bounds(choice).contains(x, y)) return choice;
        }
        return null;
    }

    public static void render(Graphics2D target, Model model, boolean compact) {
        Graphics2D g = (Graphics2D) target.create();
        try {
            button(g, Choice.START, "ENTER / SPACE", model.godArmed() ? "Start practice" : "Campaign",
                    model.godArmed() ? "Invincible · No rank" : "Normal · Auto-save", true, compact);
            button(g, Choice.PRACTICE, "G", "Invincible", "Unlimited supplies", false, compact);
            button(g, Choice.BOSS, "L", "Boss practice", "Jump to Legacy", false, compact);
            Rectangle saved = bounds(Choice.CONTINUE);
            g.setColor(new Color(10, 20, 24, 225)); g.fillRoundRect(saved.x, saved.y, saved.width, saved.height, 5, 5);
            g.setFont(GameText.font(new Font(Font.SANS_SERIF, Font.PLAIN, compact ? 18 : 15)));
            g.setColor(new Color(205, 220, 207));
            String continuation = model.otherWindow() ? "ANOTHER WINDOW IS SAVING THE CAMPAIGN"
                    : model.savedWorld() > 0 ? model.savedSegment()
                        ? GameText.message("explore.continue", model.savedWorld())
                        : "[ R ] Continue · World " + model.savedWorld() + " entrance"
                    : GameText.message("explore.noSave");
            GameText.draw(g, continuation, saved.x + 12, saved.y + 22);
            g.setFont(GameText.font(new Font(Font.SANS_SERIF, Font.PLAIN, compact ? 17 : 14)));
            for (Choice choice : new Choice[]{Choice.SETTINGS, Choice.MUTE}) {
                Rectangle r = bounds(choice);
                g.setColor(new Color(10, 20, 24, 225));
                g.fillRoundRect(r.x, r.y, r.width, r.height, 5, 5);
                g.setColor(new Color(234, 189, 117));
                GameText.draw(g, choice == Choice.SETTINGS ? "[ O ] Settings"
                        : model.muted() ? "[ M ] Unmute" : "[ M ] Mute", r.x + 9, r.y + 22);
            }
        } finally { g.dispose(); }
    }

    private static void button(Graphics2D g, Choice choice, String key, String label,
                               String detail, boolean primary, boolean compact) {
        Rectangle r = bounds(choice);
        g.setColor(primary ? new Color(228, 166, 76) : new Color(14, 23, 28, 235));
        g.fillRoundRect(r.x, r.y, r.width, r.height, 6, 6);
        g.setColor(new Color(220, 159, 74)); g.drawRoundRect(r.x, r.y, r.width, r.height, 6, 6);
        g.setColor(primary ? new Color(46, 41, 31) : new Color(240, 187, 98));
        g.setFont(GameText.font(new Font(Font.SANS_SERIF, Font.BOLD, compact ? 15 : 12)));
        GameText.draw(g, "[ " + key + " ]", r.x + 12, r.y + 17);
        g.setFont(GameText.font(new Font(Font.SANS_SERIF, Font.BOLD, 21)));
        GameText.fitFont(g, label, r.width - 24, 15);
        g.setColor(primary ? new Color(20, 26, 28) : new Color(241, 232, 212));
        GameText.draw(g, label, r.x + 12, r.y + 42);
        g.setFont(GameText.font(new Font(Font.SANS_SERIF, Font.PLAIN, compact ? 14 : 12)));
        GameText.fitFont(g, detail, r.width - 24, 10);
        g.setColor(primary ? new Color(55, 49, 37) : new Color(173, 195, 197));
        GameText.draw(g, detail, r.x + 12, r.y + 59);
    }
}
