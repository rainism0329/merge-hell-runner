package com.bigphil.mergehell;

import com.bigphil.mergehell.audio.AudioService;
import com.bigphil.mergehell.boss.BossAction;
import com.bigphil.mergehell.boss.LegacyBossController;
import com.bigphil.mergehell.engine.GameLoop;
import com.bigphil.mergehell.engine.TickScheduler;
import com.bigphil.mergehell.model.EntityType;
import com.bigphil.mergehell.model.ObstacleManager;
import com.bigphil.mergehell.model.Player;
import com.bigphil.mergehell.render.GameViewport;
import com.bigphil.mergehell.render.IndustrialArt;

import javax.imageio.ImageIO;
import javax.swing.Action;
import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/**
 * README — real, windowless visual smoke preview; this is a main program, not a JUnit test.
 *
 * <p>Run after the production classes have finished compiling. PowerShell example (JDK 17):
 * <pre>
 * $sdk = 'PATH/TO/ideaIC-2023.2.2/lib/*'
 * $cp = "build/classes/java/main;src/main/resources;$sdk"
 * New-Item -ItemType Directory -Force build/visual-preview/classes | Out-Null
 * javac --release 17 -encoding UTF-8 -cp $cp -d build/visual-preview/classes `
 *   src/test/java/com/bigphil/mergehell/GameVisualPreview.java
 * java -Djava.awt.headless=true -cp "build/visual-preview/classes;$cp" `
 *   com.bigphil.mergehell.GameVisualPreview build/visual-preview 20260910
 * </pre>
 *
 * <p>The first argument is the output directory; the optional second argument is the scene seed.
 * Existing PNGs with the same generated names are replaced; unrelated files are never deleted.
 * The same entry point can be rerun after artwork changes. Missing assets use the game's real
 * fallback renderer and are reported in the generated README, rather than replaced with mock art.
 *
 * <p>Inputs enter the real ActionMap on the EDT. A fake scheduler advances the production GameLoop
 * by one 16 ms simulation step, which publishes through the production FrameMailbox. PNGs capture
 * GamePanel.paint on the EDT; no UI, renderer, HUD or overlay is re-created in this utility.
 * Lab actions and explicitly positioned enemies make these repeatable inspection scenes, NOT
 * evidence of ordinary progression, balanced combat, or a completed playthrough. Particle
 * randomness and platform font rendering may prevent byte-identical screenshots.
 * No IDEA application, native window or sound device is started.
 * Fifteen scene PNGs are also painted at 600 × 400 and 1280 × 800 by resizing the actual panel.
 */
public final class GameVisualPreview {
    private static final long DEFAULT_SEED = 20260910L;
    private static final int MOTION_FRAMES = 96;
    private static final int WIDTH = GameViewport.LOGICAL_WIDTH;
    private static final int HEIGHT = GameViewport.LOGICAL_HEIGHT;

    private GameVisualPreview() { }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        if (args.length > 2) throw new IllegalArgumentException("Usage: GameVisualPreview [directory] [seed]");
        Path directory = Path.of(args.length == 0 ? "build/visual-preview" : args[0])
                .toAbsolutePath().normalize();
        long seed = args.length < 2 ? DEFAULT_SEED : Long.parseLong(args[1]);
        Files.createDirectories(directory.resolve("motion"));
        Files.createDirectories(directory.resolve("viewport-600x400"));
        Files.createDirectories(directory.resolve("viewport-1280x800"));
        List<String> manifest = new ArrayList<>();
        manifest.add("file,state,world_tick,player_x,player_y,facing,shot_sequence,dashing,panel_width,panel_height,settings_open");

        try (Scene scene = new Scene(seed)) {
            scene.expect(GameState.MENU);
            scene.captureResponsive(directory, "menu.png", manifest);
            scene.press("SETTINGS"); scene.tick();
            if (scene.field("settingsEditor", Object.class) == null)
                throw new IllegalStateException("SETTINGS action did not open the production settings overlay");
            scene.captureResponsive(directory, "settings.png", manifest);
            scene.press("PAUSE_ESC"); scene.tick();
            scene.press("PAUSE_ESC_RELEASE");
            scene.startLabRun();
            scene.press("RIGHT");
            scene.ticks(26);
            scene.press("RIGHT_R");
            scene.tick();
            scene.enemies().clearHostiles();
            scene.enemies().spawnEnemy(365, 440, EntityType.BUG);
            scene.enemies().spawnEnemy(460, 440, EntityType.CONFLICT);
            scene.enemies().spawnEnemy(555, 430, EntityType.CRASH);
            scene.enemies().spawnEnemy(660, 362, EntityType.LOCK);
            scene.enemies().spawnEnemy(755, 400, EntityType.TECHDEBT);
            scene.enemies().spawnEnemy(868, 360, EntityType.FIREWALL);
            scene.press("SHOOT");
            scene.tick();
            scene.expect(GameState.RUNNING);
            scene.captureResponsive(directory, "combat.png", manifest);

            scene.press("SHOOT_R");
            scene.press("PAUSE_P");
            scene.tick();
            scene.expect(GameState.PAUSED);
            scene.captureResponsive(directory, "pause.png", manifest);

            scene.press("PAUSE_P_RELEASE");
            scene.press("PAUSE_P");
            scene.tick();
            scene.expect(GameState.RUNNING);
            scene.press("LAB_UPGRADE");
            scene.tick();
            scene.expect(GameState.UPGRADE_SELECTION);
            scene.captureResponsive(directory, "upgrade.png", manifest);
        }

