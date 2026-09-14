package com.bigphil.mergehell;

import com.bigphil.mergehell.boss.BossFeedbackController;
import com.bigphil.mergehell.boss.BossArrivalController;
import com.bigphil.mergehell.boss.BossPhase;
import com.bigphil.mergehell.boss.LegacyBossController;
import com.bigphil.mergehell.combat.CombatEvent;
import com.bigphil.mergehell.combat.ProjectileSpec;
import com.bigphil.mergehell.combat.WeaponId;
import com.bigphil.mergehell.engine.GameLoop;
import com.bigphil.mergehell.engine.TickScheduler;
import com.bigphil.mergehell.model.ObstacleManager;
import com.bigphil.mergehell.model.Projectile;
import com.bigphil.mergehell.persistence.MergeHellState;
import com.bigphil.mergehell.persistence.MergeHellStateService;
import org.junit.jupiter.api.*;

import javax.swing.SwingUtilities;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/** Projectile placement is a fixture; damage, feedback, input, pause and rendering use the real loop. */
class GamePanelBossFeedbackTest {
    private final List<Harness> scenes = new ArrayList<>();
    private final MergeHellStateService storage = MergeHellStateService.getInstance();
    private MergeHellState original;

    @BeforeEach void isolate() {
        original = storage.getState(); MergeHellState state = new MergeHellState(); state.settings.muted = true;
        // Global screen effects intentionally decay on drafts; isolate the frozen boss presentation.
        state.settings.flashes = false; state.settings.shakePercent = 0;
        storage.loadState(state);
    }
    @AfterEach void close() { scenes.forEach(scene -> scene.panel.dispose()); storage.loadState(original); }

    @Test void actualNodeHitPublishesAcceptedDamageAndALocalReaction() throws Exception {
        Harness h = scene(); var node = h.boss().nodes().get(0); int before = node.hp();
        h.hit(h.boss().nodeBounds(node.id()), 25);
        assertEquals(before - 25, node.hp());
        var feedback = h.feedback().snapshot();
        var impact = feedback.impacts().stream().filter(i -> i.part() == CombatEvent.BossPart.NODE).findFirst().orElseThrow();
        assertEquals(node.id(), impact.partId()); assertEquals(25, impact.damage()); assertFalse(impact.blocked());
        assertTrue(feedback.bodyFlash() > 0); assertTrue(feedback.shielded());
    }

    @Test void aShieldedCoreConsumesTheProjectileButOnlyPublishesBlockedFeedback() throws Exception {
        Harness h = scene(); int before = h.boss().coreHp();
        h.hit(h.boss().coreBounds(), 80);
        assertEquals(before, h.boss().coreHp());
        var feedback = h.feedback().snapshot();
        assertTrue(feedback.impacts().stream().anyMatch(i -> i.part() == CombatEvent.BossPart.CORE && i.blocked() && i.damage() == 0));
        assertEquals(0, feedback.bodyFlash()); assertEquals(0, feedback.recoil()); assertTrue(feedback.shielded());
    }

    @Test void severingTheRealNodesShowsGuardBreakThenAnExposedCoreAcceptsNormalDamage() throws Exception {
        Harness h = scene();
        for (var node : h.boss().nodes()) {
            h.hit(h.boss().nodeBounds(node.id()), node.hp());
            h.choosePendingUpgrades();
        }
        assertEquals(BossPhase.CORE_EXPOSED, h.boss().phase());
        for (int tick = 0; tick < 12 && h.feedback().snapshot().shielded(); tick++) h.quietTick();
        assertFalse(h.feedback().snapshot().shielded()); assertFalse(h.feedback().snapshot().vulnerable(), "Legacy exposure does not claim Heap's +50% multiplier");
        assertNotNull(h.feedback().snapshot().banner());
        assertEquals(BossFeedbackController.Kind.GUARD_BREAK, h.feedback().snapshot().banner().kind());
        int before = h.boss().coreHp(); h.hit(h.boss().coreBounds(), 25);
        assertEquals(before - 25, h.boss().coreHp());
        assertTrue(h.feedback().snapshot().impacts().stream().anyMatch(i -> i.part() == CombatEvent.BossPart.CORE && !i.blocked() && i.damage() == 25));
    }

