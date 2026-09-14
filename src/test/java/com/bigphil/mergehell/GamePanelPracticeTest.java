package com.bigphil.mergehell;

import com.bigphil.mergehell.engine.GameLoop;
import com.bigphil.mergehell.boss.BossArrivalController;
import com.bigphil.mergehell.engine.TickScheduler;
import com.bigphil.mergehell.model.Player;
import com.bigphil.mergehell.model.Projectile;
import com.bigphil.mergehell.persistence.MergeHellState;
import com.bigphil.mergehell.persistence.MergeHellStateService;
import org.junit.jupiter.api.*;

import javax.swing.SwingUtilities;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class GamePanelPracticeTest {
    private final List<Harness> windows = new ArrayList<>();
    private final MergeHellStateService storage = MergeHellStateService.getInstance();
    private MergeHellState original;

    @BeforeEach void isolate() { original = storage.getState(); storage.loadState(new MergeHellState()); }
    @AfterEach void close() { windows.forEach(w -> w.panel.dispose()); storage.loadState(original); }

    @Test void visiblePracticeEntryStartsInvincibleWithoutReplacingTheCampaignCheckpoint() throws Exception {
        Harness campaign = window(); campaign.key("START"); campaign.tick();
        var checkpoint = storage.readCheckpoint().orElseThrow();
        campaign.panel.dispose();
        Harness practice = window(); practice.key("PRACTICE_START"); practice.tick();
        assertPractice(practice);
        Player player = (Player) field(practice.panel, "player");
        player.takeDamage(999); assertEquals(player.getMaxHp(), player.getHp());
        for (int i = 0; i < 30; i++) assertTrue(player.useBomb());
        assertEquals(checkpoint.runId, storage.readCheckpoint().orElseThrow().runId);
        practice.key("PAUSE_P"); practice.tick(); practice.key("MENU"); practice.tick();
        practice.key("UPGRADE_REROLL"); practice.tick();
        assertFalse(player.isDebugMode());
        assertFalse((boolean) field(practice.panel, "runUnranked"));
        assertEquals(checkpoint.runId, field(practice.panel, "runId"));
    }

    @Test void bossEntryFromTheMenuReachesTheRealBossWithInvincibilityAndNoPermanentRewards() throws Exception {
        Harness h = window(); h.key("LAB_BOSS_ALT"); h.tick();
        finishArrival(h);
        assertPractice(h);
        assertEquals(GameState.BOSS_FIGHT, field(h.panel, "state"));
        assertNotNull(field(h.panel, "legacyBoss"));
        assertTrue(storage.readCheckpoint().isEmpty());
        assertTrue(storage.getState().completedMissions.isEmpty());
        assertEquals(0, storage.getState().refactorPoints);
    }

    @Test void turningGodOffChangesDamageWhileKeepingPracticeUnranked() throws Exception {
        Harness h = window(); h.key("PRACTICE_START"); h.tick();
        h.key("LAB_TOGGLE_ALT"); h.tick();
        Player player = (Player) field(h.panel, "player");
        assertFalse(player.isDebugMode());
        player.takeDamage(17); assertEquals(83, player.getHp());
        assertTrue((boolean) field(h.panel, "runUnranked"));
        assertTrue((int) field(h.panel, "labOffNoticeTicks") > 0, "Turning T off confirms normal damage visibly");
        for (int i = 0; i < 190; i++) {
            ((com.bigphil.mergehell.model.ObstacleManager) field(h.panel, "enemyManager")).clearHostiles();
            h.tick();
        }
        assertEquals(0, field(h.panel, "labOffNoticeTicks"), "The confirmation must expire instead of becoming another permanent badge");
        h.key("NEW_RANKED_RUN"); h.tick();
        assertFalse(player.isDebugMode());
        assertFalse((boolean) field(h.panel, "runUnranked"));
        assertTrue(storage.readCheckpoint().isPresent());
    }

    @Test void scaledMouseCoordinatesSelectTheVisiblePracticeAndBossButtons() throws Exception {
        for (int logicalX : new int[]{304, 468}) {
            Harness h = window();
            SwingUtilities.invokeAndWait(() -> h.panel.setSize(600, 400));
            SwingUtilities.invokeAndWait(() -> { }); h.tick();
            // 960x600 fits as 600x375 with the game's real vertical letterbox offset.
            var fit = com.bigphil.mergehell.render.GameViewport.fit(600, 400);
            boolean muted = storage.getState().settings.muted;
            int muteX = (int) Math.round(fit.offsetX() + 835 * fit.drawWidth() / 960.0);
            int muteY = (int) Math.round(fit.offsetY() + 520 * fit.drawHeight() / 600.0);
            SwingUtilities.invokeAndWait(() -> h.panel.dispatchEvent(new MouseEvent(h.panel,
                    MouseEvent.MOUSE_RELEASED, 0, 0, muteX, muteY, 1, false, MouseEvent.BUTTON1)));
            h.tick();
            assertEquals(!muted, storage.getState().settings.muted);
            assertNull(field(h.panel, "settingsEditor"), "Clicking Mute must not open Settings");
            int x = (int) Math.round(fit.offsetX() + logicalX * fit.drawWidth() / 960.0);
            int y = (int) Math.round(fit.offsetY() + 458 * fit.drawHeight() / 600.0);
            SwingUtilities.invokeAndWait(() -> h.panel.dispatchEvent(new MouseEvent(h.panel,
                    MouseEvent.MOUSE_RELEASED, 0, 0, x, y, 1, false, MouseEvent.BUTTON1)));
            h.tick(); assertPractice(h);
            if (logicalX == 468) {
                finishArrival(h);
                assertEquals(GameState.BOSS_FIGHT, field(h.panel, "state"));
            }
        }
    }

    @Test void practiceKeysInsideSettingsDoNotStartOrReplaceARun() throws Exception {
        Harness h = window(); h.key("SETTINGS"); h.tick();
        var before = h.panel.getSession();
        h.key("PRACTICE_START"); h.key("LAB_BOSS_ALT"); h.tick();
        assertSame(before, h.panel.getSession());
        assertEquals(GameState.MENU, field(h.panel, "state"));
        assertFalse(((Player) field(h.panel, "player")).isDebugMode());
        assertTrue(storage.readCheckpoint().isEmpty());
    }

    @Test void pushingAgainstTheBossScreenEdgeDoesNotGiveTheShotPhantomMovement() throws Exception {
        Harness h = window(); h.key("LAB_BOSS_ALT"); h.tick();
        finishArrival(h);
        assertEquals(GameState.BOSS_FIGHT, field(h.panel, "state"));
        // Allow the real camera to reach its locked arena before testing the boundary.
        for (int i = 0; i < 100; i++) h.tick();
        Player player = (Player) field(h.panel, "player");
        double edge = (double) field(h.panel, "cameraX") + 960 - player.getBounds().width;
        player.setX(edge); player.setY(450);
        long before = player.getShotSequence();
        h.key("RIGHT"); h.key("SHOOT"); h.tick();
        assertEquals(before + 1, player.getShotSequence());
        assertEquals(edge, player.getX(), 1e-9);
        @SuppressWarnings("unchecked") var shots = (List<Projectile>) field(h.panel, "projectiles");
        Projectile shot = shots.stream().filter(p -> !p.getType().isHostile()).findFirst().orElseThrow();
        assertEquals(11, shot.getVx(), 1e-9, "A stationary Commit shot keeps its base speed at the screen edge");
    }

    private void finishArrival(Harness h) throws Exception {
        assertEquals(GameState.BOSS_WARNING, field(h.panel, "state"));
        assertEquals(BossArrivalController.DURATION_TICKS, field(h.panel, "bossWarningTimer"));
        for (int tick = 0; tick < BossArrivalController.DURATION_TICKS - 1; tick++) {
            h.tick();
            assertEquals(GameState.BOSS_WARNING, field(h.panel, "state"));
        }
        h.tick();
        assertEquals(GameState.BOSS_FIGHT, field(h.panel, "state"));
    }

    private void assertPractice(Harness h) throws Exception {
        assertTrue(((Player) field(h.panel, "player")).isDebugMode());
        assertTrue((boolean) field(h.panel, "runUnranked"));
        assertFalse((boolean) field(h.panel, "ownsCheckpoint"));
    }
    private Harness window() throws Exception { Harness h = new Harness(); windows.add(h); return h; }
    private static Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target);
    }
    private static final class Harness {
        final AtomicLong clock = new AtomicLong();
        final Scheduler scheduler = new Scheduler();
        GamePanel panel;
        Harness() throws Exception {
            SwingUtilities.invokeAndWait(() -> { panel = new GamePanel(scheduler, clock::get); panel.setSize(960, 600); });
        }
        void key(String key) throws Exception {
            SwingUtilities.invokeAndWait(() -> {
                panel.getActionMap().get(key).actionPerformed(new ActionEvent(panel, 0, key));
                var release = panel.getActionMap().get(key + "_RELEASE");
                if (release != null) release.actionPerformed(new ActionEvent(panel, 0, key));
            });
        }
        void tick() { clock.addAndGet(GameLoop.LEGACY_STEP_NANOS); scheduler.task.run(); }
    }
    private static final class Scheduler implements TickScheduler {
        Runnable task = () -> { };
        public void scheduleAtFixedRate(Runnable task, long period) { this.task = task; }
        public void dispose() { task = () -> { }; }
    }
}
