package com.bigphil.mergehell;

import com.bigphil.mergehell.combat.WeaponId;
import com.bigphil.mergehell.boss.LegacyBossController;
import com.bigphil.mergehell.engine.GameLoop;
import com.bigphil.mergehell.engine.GameSession;
import com.bigphil.mergehell.engine.TickScheduler;
import com.bigphil.mergehell.model.Player;
import com.bigphil.mergehell.persistence.CheckpointCodec;
import com.bigphil.mergehell.persistence.MergeHellState;
import com.bigphil.mergehell.persistence.MergeHellStateService;
import com.bigphil.mergehell.progression.CharacterId;
import com.bigphil.mergehell.render.GameViewport;
import com.bigphil.mergehell.render.RunFlowRenderer;
import com.bigphil.mergehell.render.SettingsOverlayRenderer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/** User-facing flow regression tests: inputs cross the real Swing-to-simulation boundary. */
class GamePanelMaturityTest {
    private final MergeHellStateService storage = MergeHellStateService.getInstance();
    private final List<Harness> windows = new ArrayList<>();
    private MergeHellState original;

    @BeforeEach void isolateStorage() {
        original = storage.getState();
        storage.loadState(new MergeHellState());
    }

    @AfterEach void cleanup() throws Exception {
        for (Harness window : windows) window.panel.dispose();
        storage.loadState(original);
        for (Harness window : windows)
            assertNotEquals(GameState.ERROR, field(window.panel, "state"),
                    String.valueOf(field(window.panel, "loopErrorMessage")));
    }

    @Test void enterOnTheMenuContinuesTheSavedChapterAndLoadoutInsteadOfReplacingIt() throws Exception {
        var checkpoint = seedCheckpoint(2);
        Harness h = window();
        h.tap("START");
        assertEquals(GameState.MENU, h.state(), "EDT input must wait for the simulation boundary");
        h.tick();
        assertEquals(GameState.RUNNING, h.state());
        assertEquals(checkpoint.runId, h.runId());
        assertEquals(2, field(h.panel, "level"));
        assertEquals(checkpoint.weapon, h.panel.getSession().runBuild().weapon());
        assertEquals(checkpoint.player.hp, h.player().getHp());
        assertEquals(checkpoint.player.bombs, h.player().getBombs());
        assertFalse((boolean) field(h.panel, "runUnranked"));
        assertFalse((boolean) field(h.panel, "confirmNewRun"));
        assertCheckpoint(checkpoint);
    }

    @Test void newRunRequiresConfirmationAndEscapeKeepsTheCurrentRunPositionAndSave() throws Exception {
        Harness h = window(); h.act("START");
        h.press("RIGHT"); h.ticks(12); h.release("RIGHT"); h.tick();
        String runId = h.runId();
        double x = h.player().getX();
        long worldTick = h.panel.getSession().worldTick();
        int bombs = h.player().getBombs();
        var checkpoint = storage.readCheckpoint().orElseThrow();
        h.act("NEW_RANKED_RUN");
        assertTrue((boolean) field(h.panel, "confirmNewRun"));
        assertEquals(GameState.PAUSED, h.state());
        assertEquals(runId, h.runId());
        h.act("BOMB"); h.act("SHOOT"); h.ticks(20);
        assertEquals(worldTick, h.panel.getSession().worldTick());
        assertEquals(bombs, h.player().getBombs());
        assertEquals(x, h.player().getX(), 1e-9);
        assertCheckpoint(checkpoint);
        h.act("PAUSE_ESC");
        assertFalse((boolean) field(h.panel, "confirmNewRun"));
        assertEquals(GameState.RUNNING, h.state());
        assertEquals(runId, h.runId());
        assertEquals(x, h.player().getX(), 1e-9);
        assertCheckpoint(checkpoint);
        h.act("NEW_RANKED_RUN");
        assertEquals(runId, h.runId());
        h.act("START");
        assertNotEquals(runId, h.runId());
        assertEquals(h.runId(), storage.readCheckpoint().orElseThrow().runId);
        assertFalse((boolean) field(h.panel, "confirmNewRun"));
        assertFalse(h.player().isDebugMode());
    }

