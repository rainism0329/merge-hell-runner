package com.bigphil.mergehell;

import com.bigphil.mergehell.i18n.GameLanguage;
import com.bigphil.mergehell.model.EntityType;
import com.bigphil.mergehell.persistence.MergeHellState;
import com.bigphil.mergehell.persistence.MergeHellStateService;
import com.bigphil.mergehell.world.BlueprintCitadelController;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Actual third-world simulation captures; fixtures choose starting positions, never structural state or VFX. */
public final class BlueprintPreview {
    private final Path out;
    private final List<String> text = new ArrayList<>();
    private final List<String> evidence = new ArrayList<>(List.of("scene,state,tick,platforms,scans,support_hp,support_state,boss_expose,hp,bombs,score"));
    private BlueprintPreview(Path out) { this.out = out; }
    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "build/blueprint-preview"); Files.createDirectories(out);
        var preview = new BlueprintPreview(out);
        for (GameLanguage language : GameLanguage.values()) {
            var saved = new MergeHellState(); saved.settings.language = language.tag(); saved.settings.muted = true;
            MergeHellStateService.getInstance().loadState(saved);
            preview.route(language); preview.boss(language);
        }
        Files.write(out.resolve("displayed-text.txt"), preview.text);
        Files.write(out.resolve("state-evidence.csv"), preview.evidence);
        System.out.println((preview.evidence.size() - 1) + " actual Blueprint panel captures: " + out.toAbsolutePath());
    }
    private void route(GameLanguage language) throws Exception {
        try (var h = game()) {
            h.place(650, 450); quiet(h, 2);
            both(h, language, "blueprint-route", true);
            for (int i = 0; i < 450 && controller(h).snapshot().scans().isEmpty(); i++) quiet(h, 1);
            require(!controller(h).snapshot().scans().isEmpty(), "The scanner must come from real timed updates");
            both(h, language, "blueprint-warning", false);
            h.key("HEAP_PURGE"); quiet(h, 18);
            require(first(h).state() == BlueprintCitadelController.SupportState.OVERLOADING, "E must start the real overload");
            both(h, language, "blueprint-overload", false);
            for (int i = 0; i < 65 && first(h).hp() > 0; i++) quiet(h, 1);
            require(first(h).state() == BlueprintCitadelController.SupportState.COLLAPSING, "The real overload starts a delayed collapse");
            both(h, language, "blueprint-collapse", false);
            quiet(h, BlueprintCitadelController.COLLAPSE_TICKS);
            require(first(h).state() == BlueprintCitadelController.SupportState.DISABLED, "A route support stays disabled");
            both(h, language, "blueprint-cleared", false);
            h.key("PAUSE_P"); h.tick(); both(h, language, "blueprint-pause", false);
        }
    }
    private void boss(GameLanguage language) throws Exception {
        try (var h = game()) {
            h.panel.getLevelManager().advanceToBossGateForTesting();
            h.place(h.panel.getLevelManager().getBossGateX(), 450);
            require(h.state() == GameState.BOSS_WARNING, "The actual gate enters preparation");
            quiet(h, 85); both(h, language, "blueprint-arrival", false);
            for (int i = 0; i < 210 && h.state() != GameState.BOSS_FIGHT; i++) quiet(h, 1);
            quiet(h, 2);
            var support = first(h);
            h.place(support.bounds().x() - 65, 450); quiet(h, 2);
            both(h, language, "blueprint-boss", false);
            press(h, "SHOOT");
            for (int i = 0; i < 180 && first(h).hp() > 0; i++) quiet(h, 1);
            h.key("SHOOT_R"); quiet(h, 1);
            require(first(h).hp() == 0 && h.boss().isVulnerable(), "Actual shots must break the support and open the real core");
            both(h, language, "blueprint-exposed", false);
        }
    }
    private void both(HeapGameHarness h, GameLanguage language, String scene, boolean sentinel) throws Exception {
        for (int width : new int[]{960, 600}) {
            int height = width == 960 ? 600 : 400;
            SwingUtilities.invokeAndWait(() -> h.panel.setSize(width, height)); SwingUtilities.invokeAndWait(() -> { });
            quiet(h, 1);
            if (sentinel) { h.enemies().spawnEnemy((int) h.player().getX() + 200, 325, EntityType.SENTINEL); h.tick(); }
            var capture = GamePanelLanguageTest.frame(h);
            String name = language.tag() + "-" + scene + "-" + width;
            text.add("=== " + name + " ==="); text.addAll(capture.lines());
            var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            SwingUtilities.invokeAndWait(() -> { var g = image.createGraphics(); try { h.panel.paint(g); } finally { g.dispose(); } });
            ImageIO.write(image, "png", out.resolve(name + ".png").toFile()); image.flush(); capture.image().flush();
            var snapshot = controller(h).snapshot(); var support = first(h);
            evidence.add(String.format(Locale.ROOT, "%s,%s,%d,%d,%d,%d,%s,%d,%d,%d,%d", name, h.state(), snapshot.tick(),
                    controller(h).platforms().size(), snapshot.scans().size(), support.hp(), support.state(),
                    h.boss() == null ? 0 : h.boss().getVulnerabilityTicks(), h.player().getHp(), h.player().getBombs(), h.context().score));
        }
    }
    private static HeapGameHarness game() throws Exception {
        var h = new HeapGameHarness(); h.set("level", 2); h.invoke("advanceLevel"); quiet(h, 2); return h;
    }
    private static BlueprintCitadelController controller(HeapGameHarness h) throws Exception { return (BlueprintCitadelController) h.get("blueprint"); }
    private static BlueprintCitadelController.SupportView first(HeapGameHarness h) throws Exception { return controller(h).snapshot().supports().get(0); }
    private static void quiet(HeapGameHarness h, int count) throws Exception {
        for (int i = 0; i < count; i++) { h.enemies().clearHostiles(); h.enemies().getEnemyBullets().clear(); h.tick(); }
    }
    private static void press(HeapGameHarness h, String name) throws Exception {
        SwingUtilities.invokeAndWait(() -> h.panel.getActionMap().get(name).actionPerformed(new ActionEvent(h.panel, 0, name)));
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
