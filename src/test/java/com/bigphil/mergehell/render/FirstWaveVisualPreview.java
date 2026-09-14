package com.bigphil.mergehell.render;

import com.bigphil.mergehell.model.EntityType;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Actual-size production renderer inspection, not a GamePanel capture or playthrough. */
public final class FirstWaveVisualPreview {
    private static final EntityType[] TYPES = {EntityType.CONFLICT, EntityType.CRASH, EntityType.LOCK, EntityType.FIREWALL};

    public static void main(String[] args) throws Exception {
        if (!GraphicsEnvironment.isHeadless()) throw new IllegalStateException("Run with -Djava.awt.headless=true");
        Path output = Path.of(args.length == 0 ? "build/first-wave/preview" : args[0]);
        Files.createDirectories(output.resolve("motion"));
        IndustrialArt art = IndustrialArt.load();
        if (!art.diagnostics().isEmpty()) throw new IllegalStateException(art.diagnostics().toString());
        IndustrialDeck deck = new IndustrialDeck();
        BufferedImage nativeStage = stage(art, deck, 0);
        ImageIO.write(nativeStage, "png", output.resolve("native-960x600.png").toFile());
        BufferedImage small = new BufferedImage(600, 400, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = small.createGraphics();
        try {
            g.setColor(Color.BLACK); g.fillRect(0, 0, 600, 400);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(nativeStage, 0, 12, 600, 375, null);
        } finally { g.dispose(); }
        ImageIO.write(small, "png", output.resolve("scaled-600x400.png").toFile());
        for (int i = 0; i < 24; i++) ImageIO.write(stage(art, deck, i / 24.0 * Math.PI * 2), "png",
                output.resolve("motion/frame-%02d.png".formatted(i)).toFile());
        poseSheet(art, output);
        Files.writeString(output.resolve("README.md"), """
                # First-wave production renderer inspection

                `native-960x600.png` uses real ActorVisuals, IndustrialArt and IndustrialDeck at the
                unchanged logical entity sizes. `scaled-600x400.png` downsizes that inspection image
                to the same 600x375 drawing area plus letterboxing; it is not a GamePanel screenshot.
                `motion/` contains 24 controlled phase samples, without wall-clock or device I/O.
                `poses.png` shows fixed left/right, hit and death inspection fixtures at 2x magnification.
                These labels/layouts are inspection guides, not shipping HUD, a real level or playthrough.
                The separate GameVisualPreview utility remains the full-panel integration check.
                """, StandardCharsets.UTF_8);
        System.out.println("First-wave renderer preview: " + output.toAbsolutePath());
    }

    private static BufferedImage stage(IndustrialArt art, IndustrialDeck deck, double phase) {
        BufferedImage image = new BufferedImage(960, 600, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            art.backdrop(g, 960, 600, 0, 1);
            deck.draw(g, 960, 480, 0);
            g.setColor(new Color(4, 11, 17, 215)); g.fillRect(16, 16, 928, 56);
            g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 17)); g.setColor(new Color(239, 227, 201));
            g.drawString("FIRST WAVE / PRODUCTION RENDERER INSPECTION", 30, 41);
            g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            g.drawString("Native actor sizes; fixed scene. This is an art probe, not a GamePanel capture.", 30, 61);
            ActorVisuals.hero(g, art, new ActorVisuals.Hero(125, 480, 1, ActorVisuals.Action.IDLE,
                    0, 0, 0, false, false, 1));
            for (int i = 0; i < TYPES.length; i++) {
                EntityType type = TYPES[i];
                double x = 260 + i * 165, bottom = type == EntityType.LOCK ? 400 + Math.sin(phase) * 3 : 480;
                ActorVisuals.enemy(g, art, new ActorVisuals.Hostile(type, x, bottom - type.height,
                        type.width, type.height, -1, type.maxHp, type.maxHp, 0, phase, 0, 0));
                g.setColor(new Color(7, 14, 20, 230)); g.fillRect((int) x - 12, 512, 142, 34);
                g.setColor(new Color(255, 210, 150)); g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
                g.drawString(type.name(), (int) x - 5, 526);
                g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
                g.drawString(type.width + "x" + type.height + " logical box", (int) x - 5, 541);
            }
        } finally { g.dispose(); }
        return image;
    }

    private static void poseSheet(IndustrialArt art, Path output) throws Exception {
        BufferedImage image = new BufferedImage(1040, 1280, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(new Color(19, 27, 34)); g.fillRect(0, 0, image.getWidth(), image.getHeight());
            for (int row = 0; row < 4; row++) for (int col = 0; col < TYPES.length; col++) {
                EntityType type = TYPES[col];
                Graphics2D cell = (Graphics2D) g.create();
                try {
                    cell.translate(col * 260 + 130, row * 320 + 284); cell.scale(2, 2);
                    cell.setColor(new Color(125, 152, 141)); cell.drawLine(-62, 0, 62, 0);
                    ActorVisuals.enemy(cell, art, new ActorVisuals.Hostile(type, -type.width / 2.0, -type.height,
                            type.width, type.height, row == 1 ? 1 : -1, type.maxHp, type.maxHp,
                            0, row * 1.15, row == 2 ? 1 : 0, row == 3 ? .65 : 0));
                } finally { cell.dispose(); }
                g.setColor(new Color(238, 220, 192)); g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
                g.drawString(type + " / " + new String[]{"LEFT", "RIGHT", "HIT", "DEATH"}[row], col * 260 + 14, row * 320 + 311);
            }
        } finally { g.dispose(); }
        ImageIO.write(image, "png", output.resolve("poses.png").toFile());
    }
}
