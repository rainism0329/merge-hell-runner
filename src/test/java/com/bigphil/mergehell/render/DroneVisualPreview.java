package com.bigphil.mergehell.render;

import com.bigphil.mergehell.combat.DroneController;
import com.bigphil.mergehell.combat.WeaponId;
import com.bigphil.mergehell.model.EntityType;
import com.bigphil.mergehell.model.Projectile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Headless production-renderer inspection, not a GamePanel capture or playthrough.
 * Run with -Djava.awt.headless=true and the normal main resources/classpath, followed by
 * com.bigphil.mergehell.render.DroneVisualPreview [output-directory]. No audio or desktop I/O.
 */
public final class DroneVisualPreview {
    private static final DroneRenderer RENDERER = new DroneRenderer();
    private static final DroneController.Bounds VISIBLE = new DroneController.Bounds(0, 80, 960, 500);

    public static void main(String[] args) throws Exception {
        if (!GraphicsEnvironment.isHeadless()) throw new IllegalStateException("Run with -Djava.awt.headless=true");
        Path output = Path.of(args.length == 0 ? "build/drone-visual/preview" : args[0]);
        Files.createDirectories(output.resolve("motion"));
        IndustrialArt art = IndustrialArt.load();
        if (!art.diagnostics().isEmpty()) throw new IllegalStateException(art.diagnostics().toString());
        IndustrialDeck deck = new IndustrialDeck();
        List<Fixture> fixtures = List.of(new Fixture(1, 175), new Fixture(2, 465), new Fixture(3, 755));
        StringBuilder evidence = new StringBuilder("frame,rank,slot,body_x,body_y,muzzle_x,muzzle_y,aim,recoil,thrust\n");
        for (int frame = 0; frame < 96; frame++) {
            for (Fixture fixture : fixtures) fixture.step(frame);
            BufferedImage image = stage(art, deck, fixtures, frame);
            ImageIO.write(image, "png", output.resolve("motion/frame-%03d.png".formatted(frame)).toFile());
            if (frame == 0) {
                ImageIO.write(image, "png", output.resolve("native-960x600.png").toFile());
                BufferedImage small = new BufferedImage(600, 400, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = small.createGraphics();
                try {
                    g.setColor(Color.BLACK); g.fillRect(0, 0, 600, 400);
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g.drawImage(image, 0, 12, 600, 375, null);
                } finally { g.dispose(); }
                ImageIO.write(small, "png", output.resolve("scaled-600x400.png").toFile());
            }
            for (Fixture fixture : fixtures) for (DroneController.Pose p : fixture.controller.snapshot().drones())
                evidence.append("%d,%d,%d,%.6f,%.6f,%.6f,%.6f,%.6f,%.3f,%.3f%n".formatted(
                        frame, p.rank(), p.slot(), p.x(), p.y(), p.muzzleX(), p.muzzleY(), p.aimRadians(), p.recoil(), p.thrust()));
        }
        poseSheet(output);
        Files.writeString(output.resolve("poses.csv"), evidence, StandardCharsets.UTF_8);
        Files.writeString(output.resolve("README.md"), """
                # Drone renderer inspection

                `native-960x600.png` draws real DroneController snapshots, DroneRenderer, ActorVisuals,
                IndustrialArt, IndustrialDeck and drone Projectiles at their shipping logical sizes.
                `scaled-600x400.png` applies the same 0.625 display scale and letterbox size as a narrow
                game panel. This is an art probe with controlled placements, not a GamePanel screenshot.
                L1 has one standard body; L2 has two separated bodies; L3 has two armored bodies/rings.
                `motion/` contains 96 sequential simulation ticks: follow, target movement, turn and
                staggered real shots. `poses.csv` records the snapshots' body/muzzle/aim/recoil values.
                `details-4x.png` is a labeled magnification for inspecting turret angles and materials.

                Run the main class `com.bigphil.mergehell.render.DroneVisualPreview` with Java's
                `-Djava.awt.headless=true`, the compiled main/test classes, and src/main/resources on
                the classpath. Its optional first argument selects the output directory. Images are
                rendered by production Java2D code; this entry uses no image generation, desktop or audio.
                The separate GameVisualPreview utility checks the complete game-panel integration.
                """, StandardCharsets.UTF_8);
        System.out.println("Drone renderer preview: " + output.toAbsolutePath());
    }

    private static final class Fixture {
        final int rank; final double baseX;
        final DroneController controller = new DroneController();
        final List<Projectile> shots = new ArrayList<>();
        double playerX, targetX, targetY; int facing;
        Fixture(int rank, double baseX) { this.rank = rank; this.baseX = baseX; }
        void step(int frame) {
            facing = frame < 48 ? 1 : -1;
            playerX = baseX + Math.sin(frame * .075) * 25;
            targetX = baseX + facing * 110;
            targetY = 438 + Math.sin(frame * .11) * 20;
            for (Projectile shot : shots) shot.update();
            shots.removeIf(shot -> shot.getX() < baseX - 145 || shot.getX() > baseX + 145);
            shots.addAll(controller.update(new DroneController.Input(playerX, 447, facing, rank,
                    WeaponId.COMMIT_CANNON, 50, VISIBLE,
                    List.of(new DroneController.Target(rank, targetX, targetY, 500, false, true)), 80, true)));
        }
    }

    private static BufferedImage stage(IndustrialArt art, IndustrialDeck deck, List<Fixture> fixtures, int frame) {
        BufferedImage image = new BufferedImage(960, 600, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            art.backdrop(g, 960, 600, 0, 1); deck.draw(g, 960, 480, 0);
            g.setColor(new Color(4, 11, 17, 225)); g.fillRect(16, 16, 928, 60);
            g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 17)); g.setColor(new Color(239, 227, 201));
            g.drawString("DRONE / PRODUCTION RENDERER INSPECTION", 30, 41);
            g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            g.drawString("Actual body and muzzle sizes. Controlled art probe; not a GamePanel capture.", 30, 62);
            for (Fixture f : fixtures) {
                ActorVisuals.enemy(g, art, new ActorVisuals.Hostile(EntityType.CONFLICT, f.targetX - 20,
                        f.targetY - 20, 40, 40, -f.facing, 500, 500, 0, frame * .2, 0, 0));
                RENDERER.render(g, f.controller.snapshot());
                ActorVisuals.hero(g, art, new ActorVisuals.Hero(f.playerX, 480, f.facing,
                        frame == 0 ? ActorVisuals.Action.IDLE : ActorVisuals.Action.RUN,
                        frame * .18, 0, 0, false, false, 1));
                for (Projectile shot : f.shots) shot.draw(g);
                g.setColor(new Color(7, 14, 20, 230)); g.fillRect((int) f.baseX - 85, 513, 190, 38);
                g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 13)); g.setColor(new Color(239, 217, 173));
                g.drawString("L" + f.rank + " / " + (f.rank == 1 ? "1 DRONE" : "2 DRONES"), (int) f.baseX - 74, 529);
                g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11)); g.setColor(new Color(115, 231, 224));
                g.drawString(f.rank == 3 ? "ARMOR + BRIGHT RING" : "ORANGE SHELL / TWIN JETS", (int) f.baseX - 74, 544);
            }
        } finally { g.dispose(); }
        return image;
    }

    private static void poseSheet(Path output) throws Exception {
        BufferedImage image = new BufferedImage(960, 600, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        double[] angles = {0, -Math.PI / 3, Math.PI, Math.PI / 2};
        try {
            g.setColor(new Color(19, 27, 34)); g.fillRect(0, 0, 960, 600);
            for (int row = 0; row < 2; row++) for (int col = 0; col < 4; col++) {
                Graphics2D cell = (Graphics2D) g.create();
                try {
                    cell.translate(col * 240 + 108, row * 280 + 133); cell.scale(4, 4);
                    double a = angles[col]; int rank = row == 0 ? 1 : 3;
                    RENDERER.render(cell, new DroneController.Snapshot(72, List.of(new DroneController.Pose(
                            0, rank, 0, 0, a, Math.cos(a) * DroneController.MUZZLE_LENGTH,
                            Math.sin(a) * DroneController.MUZZLE_LENGTH, col == 1 ? 1 : 0, .65, 1, 100, 0))));
                } finally { cell.dispose(); }
                g.setColor(new Color(234, 220, 189)); g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
                g.drawString((row == 0 ? "STANDARD" : "REINFORCED") + " / " + new String[]{"RIGHT", "SHOT UP", "LEFT", "DOWN"}[col],
                        col * 240 + 18, row * 280 + 252);
            }
            g.drawString("4x production renderer detail; no gameplay scale change", 18, 580);
        } finally { g.dispose(); }
        ImageIO.write(image, "png", output.resolve("details-4x.png").toFile());
    }
}
