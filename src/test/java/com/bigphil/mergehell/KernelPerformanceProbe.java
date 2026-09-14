package com.bigphil.mergehell;

import com.bigphil.mergehell.engine.GameLoop;
import com.bigphil.mergehell.engine.TickScheduler;
import com.bigphil.mergehell.model.*;
import com.bigphil.mergehell.persistence.MergeHellState;
import com.bigphil.mergehell.persistence.MergeHellStateService;
import com.bigphil.mergehell.render.FrameMailbox;
import com.bigphil.mergehell.render.GameViewport;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public final class KernelPerformanceProbe {
    private static final long SEED = 0x4D365F415254L;
    private static final long WARMUP_NANOS = 750_000_000L;
    private static final long SAMPLE_NANOS = 1_750_000_000L;
    private static final Field ENEMY_X = field(ObstacleManager.Enemy.class, "x");
    private static final Field ENEMY_Y = field(ObstacleManager.Enemy.class, "y");
    private static final Field LATEST = field(FrameMailbox.class, "latest");
    private static final Method BUFFER_COUNT;
    static {
        try { BUFFER_COUNT = FrameMailbox.class.getDeclaredMethod("retainedBufferCount"); BUFFER_COUNT.setAccessible(true); }
        catch (ReflectiveOperationException exception) { throw new ExceptionInInitializerError(exception); }
    }
    private record Timing(long simulation, long dispatch, long paint, boolean published,
                          int enemies, int projectiles, int enemyProjectiles, int particles) { }
    private record Memory(long heapUsed, long heapCommitted, long nonHeapUsed, int threads, long gcCount, long gcMillis) { }
    private record Distribution(double p50, double p95, double max) { }
    private static final class Scheduler implements TickScheduler {
        Runnable tick = () -> { };
        public void scheduleAtFixedRate(Runnable callback, long ignored) { tick = callback; }
        public void dispose() { tick = () -> { }; }
    }
    private static final class Harness implements AutoCloseable {
        final AtomicLong clock = new AtomicLong();
        final Scheduler scheduler = new Scheduler();
        final BufferedImage physical;
        final List<ObstacleManager.Enemy> actors = new ArrayList<>();
        final ObstacleManager manager;
        final List<Projectile> projectiles;
        final List<Particle> particles;
        final FrameMailbox mailbox;
        final int playerBulletTarget, enemyBulletTarget, particleTarget, drawWidth, drawHeight;
        GamePanel panel;
        int fillStep;

        @SuppressWarnings("unchecked") Harness(int width, int height, boolean high, int world) throws Exception {
            MergeHellState isolated = new MergeHellState();
            isolated.settings.muted = true;
            isolated.settings.volumePercent = 0;
            MergeHellStateService.getInstance().loadState(isolated);
            SwingUtilities.invokeAndWait(() -> { panel = new GamePanel(scheduler, clock::get); panel.setSize(width, height); });
            ((Random) get(panel, "random")).setSeed(SEED);
            field(GamePanel.class, "labPowerEnabled").set(panel, true);
            SwingUtilities.invokeAndWait(() -> panel.getActionMap().get("START").actionPerformed(new ActionEvent(panel, 0, "START")));
            advance();
            field(GamePanel.class, "level").setInt(panel, world);
            Method advanceLevel = GamePanel.class.getDeclaredMethod("advanceLevel"); advanceLevel.setAccessible(true); advanceLevel.invoke(panel);
            SwingUtilities.invokeAndWait(() -> { });
            advance();
            physical = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
            var transform = GameViewport.fit(width, height);
            drawWidth = transform.drawWidth(); drawHeight = transform.drawHeight();
            manager = (ObstacleManager) get(panel, "enemyManager");
            projectiles = (List<Projectile>) get(panel, "projectiles");
            particles = (List<Particle>) get(panel, "particles");
            mailbox = (FrameMailbox) get(panel, "frames");
            playerBulletTarget = high ? limit("MAX_PROJECTILES") : 32;
            enemyBulletTarget = high ? limit("MAX_ENEMY_PROJECTILES") : 12;
            particleTarget = high ? limit("MAX_PARTICLES") : 120;
            EntityType[] types = {EntityType.BUG, EntityType.LOCK, EntityType.CRASH, EntityType.CONFLICT,
                    EntityType.TECHDEBT, EntityType.LEAK, EntityType.SENTINEL, EntityType.INTERRUPT, EntityType.MIRROR};
            for (int i = 0; i < (high ? limit("MAX_HOSTILES") : 6); i++) actors.add(new ObstacleManager.Enemy(0, 0, types[i % types.length], 1, SEED + i));
        }
        void advance() {
            clock.addAndGet(GameLoop.LEGACY_STEP_NANOS);
            scheduler.tick.run();
        }
        // Synthetic load construction is outside measured callbacks; actual update/collision/render remain production code.
        void prepare() throws Exception {
            manager.getEnemies().clear(); manager.getEnemies().addAll(actors);
            for (int i = 0; i < actors.size(); i++) {
                ENEMY_X.setDouble(actors.get(i), 530 + (i % 8) * 47);
                ENEMY_Y.setDouble(actors.get(i), 300 + (i / 8) * 22);
            }
            projectiles.clear(); manager.getEnemyBullets().clear(); particles.clear();
            for (int i = 0; i < playerBulletTarget; i++)
                projectiles.add(new Projectile(60 + i % 80 * 10, 65 + i / 80 * 12, 2, 0, ProjectileType.COMMIT));
            for (int i = 0; i < enemyBulletTarget; i++)
                manager.getEnemyBullets().add(new Projectile(180 + i % 70 * 10, 155 + i / 70 * 12, -1, 0, ProjectileType.ENEMY));
            for (int i = 0; i < particleTarget; i++)
                particles.add(new Particle(30 + i % 90 * 10, 210 + i / 90 * 14,
                        (i & 1) == 0 ? Color.CYAN : Color.ORANGE, (i % 3 - 1) * .3, 0, .02f));
            fillStep++;
        }
        Timing frame() throws Exception {
            prepare();
            Object before = LATEST.get(mailbox);
            long begin = System.nanoTime();
            advance();
            long simulation = System.nanoTime() - begin;
            boolean published = before != LATEST.get(mailbox);
            long[] edt = new long[2];
            long queued = System.nanoTime();
            SwingUtilities.invokeAndWait(() -> {
                long paintStart = System.nanoTime(); edt[0] = paintStart - queued;
                Graphics2D graphics = physical.createGraphics();
                try { panel.paint(graphics); } finally { graphics.dispose(); }
                edt[1] = System.nanoTime() - paintStart;
            });
            if (get(panel, "state") != GameState.RUNNING) throw new IllegalStateException("Benchmark left RUNNING state");
            return new Timing(simulation, edt[0], edt[1], published, manager.getEnemies().size(),
                    projectiles.size(), manager.getEnemyBullets().size(), particles.size());
        }
        public void close() {
            panel.dispose(); physical.flush();
            try {
                if (!mailbox.isClosed() || (int) BUFFER_COUNT.invoke(mailbox) != 0)
                    throw new IllegalStateException("FrameMailbox retained raster buffers after close");
            } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
        }
    }
    private static Field field(Class<?> type, String name) {
        try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field; }
        catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
    }
    private static Object get(Object owner, String name) throws IllegalAccessException { return field(owner.getClass(), name).get(owner); }
    private static int limit(String name) throws ReflectiveOperationException {
        return com.bigphil.mergehell.engine.EntityLimits.class.getField(name).getInt(null);
    }
    private static Memory memory() {
        var bean = ManagementFactory.getMemoryMXBean();
        long gcCount = 0, gcMillis = 0;
        for (var gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            gcCount += Math.max(0, gc.getCollectionCount()); gcMillis += Math.max(0, gc.getCollectionTime());
        }
        return new Memory(bean.getHeapMemoryUsage().getUsed(), bean.getHeapMemoryUsage().getCommitted(),
                bean.getNonHeapMemoryUsage().getUsed(), ManagementFactory.getThreadMXBean().getThreadCount(), gcCount, gcMillis);
    }
    private static Distribution distribution(List<Timing> timings, int selector) {
        long[] values = timings.stream().mapToLong(t -> selector == 0 ? t.simulation() : selector == 1 ? t.dispatch() : t.paint()).sorted().toArray();
        return new Distribution(values[(int) Math.ceil(values.length * .5) - 1] / 1e6,
                values[(int) Math.ceil(values.length * .95) - 1] / 1e6, values[values.length - 1] / 1e6);
    }
    public static void main(String[] args) throws Exception {
        if (!java.awt.GraphicsEnvironment.isHeadless()) throw new IllegalStateException("Requires headless JVM");
        if (com.intellij.openapi.application.ApplicationManager.getApplication() != null) throw new IllegalStateException("Must not run in user's IDE application");
        Path output = Path.of(args[0]); Files.createDirectories(output);
        SwingUtilities.invokeAndWait(() -> { });
        System.gc(); Thread.sleep(60);
        Memory baseline = memory();
        List<String> summary = new ArrayList<>();
        List<String> csv = new ArrayList<>();
        summary.add("headless=true java=" + System.getProperty("java.version") + " max_heap_bytes=" + Runtime.getRuntime().maxMemory());
        summary.add("GamePanel=" + GamePanel.class.getProtectionDomain().getCodeSource().getLocation());
        summary.add("warmup_ms=" + WARMUP_NANOS / 1_000_000 + " sample_window_ms=" + SAMPLE_NANOS / 1_000_000
                + " per_case; synthetic load fill excluded; actual GameLoop -> GamePanel -> FrameMailbox publish and EDT panel.paint; unpaced invocation timings, not FPS");
        summary.add("baseline=" + baseline);
        csv.add("case,width,height,draw_width,draw_height,index,simulation_publish_ms,edt_dispatch_ms,edt_paint_ms,published,enemies,player_bullets,enemy_bullets,particles");
        int[][] sizes = {{960,600},{600,400},{1280,800}};
        long wallStart = System.nanoTime();
        for (int world : new int[]{1,3}) for (boolean high : new boolean[]{false,true}) for (int[] size : sizes) {
            String name = "world" + (world + 1) + "_" + (high ? "high" : "normal") + "_" + size[0];
            long setupStart = System.nanoTime();
            try (Harness harness = new Harness(size[0], size[1], high, world)) {
                double setupMillis = (System.nanoTime() - setupStart) / 1e6;
                long start = System.nanoTime();
                do { harness.frame(); } while (System.nanoTime() - start < WARMUP_NANOS);
                Memory before = memory();
                List<Timing> timings = new ArrayList<>();
                start = System.nanoTime();
                do { timings.add(harness.frame()); } while (System.nanoTime() - start < SAMPLE_NANOS);
                Memory after = memory();
                if (timings.size() < 20) throw new IllegalStateException("Insufficient samples: " + name);
                int index = 0;
                for (Timing t : timings) csv.add(String.format(Locale.ROOT, "%s,%d,%d,%d,%d,%d,%.6f,%.6f,%.6f,%s,%d,%d,%d,%d", name, size[0], size[1], harness.drawWidth,harness.drawHeight,index++,
                        t.simulation()/1e6, t.dispatch()/1e6, t.paint()/1e6, t.published(),t.enemies(),t.projectiles(),t.enemyProjectiles(),t.particles()));
                long published = timings.stream().filter(Timing::published).count();
                summary.add(name + " physical=" + size[0] + "x" + size[1] + " draw=" + harness.drawWidth + "x" + harness.drawHeight
                        + " setup_ms=" + setupMillis + " samples=" + timings.size() + " simulation_publish_ms=" + distribution(timings,0)
                        + " edt_dispatch_ms=" + distribution(timings,1) + " edt_paint_ms=" + distribution(timings,2)
                        + " published=" + published + "/" + timings.size() + " before=" + before + " after=" + after);
                System.out.println(summary.get(summary.size()-1));
            }
        }
        SwingUtilities.invokeAndWait(() -> { });
        Memory afterClose = memory();
        System.gc(); Thread.sleep(100);
        Memory afterGc = memory();
        summary.add("after_close=" + afterClose);
        summary.add("after_explicit_gc=" + afterGc);
        summary.add("total_wall_seconds=" + (System.nanoTime() - wallStart)/1e9);
        summary.add("threads=" + Thread.getAllStackTraces().keySet().stream().map(Thread::getName).sorted().toList());
        summary.add("all_twelve_mailboxes_closed_with_zero_retained_buffers=true");
        Files.write(output.resolve("samples.csv"), csv);
        Files.write(output.resolve("summary.txt"), summary);
        System.out.println(summary.get(summary.size()-3));
    }
}
