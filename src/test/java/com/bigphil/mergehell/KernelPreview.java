package com.bigphil.mergehell;

import com.bigphil.mergehell.i18n.GameLanguage;
import com.bigphil.mergehell.model.EntityType;
import com.bigphil.mergehell.persistence.MergeHellState;
import com.bigphil.mergehell.persistence.MergeHellStateService;
import com.bigphil.mergehell.world.KernelCoreController;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Real fourth-world input/update/paint captures; no fixture injects rail phases, boss charges or exposure. */
public final class KernelPreview {
    private final Path out;
    private final List<String> text = new ArrayList<>();
    private final List<String> evidence = new ArrayList<>(List.of(
            "scene,state,tick,platforms,rail_count,rail_phase,rail_ticks,active_rails,station_id,station_state,station_ticks,boss_warning,boss_dash,controller_expose,boss_expose,hp,bombs,score"));

    private KernelPreview(Path out) { this.out = out; }
    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "build/kernel-preview"); Files.createDirectories(out);
        var preview = new KernelPreview(out);
        for (GameLanguage language : GameLanguage.values()) {
            var saved = new MergeHellState(); saved.settings.language = language.tag(); saved.settings.muted = true;
            MergeHellStateService.getInstance().loadState(saved);
            preview.route(language); preview.boss(language);
        }
        Files.write(out.resolve("displayed-text.txt"), preview.text);
        Files.write(out.resolve("state-evidence.csv"), preview.evidence);
        System.out.println((preview.evidence.size() - 1) + " actual Kernel panel captures: " + out.toAbsolutePath());
    }

    private void route(GameLanguage language) throws Exception {
        try (var h = game()) {
            h.place(705, 450); quiet(h, 2);
            both(h, language, "kernel-route", true);
            awaitRail(h, KernelCoreController.RailPhase.WARNING);
            both(h, language, "kernel-warning", false);
            awaitRail(h, KernelCoreController.RailPhase.ACTIVE);
            both(h, language, "kernel-active", false);
            require(h.player().getHp() == 100, "The full switch interaction zone must remain safe during live current");
            h.key("HEAP_PURGE"); quiet(h, 1);
            require(station(h).state() == KernelCoreController.NodeState.DISABLED, "Real E input must disable the station");
            require(h.context().score == 250, "The first power cut must award its real route reward");
            both(h, language, "kernel-poweroff", false);
            h.key("PAUSE_P"); h.tick();
            both(h, language, "kernel-pause", false);
        }
    }

    private void boss(GameLanguage language) throws Exception {
        try (var h = game()) {
            h.panel.getLevelManager().advanceToBossGateForTesting();
            h.place(h.panel.getLevelManager().getBossGateX(), 450);
            require(h.state() == GameState.BOSS_WARNING, "The real gate must enter boss preparation");
            quiet(h, 85); both(h, language, "kernel-arrival", false);
            for (int tick = 0; tick < 220 && h.state() != GameState.BOSS_FIGHT; tick++) quiet(h, 1);
            require(h.state() == GameState.BOSS_FIGHT, "Boss preparation must complete naturally");
            quiet(h, 2);
            var node = station(h);
            h.place(node.bounds().centerX() - 15, 450); h.key("HEAP_PURGE"); quiet(h, 1);
            require(station(h).state() == KernelCoreController.NodeState.ARMED, "E must arm the real arena fault");
            both(h, language, "kernel-armed", false);
            double camera = (double) h.get("cameraX");
            h.place(camera + 130, 450);
            for (int tick = 0; tick < 420 && h.boss().getWarningTicks() == 0; tick++) quiet(h, 1);
            require(h.boss().getWarningTicks() > 0, "A real boss update must announce the charge");
            both(h, language, "kernel-chargewarning", false);
            for (int tick = 0; tick < 100 && !h.boss().isVulnerable(); tick++) quiet(h, 1);
            require(station(h).state() == KernelCoreController.NodeState.COOLDOWN && h.boss().isVulnerable(),
                    "A natural dash must hit the armed fault and open the actual boss core");
            both(h, language, "kernel-exposed", false);
        }
    }

    private void both(HeapGameHarness h, GameLanguage language, String scene, boolean interrupt) throws Exception {
        for (int width : new int[] {960, 600}) {
            int height = width == 960 ? 600 : 400;
            SwingUtilities.invokeAndWait(() -> h.panel.setSize(width, height));
            SwingUtilities.invokeAndWait(() -> { }); quiet(h, 1);
            if (interrupt) { h.enemies().spawnEnemy((int) h.player().getX() + 210, 360, EntityType.INTERRUPT); h.tick(); }
            var capture = GamePanelLanguageTest.frame(h);
            String name = language.tag() + "-" + scene + "-" + width;
            text.add("=== " + name + " ==="); text.addAll(capture.lines());
            var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            SwingUtilities.invokeAndWait(() -> {
                var graphics = image.createGraphics();
                try { h.panel.paint(graphics); } finally { graphics.dispose(); }
            });
            ImageIO.write(image, "png", out.resolve(name + ".png").toFile()); image.flush(); capture.image().flush();
            var snapshot = controller(h).snapshot(); var node = station(h);
            var rail = snapshot.rails().stream().filter(item -> item.stationId() == node.id()).findFirst().orElse(null);
            evidence.add(String.format(Locale.ROOT, "%s,%s,%d,%d,%d,%s,%d,%d,%d,%s,%d,%d,%s,%d,%d,%d,%d,%d",
                    name, h.state(), snapshot.tick(), controller(h).platforms().size(), snapshot.rails().size(),
                    rail == null ? "NONE" : rail.phase(), rail == null ? 0 : rail.ticksRemaining(),
                    snapshot.rails().stream().filter(item -> item.phase() == KernelCoreController.RailPhase.ACTIVE).count(),
                    node.id(), node.state(), node.ticksRemaining(), h.boss() == null ? 0 : h.boss().getWarningTicks(),
                    h.boss() != null && h.boss().isDashing(), snapshot.bossExposeTicks(),
                    h.boss() == null ? 0 : h.boss().getVulnerabilityTicks(), h.player().getHp(), h.player().getBombs(), h.context().score));
        }
    }

    private static HeapGameHarness game() throws Exception {
        var h = new HeapGameHarness(); h.set("level", 3); h.invoke("advanceLevel"); quiet(h, 2); return h;
    }
    private static KernelCoreController controller(HeapGameHarness h) throws Exception { return (KernelCoreController) h.get("kernel"); }
    private static KernelCoreController.StationView station(HeapGameHarness h) throws Exception {
        var snapshot = controller(h).snapshot(); int id = snapshot.bossActive() ? 102 : 1;
        return snapshot.stations().stream().filter(node -> node.id() == id).findFirst().orElseThrow();
    }
    private static void awaitRail(HeapGameHarness h, KernelCoreController.RailPhase phase) throws Exception {
        for (int tick = 0; tick < 700; tick++) {
            if (controller(h).snapshot().rails().stream().anyMatch(rail -> rail.phase() == phase)) return;
            quiet(h, 1);
        }
        throw new IllegalStateException("The actual rail never entered " + phase);
    }
    private static void quiet(HeapGameHarness h, int ticks) throws Exception {
        for (int tick = 0; tick < ticks; tick++) { h.enemies().clearHostiles(); h.enemies().getEnemyBullets().clear(); h.tick(); }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
