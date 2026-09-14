package com.bigphil.mergehell;

import com.bigphil.mergehell.i18n.GameLanguage;
import com.bigphil.mergehell.persistence.MergeHellState;
import com.bigphil.mergehell.persistence.MergeHellStateService;
import com.bigphil.mergehell.world.TraversalEnvironment;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Real input/update/paint captures with explicit spawn fixtures; never constructs water particles. */
public final class EnvironmentPreview {
    private final Path output;
    private final List<String> transcript = new ArrayList<>();
    private final List<String> states = new ArrayList<>(List.of(
            "capture,state,world_tick,water_tick,player_x,player_y,feet,grounded,ripples,droplets,props,bombs,hp,right_held,left_held,shoot_held,dashing,boss_arrival_ticks"));

    private EnvironmentPreview(Path output) { this.output = output; }

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args.length == 0 ? "build/environment-preview" : args[0]);
        Files.createDirectories(output);
        EnvironmentPreview preview = new EnvironmentPreview(output);
        for (GameLanguage language : GameLanguage.values()) {
            MergeHellState stored = new MergeHellState();
            stored.settings.language = language.tag(); stored.settings.muted = true;
            MergeHellStateService.getInstance().loadState(stored);
            preview.waterAndProps(language);
            preview.heapRoute(language);
            preview.arrival(language);
        }
        Files.write(output.resolve("displayed-text.txt"), preview.transcript);
        Files.write(output.resolve("state-evidence.csv"), preview.states);
        Files.writeString(output.resolve("README.md"), "# Environment presentation evidence\n\n"
                + "Actual GamePanel input, fixed-step simulation, frame publication and EDT paint at 960×600 and 600×400, in English and Simplified Chinese. Audio remains muted.\n\n"
                + "Fixtures only select a world, initial actor position and starting supply/health values, and clear hostile actors/projectiles so each feature is visible. Walking, dash, jump/landing, shots, prop destruction and Boss arrival all use production inputs and updates. No ripple, droplet, explosion or reward is injected.\n\n"
                + "Each size capture advances one real tick to apply viewport layout; state-evidence.csv records that small difference. displayed-text.txt records final production drawing text. Heap upper-route evidence walks the low steps, then jumps and falls onto the 80px deck. Boss evidence uses the existing practice jump to the gate, disables invincibility, then moves during preparation.\n");
        System.out.println((preview.states.size() - 1) + " real bilingual environment captures: " + output.toAbsolutePath());
    }

    private void waterAndProps(GameLanguage language) throws Exception {
        try (HeapGameHarness h = new HeapGameHarness()) {
            repository(h);
            h.place(320, 450); press(h, "RIGHT"); quiet(h, 15);
            require(!environment(h).snapshot().ripples().isEmpty(), "Walking must create real water contacts");
            both(h, language, "water-walk");
            h.key("RIGHT_R"); quiet(h, 1);
            h.key("DASH"); quiet(h, 3);
            require(h.player().isDashing(), "Dash fixture must still be active");
            both(h, language, "water-dash");
            quiet(h, 5); h.place(410, 450);
            h.key("JUMP"); quiet(h, 1);
            require(!h.player().isGrounded(), "Jump must leave the water surface");
            for (int tick = 0; tick < 70 && !h.player().isGrounded(); tick++) quiet(h, 1);
            require(h.player().isGrounded(), "Jump fixture must land naturally");
            require(environment(h).snapshot().ripples().stream().anyMatch(r -> r.strength() > 2),
                    "Landing must produce the stronger production splash: x=" + h.player().getX()
                            + " feet=" + (h.player().getY() + 30) + " scene=" + environment(h).snapshot());
            quiet(h, 2); both(h, language, "water-landing");

            var capacitor = firstProp(h, TraversalEnvironment.PropKind.CAPACITOR);
            h.place(capacitor.x() - 110, 450); press(h, "RIGHT"); quiet(h, 1); h.key("RIGHT_R"); quiet(h, 1);
            both(h, language, "capacitor-before");
            shootProp(h, capacitor.id()); both(h, language, "capacitor-discharge");

            var supplies = firstProp(h, TraversalEnvironment.PropKind.SUPPLY);
            while (h.player().getBombs() > 1) h.player().useBomb();
            h.player().setInvincibleTimer(0); h.player().setShieldTimer(0); h.player().takeDamage(30); h.player().setInvincibleTimer(0);
            h.place(supplies.x() - 110, 450);
            both(h, language, "supplies-before");
            int beforeBombs = h.player().getBombs(), beforeHp = h.player().getHp();
            shootProp(h, supplies.id());
            require(h.player().getBombs() == beforeBombs + 1 && h.player().getHp() == Math.min(100, beforeHp + 15),
                    "Supply capture must show the actual one-time resource reward");
            both(h, language, "supplies-after");
        }
    }

    private void heapRoute(GameLanguage language) throws Exception {
        try (HeapGameHarness h = new HeapGameHarness()) {
            h.place(935, 450); press(h, "RIGHT");
            for (int tick = 0; tick < 36 && h.player().getX() < 1090; tick++) quiet(h, 1);
            require(h.player().isGrounded() && Math.abs(h.player().getY() - 402) < .01,
                    "Low route must be reached by walking the real 24px steps");
            both(h, language, "heap-low-steps");
            h.key("RIGHT_R"); quiet(h, 1);
            h.key("JUMP"); press(h, "RIGHT"); quiet(h, 28);
            h.key("RIGHT_R"); quiet(h, 1);
            for (int tick = 0; tick < 55 && !h.player().isGrounded(); tick++) quiet(h, 1);
            require(h.player().isGrounded() && Math.abs(h.player().getY() - 370) < .01,
                    "Upper route must be reached by a real jump and downward deck landing");
            both(h, language, "heap-upper-route");
        }
    }

    private void arrival(GameLanguage language) throws Exception {
        try (HeapGameHarness h = new HeapGameHarness()) {
            repository(h); h.key("LAB_TOGGLE_ALT"); quiet(h, 1); h.key("LAB_BOSS_ALT");
            for (int tick = 0; tick < 15 && h.state() != GameState.BOSS_WARNING; tick++) quiet(h, 1);
            require(h.state() == GameState.BOSS_WARNING, "Boss capture must use the real arrival state");
            h.key("LAB_TOGGLE_ALT"); quiet(h, 76);
            double before = h.player().getX(); press(h, "LEFT"); quiet(h, 12); h.key("LEFT_R"); quiet(h, 1);
            require(h.player().getX() < before && !h.player().isDebugMode(), "Preparation must allow safe repositioning");
            both(h, language, "boss-arrival");
        }
    }

    private void both(HeapGameHarness h, GameLanguage language, String scene) throws Exception {
        for (int width : new int[]{960, 600}) {
            int height = width == 960 ? 600 : 400;
            SwingUtilities.invokeAndWait(() -> h.panel.setSize(width, height));
            SwingUtilities.invokeAndWait(() -> { }); quiet(h, 1);
            var capture = GamePanelLanguageTest.frame(h);
            String name = language.tag() + "-" + scene + "-" + width;
            transcript.add("=== " + name + " ==="); transcript.addAll(capture.lines());
            if (language == GameLanguage.ENGLISH)
                require(capture.lines().stream().noneMatch(GamePanelLanguageTest::hasHan), "Chinese text in English scene: " + name);
            BufferedImage frame = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            SwingUtilities.invokeAndWait(() -> {
                Graphics2D g = frame.createGraphics(); try { h.panel.paint(g); } finally { g.dispose(); }
            });
            ImageIO.write(frame, "png", output.resolve(name + ".png").toFile());
            frame.flush(); capture.image().flush();
            var environment = environment(h).snapshot();
            states.add(String.format(Locale.ROOT, "%s,%s,%d,%d,%.2f,%.2f,%.2f,%s,%d,%d,%d,%d,%d,%s,%s,%s,%s,%d", name,
                    h.state(), h.panel.getSession().worldTick(), environment.tick(), h.player().getX(), h.player().getY(),
                    h.player().getY() + h.player().getBounds().height, h.player().isGrounded(),
                    environment.ripples().size(), environment.droplets().size(), environment.props().size(),
                    h.player().getBombs(), h.player().getHp(), h.get("keyRight"), h.get("keyLeft"), h.get("keyShoot"),
                    h.player().isDashing(), h.get("bossWarningTimer")));
        }
    }

    private static void repository(HeapGameHarness h) throws Exception { h.set("level", 0); h.invoke("advanceLevel"); quiet(h, 2); }
    private static TraversalEnvironment environment(HeapGameHarness h) throws Exception { return (TraversalEnvironment) h.get("environment"); }
    private static TraversalEnvironment.PropView firstProp(HeapGameHarness h, TraversalEnvironment.PropKind kind) throws Exception {
        return environment(h).snapshot().props().stream().filter(prop -> prop.kind() == kind).findFirst().orElseThrow();
    }
    private static void shootProp(HeapGameHarness h, long id) throws Exception {
        press(h, "SHOOT");
        for (int tick = 0; tick < 45 && environment(h).snapshot().props().stream().anyMatch(prop -> prop.id() == id); tick++) quiet(h, 1);
        h.key("SHOOT_R"); quiet(h, 1);
        require(environment(h).snapshot().props().stream().noneMatch(prop -> prop.id() == id), "Real shot must destroy the selected prop");
    }
    private static void quiet(HeapGameHarness h, int ticks) throws Exception {
        for (int tick = 0; tick < ticks; tick++) {
            h.enemies().clearHostiles(); h.enemies().getEnemyBullets().clear(); h.tick();
        }
    }
    private static void press(HeapGameHarness h, String action) throws Exception {
        SwingUtilities.invokeAndWait(() -> h.panel.getActionMap().get(action).actionPerformed(new ActionEvent(h.panel, 0, action)));
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