        try (Scene scene = new Scene(seed)) {
            scene.press("LAB_BOSS_ALT"); // Visible menu L entry starts invincible Boss practice.
            for (int tick = 0; tick < 300 && scene.state() != GameState.BOSS_FIGHT; tick++) scene.tick();
            scene.expect(GameState.BOSS_FIGHT);
            if (scene.field("legacyBoss", Object.class) == null)
                throw new IllegalStateException("LAB_BOSS did not create the Legacy dependency controller");
            scene.ticks(45); // Let the real arrival flash/shake settle; retain the encounter's live visuals.
            scene.expect(GameState.BOSS_FIGHT);
            scene.captureResponsive(directory, "legacy-boss.png", manifest);
            scene.press("PAUSE_P"); scene.tick();
            scene.expect(GameState.PAUSED);
            scene.captureResponsive(directory, "legacy-boss-paused.png", manifest);
        }

        // Phase fixtures use the production controller and full panel rendering. Direct damage
        // deliberately stages these art checks; this sequence is not normal-playthrough evidence.
        try (Scene scene = new Scene(seed)) {
            scene.startLabRun();
            scene.press("LAB_BOSS");
            for (int tick = 0; tick < 300 && scene.state() != GameState.BOSS_FIGHT; tick++) scene.tick();
            scene.expect(GameState.BOSS_FIGHT);
            LegacyBossController boss = scene.field("legacyBoss", LegacyBossController.class);
            for (int tick = 0; tick < 180 && !(boss.snapshot().telegraph() instanceof BossAction.Laser); tick++) scene.tick();
            if (!(boss.snapshot().telegraph() instanceof BossAction.Laser))
                throw new IllegalStateException("No live laser warning for the phase preview");
            scene.captureResponsive(directory, "legacy-laser-warning.png", manifest);
            scene.field("settings", com.bigphil.mergehell.persistence.MergeHellState.Settings.class).flashes = false;
            scene.tick();
            scene.captureResponsive(directory, "legacy-warning-flashes-off.png", manifest);
            for (int tick = 0; tick < 180 && !(boss.snapshot().telegraph() instanceof BossAction.Volley); tick++) scene.tick();
            if (!(boss.snapshot().telegraph() instanceof BossAction.Volley))
                throw new IllegalStateException("No live volley warning for the phase preview");
            scene.captureResponsive(directory, "legacy-volley-warning.png", manifest);
            boss.damageNode(1, Integer.MAX_VALUE);
            scene.tick();
            scene.captureResponsive(directory, "legacy-one-link-broken.png", manifest);
            boss.damageAllNodes(Integer.MAX_VALUE);
            scene.tick();
            scene.captureResponsive(directory, "legacy-core-exposed.png", manifest);
            boss.damageCore(1_500);
            scene.tick();
            scene.captureResponsive(directory, "legacy-enraged.png", manifest);
            boss.damageCore(Integer.MAX_VALUE);
            scene.ticks(34);
            scene.captureResponsive(directory, "legacy-defeated.png", manifest);
            scene.ticks(65);
            scene.expect(GameState.MISSION_COMPLETE);
            scene.captureResponsive(directory, "mission-complete.png", manifest);
        }

        try (Scene scene = new Scene(seed)) {
            scene.startLabRun();
            scene.press("RIGHT");
            scene.press("SHOOT");
            long previousWorldTick = scene.panel.getSession().worldTick();
            for (int frame = 0; frame < MOTION_FRAMES; frame++) {
                // This is an animation inspection lane: clear hostiles between real simulation steps
                // so damage/upgrade choices do not interrupt the joint and muzzle sequence.
                scene.enemies().clearHostiles();
                if (frame == 8) scene.press("JUMP");
                if (frame == 9) scene.press("JUMP_RELEASE");
                if (frame == 54) scene.press("DASH");
                if (frame == 55) scene.press("DASH_RELEASE");
                if (frame == 64) {
                    scene.press("RIGHT_R");
                    scene.press("LEFT");
                }
                scene.tick();
                scene.expect(GameState.RUNNING);
                long worldTick = scene.panel.getSession().worldTick();
                if (worldTick != previousWorldTick + 1)
                    throw new IllegalStateException("Motion sequence skipped a simulation step at frame " + frame);
                previousWorldTick = worldTick;
                scene.capture(directory, String.format(Locale.ROOT, "motion/frame-%03d.png", frame), manifest);
            }
        }

