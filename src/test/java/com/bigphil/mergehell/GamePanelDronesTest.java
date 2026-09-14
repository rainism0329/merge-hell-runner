package com.bigphil.mergehell;

import com.bigphil.mergehell.boss.LegacyBossController;
import com.bigphil.mergehell.boss.BossArrivalController;
import com.bigphil.mergehell.combat.DroneController;
import com.bigphil.mergehell.engine.GameLoop;
import com.bigphil.mergehell.engine.TickScheduler;
import com.bigphil.mergehell.model.*;
import com.bigphil.mergehell.persistence.MergeHellState;
import com.bigphil.mergehell.persistence.MergeHellStateService;
import com.bigphil.mergehell.progression.UpgradeCatalog;
import com.bigphil.mergehell.progression.UpgradeId;
import org.junit.jupiter.api.*;
import javax.swing.SwingUtilities;
import java.awt.event.ActionEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

/** Integration uses real input/loop paths; direct upgrade/target placement is an explicit fixture. */
class GamePanelDronesTest {
    private final List<Harness> windows = new ArrayList<>();
    private final MergeHellStateService storage = MergeHellStateService.getInstance();
    private MergeHellState original;
    @BeforeEach void isolate() { original = storage.getState(); storage.loadState(new MergeHellState()); }
    @AfterEach void close() { windows.forEach(h -> h.panel.dispose()); storage.loadState(original); }

    @Test void rankCreatesVisibleCompanionsAndTheyShootWithoutThePlayerFiring() throws Exception {
        Harness h = start(); h.rank(1); h.tick();
        assertEquals(1, h.drones().snapshot().drones().size());
        h.rank(2); h.enemies().spawnEnemy(450, 400, EntityType.TECHDEBT);
        // New actors are protected for 30 steps; autonomous targeting must respect that grace period.
        for (int i = 0; i < 30; i++) h.tick();
        var poses = h.drones().snapshot().drones();
        assertEquals(2, poses.size());
        assertNotEquals(poses.get(0).x(), poses.get(1).x());
        assertTrue(poses.stream().anyMatch(p -> p.targetId() >= 16));
        assertTrue(h.shots().stream().anyMatch(p -> p.getDamageKind() == com.bigphil.mergehell.combat.CombatEvent.DamageKind.DRONE));
        assertEquals(0, h.player().getShotSequence(), "Autonomous fire does not impersonate a player's trigger");
        h.rank(3); h.tick();
        assertEquals(2, h.drones().snapshot().drones().size());
        assertTrue(h.drones().snapshot().drones().stream().allMatch(p -> p.rank() == 3));
    }

    @Test void pauseAndUpgradeSelectionFreezeTheSamePublishedCompanionSnapshot() throws Exception {
        Harness h = start(); h.rank(2); h.tick();
        h.key("PAUSE_P"); h.tick();
        var paused = h.drones().snapshot();
        for (int i = 0; i < 25; i++) h.tick();
        assertSame(paused, h.drones().snapshot());
        h.key("PAUSE_P"); h.tick();
        assertTrue(h.drones().snapshot().tick() > paused.tick());
        h.key("LAB_TOGGLE_ALT"); h.tick(); h.key("LAB_UPGRADE_ALT"); h.tick();
        assertEquals(GameState.UPGRADE_SELECTION, get(h.panel, "state"));
        var draft = h.drones().snapshot();
        for (int i = 0; i < 25; i++) h.tick();
        assertSame(draft, h.drones().snapshot());
    }

    @Test void legacyTargetsLiveDependenciesAndThenTheExposedCoreWithoutMinorEnemies() throws Exception {
        Harness h = start(); h.rank(2); h.key("LAB_TOGGLE_ALT"); h.key("LAB_BOSS_ALT");
        h.tick();
        assertEquals(GameState.BOSS_WARNING, get(h.panel, "state"));
        assertEquals(BossArrivalController.DURATION_TICKS, get(h.panel, "bossWarningTimer"));
        for (int tick = 0; tick < BossArrivalController.DURATION_TICKS - 1; tick++) {
            h.tick(); assertEquals(GameState.BOSS_WARNING, get(h.panel, "state"));
        }
        h.tick();
        assertEquals(GameState.BOSS_FIGHT, get(h.panel, "state"));
        h.player().setX(330); invoke(h.panel, "resetDrones"); h.enemies().clearHostiles(); h.tick();
        assertTrue(h.drones().snapshot().drones().stream().anyMatch(p -> p.targetId() >= 1 && p.targetId() <= 3));
        LegacyBossController boss = (LegacyBossController) get(h.panel, "legacyBoss");
        boss.damageAllNodes(Integer.MAX_VALUE);
        long beforePhase = h.panel.getSession().worldTick();
        for (int i = 0; i < 12 && h.panel.getSession().worldTick() == beforePhase; i++) {
            h.enemies().clearHostiles(); h.tick();
        }
        assertTrue(h.panel.getSession().worldTick() > beforePhase, "Reacquire after the bounded guard-break hitstop");
        assertTrue(h.drones().snapshot().drones().stream().allMatch(p -> p.targetId() == 4));
    }

