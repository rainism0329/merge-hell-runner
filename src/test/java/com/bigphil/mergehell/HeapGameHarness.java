package com.bigphil.mergehell;

import com.bigphil.mergehell.boss.BossFeedbackController;
import com.bigphil.mergehell.engine.GameLoop;
import com.bigphil.mergehell.engine.TickScheduler;
import com.bigphil.mergehell.model.*;
import com.bigphil.mergehell.world.HeapDistrictController;
import javax.swing.SwingUtilities;
import java.awt.event.ActionEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** Explicit chapter/position fixtures around the real input, simulation and paint pipeline. */
final class HeapGameHarness implements AutoCloseable {
    final AtomicLong clock = new AtomicLong();
    final Scheduler scheduler = new Scheduler();
    GamePanel panel;
    HeapGameHarness() throws Exception {
        SwingUtilities.invokeAndWait(() -> { panel = new GamePanel(scheduler, clock::get); panel.setSize(960, 600); });
        key("START"); tick(); set("level", 1); invoke("advanceLevel"); tick();
    }
    void key(String name) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            panel.getActionMap().get(name).actionPerformed(new ActionEvent(panel, 0, name));
            var release = panel.getActionMap().get(name + "_RELEASE");
            // Held controls use their real *_R edge. Releasing only the buffer here
            // would leave the simulation moving while making the later release a no-op.
            if (release != null && panel.getActionMap().get(name + "_R") == null)
                release.actionPerformed(new ActionEvent(panel, 0, name));
        });
    }
    void tick() throws Exception {
        clock.addAndGet(GameLoop.LEGACY_STEP_NANOS); scheduler.task.run();
        if (state() == GameState.ERROR) throw new IllegalStateException("Game loop failed: " + get("loopErrorMessage"));
    }
    void ticks(int n) throws Exception { for (int i = 0; i < n; i++) tick(); }
    void quietTicks(int n) throws Exception {
        for (int i = 0; i < n; i++) { enemies().clearHostiles(); tick(); }
    }
    void place(double x, double y) throws Exception {
        player().setX(x); player().setY(y);
        if (state() != GameState.BOSS_FIGHT) set("cameraX", Math.max(0, x - 288));
        tick();
    }
    void bossPractice() throws Exception {
        key("LAB_TOGGLE_ALT"); tick(); key("LAB_BOSS_ALT");
        for (int i = 0; i < 220 && state() != GameState.BOSS_FIGHT; i++) tick();
        if (state() != GameState.BOSS_FIGHT) throw new IllegalStateException("Boss did not start");
        quietTicks(15);
    }
    void invoke(String name) throws Exception {
        Method method = GamePanel.class.getDeclaredMethod(name); method.setAccessible(true); method.invoke(panel);
    }
    Object get(String name) throws Exception {
        Field field = GamePanel.class.getDeclaredField(name); field.setAccessible(true); return field.get(panel);
    }
    void set(String name, Object value) throws Exception {
        Field field = GamePanel.class.getDeclaredField(name); field.setAccessible(true); field.set(panel, value);
    }
    GameState state() throws Exception { return (GameState) get("state"); }
    Player player() throws Exception { return (Player) get("player"); }
    Boss boss() throws Exception { return (Boss) get("boss"); }
    ObstacleManager enemies() throws Exception { return (ObstacleManager) get("enemyManager"); }
    HeapDistrictController heap() throws Exception { return (HeapDistrictController) get("heap"); }
    BossFeedbackController feedback() throws Exception { return (BossFeedbackController) get("bossFeedback"); }
    CollisionSystem.Context context() throws Exception { return (CollisionSystem.Context) get("ctx"); }
    @SuppressWarnings("unchecked") List<Projectile> shots() throws Exception { return (List<Projectile>) get("projectiles"); }
    @Override public void close() { panel.dispose(); }
    private static final class Scheduler implements TickScheduler {
        Runnable task = () -> { };
        public void scheduleAtFixedRate(Runnable runnable, long period) { task = runnable; }
        public void dispose() { task = () -> { }; }
    }
}
