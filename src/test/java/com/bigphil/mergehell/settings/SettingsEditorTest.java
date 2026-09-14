package com.bigphil.mergehell.settings;

import com.bigphil.mergehell.persistence.MergeHellState.Settings;
import org.junit.jupiter.api.Test;

import static com.bigphil.mergehell.settings.SettingsEditor.Command.*;
import static com.bigphil.mergehell.settings.SettingsEditor.Result.*;
import static org.junit.jupiter.api.Assertions.*;

class SettingsEditorTest {
    @Test void languageChoiceIsImmediateAndSnapshotsKeepTheirOwnValue() {
        Settings source = new Settings(); source.language = "en";
        SettingsEditor editor = new SettingsEditor(source); select(editor, SettingsEditor.Option.LANGUAGE);
        var before = editor.snapshot();
        assertEquals(VALUE_CHANGED, editor.handle(RIGHT));
        assertEquals("zh-CN", editor.settings().language); assertEquals("en", before.values().toSettings().language);
        assertEquals("en", source.language);
        assertEquals(VALUE_CHANGED, editor.handle(ACTIVATE)); assertEquals("en", editor.settings().language);
    }

    @Test
    void keyboardSelectionWrapsAndEveryOptionIsReachable() {
        SettingsEditor editor = new SettingsEditor(new Settings());
        assertEquals(SettingsEditor.Option.SHAKE, editor.snapshot().selected());
        assertEquals(SELECTION_CHANGED, editor.handle(UP));
        assertEquals(SettingsEditor.Option.LANGUAGE, editor.snapshot().selected());
        for (var option : SettingsEditor.Option.values()) {
            assertEquals(SELECTION_CHANGED, editor.handle(DOWN));
            assertEquals(option, editor.snapshot().selected());
        }
    }

    @Test
    void percentageControlsUseFivePointStepsAndStopAtBothBounds() {
        for (var option : SettingsEditor.Option.values()) {
            if (!option.percentage()) continue;
            SettingsEditor editor = new SettingsEditor(new Settings());
            select(editor, option);
            int original = editor.snapshot().values().value(option);
            assertEquals(VALUE_CHANGED, editor.handle(LEFT));
            assertEquals(original - 5, editor.snapshot().values().value(option));
            assertEquals(UNCHANGED, editor.handle(ACTIVATE));
            for (int i = 0; i < 30; i++) editor.handle(LEFT);
            assertEquals(0, editor.snapshot().values().value(option));
            assertEquals(UNCHANGED, editor.handle(LEFT));
            for (int i = 0; i < 30; i++) editor.handle(RIGHT);
            assertEquals(100, editor.snapshot().values().value(option));
            assertEquals(UNCHANGED, editor.handle(RIGHT));
        }
    }

    @Test
    void booleanControlsHaveExplicitLeftOffRightOnAndEnterToggle() {
        for (var option : SettingsEditor.Option.values()) {
            if (option.percentage()) continue;
            SettingsEditor editor = new SettingsEditor(new Settings());
            select(editor, option);
            editor.handle(LEFT);
            assertEquals(0, editor.snapshot().values().value(option));
            assertEquals(UNCHANGED, editor.handle(LEFT));
            assertEquals(VALUE_CHANGED, editor.handle(RIGHT));
            assertEquals(1, editor.snapshot().values().value(option));
            assertEquals(UNCHANGED, editor.handle(RIGHT));
            assertEquals(VALUE_CHANGED, editor.handle(ACTIVATE));
            assertEquals(0, editor.snapshot().values().value(option));
            assertEquals(VALUE_CHANGED, editor.handle(ACTIVATE));
            assertEquals(1, editor.snapshot().values().value(option));
        }
    }

    @Test
    void mutableBeansAndOldSnapshotsCannotChangeTheEditor() {
        Settings original = new Settings();
        original.muted = true;
        SettingsEditor editor = new SettingsEditor(original);
        SettingsEditor.Snapshot before = editor.snapshot();
        original.shakePercent = 1;
        original.muted = false;
        Settings export = editor.settings();
        export.shakePercent = 2;
        export.muted = false;
        Settings fromSnapshot = before.values().toSettings();
        fromSnapshot.shakePercent = 3;
        assertEquals(70, editor.settings().shakePercent);
        assertTrue(editor.settings().muted);
        editor.handle(RIGHT);
        assertEquals(75, editor.settings().shakePercent);
        assertEquals(70, before.values().shakePercent());
        assertTrue(before.values().muted());
        assertNotSame(editor.settings(), editor.settings());
    }

    @Test
    void closeRequestsPreserveAppliedValuesAndMuteKeepsVolume() {
        SettingsEditor editor = new SettingsEditor(new Settings());
        select(editor, SettingsEditor.Option.MUTED);
        assertEquals(VALUE_CHANGED, editor.handle(ACTIVATE));
        SettingsEditor.Snapshot beforeClose = editor.snapshot();
        assertEquals(CLOSE_REQUESTED, editor.handle(BACK));
        assertEquals(beforeClose, editor.snapshot());
        assertFalse(editor.settings().muted);
        assertEquals(35, editor.settings().volumePercent);
    }

    @Test
    void missingAndCorruptPercentagesNormalizeWithoutChangingTheSource() {
        assertEquals(new Settings().shakePercent, new SettingsEditor(null).settings().shakePercent);
        Settings source = new Settings();
        source.shakePercent = Integer.MIN_VALUE;
        source.particlePercent = Integer.MAX_VALUE;
        source.volumePercent = -9;
        Settings normalized = new SettingsEditor(source).settings();
        assertEquals(0, normalized.shakePercent);
        assertEquals(100, normalized.particlePercent);
        assertEquals(0, normalized.volumePercent);
        assertEquals(-9, source.volumePercent);
    }

    private static void select(SettingsEditor editor, SettingsEditor.Option option) {
        for (int i = 0; i < option.ordinal(); i++) editor.handle(DOWN);
        assertEquals(option, editor.snapshot().selected());
    }
}
