package com.bigphil.mergehell.render;

import com.bigphil.mergehell.model.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.ArrayList;

/** Production encounter/renderer fixtures, not a playthrough or generated-art mockup. */
public final class BossDepthPreview {
    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length == 0 ? "build/boss-depth-20260921/visual" : args[0]);
        Files.createDirectories(out);
        String[] actions = {"LEFT_SWEEP", "RIGHT_SWEEP", "GANTRY_LOCK", "HEART_PULSE", "ROOT_SURGE"};
        BufferedImage sheet = new BufferedImage(1200, actions.length * 375, BufferedImage.TYPE_INT_RGB);
        Graphics2D sheetGraphics = sheet.createGraphics();
        for (int row = 0; row < actions.length; row++) {
            String action = actions[row]; int chapter = row < 3 ? 2 : 4;
            Boss boss = new Boss("Preview", 16000, "!", 960, chapter, 9);
            ObstacleManager enemies = new ObstacleManager(); var shots = new ArrayList<Projectile>();
            boss.previewArrival(1, 480); boss.activate(); advance(boss, enemies, shots);
            var core = boss.getParts().get(2);
            boss.damageAt(core.bounds().rectangle(), row < 2 ? 10000 : chapter == 2 ? 20000 : 30000);
            for (int i = 0; i < 5000 && !boss.getEncounterAction().equals(action); i++) advance(boss, enemies, shots);
            if (!boss.getEncounterAction().equals(action)) throw new AssertionError(action + " not reached");
            for (int column = 0; column < 2; column++) {
                if (column == 1) while (boss.getWarningTicks() > 0) advance(boss, enemies, shots);
                BufferedImage frame = new BufferedImage(960, 600, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = frame.createGraphics();
                g.setColor(new Color(16, 22, 30)); g.fillRect(0, 0, 960, 600);
                g.setColor(new Color(34, 43, 47)); g.fillRect(0, 480, 960, 120);
                g.setColor(new Color(91, 110, 115)); g.drawLine(0, 480, 960, 480);
                ChapterActorRenderer.boss(g, boss, 0, false, false);
                for (Projectile shot : shots) shot.draw(g);
                g.setColor(new Color(130, 216, 233)); g.drawRect(180, column == 0 ? 450 : 462, 30, column == 0 ? 30 : 18);
                g.setFont(new Font("SansSerif", Font.PLAIN, 18)); g.setColor(Color.WHITE);
                g.drawString(action + " / " + (column == 0 ? "WARNING" : "ACTIVE") + " / stage " + boss.getCombatStage(), 20, 28);
                g.dispose();
                ImageIO.write(frame, "png", out.resolve(action.toLowerCase() + "-" + column + ".png").toFile());
                sheetGraphics.drawImage(frame, column * 600, row * 375, 600, 375, null);
            }
        }
        sheetGraphics.dispose(); ImageIO.write(sheet, "png", out.resolve("contact-sheet.png").toFile());
        transitionSheet(out);
        System.out.println(out.toAbsolutePath());
    }
    private static void transitionSheet(Path out) throws Exception {
        BufferedImage sheet = new BufferedImage(1200, 750, BufferedImage.TYPE_INT_RGB);
        var sheetGraphics = sheet.createGraphics();
        for (int row = 0; row < 2; row++) {
            int chapter = row == 0 ? 2 : 4;
            Boss boss = new Boss("Transition", 16000, "!", 960, chapter, 9);
            ObstacleManager enemies = new ObstacleManager(); var shots = new ArrayList<Projectile>();
            boss.previewArrival(1, 480); boss.activate(); advance(boss, enemies, shots);
            if (chapter == 2) for (var part : boss.getParts()) if (!part.id().equals("core"))
                boss.damageAt(part.bounds().rectangle(), part.hp());
            boss.damageAt(boss.getParts().get(2).bounds().rectangle(), chapter == 2 ? 6000 : 30000);
            advance(boss, enemies, shots);
            for (int column = 0; column < 2; column++) {
                if (column == 1) while (boss.getEncounterAction().equals("RECONFIGURE")) advance(boss, enemies, shots);
                var core = boss.getParts().get(2);
                if (core.weak() != (column == 1)) throw new AssertionError("Unexpected transition weak state");
                var frame = new BufferedImage(960, 600, BufferedImage.TYPE_INT_RGB); var g = frame.createGraphics();
                g.setColor(new Color(16, 22, 30)); g.fillRect(0, 0, 960, 600);
                g.setColor(new Color(34, 43, 47)); g.fillRect(0, 480, 960, 120);
                ChapterActorRenderer.boss(g, boss, 0, false, false);
                g.setColor(Color.WHITE); g.setFont(new Font("SansSerif", Font.PLAIN, 18));
                g.drawString("Chapter " + (chapter + 1) + " / " + (column == 0 ? "CORE CONTRACTING / normal damage" : "CORE OPEN / weak-point bonus"), 20, 28);
                g.dispose();
                ImageIO.write(frame, "png", out.resolve("transition-" + chapter + "-" + column + ".png").toFile());
                sheetGraphics.drawImage(frame, column * 600, row * 375, 600, 375, null);
            }
        }
        sheetGraphics.dispose(); ImageIO.write(sheet, "png", out.resolve("transitions.png").toFile());
    }
    private static void advance(Boss boss, ObstacleManager enemies, ArrayList<Projectile> shots) {
        shots.forEach(Projectile::update);
        shots.removeIf(p -> p.isDead() || p.getX() < -20 || p.getX() > 980 || p.getY() > 620);
        boss.update(enemies, 480, 180, 450, shots);
    }
}