    @Test void enablingPracticeKeepsTheRankedCheckpointAndMenuEnterRestoresTheRankedRun() throws Exception {
        seedCheckpoint(2);
        Harness h = window(); h.act("START"); h.ticks(5);
        var checkpoint = storage.readCheckpoint().orElseThrow();
        h.act("LAB_TOGGLE_ALT");
        assertTrue(h.player().isDebugMode());
        assertTrue((boolean) field(h.panel, "runUnranked"));
        assertFalse((boolean) field(h.panel, "ownsCheckpoint"));
        h.player().setX(h.player().getX() + 700);
        h.act("BOMB");
        assertCheckpoint(checkpoint);
        h.act("PAUSE_P"); h.act("MENU");
        assertEquals(GameState.MENU, h.state());
        h.act("START");
        assertEquals(GameState.RUNNING, h.state());
        assertEquals(checkpoint.runId, h.runId());
        assertEquals(checkpoint.mission, field(h.panel, "level"));
        assertEquals(checkpoint.checkpointX, h.player().getX(), 1e-9);
        assertEquals(checkpoint.player.bombs, h.player().getBombs());
        assertFalse(h.player().isDebugMode());
        assertFalse((boolean) field(h.panel, "labPowerEnabled"));
        assertFalse((boolean) field(h.panel, "runUnranked"));
        assertCheckpoint(checkpoint);
    }

    @Test void aLockedPracticeCharacterCannotConvertItsExistingRunIntoARewardEligibleCampaign() throws Exception {
        var checkpoint = seedCheckpoint(2);
        assertFalse(storage.getState().unlockedCharacters.contains(CharacterId.ENGINEER));
        Harness h = window();
        h.act("CHARACTER"); h.act("CHARACTER"); h.act("CHARACTER");
        assertEquals(CharacterId.ENGINEER, field(h.panel, "selectedCharacter"));
        h.act("PRACTICE_START");
        assertEquals(CharacterId.ENGINEER, h.panel.getSession().runBuild().character());
        String practiceRun = h.runId();
        var practiceSession = h.panel.getSession();
        h.act("NEW_RANKED_RUN");
        assertTrue((boolean) field(h.panel, "confirmNewRun"));
        h.act("START");
        assertFalse((boolean) field(h.panel, "confirmNewRun"));
        assertEquals(GameState.RUNNING, h.state());
        assertSame(practiceSession, h.panel.getSession());
        assertEquals(practiceRun, h.runId());
        assertTrue((boolean) field(h.panel, "runUnranked"));
        assertTrue((boolean) field(h.panel, "labPowerEnabled"));
        assertTrue(h.player().isDebugMode());
        assertFalse((boolean) field(h.panel, "ownsCheckpoint"));
        assertCheckpoint(checkpoint);

        // Carry the rejected launch through a real practice Boss settlement: retaining only
        // the screen label would be insufficient if the old session could still earn rewards.
        h.act("LAB_BOSS_ALT");
        for (int tick = 0; tick < 210 && h.state() == GameState.BOSS_WARNING; tick++) h.tick();
        assertEquals(GameState.BOSS_FIGHT, h.state());
        var boss = (LegacyBossController) field(h.panel, "legacyBoss");
        boss.damageAllNodes(Integer.MAX_VALUE); boss.damageCore(Integer.MAX_VALUE);
        for (int tick = 0; tick < 120 && h.state() != GameState.MISSION_COMPLETE; tick++) h.tick();
        assertEquals(GameState.MISSION_COMPLETE, h.state());
        assertTrue(storage.getState().completedMissions.isEmpty());
        assertEquals(0, storage.getState().refactorPoints);
        assertTrue(storage.getState().rankedScores.values().stream().allMatch(List::isEmpty));
        assertFalse(storage.getState().unlockedCharacters.contains(CharacterId.ENGINEER));
        assertCheckpoint(checkpoint);
    }

