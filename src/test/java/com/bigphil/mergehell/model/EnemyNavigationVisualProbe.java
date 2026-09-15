package com.bigphil.mergehell.model;

import com.bigphil.mergehell.render.*;
import com.bigphil.mergehell.world.ExplorationRoute;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Reproducible production-model keyframes. No position changes occur after the initial fixture spawn. */
public final class EnemyNavigationVisualProbe {
    private record Scenario(String name, int chapter, EntityType type, List<ExplorationRoute.Block> blocks, int spawnY) { }
    private record Frame(int tick, ActorVisuals.Hostile pose, int shots) { }
    private record Result(Scenario scenario, List<Frame> frames, int crossedAt, int shotAfterCrossing, double p95Micros, double maxMicros) { }
    private static final int GROUND = 480, CELL_WIDTH = 430, CELL_HEIGHT = 340;

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args.length == 0 ? "build/polish-nav/visual" : args[0]);
        Files.createDirectories(output);
        List<Scenario> scenarios = new ArrayList<>();
        scenarios.add(authored("1 / street wall / conflict", 0, EntityType.CONFLICT, 940));
        scenarios.add(authored("2 / low maintenance roof / heavy", 1, EntityType.TECHDEBT, 8680));
        scenarios.add(authored("3 / high gantry / warden", 2, EntityType.WARDEN, 8070));
        scenarios.add(authored("4 / foundry wall / slag spitter", 3, EntityType.SLAG_SPITTER, 8980));
        scenarios.add(authored("5 / chitin wall / mirror", 4, EntityType.MIRROR, 9080));
        scenarios.add(new Scenario("3 / hanging beam / flying rigger", 2, EntityType.RIGGER,
                List.of(block(400, 150, 170, 170, 2, true)), 310));
        scenarios.add(new Scenario("4 / joined foundations / driller", 3, EntityType.DRILLER,
                List.of(block(390, 355, 95, 125, 3, false), block(490, 305, 100, 175, 3, false)), 444));
        List<Result> results = new ArrayList<>();
        for (Scenario scenario : scenarios) results.add(simulate(scenario));
        IndustrialArt art = IndustrialArt.load(); ChapterArt.preload();
        BufferedImage sheet = new BufferedImage(CELL_WIDTH * 4, CELL_HEIGHT * results.size(), BufferedImage.TYPE_INT_RGB);
        Graphics2D sheetGraphics = sheet.createGraphics();
        for (int row = 0; row < results.size(); row++) {
            Result result = results.get(row);
            int finalIndex = result.crossedAt;
            int[] ticks = {0, finalIndex / 3, finalIndex * 2 / 3, finalIndex};
            BufferedImage strip = new BufferedImage(CELL_WIDTH * 4, CELL_HEIGHT, BufferedImage.TYPE_INT_RGB);
            Graphics2D stripGraphics = strip.createGraphics();
            for (int col = 0; col < ticks.length; col++) {
                BufferedImage cell = paint(art, result.scenario, result.frames.get(ticks[col]));
                stripGraphics.drawImage(cell, col * CELL_WIDTH, 0, null);
                sheetGraphics.drawImage(cell, col * CELL_WIDTH, row * CELL_HEIGHT, null);
            }
            stripGraphics.dispose(); ImageIO.write(strip, "png", output.resolve("route-" + (row + 1) + ".png").toFile());
        }
        sheetGraphics.dispose(); ImageIO.write(sheet, "png", output.resolve("navigation-keyframes.png").toFile());
        List<String> csv = new ArrayList<>();
        csv.add("scenario,type,cross_ticks,post_cross_shot_ticks,model_tick_p95_us,model_tick_max_us");
        for (Result result : results) csv.add(String.format(java.util.Locale.ROOT, "%s,%s,%d,%d,%.3f,%.3f",
                result.scenario.name, result.scenario.type, result.crossedAt, result.shotAfterCrossing, result.p95Micros, result.maxMicros));
        Files.write(output.resolve("results.csv"), csv);
        Files.writeString(output.resolve("stress.txt"), stress());
        System.out.println(String.join(System.lineSeparator(), csv));
    }

    private static Scenario authored(String name, int chapter, EntityType type, int x) {
        var block = new ExplorationRoute(chapter).snapshot().blocks().stream().filter(b -> b.bounds().x == x).findFirst().orElseThrow();
        return new Scenario(name, chapter, type, List.of(block), GROUND - type.height);
    }
    private static ExplorationRoute.Block block(int x, int y, int w, int h, int chapter, boolean canopy) {
        return new ExplorationRoute.Block(new Rectangle(x, y, w, h), ExplorationRoute.Material.values()[chapter], canopy);
    }

    private static Result simulate(Scenario scenario) {
        List<Rectangle> solids = scenario.blocks.stream().map(ExplorationRoute.Block::bounds).toList();
        int left = solids.stream().mapToInt(b -> b.x).min().orElseThrow();
        int right = solids.stream().mapToInt(b -> b.x + b.width).max().orElseThrow();
        double targetX = left - 155;
        var manager = new ObstacleManager(2026091501L + scenario.chapter);
        manager.setSolids(solids);
        manager.spawnEnemy(right + 70, scenario.spawnY, scenario.type);
        var enemy = manager.getEnemies().get(0);
        var player = new Player((int)targetX, 450);
        var actors = new ActorVisuals();
        List<Projectile> shots = new ArrayList<>();
        List<Frame> frames = new ArrayList<>();
        List<Long> durations = new ArrayList<>();
        int crossed = -1, shotAt = -1, shotCount = 0;
        actors.update(player, manager.getEnemies(), .016);
        frames.add(new Frame(0, actors.snapshot().enemies().get(0), 0));
        for (int tick = 1; tick <= 1000; tick++) {
            long begin = System.nanoTime();
            double beforeX = enemy.getX();
            enemy.update(1, targetX, 440, left - 300, right + 400, GROUND);
            manager.resolveSolidMotion(enemy, beforeX);
            int previousShots = shots.size();
            enemy.maybeShoot(440, shots);
            if (shots.size() > previousShots) {
                shotCount += shots.size() - previousShots;
                if (crossed >= 0 && shotAt < 0) shotAt = tick - crossed;
            }
            durations.add(System.nanoTime() - begin);
            boolean buried = enemy.getTactics().mode() == ObstacleManager.Enemy.Mode.BURROWED;
            if (!buried) for (Rectangle solid : solids)
                if (enemy.getBounds().intersects(solid)) throw new AssertionError(scenario.name + " clips " + solid + " at " + tick);
            actors.update(player, manager.getEnemies(), .016);
            frames.add(new Frame(tick, actors.snapshot().enemies().get(0), shotCount));
            if (crossed < 0 && enemy.getX() + scenario.type.width <= left
                    && (scenario.type == EntityType.RIGGER || Math.abs(enemy.getY() + scenario.type.height - GROUND) < 2)) crossed = tick;
            if (crossed >= 0 && tick - crossed >= 250) break;
        }
        if (crossed < 0) throw new AssertionError(scenario.name + " never crossed (" + enemy.getX() + "," + enemy.getY() + ")");
        boolean shoots = switch (scenario.type) { case CONFLICT, TECHDEBT, SLAG_SPITTER, MIRROR, RIGGER -> true; default -> false; };
        if (shoots && shotAt < 0) throw new AssertionError(scenario.name + " never resumed firing");
        durations.sort(Comparator.naturalOrder());
        return new Result(scenario, frames, crossed, shotAt, durations.get((int)(durations.size() * .95)) / 1000.0,
                durations.get(durations.size() - 1) / 1000.0);
    }

    private static BufferedImage paint(IndustrialArt art, Scenario scenario, Frame frame) {
        BufferedImage image = new BufferedImage(CELL_WIDTH, CELL_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(12, 18, 24)); g.fillRect(0, 0, CELL_WIDTH, CELL_HEIGHT);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13)); g.setColor(new Color(239, 222, 190));
        g.drawString(scenario.name, 10, 20);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12)); g.setColor(new Color(153, 188, 191));
        g.drawString("tick " + frame.tick + " | " + frame.pose.tactics().mode() + " | shots " + frame.shots, 10, 39);
        double center = scenario.blocks.stream().mapToDouble(b -> b.bounds().getCenterX()).average().orElseThrow();
        double camera = center - 270;
        Graphics2D scene = (Graphics2D)g.create();
        scene.clipRect(0, 48, CELL_WIDTH, CELL_HEIGHT - 48);
        scene.translate(0, 48); scene.scale(CELL_WIDTH / 540.0, (CELL_HEIGHT - 48) / 367.0); scene.translate(0, -140);
        if (scenario.chapter >= 2) ChapterArt.load().backdrop(scene, 540, 600, camera, scenario.chapter);
        else art.backdrop(scene, 540, 600, camera, scenario.chapter);
        scene.translate(-camera, 0);
        var route = new ExplorationRoute.Snapshot(scenario.chapter, scenario.blocks, List.of(), List.of(), Set.of(), "", false);
        ExplorationRenderer.world(scene, art, route, camera, 540);
        if (scenario.chapter >= 2) {
            ChapterArt.load().terrain(scene, scenario.chapter, "deck", camera, GROUND - 3, 540, 75);
        } else {
            scene.setColor(new Color(38, 39, 36)); scene.fillRect((int)camera, GROUND, 541, 75);
            scene.setColor(new Color(137, 122, 91)); scene.drawLine((int)camera, GROUND, (int)camera + 540, GROUND);
        }
        if (!ChapterActorRenderer.enemy(scene, frame.pose, frame.tick * .016, true)
                && !HeapActorRenderer.renderLeak(scene, frame.pose)) ActorVisuals.enemy(scene, art, frame.pose);
        scene.dispose(); g.dispose(); return image;
    }

    private static String stress() {
        List<Long> times = new ArrayList<>();
        for (int pass = 0; pass < 12; pass++) {
            var manager = new ObstacleManager(51);
            manager.setSolids(List.of(new Rectangle(400, 300, 150, 180), new Rectangle(620, 320, 120, 122)));
            for (int i = 0; i < 32; i++) manager.spawnEnemy(760 + i % 4 * 8, 422, EntityType.SENTINEL);
            for (int tick = 0; tick < 420; tick++) {
                long begin = System.nanoTime();
                for (var enemy : manager.getEnemies()) {
                    double before = enemy.getX();
                    enemy.update(1, 200, 440, 0, 1100, GROUND);
                    manager.resolveSolidMotion(enemy, before);
                }
                if (pass >= 2) times.add(System.nanoTime() - begin);
            }
        }
        times.sort(Comparator.naturalOrder());
        return String.format(java.util.Locale.ROOT,
                "32 simultaneous enemies; two solid obstacles; 2 warmup + 10 measured runs of 420 ticks.\n" +
                        "p50=%.3f ms, p95=%.3f ms, p99=%.3f ms, max=%.3f ms.\n" +
                        "Model/update/navigation only; excludes Swing painting, IDE scheduling and audio.\n",
                times.get(times.size() / 2) / 1e6, times.get((int)(times.size() * .95)) / 1e6,
                times.get((int)(times.size() * .99)) / 1e6, times.get(times.size() - 1) / 1e6);
    }
}