        Files.write(directory.resolve("frames.csv"), manifest, StandardCharsets.UTF_8);
        writeReadme(directory, seed);
        System.out.println("Visual preview complete: " + directory);
        System.out.println("15 real game screenshots + 30 resized EDT paints + " + MOTION_FRAMES
                + " consecutive 16 ms motion frames.");
        System.out.println("Controlled LAB scenarios; not normal-playthrough or audio-device evidence.");
    }

    private static void writeReadme(Path directory, long seed) throws IOException {
        IndustrialArt art = IndustrialArt.load();
        StringBuilder assets = new StringBuilder();
        for (String id : List.of("repair", "hostiles", "first-wave", "city"))
            assets.append("- ").append(id).append(": ")
                    .append(art.has(id) ? "production atlas loaded" : "production vector fallback").append('\n');
        for (var diagnostic : art.diagnostics()) assets.append("- Asset diagnostic: ").append(diagnostic).append('\n');
        Files.writeString(directory.resolve("README.md"), """
                # Real game visual preview

                These PNGs come from GamePanel's production simulation → renderer → FrameMailbox →
                EDT paint path. They are controlled, unranked LAB inspection scenes, not evidence
                of ordinary progression, a completed playthrough, combat balance or audible output.
                No screenshot contains a replacement HUD, composited mock interface or invented state.

                - Dimensions: %d × %d; seed: %d; Java: %s.
                - 15 native-size scene PNGs plus 30 actual EDT paints of the same scenes at
                  600 × 400 and 1280 × 800, in viewport-600x400/ and viewport-1280x800/.
                  Only the real GamePanel size changes; the game's own viewport scaling handles
                  fit and letterboxing. No screenshot resampling or replacement layout is used.
                - Headless mode and AudioService mute are enabled; no native window or audio device opens.
                - menu.png: the initial, untouched menu frame.
                - settings.png: the menu SETTINGS action opens the real keyboard settings overlay.
                - combat.png: visible G practice entry, 26 RIGHT steps, then all six first-world
                  enemy types positioned for art inspection and the real SHOOT action.
                - pause.png: PAUSE_P from that battle through the normal input queue.
                - upgrade.png: resume, then the built-in LAB_UPGRADE action.
                - legacy-boss.png: visible L menu entry, actual controller spawn, then 45 live steps.
                - legacy-boss-paused.png: PAUSE_P from that encounter, for checking overlay ordering.
                - legacy-laser-warning.png / legacy-warning-flashes-off.png: a real controller
                  warning before firing, including the flashes-disabled setting.
                - legacy-volley-warning.png: real locked-target volley warning; its rays use the
                  same angles and shot count as the live projectile emitter.
                - legacy-one-link-broken.png / legacy-core-exposed.png / legacy-enraged.png /
                  legacy-defeated.png: controller damage calls stage art inspection states, followed
                  by real simulation and EDT painting. These are deliberately controlled fixtures.
                - mission-complete.png: the same fixture continues through the real death delay
                  into the production completion screen, including manual continue/menu choices.
                - motion/frame-000.png through frame-%03d.png: consecutive 16 ms simulation frames
                  (62.5 frames/second, %.3f seconds), RIGHT + SHOOT, JUMP at 8, DASH at 54,
                  switch to LEFT at 64. Hostiles are cleared before each step to isolate motion.
                - frames.csv records the actual state/world tick/player position/facing/shot sequence,
                  panel dimensions and whether settings are open; it contains no inferred render state.

                Scene seeds and input schedules are repeatable. Nondeterministic particle effects
                and platform font rendering may still vary between runs.
                These captures cannot validate focus/window lifecycle, actual device audio or smooth
                real-time display timing. They do exercise the game's real rendering and input paths.

                ## Current resource availability

                %s
                ## Repeat

                See the README-style Javadoc in
                src/test/java/com/bigphil/mergehell/GameVisualPreview.java for JDK 17 compile/run commands.
                Rebuild production classes first, keep src/main/resources on the runtime classpath, and
                run GameVisualPreview [output-directory] [seed]. Generated filenames are overwritten;
                unrelated files are not removed. Refreshing the assets needs no preview code changes.
                """.formatted(WIDTH, HEIGHT, seed, System.getProperty("java.version"),
                MOTION_FRAMES - 1, MOTION_FRAMES * GameLoop.LEGACY_STEP_NANOS / 1e9, assets), StandardCharsets.UTF_8);
    }

    private static final class Scene implements AutoCloseable {
        private final AtomicLong clock = new AtomicLong();
        private final FakeScheduler scheduler = new FakeScheduler();
        private GamePanel panel;

        private Scene(long seed) throws Exception {
            SwingUtilities.invokeAndWait(() -> {
                panel = new GamePanel(scheduler, clock::get);
                panel.setSize(WIDTH, HEIGHT);
            });
            try {
                field("audio", AudioService.class).setMuted(true);
                field("random", Random.class).setSeed(seed);
                enemies().reset(seed ^ 0x454E454D494553L);
            } catch (Exception failure) {
                close();
                throw failure;
            }
        }

        private void startLabRun() throws Exception {
            press("PRACTICE_START");
            tick();
            press("PRACTICE_START_RELEASE");
            expect(GameState.RUNNING);
            if (!field("runUnranked", Boolean.class) || !field("player", Player.class).isDebugMode())
                throw new IllegalStateException("Preview must remain an unranked LAB run");
        }

        private void press(String name) throws Exception {
            SwingUtilities.invokeAndWait(() -> {
                Action action = panel.getActionMap().get(name);
                if (action == null) throw new IllegalArgumentException("No production action named " + name);
                action.actionPerformed(new ActionEvent(panel, ActionEvent.ACTION_PERFORMED, name));
            });
        }

        private void tick() throws Exception {
            clock.addAndGet(GameLoop.LEGACY_STEP_NANOS);
            scheduler.fire();
            if (state() == GameState.ERROR)
                throw new IllegalStateException("Production loop failed: " + field("loopErrorMessage", String.class));
        }

        private void ticks(int count) throws Exception { for (int tick = 0; tick < count; tick++) tick(); }
        private GameState state() throws Exception { return field("state", GameState.class); }
        private ObstacleManager enemies() throws Exception { return field("enemyManager", ObstacleManager.class); }

        private void expect(GameState expected) throws Exception {
            if (state() != expected) throw new IllegalStateException("Expected " + expected + ", got " + state());
        }

        private void capture(Path directory, String name, List<String> manifest) throws Exception {
            capture(directory, name, manifest, WIDTH, HEIGHT);
        }

        private void captureResponsive(Path directory, String name, List<String> manifest) throws Exception {
            capture(directory, name, manifest);
            capture(directory, "viewport-600x400/" + name, manifest, 600, 400);
            capture(directory, "viewport-1280x800/" + name, manifest, 1280, 800);
        }

        private void capture(Path directory, String name, List<String> manifest, int width, int height) throws Exception {
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            int originalWidth = panel.getWidth(), originalHeight = panel.getHeight();
            boolean resized = originalWidth != width || originalHeight != height;
            try {
                if (resized) {
                    SwingUtilities.invokeAndWait(() -> panel.setSize(width, height));
                    SwingUtilities.invokeAndWait(() -> { }); // Deliver the real component-resize event.
                    tick(); // Consume the layout command and publish the actual new logical frame.
                }
                SwingUtilities.invokeAndWait(() -> {
                    Graphics2D graphics = image.createGraphics();
                    try {
                        panel.paint(graphics);
                    } finally {
                        graphics.dispose();
                    }
                });
                if (!ImageIO.write(image, "png", directory.resolve(name).toFile()))
                    throw new IOException("PNG encoder unavailable");
                Player player = field("player", Player.class);
                manifest.add(String.format(Locale.ROOT, "%s,%s,%d,%.3f,%.3f,%d,%d,%s,%d,%d,%s", name, state(),
                        panel.getSession().worldTick(), player.getX(), player.getY(),
                        player.getFacingDir(), player.getShotSequence(), player.isDashing(), width, height,
                        field("settingsEditor", Object.class) != null));
            } finally {
                image.flush();
                if (resized) {
                    SwingUtilities.invokeAndWait(() -> panel.setSize(originalWidth, originalHeight));
                    SwingUtilities.invokeAndWait(() -> { });
                    tick();
                }
            }
        }

        private <T> T field(String name, Class<T> type) throws Exception {
            Field field = GamePanel.class.getDeclaredField(name);
            field.setAccessible(true);
            return type.cast(field.get(panel));
        }

        private static void seedRandom(Class<?> owner, Object instance, long seed) throws Exception {
            Field random = owner.getDeclaredField("random");
            random.setAccessible(true);
            ((Random) random.get(instance)).setSeed(seed);
        }

        @Override public void close() { if (panel != null) panel.dispose(); }
    }

    private static final class FakeScheduler implements TickScheduler {
        private Runnable task;
        @Override public void scheduleAtFixedRate(Runnable task, long periodMillis) { this.task = task; }
        private void fire() {
            if (task == null) throw new IllegalStateException("Preview scheduler is not running");
            task.run();
        }
        @Override public void dispose() { task = null; }
    }
}