    @Test void gameOverQReturnsToMenuAndLPracticesTheChapterThatActuallyFailed() throws Exception {
        seedCheckpoint(2);
        Harness first = window(); first.act("START"); first.failRun();
        first.act("MENU");
        assertEquals(GameState.MENU, first.state());
        first.panel.dispose();

        seedCheckpoint(3);
        Harness retry = window(); retry.act("START"); retry.failRun();
        WeaponId weapon = retry.panel.getSession().runBuild().weapon();
        retry.act("LAB_BOSS_ALT");
        assertEquals(GameState.BOSS_WARNING, retry.state());
        assertEquals(3, field(retry.panel, "level"), "Practice must preserve the failed chapter, not restart chapter one");
        assertEquals(weapon, retry.panel.getSession().runBuild().weapon());
        assertTrue(retry.player().isDebugMode());
        assertTrue((boolean) field(retry.panel, "runUnranked"));
        assertFalse((boolean) field(retry.panel, "ownsCheckpoint"));
        assertTrue(storage.readCheckpoint().isEmpty(), "Boss practice must not manufacture a ranked checkpoint");
    }

    @Test void pausedFramesStayCachedUntilAnInputSettingsChangeOrResizeRequiresOne() throws Exception {
        Harness h = window(); h.act("START"); h.act("PAUSE_P");
        long worldTick = h.panel.getSession().worldTick();
        long pausedFrames = h.publishedFrames();
        h.ticks(90);
        assertEquals(pausedFrames, h.publishedFrames(), "A stationary pause must not render at gameplay cadence");
        assertEquals(worldTick, h.panel.getSession().worldTick());
        h.act("MUTE");
        assertTrue(h.publishedFrames() > pausedFrames, "Paused feedback must refresh after a real key input");
        h.assertPausedAndIdle(worldTick);
        long beforeSettings = h.publishedFrames();
        h.act("SETTINGS");
        assertNotNull(field(h.panel, "settingsEditor"));
        assertTrue(h.publishedFrames() > beforeSettings);
        h.act("RIGHT");
        h.assertPausedAndIdle(worldTick);
        long beforeResize = h.publishedFrames();
        h.resize(600, 400); h.tick();
        assertTrue(h.publishedFrames() > beforeResize, "Resize must invalidate the cached presentation");
        assertTrue((boolean) field(h.panel, "compactDisplay"));
        h.assertPausedAndIdle(worldTick);
        h.act("PAUSE_ESC");
        assertNull(field(h.panel, "settingsEditor"));
        h.assertPausedAndIdle(worldTick);
    }

    @Test void scaledMouseSettingsAdjustPercentagesToggleValuesAndCloseWithoutResuming() throws Exception {
        Harness h = window(); h.resize(600, 400); h.act("START"); h.act("PAUSE_P");
        long worldTick = h.panel.getSession().worldTick();
        h.click(RunFlowRenderer.pauseSettingsBounds());
        assertNotNull(field(h.panel, "settingsEditor"));
        // Click visible slider midpoint and the CRT switch at the smaller, letterboxed size.
        h.click(637, 150);
        int midpoint = storage.getState().settings.shakePercent;
        assertEquals(50, midpoint, 1, "A compact physical pixel quantizes to roughly one percentage point");
        boolean crt = storage.getState().settings.crt;
        h.click(763, 180);
        assertEquals(!crt, storage.getState().settings.crt);
        // Selecting a percentage label must not reset that slider to zero.
        h.click(300, 150);
        assertEquals(midpoint, storage.getState().settings.shakePercent);
        h.click(559, 150);
        assertEquals(0, storage.getState().settings.shakePercent);
        h.click(715, 150);
        assertEquals(100, storage.getState().settings.shakePercent);
        h.click(SettingsOverlayRenderer.closeBounds());
        assertNull(field(h.panel, "settingsEditor"));
        h.assertPausedAndIdle(worldTick);
    }

