package com.bigphil.mergehell;

import com.bigphil.mergehell.i18n.*;
import com.bigphil.mergehell.persistence.*;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;

/** Real panel captures with explicit chapter/pose fixtures, plus final displayed text for language QA. */
public final class LanguagePreview {
    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length == 0 ? "build/language-preview" : args[0]); Files.createDirectories(out);
        List<String> transcript = new ArrayList<>();
        for (GameLanguage language : GameLanguage.values()) {
            MergeHellState stored = new MergeHellState(); stored.settings.language = language.tag(); stored.settings.muted = true;
            MergeHellStateService.getInstance().loadState(stored);
            try (HeapGameHarness h = new HeapGameHarness()) {
                h.set("state", GameState.MENU); h.set("level", 0);
                both(h, out, language, "menu", transcript);
                h.key("SETTINGS"); h.tick(); h.key("MENU_UP"); h.tick();
                both(h, out, language, "settings", transcript);
                for (int row = 0; row < 4; row++) { h.key("MENU_UP"); h.tick(); }
                both(h, out, language, "settings-background", transcript);
                h.key("MENU_DOWN"); h.tick();
                both(h, out, language, "settings-mute", transcript);
                h.key("PAUSE_ESC"); h.tick(); h.set("state", GameState.RUNNING); h.set("level", 1);
                h.key("LAB_TOGGLE_ALT"); h.tick(); h.place(745, 450);
                both(h, out, language, "heap-choice", transcript);
                h.key("HEAP_SALVAGE"); h.tick(); h.ticks(61);
                both(h, out, language, "heap-leak", transcript);
                h.key("LAB_UPGRADE_ALT"); h.tick();
                both(h, out, language, "upgrade", transcript);
            }
            try (HeapGameHarness h = new HeapGameHarness()) {
                h.bossPractice();
                var station = h.heap().snapshot().nodes().stream().filter(n -> n.id() == 101).findFirst().orElseThrow();
                h.place(station.bounds().centerX() - 15, 450); h.key("HEAP_PURGE"); h.tick(); h.quietTicks(49);
                h.key("BOMB"); h.tick(); both(h, out, language, "boss-impact", transcript);
                h.key("PAUSE_P"); h.tick(); both(h, out, language, "pause", transcript);
                h.set("state", GameState.MISSION_COMPLETE); h.set("missionClearBonus", 1250);
                h.set("missionFirstClearReward", true); both(h, out, language, "complete", transcript);
            }
            try (HeapGameHarness h = new HeapGameHarness()) {
                h.set("level", 0); h.invoke("advanceLevel"); h.tick();
                h.key("LAB_TOGGLE_ALT"); h.tick();
                both(h, out, language, "repository", transcript);
                h.key("LAB_BOSS_ALT");
                for (int tick = 0; tick < 230 && h.state() != GameState.BOSS_FIGHT; tick++) h.tick();
                if (h.state() != GameState.BOSS_FIGHT) throw new IllegalStateException("Legacy fixture failed");
                both(h, out, language, "legacy", transcript);
                h.set("state", GameState.GAME_OVER); both(h, out, language, "game-over", transcript);
                for (int world = 2; world < 5; world++) {
                    h.set("level", world); h.invoke("advanceLevel"); h.tick();
                    if (!h.player().isDebugMode()) { h.key("LAB_TOGGLE_ALT"); h.tick(); }
                    h.key("LAB_BOSS_ALT");
                    for (int tick = 0; tick < 230 && h.state() != GameState.BOSS_FIGHT; tick++) h.tick();
                    if (h.state() != GameState.BOSS_FIGHT) throw new IllegalStateException("Boss fixture failed: " + world);
                    h.quietTicks(10);
                    both(h, out, language, "world-" + (world + 1) + "-boss", transcript);
                }
                var failure = GamePanel.class.getDeclaredMethod("handleLoopError", Throwable.class);
                failure.setAccessible(true); failure.invoke(h.panel, new IllegalStateException("Explicit language QA fixture"));
                both(h, out, language, "error", transcript);
            }
        }
        Files.write(out.resolve("displayed-text.txt"), transcript);
        long captures = transcript.stream().filter(line -> line.startsWith("=== ")).count();
        System.out.println(captures + " bilingual panel captures and final drawn text: " + out.toAbsolutePath());
    }
    private static void both(HeapGameHarness h, Path out, GameLanguage language, String scene, List<String> text) throws Exception {
        for (int width : new int[]{960, 600}) {
            int height = width == 960 ? 600 : 400;
            SwingUtilities.invokeAndWait(() -> h.panel.setSize(width, height));
            SwingUtilities.invokeAndWait(() -> { });
            if (h.state() != GameState.ERROR) h.tick();
            var captured = GamePanelLanguageTest.frame(h);
            String name = language.tag() + "-" + scene + "-" + width;
            text.add("=== " + name + " ==="); text.addAll(captured.lines());
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            SwingUtilities.invokeAndWait(() -> {
                Graphics2D g = image.createGraphics();
                try { h.panel.paint(g); } finally { g.dispose(); }
            });
            ImageIO.write(image, "png", out.resolve(name + ".png").toFile());
            image.flush(); captured.image().flush();
        }
    }
}
