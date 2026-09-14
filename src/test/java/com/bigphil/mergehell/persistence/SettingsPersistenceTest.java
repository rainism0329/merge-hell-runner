package com.bigphil.mergehell.persistence;

import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class SettingsPersistenceTest {
    @Test void explicitUnmuteAndIndependentBackgroundVolumeSurviveRealIdeXml() {
        MergeHellState state = new MergeHellState();
        assertTrue(state.settings.muted);
        state.settings.muted = false; state.settings.backgroundVolumePercent = 0;
        state.settings.volumePercent = 45;
        Element xml = XmlSerializer.serialize(state);
        assertEquals(1, removeOption(xml.clone(), "muted"), "Explicit false must be serialized against the quiet default");
        var service = new MergeHellStateService();
        service.loadState(XmlSerializer.deserialize(xml, MergeHellState.class));
        assertFalse(service.getState().settings.muted);
        assertEquals(0, service.getState().settings.backgroundVolumePercent);
        assertEquals(45, service.getState().settings.volumePercent);
        var settings = service.getState().settings; settings.backgroundVolumePercent = 150;
        service.updateSettings(settings); settings.backgroundVolumePercent = 9;
        assertEquals(100, service.getState().settings.backgroundVolumePercent);
        settings.backgroundVolumePercent = -1; service.updateSettings(settings);
        assertEquals(0, service.getState().settings.backgroundVolumePercent);
    }

    @Test void languageSurvivesIdeXmlAndSettingsCopiesWithoutChangingOtherProgress() {
        for (String language : new String[]{"en", "zh-CN"}) {
            MergeHellState state = new MergeHellState(); state.settings.language = language;
            state.settings.volumePercent = 47; state.topScores.add(1234); state.refactorPoints = 250;
            Element xml = XmlSerializer.serialize(state);
            assertEquals(1, removeOption(xml.clone(), "language"), "Explicit choice must survive an OS-language change");
            MergeHellState decoded = XmlSerializer.deserialize(xml, MergeHellState.class);
            MergeHellStateService service = new MergeHellStateService(); service.loadState(decoded);
            assertEquals(language, service.getState().settings.language);
            var copy = service.getState(); copy.settings.language = "invalid";
            assertEquals(language, service.getState().settings.language);
            assertEquals(47, service.getState().settings.volumePercent);
            assertEquals(1234, service.getState().topScores.get(0));
            assertEquals(250, service.getState().refactorPoints);
        }
    }

    @Test void missingAndInvalidLanguageUseSystemDefaultAndDoNotBreakOldSaves() {
        String fallback = com.bigphil.mergehell.i18n.GameLanguage.systemDefault().tag();
        MergeHellState old = new MergeHellState(); old.settings.language = fallback.equals("en") ? "zh-CN" : "en";
        old.settings.volumePercent = 61;
        Element xml = XmlSerializer.serialize(old);
        assertEquals(1, removeOption(xml, "language"));
        var decoded = XmlSerializer.deserialize(xml, MergeHellState.class);
        assertEquals(fallback, new StateMigrator(() -> null).migrate(decoded).settings.language);
        for (String value : new String[]{null, "", "unsupported"}) {
            old.settings.language = value;
            var migrated = new StateMigrator(() -> null).migrate(old);
            assertEquals(fallback, migrated.settings.language); assertEquals(61, migrated.settings.volumePercent);
        }
    }

    @Test
    void olderSchemaOneAndTwoXmlWithoutMuteStartsQuietlyAndKeepsExistingPreferences() {
        for (int schema : new int[]{1, 2}) {
            MergeHellState old = new MergeHellState();
            old.schemaVersion = schema;
            old.settings.volumePercent = 73;
            old.settings.crt = false;
            old.settings.highContrast = true;
            old.settings.muted = false;
            old.topScores.add(2_000);
            Element xml = XmlSerializer.serialize(old);
            assertEquals(1, removeOption(xml, "muted"), "Fixture must actually omit the new XML field");
            MergeHellState decoded = XmlSerializer.deserialize(xml, MergeHellState.class);
            MergeHellState migrated = new StateMigrator(() -> null).migrate(decoded);
            assertEquals(2, migrated.schemaVersion);
            assertTrue(migrated.settings.muted);
            assertEquals(20, migrated.settings.backgroundVolumePercent);
            assertEquals(73, migrated.settings.volumePercent);
            assertFalse(migrated.settings.crt);
            assertTrue(migrated.settings.highContrast);
            assertEquals(2_000, migrated.topScores.get(0));
        }
    }

    @Test
    void persistentMuteAndVolumeRoundTripIndependentlyThroughRealIdeXml() {
        MergeHellState state = new MergeHellState();
        state.settings.muted = true;
        state.settings.volumePercent = 37;
        MergeHellState decoded = XmlSerializer.deserialize(XmlSerializer.serialize(state), MergeHellState.class);
        MergeHellStateService service = new MergeHellStateService();
        service.loadState(decoded);
        assertEquals(2, service.getState().schemaVersion);
        assertTrue(service.getState().settings.muted);
        assertEquals(37, service.getState().settings.volumePercent);
    }

    @Test
    void settingsUpdatesReadsAndMigrationNeverShareMutableState() {
        MergeHellStateService service = new MergeHellStateService();
        MergeHellState.Settings input = new MergeHellState.Settings();
        input.muted = true;
        input.volumePercent = 37;
        service.updateSettings(input);
        input.muted = false;
        input.volumePercent = 0;
        MergeHellState read = service.getState();
        assertTrue(read.settings.muted);
        read.settings.muted = false;
        read.settings.volumePercent = 100;
        assertTrue(service.getState().settings.muted);
        assertEquals(37, service.getState().settings.volumePercent);

        MergeHellState source = service.getState();
        MergeHellState migrated = new StateMigrator(() -> null).migrate(source);
        source.settings.muted = false;
        assertTrue(migrated.settings.muted);
    }

    @Test
    void settingsUpdatePreservesTheActiveCheckpointAndItsOwner() {
        MergeHellStateService service = new MergeHellStateService();
        MergeHellState.ActiveRun run = CheckpointCodecTest.freshRun();
        assertTrue(service.claimRun("window", run.runId));
        assertTrue(service.saveCheckpoint("window", run));
        MergeHellState.Settings settings = service.getState().settings;
        settings.muted = true;
        service.updateSettings(settings);
        assertEquals(run.runId, service.readCheckpoint().orElseThrow().runId);
        assertTrue(service.ownedByAnotherWindow("other"));
        assertFalse(service.claimRun("other", java.util.UUID.randomUUID().toString()));
        assertTrue(service.getState().settings.muted);
    }

    private static int removeOption(Element element, String name) {
        int removed = 0;
        for (Element child : new ArrayList<>(element.getChildren())) {
            if (child.getName().equals("option") && name.equals(child.getAttributeValue("name"))) {
                child.detach();
                removed++;
            } else removed += removeOption(child, name);
        }
        return removed;
    }
}
