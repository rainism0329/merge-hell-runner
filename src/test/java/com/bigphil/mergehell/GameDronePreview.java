package com.bigphil.mergehell;

import com.bigphil.mergehell.combat.DroneController;
import com.bigphil.mergehell.engine.GameLoop;
import com.bigphil.mergehell.engine.TickScheduler;
import com.bigphil.mergehell.model.EntityType;
import com.bigphil.mergehell.model.ObstacleManager;
import com.bigphil.mergehell.persistence.MergeHellState;
import com.bigphil.mergehell.persistence.MergeHellStateService;
import com.bigphil.mergehell.progression.UpgradeCatalog;
import com.bigphil.mergehell.progression.UpgradeId;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** Real panel captures with deliberately installed ranks/positioned targets; not normal play evidence. */
public final class GameDronePreview {
    public static void main(String[] args) throws Exception {
        if (!java.awt.GraphicsEnvironment.isHeadless()) throw new IllegalStateException("Use headless mode");
        Path out = Path.of(args.length == 0 ? "build/drone-game-preview" : args[0]);
        Files.createDirectories(out.resolve("motion"));
        MergeHellState state = new MergeHellState(); state.settings.muted = true;
        MergeHellStateService.getInstance().loadState(state);
        List<String> manifest = new ArrayList<>();
        manifest.add("file,state,world_tick,drone_tick,count,invincible,unranked");
        try (Scene scene = new Scene()) {
            scene.key("PRACTICE_START"); scene.tick();
            scene.rank(1); scene.enemies().spawnEnemy(510, 400, EntityType.TECHDEBT);
            scene.ticks(30); scene.captureBoth(out, "rank-1", manifest);
            scene.rank(2); scene.enemies().spawnEnemy(570, 310, EntityType.LOCK);
            scene.ticks(30); scene.captureBoth(out, "rank-2", manifest);
            scene.rank(3); scene.drones().reset(); scene.tick();
            scene.captureBoth(out, "rank-3", manifest);
            scene.key("PAUSE_P"); scene.tick(); scene.captureBoth(out, "paused", manifest);
            scene.key("PAUSE_P"); scene.tick();
            scene.key("LAB_TOGGLE_ALT"); scene.tick(); scene.captureBoth(out, "god-off-confirmation", manifest);
            for (int i = 0; i < 190; i++) { scene.enemies().clearHostiles(); scene.tick(); }
            scene.captureBoth(out, "god-off-after-notice", manifest);
            scene.key("RIGHT");
            for (int frame = 0; frame < 30; frame++) {
                scene.enemies().clearHostiles(); scene.tick();
                scene.capture(out, "motion/frame-%02d.png".formatted(frame), 960, 600, manifest);
            }
            scene.key("RIGHT_R");
        }
        try (Scene scene = new Scene()) {
            scene.key("LAB_BOSS_ALT"); scene.tick(); scene.rank(3);
            for (int i = 0; i < 120 && scene.state() != GameState.BOSS_FIGHT; i++) scene.tick();
            scene.key("RIGHT"); scene.ticks(42); scene.key("RIGHT_R");
            scene.enemies().clearHostiles(); scene.tick();
            scene.captureBoth(out, "boss-support", manifest);
        }
        Files.write(out.resolve("frames.csv"), manifest);
        Files.writeString(out.resolve("README.md"), """
                # Drone integration frames

                Real GamePanel input → 16ms simulation → FrameMailbox → EDT paint.
                Seven scenes at 960x600 and 600x400, plus 30 consecutive movement frames (44 PNGs).
                Drone ranks and targets are explicit inspection fixtures; G/L/T/P/movement use
                the actual ActionMap. Ranks 1/2/3, autonomous fire, pause, T-off confirmation and
                expiry, following and Legacy support are captured. No replacement HUD or image
                composition is used. No native IDE, audio device or real-time FPS claim.
                """);
        System.out.println("44 real panel drone inspection frames: " + out.toAbsolutePath());
    }

    private static final class Scene implements AutoCloseable {
        final AtomicLong clock = new AtomicLong();
        final Scheduler scheduler = new Scheduler();
        GamePanel panel;
        Scene() throws Exception {
            SwingUtilities.invokeAndWait(() -> { panel = new GamePanel(scheduler, clock::get); panel.setSize(960, 600); });
        }
        void key(String key) throws Exception {
            SwingUtilities.invokeAndWait(() -> {
                panel.getActionMap().get(key).actionPerformed(new ActionEvent(panel, 0, key));
                var release = panel.getActionMap().get(key + "_RELEASE");
                if (release != null) release.actionPerformed(new ActionEvent(panel, 0, key));
            });
        }
        void tick() throws Exception {
            clock.addAndGet(GameLoop.LEGACY_STEP_NANOS); scheduler.task.run();
            if (state() == GameState.ERROR) throw new IllegalStateException("Production loop failed");
        }
        void ticks(int count) throws Exception { for (int i = 0; i < count; i++) tick(); }
        void rank(int target) {
            while (panel.getSession().runBuild().buildStats().droneLevel() < target)
                panel.getSession().runBuild().apply(UpgradeCatalog.definition(UpgradeId.DRONE_COPILOT));
        }
        GameState state() throws Exception { return (GameState) field("state"); }
        DroneController drones() throws Exception { return (DroneController) field("drones"); }
        ObstacleManager enemies() throws Exception { return (ObstacleManager) field("enemyManager"); }
        Object field(String name) throws Exception {
            Field f = GamePanel.class.getDeclaredField(name); f.setAccessible(true); return f.get(panel);
        }
        void captureBoth(Path out, String name, List<String> manifest) throws Exception {
            capture(out, name + "-960.png", 960, 600, manifest);
            capture(out, name + "-600.png", 600, 400, manifest);
        }
        void capture(Path out, String name, int width, int height, List<String> manifest) throws Exception {
            if (panel.getWidth() != width || panel.getHeight() != height) {
                SwingUtilities.invokeAndWait(() -> panel.setSize(width, height));
                SwingUtilities.invokeAndWait(() -> { }); tick();
            }
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            SwingUtilities.invokeAndWait(() -> {
                Graphics2D g = image.createGraphics();
                try { panel.paint(g); } finally { g.dispose(); }
            });
            ImageIO.write(image, "png", out.resolve(name).toFile()); image.flush();
            manifest.add(name + "," + state() + "," + panel.getSession().worldTick() + ","
                    + drones().snapshot().tick() + "," + drones().snapshot().drones().size()
                    + "," + field("labPowerEnabled") + "," + field("runUnranked"));
        }
        @Override public void close() { panel.dispose(); }
    }
    private static final class Scheduler implements TickScheduler {
        Runnable task = () -> { };
        public void scheduleAtFixedRate(Runnable task, long period) { this.task = task; }
        public void dispose() { this.task = () -> { }; }
    }
}
