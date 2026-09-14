package com.bigphil.mergehell;

import com.bigphil.mergehell.combat.WeaponId;
import com.bigphil.mergehell.engine.GameLoop;
import com.bigphil.mergehell.engine.TickScheduler;
import com.bigphil.mergehell.model.Player;
import com.bigphil.mergehell.persistence.MergeHellState;
import com.bigphil.mergehell.persistence.MergeHellStateService;
import org.junit.jupiter.api.*;
import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class GamePanelEvolutionTest {
    private final List<Harness> windows = new ArrayList<>();
    private MergeHellState original;
    @BeforeEach void isolateState() {
        original = MergeHellStateService.getInstance().getState();
        MergeHellStateService.getInstance().loadState(new MergeHellState());
    }
    @AfterEach void cleanup() {
        windows.forEach(window -> window.panel.dispose());
        MergeHellStateService.getInstance().loadState(original);
    }

    @Test void allSixStartingWeaponsAreReachableFromTheRealMenu() throws Exception {
        Harness h = window();
        for (WeaponId weapon : WeaponId.values()) {
            assertEquals(weapon, field(h.panel, "selectedStartingWeapon"));
            h.key("START"); h.tick();
            assertEquals(weapon, h.panel.getSession().runBuild().weapon());
            set(h.panel, "state", GameState.MENU);
            h.key("WEAPON"); h.tick();
        }
        assertEquals(WeaponId.COMMIT_CANNON, field(h.panel, "selectedStartingWeapon"));
    }

    @Test void levelEntryResetsRecoveryButPreservesResourcesAndCanBeContinued() throws Exception {
        Harness first = window(); first.key("START"); first.tick();
        Player player = (Player) field(first.panel, "player");
        player.takeDamage(31); player.useBomb();
        int hp = player.getHp(), bombs = player.getBombs();
        set(first.panel, "recoveryX", 3_700.0); set(first.panel, "recoveryY", 111.0);
        set(first.panel, "level", 1);
        var advance = GamePanel.class.getDeclaredMethod("advanceLevel"); advance.setAccessible(true); advance.invoke(first.panel);
        assertEquals(100.0, field(first.panel, "recoveryX"));
        assertEquals(480.0, field(first.panel, "recoveryY"));
        assertEquals(hp, player.getHp()); assertEquals(bombs, player.getBombs());
        var saved = MergeHellStateService.getInstance().readCheckpoint().orElseThrow();
        assertEquals(1, saved.mission); assertEquals(hp, saved.player.hp);
        first.panel.dispose();

        Harness second = window(); second.key("UPGRADE_REROLL"); second.tick();
        Player restored = (Player) field(second.panel, "player");
        assertEquals(GameState.RUNNING, field(second.panel, "state"));
        assertEquals(1, field(second.panel, "level"));
        assertEquals(hp, restored.getHp()); assertEquals(bombs, restored.getBombs());
        assertEquals(saved.runId, field(second.panel, "runId"));
    }

    @Test void aSecondWindowCannotOverwriteTheFirstWindowsCheckpoint() throws Exception {
        Harness first = window(); first.key("START"); first.tick();
        String runId = MergeHellStateService.getInstance().readCheckpoint().orElseThrow().runId;
        Harness second = window(); second.key("START"); second.tick();
        assertEquals(runId, MergeHellStateService.getInstance().readCheckpoint().orElseThrow().runId);
        assertFalse((boolean) field(second.panel, "ownsCheckpoint"));
        second.panel.dispose();
        assertEquals(runId, MergeHellStateService.getInstance().readCheckpoint().orElseThrow().runId);
    }

    @Test void pausedFramesStayIdenticalAndRestartClearsOldHitstop() throws Exception {
        Harness h = window(); h.key("START"); h.tick();
        h.key("PAUSE_P"); h.tick();
        int paused = h.pixels();
        for (int i = 0; i < 30; i++) h.tick();
        assertEquals(paused, h.pixels());
        set(h.panel, "hitstop", 19);
        h.key("NEW_RANKED_RUN"); h.tick();
        assertEquals(0, field(h.panel, "hitstop"));
        assertEquals(GameState.RUNNING, field(h.panel, "state"));
    }

    @Test void settingsConsumeMenuKeysWithoutStartingOrResumingCombatAndPersistMute() throws Exception {
        Harness h = window(); h.key("SETTINGS"); h.tick();
        assertNotNull(field(h.panel, "settingsEditor"));
        h.key("START"); h.tick();
        assertEquals(GameState.MENU, field(h.panel, "state"));
        h.key("PAUSE_ESC"); h.tick();
        assertNull(field(h.panel, "settingsEditor"));
        h.key("MUTE"); h.tick();
        assertFalse(MergeHellStateService.getInstance().getState().settings.muted);
        h.key("START"); h.tick(); h.key("PAUSE_P"); h.tick();
        long before = h.panel.getSession().worldTick();
        h.key("SETTINGS"); h.tick(); h.key("RIGHT"); h.tick();
        h.key("PAUSE_ESC"); h.tick();
        assertEquals(GameState.PAUSED, field(h.panel, "state"));
        assertEquals(before, h.panel.getSession().worldTick());
        assertFalse((boolean) field(h.panel, "keyRight"));
        Harness reopened = window();
        assertFalse(((MergeHellState.Settings) field(reopened.panel, "settings")).muted);
    }

    @Test void continuingALevelRestoresItsIndependentSpawnStream() throws Exception {
        Harness first = window(); first.key("START"); first.tick();
        // Start at an authored level entrance to include its randomized coin layout.
        set(first.panel, "level", 2);
        var advance = GamePanel.class.getDeclaredMethod("advanceLevel"); advance.setAccessible(true); advance.invoke(first.panel);
        long expectedSpawn = ((java.util.Random) field(first.panel, "random")).nextLong();
        var expectedCoins = first.panel.getLevelManager().getCoins().stream().map(coin -> coin.x + ":" + coin.y).toList();
        first.panel.dispose();
        Harness resumed = window();
        var continueRun = GamePanel.class.getDeclaredMethod("continueRun"); continueRun.setAccessible(true); continueRun.invoke(resumed.panel);
        assertEquals(expectedSpawn, ((java.util.Random) field(resumed.panel, "random")).nextLong());
        assertEquals(expectedCoins, resumed.panel.getLevelManager().getCoins().stream().map(coin -> coin.x + ":" + coin.y).toList());
    }

    @Test void resizingAPausedPanelChangesLayoutWithoutAdvancingTheWorld() throws Exception {
        Harness h = window(); h.key("START"); h.tick(); h.key("PAUSE_P"); h.tick();
        long before = h.panel.getSession().worldTick();
        SwingUtilities.invokeAndWait(() -> h.panel.setSize(600, 400));
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> {
            var lost = new java.awt.event.FocusEvent(h.panel, java.awt.event.FocusEvent.FOCUS_LOST);
            for (var listener : h.panel.getFocusListeners()) listener.focusLost(lost);
        });
        h.tick();
        assertTrue((boolean) field(h.panel, "compactDisplay"));
        assertEquals(before, h.panel.getSession().worldTick());
        SwingUtilities.invokeAndWait(() -> h.panel.setSize(1280, 800));
        SwingUtilities.invokeAndWait(() -> { });
        h.tick();
        assertFalse((boolean) field(h.panel, "compactDisplay"));
        assertEquals(before, h.panel.getSession().worldTick());
    }

    @Test void losingALifeAtTheInitialRecoveryPointNeverPublishesThePlayerBelowTheFloor() throws Exception {
        Harness h = window(); h.key("START"); h.tick();
        Player player = (Player) field(h.panel, "player");
        player.takeDamage(200);
        h.tick();
        assertEquals(2, player.getLives());
        assertEquals(100, player.getHp());
        assertTrue(player.getBounds().getMaxY() <= 480);
    }

    private Harness window() throws Exception {
        Harness h = new Harness(); windows.add(h); return h;
    }
    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
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
        void tick() { clock.addAndGet(GameLoop.LEGACY_STEP_NANOS); scheduler.callback.run(); }
        int pixels() throws Exception {
            BufferedImage image = new BufferedImage(960, 600, BufferedImage.TYPE_INT_ARGB);
            SwingUtilities.invokeAndWait(() -> { Graphics2D g = image.createGraphics(); try { panel.paint(g); } finally { g.dispose(); } });
            return Arrays.hashCode(((DataBufferInt) image.getRaster().getDataBuffer()).getData());
        }
    }
    private static final class Scheduler implements TickScheduler {
        Runnable callback = () -> { };
        public void scheduleAtFixedRate(Runnable tick, long period) { callback = tick; }
        public void dispose() { callback = () -> { }; }
    }
}