    @Test void laterWorldBossIsAValidAutonomousTargetAndDeadBossIsNot() throws Exception {
        Harness h = start(); h.rank(2);
        set(h.panel, "level", 1); invoke(h.panel, "advanceLevel");
        Boss boss = new Boss("Drone fixture", 1_000, "B", 250, 1, 73L); boss.activate();
        set(h.panel, "boss", boss); set(h.panel, "state", GameState.BOSS_FIGHT);
        h.tick();
        assertTrue(h.drones().snapshot().drones().stream().anyMatch(p -> p.targetId() == 5));
        boss.takeDamage(Integer.MAX_VALUE);
        long beforeDefeat = h.panel.getSession().worldTick();
        for (int i = 0; i < 12 && h.panel.getSession().worldTick() == beforeDefeat; i++) h.tick();
        assertTrue(h.panel.getSession().worldTick() > beforeDefeat, "Defeat feedback must release the simulation");
        assertTrue(h.drones().snapshot().drones().stream().noneMatch(p -> p.targetId() == 5));
    }

    @Test void levelEntryContinueRespawnAndNewRunRebuildOrRemoveCompanionsFromTheExistingRank() throws Exception {
        Harness h = start(); h.rank(3); h.tick();
        set(h.panel, "level", 1); invoke(h.panel, "advanceLevel"); h.tick();
        assertEquals(2, h.drones().snapshot().drones().size());
        assertEquals(3, storage.readCheckpoint().orElseThrow().upgradeRanks.get(UpgradeId.DRONE_COPILOT));
        h.key("PAUSE_P"); h.tick(); h.key("MENU"); h.tick(); h.key("UPGRADE_REROLL"); h.tick();
        assertEquals(3, h.panel.getSession().runBuild().buildStats().droneLevel());
        assertEquals(2, h.drones().snapshot().drones().size());
        h.player().setX(500); h.tick();
        h.player().takeDamage(Integer.MAX_VALUE); h.tick();
        assertEquals(2, h.player().getLives());
        double respawnX = h.player().getX();
        assertTrue(h.drones().snapshot().drones().stream().allMatch(p -> Math.abs(p.x() - respawnX) < 100));
        h.key("NEW_RANKED_RUN"); h.tick();
        assertTrue(h.drones().snapshot().drones().isEmpty());
        assertTrue(h.shots().stream().noneMatch(p -> p.getDamageKind() == com.bigphil.mergehell.combat.CombatEvent.DamageKind.DRONE));
    }

    private Harness start() throws Exception {
        Harness h = new Harness(); windows.add(h); h.key("START"); h.tick(); return h;
    }
    private static Object get(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target);
    }
    private static void set(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); f.set(target, value);
    }
    private static void invoke(Object target, String name) throws Exception {
        Method m = target.getClass().getDeclaredMethod(name); m.setAccessible(true); m.invoke(target);
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
        void rank(int rank) {
            while (panel.getSession().runBuild().buildStats().droneLevel() < rank)
                panel.getSession().runBuild().apply(UpgradeCatalog.definition(UpgradeId.DRONE_COPILOT));
        }
        DroneController drones() throws Exception { return (DroneController) get(panel, "drones"); }
        Player player() throws Exception { return (Player) get(panel, "player"); }
        ObstacleManager enemies() throws Exception { return (ObstacleManager) get(panel, "enemyManager"); }
        @SuppressWarnings("unchecked") List<Projectile> shots() throws Exception { return (List<Projectile>) get(panel, "projectiles"); }
    }
    private static final class Scheduler implements TickScheduler {
        Runnable task = () -> { };
        public void scheduleAtFixedRate(Runnable task, long period) { this.task = task; }
        public void dispose() { task = () -> { }; }
    }
}