    @Test void pauseMouseActionsResumeOrReturnToMenuWithoutReplacingProgress() throws Exception {
        Harness h = window(); h.resize(600, 400); h.act("START");
        String runId = h.runId();
        var checkpoint = storage.readCheckpoint().orElseThrow();
        h.act("PAUSE_P");
        long pausedTick = h.panel.getSession().worldTick();
        h.click(RunFlowRenderer.resumeBounds());
        assertEquals(GameState.RUNNING, h.state());
        assertTrue(h.panel.getSession().worldTick() > pausedTick);
        assertEquals(runId, h.runId());
        h.act("PAUSE_P"); h.click(RunFlowRenderer.pauseMenuBounds());
        assertEquals(GameState.MENU, h.state());
        assertCheckpoint(checkpoint);
    }

    @Test void failureMouseButtonsOfferMenuBossPracticeAndARealRetry() throws Exception {
        for (int choice = 0; choice < 3; choice++) {
            seedCheckpoint(2);
            Harness h = window(); h.resize(600, 400); h.act("START"); h.failRun();
            String failedRun = h.runId();
            if (choice == 0) {
                h.click(RunFlowRenderer.menuBounds());
                assertEquals(GameState.MENU, h.state());
            } else if (choice == 1) {
                h.click(RunFlowRenderer.practiceBounds());
                assertEquals(GameState.BOSS_WARNING, h.state());
                assertEquals(2, field(h.panel, "level"));
                assertTrue(h.player().isDebugMode());
            } else {
                h.click(RunFlowRenderer.retryBounds());
                assertEquals(GameState.RUNNING, h.state());
                assertNotEquals(failedRun, h.runId());
                assertFalse(h.player().isDebugMode());
                assertEquals(h.runId(), storage.readCheckpoint().orElseThrow().runId);
            }
            h.panel.dispose();
        }
    }

    @Test void mouseNewRunConfirmationSupportsCancelAndExplicitAcceptance() throws Exception {
        var checkpoint = seedCheckpoint(1);
        Harness h = window(); h.resize(600, 400); h.act("START");
        h.act("NEW_RANKED_RUN"); h.click(RunFlowRenderer.cancelBounds());
        assertEquals(checkpoint.runId, h.runId());
        assertFalse((boolean) field(h.panel, "confirmNewRun"));
        assertCheckpoint(checkpoint);
        h.act("NEW_RANKED_RUN"); h.click(RunFlowRenderer.confirmBounds());
        assertFalse((boolean) field(h.panel, "confirmNewRun"));
        assertNotEquals(checkpoint.runId, h.runId());
        assertEquals(GameState.RUNNING, h.state());
    }

    private MergeHellState.ActiveRun seedCheckpoint(int chapter) {
        var session = new GameSession(73, WeaponId.COMMIT_CANNON);
        session.beginMission(chapter);
        var player = new Player(100, 480);
        player.setRunBuild(session.runBuild());
        player.takeDamage(23);
        player.useBomb();
        var checkpoint = CheckpointCodec.capture(UUID.randomUUID().toString(), 1234, 5000, session, player.checkpoint());
        storage.saveActiveRun(checkpoint);
        assertTrue(storage.readCheckpoint().isPresent());
        return checkpoint;
    }

    private void assertCheckpoint(MergeHellState.ActiveRun expected) {
        var actual = storage.readCheckpoint().orElseThrow();
        assertEquals(expected.runId, actual.runId);
        assertEquals(expected.seed, actual.seed);
        assertEquals(expected.mission, actual.mission);
        assertEquals(expected.score, actual.score);
        assertEquals(expected.worldTick, actual.worldTick);
        assertEquals(expected.checkpointX, actual.checkpointX);
        assertEquals(expected.checkpointY, actual.checkpointY);
        assertEquals(expected.weapon, actual.weapon);
        assertEquals(expected.player.hp, actual.player.hp);
        assertEquals(expected.player.bombs, actual.player.bombs);
        assertEquals(expected.player.lives, actual.player.lives);
        assertEquals(expected.upgradeRanks, actual.upgradeRanks);
    }