    @Test void pauseAndUpgradeScreensFreezeBothBossFeedbackAndThePublishedPixels() throws Exception {
        Harness h = scene(); h.hit(h.boss().nodeBounds(0), 25);
        h.key("PAUSE_P"); h.tick(); assertEquals(GameState.PAUSED, h.state());
        var paused = h.feedback().snapshot(); int[] pausedPixels = h.pixels();
        for (int tick = 0; tick < 25; tick++) h.tick();
        assertEquals(paused, h.feedback().snapshot()); assertArrayEquals(pausedPixels, h.pixels());
        h.key("PAUSE_P"); h.tick();
        h.key("LAB_UPGRADE_ALT"); h.tick(); assertEquals(GameState.UPGRADE_SELECTION, h.state());
        var draft = h.feedback().snapshot(); int[] draftPixels = h.pixels();
        for (int tick = 0; tick < 25; tick++) h.tick();
        assertEquals(draft, h.feedback().snapshot()); assertArrayEquals(draftPixels, h.pixels());
        h.choosePendingUpgrades();
        assertEquals(GameState.BOSS_FIGHT, h.state());
    }

    private Harness scene() throws Exception {
        Harness h = new Harness(); scenes.add(h);
        h.key("LAB_BOSS_ALT"); h.tick();
        assertEquals(GameState.BOSS_WARNING, h.state());
        assertEquals(BossArrivalController.DURATION_TICKS, field(h.panel, "bossWarningTimer"));
        for (int tick = 0; tick < BossArrivalController.DURATION_TICKS - 1; tick++) {
            h.tick(); assertEquals(GameState.BOSS_WARNING, h.state());
        }
        h.tick();
        assertEquals(GameState.BOSS_FIGHT, h.state());
        h.quietTick(); return h;
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
    private static final class Harness {
        final AtomicLong clock = new AtomicLong(); final Scheduler scheduler = new Scheduler(); GamePanel panel;
        Harness() throws Exception {
            SwingUtilities.invokeAndWait(() -> { panel = new GamePanel(scheduler, clock::get); panel.setSize(960, 600); });
        }
        void key(String name) throws Exception {
            SwingUtilities.invokeAndWait(() -> {
                panel.getActionMap().get(name).actionPerformed(new ActionEvent(panel, 0, name));
                var release = panel.getActionMap().get(name + "_RELEASE");
                if (release != null) release.actionPerformed(new ActionEvent(panel, 0, name));
            });
        }
        void tick() throws Exception {
            clock.addAndGet(GameLoop.LEGACY_STEP_NANOS); scheduler.task.run();
            assertNotEquals(GameState.ERROR, state(), () -> "Real GamePanel loop entered ERROR");
        }
        void quietTick() throws Exception { enemies().clearHostiles(); enemies().getEnemyBullets().clear(); tick(); }
        void hit(Rectangle bounds, int damage) throws Exception {
            Projectile shot = new Projectile(bounds.x + 3, bounds.y + 3,
                    new ProjectileSpec(WeaponId.COMMIT_CANNON, damage, 0, 0, false, 0, 0, 0));
            shots().add(shot);
            for (int tick = 0; tick < 24 && !shot.isDead(); tick++) quietTick();
            assertTrue(shot.isDead(), "Placed projectile must be consumed by the real collision loop");
        }
        void choosePendingUpgrades() throws Exception {
            for (int choice = 0; choice < 4 && state() == GameState.UPGRADE_SELECTION; choice++) { key("UPGRADE_1"); quietTick(); }
            assertNotEquals(GameState.UPGRADE_SELECTION, state());
        }
        int[] pixels() throws Exception {
            BufferedImage image = new BufferedImage(960, 600, BufferedImage.TYPE_INT_ARGB);
            SwingUtilities.invokeAndWait(() -> { var g = image.createGraphics(); panel.paint(g); g.dispose(); });
            return image.getRGB(0, 0, 960, 600, null, 0, 960);
        }
        GameState state() throws Exception { return (GameState) field(panel, "state"); }
        LegacyBossController boss() throws Exception { return (LegacyBossController) field(panel, "legacyBoss"); }
        BossFeedbackController feedback() throws Exception { return (BossFeedbackController) field(panel, "bossFeedback"); }
        ObstacleManager enemies() throws Exception { return (ObstacleManager) field(panel, "enemyManager"); }
        @SuppressWarnings("unchecked") List<Projectile> shots() throws Exception { return (List<Projectile>) field(panel, "projectiles"); }
    }
    private static final class Scheduler implements TickScheduler {
        Runnable task = () -> { };
        public void scheduleAtFixedRate(Runnable runnable, long period) { task = runnable; }
        public void dispose() { task = () -> { }; }
    }
}
