package com.bigphil.mergehell;

import com.bigphil.mergehell.engine.GameLoop;
import com.bigphil.mergehell.engine.TickScheduler;
import com.bigphil.mergehell.model.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class GamePanelInputTest {
    private final AtomicLong clock = new AtomicLong();
    private final FakeScheduler scheduler = new FakeScheduler();
    private GamePanel panel;

    @BeforeEach
    void createPanelWithoutWindowOrBackgroundThread() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            panel = new GamePanel(scheduler, clock::get);
            panel.setSize(960, 600);
        });
    }

    @AfterEach
    void disposePanel() throws Exception {
        panel.dispose();
        assertNotEquals(GameState.ERROR, field("state", GameState.class),
                field("loopErrorMessage", String.class));
    }

    @Test
    void swingKeysOnlyChangeWorldAtSimulationBoundaryAndDoNotAutoRepeat() throws Exception {
        press("START");
        assertNull(panel.getLevelManager(), "The EDT must only enqueue the start command");
        tick();
        assertNotNull(panel.getLevelManager());

        Player player = field("player", Player.class);
        assertEquals(2, player.getJumpsRemaining());
        press("JUMP");
        assertEquals(2, player.getJumpsRemaining());
        tick();
        assertEquals(1, player.getJumpsRemaining());
        press("JUMP");
        tick();
        assertEquals(1, player.getJumpsRemaining(), "OS key repeat must not use the second jump");
        press("JUMP_RELEASE");
        press("JUMP");
        tick();
        assertEquals(0, player.getJumpsRemaining());
    }

    @Test
    void pausedGameplayCannotSpendBombsOrStartAttacks() throws Exception {
        press("START");
        tick();
        press("PAUSE_P");
        tick();
        assertEquals(GameState.PAUSED, field("state", GameState.class));
        Player player = field("player", Player.class);
        int bombs = player.getBombs();
        press("BOMB");
        press("DASH");
        press("MELEE");
        tick();
        assertEquals(bombs, player.getBombs());
        assertFalse(player.isDashing());
        assertFalse(player.isMeleeActive());
        press("PAUSE_P");
        tick();
        assertEquals(GameState.PAUSED, field("state", GameState.class));
        press("PAUSE_P_RELEASE");
        press("PAUSE_P");
        long before = panel.getSession().worldTick();
        clock.addAndGet(2_000_000_000L);
        scheduler.fire();
        assertEquals(GameState.RUNNING, field("state", GameState.class));
        assertEquals(before + 1, panel.getSession().worldTick(), "Resume cannot run a catch-up batch");
    }

    @Test
    void focusLossCancelsQueuedAttackAndPausesBeforeAnotherWorldTick() throws Exception {
        press("START");
        tick();
        Player player = field("player", Player.class);
        int bombs = player.getBombs();
        long before = panel.getSession().worldTick();
        press("RIGHT");
        press("BOMB");
        SwingUtilities.invokeAndWait(() -> {
            FocusEvent event = new FocusEvent(panel, FocusEvent.FOCUS_LOST);
            for (var listener : panel.getFocusListeners()) listener.focusLost(event);
        });
        tick();
        assertEquals(GameState.PAUSED, field("state", GameState.class));
        assertEquals(bombs, player.getBombs());
        assertEquals(before, panel.getSession().worldTick());
        assertEquals(false, field("keyRight", Boolean.class));
        press("PAUSE_P");
        tick();
        assertEquals(GameState.RUNNING, field("state", GameState.class));
    }

    @Test
    void paintingDoesNotConsumeFeedbackAndPauseFreezesItsClock() throws Exception {
        setField("shakeTimer", 10);
        setField("flashTimer", 8);
        BufferedImage image = new BufferedImage(960, 600, BufferedImage.TYPE_INT_ARGB);
        SwingUtilities.invokeAndWait(() -> {
            Graphics2D graphics = image.createGraphics();
            try {
                panel.paintComponent(graphics);
                panel.paintComponent(graphics);
            } finally {
                graphics.dispose();
            }
        });
        assertEquals(10, field("shakeTimer", Integer.class));
        assertEquals(8, field("flashTimer", Integer.class));
        tick();
        assertEquals(9, field("shakeTimer", Integer.class));
        assertEquals(7, field("flashTimer", Integer.class));
        setField("state", GameState.PAUSED);
        tick();
        assertEquals(9, field("shakeTimer", Integer.class));
        assertEquals(7, field("flashTimer", Integer.class));
    }

    @Test
    void mouseUpgradeSelectionIsConsumedOnceAtSimulationBoundary() throws Exception {
        press("START");
        tick();
        panel.getSession().awardBuildXp(100);
        setField("state", GameState.UPGRADE_SELECTION);
        int pending = panel.getSession().buildProgress().pendingChoices();
        var bounds = new com.bigphil.mergehell.render.UpgradeOverlayRenderer().cardBounds(0);
        SwingUtilities.invokeAndWait(() -> {
            MouseEvent event = new MouseEvent(panel, MouseEvent.MOUSE_RELEASED, 0, 0,
                    bounds.x + bounds.width / 2, bounds.y + bounds.height / 2, 1, false);
            for (var listener : panel.getMouseListeners()) listener.mouseReleased(event);
        });
        assertEquals(pending, panel.getSession().buildProgress().pendingChoices());
        tick();
        assertEquals(pending - 1, panel.getSession().buildProgress().pendingChoices());
    }

    private void press(String action) throws Exception {
        SwingUtilities.invokeAndWait(() -> panel.getActionMap().get(action)
                .actionPerformed(new ActionEvent(panel, ActionEvent.ACTION_PERFORMED, action)));
    }

    private void tick() {
        clock.addAndGet(GameLoop.LEGACY_STEP_NANOS);
        scheduler.fire();
    }

    private <T> T field(String name, Class<T> type) throws Exception {
        Field field = GamePanel.class.getDeclaredField(name);
        field.setAccessible(true);
        return type.cast(field.get(panel));
    }

    private void setField(String name, Object value) throws Exception {
        Field field = GamePanel.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(panel, value);
    }

    private static final class FakeScheduler implements TickScheduler {
        private Runnable task;
        @Override public void scheduleAtFixedRate(Runnable task, long periodMillis) { this.task = task; }
        void fire() { task.run(); }
        @Override public void dispose() { task = () -> { }; }
    }
}