    private Harness window() throws Exception {
        Harness h = new Harness(); windows.add(h); return h;
    }
    private static Object field(GamePanel panel, String name) throws Exception {
        Field f = GamePanel.class.getDeclaredField(name); f.setAccessible(true); return f.get(panel);
    }

    private static final class Harness {
        final AtomicLong clock = new AtomicLong();
        final Scheduler scheduler = new Scheduler();
        GamePanel panel;
        Harness() throws Exception {
            SwingUtilities.invokeAndWait(() -> { panel = new GamePanel(scheduler, clock::get); panel.setSize(960, 600); });
            SwingUtilities.invokeAndWait(() -> { });
        }
        void press(String key) throws Exception {
            SwingUtilities.invokeAndWait(() -> {
                var action = panel.getActionMap().get(key);
                assertNotNull(action, "Missing real key binding: " + key);
                action.actionPerformed(new ActionEvent(panel, ActionEvent.ACTION_PERFORMED, key));
            });
        }
        void release(String key) throws Exception {
            press(switch (key) {
                case "LEFT", "RIGHT", "SHOOT", "MENU_UP", "MENU_DOWN", "AIM_LOCK" -> key + "_R";
                default -> key + "_RELEASE";
            });
        }
        void tap(String key) throws Exception { press(key); release(key); }
        void act(String key) throws Exception { tap(key); tick(); }
        void tick() { clock.addAndGet(GameLoop.LEGACY_STEP_NANOS); scheduler.callback.run(); }
        void ticks(int count) { for (int i = 0; i < count; i++) tick(); }
        void resize(int width, int height) throws Exception {
            SwingUtilities.invokeAndWait(() -> panel.setSize(width, height));
            SwingUtilities.invokeAndWait(() -> { });
        }
        void click(Rectangle bounds) throws Exception { click((int) bounds.getCenterX(), (int) bounds.getCenterY()); }
        void click(int logicalX, int logicalY) throws Exception {
            SwingUtilities.invokeAndWait(() -> {
                var fit = GameViewport.fit(panel.getWidth(), panel.getHeight());
                int x = (int) Math.round(fit.offsetX() + logicalX * fit.drawWidth() / 960.0);
                int y = (int) Math.round(fit.offsetY() + logicalY * fit.drawHeight() / 600.0);
                panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_RELEASED, 0, 0, x, y,
                        1, false, MouseEvent.BUTTON1));
            });
            tick();
        }
        Player player() throws Exception { return (Player) field(panel, "player"); }
        GameState state() throws Exception { return (GameState) field(panel, "state"); }
        String runId() throws Exception { return (String) field(panel, "runId"); }
        long publishedFrames() throws Exception { return (long) field(panel, "publishedFrames"); }
        void assertPausedAndIdle(long worldTick) throws Exception {
            assertEquals(GameState.PAUSED, state());
            assertEquals(worldTick, panel.getSession().worldTick());
            long published = publishedFrames();
            ticks(30);
            assertEquals(published, publishedFrames());
            assertEquals(worldTick, panel.getSession().worldTick());
        }
        void failRun() throws Exception {
            ticks(8); // Let the real Continue transition settle before applying final damage.
            while (player().getLives() > 1) player().loseLife();
            player().setShieldTimer(0); player().setInvincibleTimer(0); player().takeDamage(1000);
            for (int i = 0; i < 15 && state() != GameState.GAME_OVER; i++) tick();
            assertEquals(GameState.GAME_OVER, state(), "The real death path must reach the failure screen");
        }
    }

    private static final class Scheduler implements TickScheduler {
        Runnable callback = () -> { };
        @Override public void scheduleAtFixedRate(Runnable callback, long periodMillis) { this.callback = callback; }
        @Override public void dispose() { callback = () -> { }; }
    }
}
