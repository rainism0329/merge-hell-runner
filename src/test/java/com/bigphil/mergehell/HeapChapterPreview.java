package com.bigphil.mergehell;

import com.bigphil.mergehell.model.*;
import com.bigphil.mergehell.persistence.*;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Chapter fixtures rendered by the actual panel; no replacement HUD or composited screenshots. */
public final class HeapChapterPreview {
    public static void main(String[] args) throws Exception {
        if (!java.awt.GraphicsEnvironment.isHeadless()) throw new IllegalStateException("Use headless mode");
        Path out = Path.of(args.length == 0 ? "build/heap-game-preview" : args[0]); Files.createDirectories(out.resolve("motion"));
        MergeHellState state = new MergeHellState(); state.settings.muted = true;
        MergeHellStateService.getInstance().loadState(state);
        List<String> rows = new ArrayList<>(); rows.add("file,state,world_tick,heap_tick,pressure,boss_hp,exposed");
        try (HeapGameHarness h = new HeapGameHarness()) {
            h.key("LAB_TOGGLE_ALT"); h.tick(); h.place(745, 450);
            h.enemies().spawnEnemy(1000, 345, EntityType.LEAK);
            h.quietTicks(2); h.enemies().spawnEnemy(940, 350, EntityType.LEAK); h.tick();
            both(h, out, "gc-choice", rows);
            h.key("HEAP_SALVAGE"); h.tick(); both(h, out, "salvage-warning", rows);
            h.ticks(61); both(h, out, "leak-active", rows);
            h.place(2585, 450); h.key("HEAP_PURGE"); h.tick(); h.quietTicks(20);
            both(h, out, "gc-channel", rows); h.quietTicks(31); both(h, out, "gc-cleaned", rows);
            h.key("PAUSE_P"); h.tick(); both(h, out, "paused", rows);
        }
        try (HeapGameHarness h = new HeapGameHarness()) {
            h.bossPractice();
            for (int i = 0; i < 430 && h.heap().snapshot().blocks().stream().noneMatch(b -> b.warningTicksRemaining() == 0); i++) h.quietTicks(1);
            both(h, out, "boss-reflux", rows);
            var station = h.heap().snapshot().nodes().stream().filter(n -> n.id() == 101).findFirst().orElseThrow();
            h.place(station.bounds().centerX() - 15, 450); h.key("HEAP_PURGE"); h.tick(); h.quietTicks(49);
            both(h, out, "boss-exposed", rows);
            h.key("BOMB"); h.tick(); both(h, out, "boss-impact", rows);
            h.key("RIGHT");
            for (int i = 0; i < 24; i++) {
                h.tick(); capture(h, out, "motion/frame-%02d.png".formatted(i), 960, 600, rows);
            }
            h.key("RIGHT_R");
            h.key("PAUSE_P"); h.tick(); both(h, out, "boss-paused", rows);
        }
        Files.write(out.resolve("frames.csv"), rows);
        System.out.println((rows.size() - 1) + " real panel Heap frames: " + out.toAbsolutePath());
    }
    private static void both(HeapGameHarness h, Path out, String name, List<String> rows) throws Exception {
        capture(h, out, name + "-960.png", 960, 600, rows);
        capture(h, out, name + "-600.png", 600, 400, rows);
    }
    private static void capture(HeapGameHarness h, Path out, String name, int width, int height,
                                List<String> rows) throws Exception {
        if (h.panel.getWidth() != width || h.panel.getHeight() != height) {
            SwingUtilities.invokeAndWait(() -> h.panel.setSize(width, height));
            SwingUtilities.invokeAndWait(() -> { }); h.tick();
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        SwingUtilities.invokeAndWait(() -> {
            Graphics2D g = image.createGraphics();
            try { h.panel.paint(g); } finally { g.dispose(); }
        });
        ImageIO.write(image, "png", out.resolve(name).toFile()); image.flush();
        rows.add(name + "," + h.state() + "," + h.panel.getSession().worldTick() + "," + h.heap().snapshot().tick()
                + "," + h.heap().snapshot().pressure() + "," + (h.boss() == null ? 0 : h.boss().getHp())
                + "," + (h.boss() != null && h.boss().isVulnerable()));
    }
}
