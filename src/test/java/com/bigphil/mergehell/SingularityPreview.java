package com.bigphil.mergehell;

import com.bigphil.mergehell.i18n.GameLanguage;
import com.bigphil.mergehell.model.EntityType;
import com.bigphil.mergehell.persistence.MergeHellState;
import com.bigphil.mergehell.persistence.MergeHellStateService;
import com.bigphil.mergehell.world.SingularityEdgeController;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Actual fifth-world input/update/paint captures. Fixtures only choose chapter, position, hostiles and boss damage. */
public final class SingularityPreview {
    private final Path out;
    private final List<String> text = new ArrayList<>();
    private final List<String> evidence = new ArrayList<>(List.of(
            "scene,state,tick,platforms,hazard_count,echo,hazard_phase,hazard_ticks,anchor_id,anchor_state,anchor_ticks,armed_count,cooldown_count,boss_warning,predicted_shots,boss_reboot,boss_stage,controller_expose,boss_expose,hp,bombs,score,lab,flashes"));

    private SingularityPreview(Path out) { this.out = out; }
    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "build/singularity-preview"); Files.createDirectories(out);
        var preview = new SingularityPreview(out);
        for (GameLanguage language : GameLanguage.values()) {
            var saved = new MergeHellState(); saved.settings.language = language.tag(); saved.settings.muted = true;
            saved.settings.flashes = language != GameLanguage.ENGLISH;
            MergeHellStateService.getInstance().loadState(saved);
            preview.route(language); preview.boss(language);
        }
        Files.write(out.resolve("displayed-text.txt"), preview.text);
        Files.write(out.resolve("state-evidence.csv"), preview.evidence);
        System.out.println((preview.evidence.size() - 1) + " actual Singularity panel captures: " + out.toAbsolutePath());
    }

    private void route(GameLanguage language) throws Exception {
        try (var h = game()) {
            h.place(885, 450); quiet(h, 2);
            both(h, language, "route", 1, true);
            awaitEcho(h, SingularityEdgeController.HazardPhase.WARNING);
            both(h, language, "memorywarning", 1, false);
            awaitEcho(h, SingularityEdgeController.HazardPhase.ACTIVE);
            both(h, language, "memoryactive", 1, false);
            h.key("HEAP_PURGE"); quiet(h, 1);
            require(anchor(h, 1).state() == SingularityEdgeController.AnchorState.STABILIZED, "Actual E must stabilize the echo");
            require(h.context().score == 250, "The first stabilization must award its route reward");
            both(h, language, "stabilized", 1, false);
        }
        for (int id : new int[] {2, 3}) {
            try (var h = game()) {
                h.place((id == 2 ? 3000 : 5000) - 15, 450); quiet(h, 2);
                awaitEcho(h, SingularityEdgeController.HazardPhase.ACTIVE);
                both(h, language, id == 2 ? "blueprintactive" : "kernelactive", id, false);
            }
        }
    }

    private void boss(GameLanguage language) throws Exception {
        try (var h = game()) {
            h.panel.getLevelManager().advanceToBossGateForTesting();
            h.place(h.panel.getLevelManager().getBossGateX(), 450);
            require(h.state() == GameState.BOSS_WARNING, "The actual gate must enter boss preparation");
            quiet(h, 85); both(h, language, "arrival", 1, false);
            for (int tick = 0; tick < 220 && h.state() != GameState.BOSS_FIGHT; tick++) quiet(h, 1);
            require(h.state() == GameState.BOSS_FIGHT, "Preparation must finish naturally"); quiet(h, 2);
            h.place(anchor(h, 101).bounds().centerX() - 15, 450); h.key("HEAP_PURGE"); quiet(h, 1);
            require(anchor(h, 101).state() == SingularityEdgeController.AnchorState.ARMED, "E must arm the first actual anchor");
            both(h, language, "armed", 101, false);
            for (int tick = 0; tick < 300 && h.boss().getSingularityPredictedShots().isEmpty(); tick++) quiet(h, 1);
            require(!h.boss().getSingularityPredictedShots().isEmpty(), "The real boss must announce a locked volley");
            both(h, language, "volleywarning", 101, false);
            h.place(anchor(h, 102).bounds().centerX() - 15, 450); h.key("HEAP_PURGE"); quiet(h, 1);
            require(h.boss().isVulnerable(), "Two different real E presses must interrupt the boss");
            both(h, language, "exposed", 102, false);
            double camera = (double) h.get("cameraX"); h.place(camera + 130, 450);
            for (int tick = 0; tick < 180 && h.boss().isVulnerable(); tick++) quiet(h, 1);
            require(!h.boss().isVulnerable() && controller(h).snapshot().bossExposeTicks() == 0
                    && anchor(h, 101).state() == SingularityEdgeController.AnchorState.COOLDOWN,
                    "The completed core window must leave both actual anchors cooling down");
            both(h, language, "cooldown", 101, false);
            // Public damage takes the same phase boundary as combat; phase and reboot state are never injected.
            h.boss().damage(h.boss().getMaxHp() * 2 / 5);
            for (int tick = 0; tick < 30 && h.boss().getSingularityRebootTicks() == 0; tick++) quiet(h, 1);
            require(h.boss().getCombatStage() == 2 && h.boss().getSingularityRebootTicks() > 0,
                    "Damage must trigger the real stage-two reset");
            both(h, language, "phase2", 101, false);
            h.boss().damage(h.boss().getMaxHp());
            for (int tick = 0; tick < 300 && h.state() != GameState.MISSION_COMPLETE; tick++) quiet(h, 1);
            require(h.state() == GameState.MISSION_COMPLETE, "The real boss death must settle the final chapter");
            h.key("START"); h.tick();
            require(h.state() == GameState.VICTORY, "The final settlement must reach victory");
            both(h, language, "victory", 0, false);
        }
    }

    private void both(HeapGameHarness h, GameLanguage language, String scene, int anchorId, boolean actor) throws Exception {
        for (int width : new int[] {960, 600}) {
            int height = width == 960 ? 600 : 400;
            SwingUtilities.invokeAndWait(() -> h.panel.setSize(width, height));
            SwingUtilities.invokeAndWait(() -> { }); quiet(h, 1);
            if (actor) { h.enemies().spawnEnemy((int) h.player().getX() + 210, 355, EntityType.MIRROR); h.tick(); }
            var capture = GamePanelLanguageTest.frame(h);
            String name = language.tag() + "-singularity-" + scene + "-" + width;
            text.add("=== " + name + " ==="); text.addAll(capture.lines());
            var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            SwingUtilities.invokeAndWait(() -> {
                var graphics = image.createGraphics();
                try { h.panel.paint(graphics); } finally { graphics.dispose(); }
            });
            ImageIO.write(image, "png", out.resolve(name + ".png").toFile()); image.flush(); capture.image().flush();
            var snapshot = controller(h).snapshot();
            var node = snapshot.anchors().stream().filter(item -> item.id() == anchorId).findFirst().orElse(null);
            var field = snapshot.hazards().stream().findFirst().orElse(null);
            evidence.add(String.format(Locale.ROOT, "%s,%s,%d,%d,%d,%s,%s,%d,%d,%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%s,%s",
                    name, h.state(), snapshot.tick(), controller(h).platforms().size(), snapshot.hazards().size(),
                    field == null ? "NONE" : field.echo(), field == null ? "NONE" : field.phase(), field == null ? 0 : field.ticksRemaining(),
                    node == null ? 0 : node.id(), node == null ? "NONE" : node.state(), node == null ? 0 : node.ticksRemaining(),
                    snapshot.anchors().stream().filter(item -> item.state() == SingularityEdgeController.AnchorState.ARMED).count(),
                    snapshot.anchors().stream().filter(item -> item.state() == SingularityEdgeController.AnchorState.COOLDOWN).count(),
                    h.boss() == null ? 0 : h.boss().getSingularityWarningTicks(),
                    h.boss() == null ? 0 : h.boss().getSingularityPredictedShots().size(),
                    h.boss() == null ? 0 : h.boss().getSingularityRebootTicks(),
                    h.boss() == null ? 0 : h.boss().getCombatStage(), snapshot.bossExposeTicks(),
                    h.boss() == null ? 0 : h.boss().getVulnerabilityTicks(), h.player().getHp(), h.player().getBombs(), h.context().score,
                    h.player().isDebugMode(), language != GameLanguage.ENGLISH));
            require(h.player().getHp() == 100 && !h.player().isDebugMode(), "Safe preview positions must stay unharmed without Lab");
        }
    }

    private static HeapGameHarness game() throws Exception {
        var h = new HeapGameHarness(); h.set("level", 4); h.invoke("advanceLevel"); quiet(h, 2); return h;
    }
    private static SingularityEdgeController controller(HeapGameHarness h) throws Exception { return (SingularityEdgeController) h.get("singularity"); }
    private static SingularityEdgeController.AnchorView anchor(HeapGameHarness h, int id) throws Exception {
        return controller(h).snapshot().anchors().stream().filter(node -> node.id() == id).findFirst().orElseThrow();
    }
    private static void awaitEcho(HeapGameHarness h, SingularityEdgeController.HazardPhase phase) throws Exception {
        for (int tick = 0; tick < 700; tick++) {
            if (controller(h).snapshot().hazards().stream().anyMatch(field -> field.phase() == phase)) return;
            quiet(h, 1);
        }
        throw new IllegalStateException("The actual echo never entered " + phase);
    }
    private static void quiet(HeapGameHarness h, int ticks) throws Exception {
        for (int tick = 0; tick < ticks; tick++) { h.enemies().clearHostiles(); h.enemies().getEnemyBullets().clear(); h.tick(); }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
