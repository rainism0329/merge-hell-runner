package com.bigphil.mergehell.settings;

import com.bigphil.mergehell.persistence.MergeHellState.Settings;
import com.bigphil.mergehell.i18n.GameLanguage;

import java.util.Objects;

/**
 * Simulation-thread settings editor. Changes are immediate; BACK requests closing, not reverting.
 * The caller applies/persists VALUE_CHANGED through settings(), and keeps menu/pause ownership.
 * No input listener, rendering, audio I/O or persistence callback runs inside this model.
 */
public final class SettingsEditor {
    public enum Command { UP, DOWN, LEFT, RIGHT, ACTIVATE, BACK }
    public enum Result { UNCHANGED, SELECTION_CHANGED, VALUE_CHANGED, CLOSE_REQUESTED }

    public enum Option {
        SHAKE("SCREEN SHAKE", "Scale camera shake during hits, explosions and boss attacks.", true),
        CRT("CRT FILTER", "Add the CRT scanline treatment to the game image.", false),
        FLASHES("FLASH EFFECTS", "Allow full-screen flashes from combat effects.", false),
        PARTICLES("PARTICLE DENSITY", "Control how many decorative combat particles are displayed.", true),
        VOLUME("MASTER VOLUME", "Adjust all audio; mute preserves this level.", true),
        BACKGROUND("BACKGROUND AUDIO", "Scene ambience and music. Set to 0 for effects only.", true),
        MUTED("MUTE AUDIO", "Start quietly by default. M toggles all game audio.", false),
        AUTO_FIRE("AUTO FIRE", "Fire the equipped weapon automatically during active gameplay.", false),
        HIGH_CONTRAST("HIGH CONTRAST", "Strengthen the contrast of HUD and gameplay indicators.", false),
        LANGUAGE("LANGUAGE", "Choose one language for every game screen and message.", false);

        private final String label;
        private final String description;
        private final boolean percentage;

        Option(String label, String description, boolean percentage) {
            this.label = label;
            this.description = description;
            this.percentage = percentage;
        }

        public String label() { return label; }
        public String description() { return description; }
        public boolean percentage() { return percentage; }
    }

    /** Immutable values are safe to pass to a renderer; bean exports always allocate a fresh copy. */
    public record Values(int shakePercent, boolean crt, boolean flashes, int particlePercent,
                         int volumePercent, int backgroundVolumePercent, boolean muted, boolean autoFire, boolean highContrast, GameLanguage language) {
        public Values {
            shakePercent = clamp(shakePercent);
            particlePercent = clamp(particlePercent);
            volumePercent = clamp(volumePercent);
            backgroundVolumePercent = clamp(backgroundVolumePercent);
            language = language == null ? GameLanguage.systemDefault() : language;
        }

        public static Values from(Settings settings) {
            Settings source = settings == null ? new Settings() : settings;
            return new Values(source.shakePercent, source.crt, source.flashes, source.particlePercent,
                    source.volumePercent, source.backgroundVolumePercent, source.muted, source.autoFire, source.highContrast, GameLanguage.fromTag(source.language));
        }

        public Settings toSettings() {
            Settings out = new Settings();
            out.shakePercent = shakePercent;
            out.crt = crt;
            out.flashes = flashes;
            out.particlePercent = particlePercent;
            out.volumePercent = volumePercent;
            out.backgroundVolumePercent = backgroundVolumePercent;
            out.muted = muted;
            out.autoFire = autoFire;
            out.highContrast = highContrast;
            out.language = language.tag();
            return out;
        }

        /** Percentages are 0–100; boolean options are represented as 0/1. */
        public int value(Option option) {
            return switch (Objects.requireNonNull(option)) {
                case SHAKE -> shakePercent;
                case CRT -> crt ? 1 : 0;
                case FLASHES -> flashes ? 1 : 0;
                case PARTICLES -> particlePercent;
                case VOLUME -> volumePercent;
                case BACKGROUND -> backgroundVolumePercent;
                case MUTED -> muted ? 1 : 0;
                case AUTO_FIRE -> autoFire ? 1 : 0;
                case HIGH_CONTRAST -> highContrast ? 1 : 0;
                case LANGUAGE -> language.ordinal();
            };
        }
    }

    public record Snapshot(Option selected, Values values) {
        public Snapshot { Objects.requireNonNull(selected); Objects.requireNonNull(values); }
    }

    private static final Option[] OPTIONS = Option.values();
    private final Settings draft;
    private int selectedIndex;

    public SettingsEditor(Settings source) { draft = Values.from(source).toSettings(); }

    public Settings settings() { return Values.from(draft).toSettings(); }
    public Snapshot snapshot() { return new Snapshot(OPTIONS[selectedIndex], Values.from(draft)); }

    public Result handle(Command command) {
        return switch (Objects.requireNonNull(command)) {
            case UP -> move(-1);
            case DOWN -> move(1);
            case LEFT -> adjust(-1);
            case RIGHT -> adjust(1);
            case ACTIVATE -> OPTIONS[selectedIndex].percentage() ? Result.UNCHANGED : toggle();
            case BACK -> Result.CLOSE_REQUESTED;
        };
    }

    private Result move(int direction) {
        selectedIndex = Math.floorMod(selectedIndex + direction, OPTIONS.length);
        return Result.SELECTION_CHANGED;
    }

    private Result toggle() {
        Option option = OPTIONS[selectedIndex];
        return write(option, Values.from(draft).value(option) == 0 ? 1 : 0);
    }

    private Result adjust(int direction) {
        Option option = OPTIONS[selectedIndex];
        int value = Values.from(draft).value(option);
        return write(option, option.percentage() ? clamp(value + direction * 5) : direction > 0 ? 1 : 0);
    }

    private Result write(Option option, int value) {
        if (Values.from(draft).value(option) == value) return Result.UNCHANGED;
        switch (option) {
            case SHAKE -> draft.shakePercent = value;
            case CRT -> draft.crt = value != 0;
            case FLASHES -> draft.flashes = value != 0;
            case PARTICLES -> draft.particlePercent = value;
            case VOLUME -> draft.volumePercent = value;
            case BACKGROUND -> draft.backgroundVolumePercent = value;
            case MUTED -> draft.muted = value != 0;
            case AUTO_FIRE -> draft.autoFire = value != 0;
            case HIGH_CONTRAST -> draft.highContrast = value != 0;
            case LANGUAGE -> draft.language = GameLanguage.values()[value].tag();
        }
        return Result.VALUE_CHANGED;
    }

    private static int clamp(int value) { return Math.max(0, Math.min(100, value)); }
}
