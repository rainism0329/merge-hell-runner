package com.bigphil.mergehell;

import com.bigphil.mergehell.audio.*;
import com.bigphil.mergehell.persistence.*;
import org.junit.jupiter.api.*;
import javax.swing.SwingUtilities;
import java.awt.event.FocusEvent;
import static org.junit.jupiter.api.Assertions.*;

class GamePanelAudioTest {
    private final MergeHellStateService storage = MergeHellStateService.getInstance();
    private MergeHellState original;
    @BeforeEach void isolate() { original = storage.getState(); storage.loadState(new MergeHellState()); }
    @AfterEach void restore() { storage.loadState(original); }

    @Test void gameplayStartsMutedAndKeyboardChoicePersistsWithoutChangingVolume() throws Exception {
        try (HeapGameHarness h = new HeapGameHarness()) {
            AudioService audio = audio(h);
            assertTrue(audio.isMuted()); assertFalse(audio.isPaused());
            assertEquals(AmbienceScene.HEAP, audio.backgroundScene());
            h.key("MUTE"); h.tick(); assertFalse(audio.isMuted());
            assertFalse(storage.getState().settings.muted);
            assertEquals(35, storage.getState().settings.volumePercent);
            h.key("PAUSE_P"); h.tick(); assertTrue(audio.isPaused());
            h.key("MUTE"); h.tick(); assertTrue(audio.isMuted()); assertTrue(audio.isPaused());
            h.key("PAUSE_P"); h.tick(); assertTrue(audio.isMuted()); assertFalse(audio.isPaused());
        }
    }

    @Test void returningFocusDoesNotResumeTheGameOrItsBackgroundAudio() throws Exception {
        try (HeapGameHarness h = new HeapGameHarness()) {
            h.key("MUTE"); h.tick();
            focus(h, false); assertTrue(audio(h).isPaused(), "Focus loss silences before the next game tick");
            h.tick(); assertEquals(GameState.PAUSED, h.state());
            focus(h, true); h.tick(); assertTrue(audio(h).isPaused());
            h.key("PAUSE_P"); h.tick(); assertFalse(audio(h).isPaused());
        }
    }

    @Test void upgradesSuspendAudioAndFocusLossCannotBeOverwrittenByAFinishingTick() throws Exception {
        try (HeapGameHarness h = new HeapGameHarness()) {
            h.key("LAB_TOGGLE_ALT"); h.tick(); h.key("LAB_UPGRADE_ALT"); h.tick();
            assertEquals(GameState.UPGRADE_SELECTION, h.state()); assertTrue(audio(h).isPaused());
            focus(h, false);
            // A state-changing command already waiting in the same input batch must not undo suspension.
            h.key("NEW_RANKED_RUN"); h.tick(); assertTrue(audio(h).isPaused());
            focus(h, true); h.tick();
            assertEquals(h.state() != GameState.RUNNING, audio(h).isPaused());
        }
    }

    @Test void bossPracticeUsesBossMusicAndSettlementMenuAndErrorsAreSilent() throws Exception {
        try (HeapGameHarness h = new HeapGameHarness()) {
            h.bossPractice(); assertEquals(AmbienceScene.BOSS, audio(h).backgroundScene());
            for (GameState state : new GameState[]{GameState.MISSION_COMPLETE, GameState.MENU,
                    GameState.GAME_OVER, GameState.VICTORY}) {
                h.set("state", state); h.tick(); assertTrue(audio(h).isPaused(), state.name());
                assertEquals(AmbienceScene.NONE, audio(h).backgroundScene());
            }
            h.set("state", GameState.RUNNING); h.invoke("syncAudio"); assertFalse(audio(h).isPaused());
            var method = GamePanel.class.getDeclaredMethod("handleLoopError", Throwable.class);
            method.setAccessible(true); method.invoke(h.panel, new IllegalStateException("audio fixture"));
            assertTrue(audio(h).isPaused()); assertEquals(GameState.ERROR, h.state());
        }
    }

    @Test void removingAndClosingThePanelSilencesItWithoutWaitingForAnotherTick() throws Exception {
        try (HeapGameHarness h = new HeapGameHarness()) {
            h.key("MUTE"); h.tick();
            SwingUtilities.invokeAndWait(h.panel::removeNotify);
            assertTrue(audio(h).isPaused());
            h.panel.dispose(); assertEquals(AudioService.Status.CLOSED, audio(h).status());
        }
    }

    private static AudioService audio(HeapGameHarness h) throws Exception { return (AudioService) h.get("audio"); }
    private static void focus(HeapGameHarness h, boolean gained) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var event = new FocusEvent(h.panel, gained ? FocusEvent.FOCUS_GAINED : FocusEvent.FOCUS_LOST);
            for (var listener : h.panel.getFocusListeners()) {
                if (gained) listener.focusGained(event); else listener.focusLost(event);
            }
        });
    }
}
