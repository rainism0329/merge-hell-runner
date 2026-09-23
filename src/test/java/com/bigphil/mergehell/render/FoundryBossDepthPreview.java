package com.bigphil.mergehell.render;

import com.bigphil.mergehell.model.*;
import com.bigphil.mergehell.world.ChapterRouteController;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.ArrayList;

/** Real simulation/renderer fixtures, not a playthrough or generated-art mockup. */
public final class FoundryBossDepthPreview {
    private static final class Scene {
        final Boss boss = new Boss("Foundry", 16000, "!", 960, 3, 9);
        final ObstacleManager enemies = new ObstacleManager();
        final ArrayList<Projectile> shots = new ArrayList<>();
        Scene(int stage) {
            boss.previewArrival(1, 480); boss.activate(); tick();
            if (stage > 1) boss.damageAt(part("core").bounds().rectangle(), stage == 2 ? 10000 : 20000);
            tick();
        }
        void tick() {
            shots.forEach(Projectile::update); shots.removeIf(p -> p.isDead() || p.getY() > 620 || p.getX() < -20 || p.getX() > 980);
            boss.update(enemies, 480, 180, 450, shots);
        }
        void until(String action) {
            for (int i = 0; i < 5000 && !boss.getEncounterAction().equals(action); i++) tick();
            if (!boss.getEncounterAction().equals(action)) throw new AssertionError(action);
        }
        Boss.PartView part(String id) { return boss.getParts().stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow(); }
        Boss.AttackTelegraph trail() { return boss.getAttackTelegraphs().stream().filter(t -> t.id().equals("slag-trail")).findFirst().orElse(null); }
    }
    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length == 0 ? "build/foundry-boss-20260921/visual" : args[0]); Files.createDirectories(out);
        BufferedImage sheet = new BufferedImage(1200, 1500, BufferedImage.TYPE_INT_RGB); var sg = sheet.createGraphics();
        for (int row = 0; row < 4; row++) {
            Scene s = new Scene(row == 1 ? 3 : 2);
            if (row == 0 || row == 3) {
                s.until("DRILL_CHARGE"); while (s.boss.getEncounterAction().equals("DRILL_CHARGE")) s.tick();
                if (s.trail() == null) throw new AssertionError("No heat trail");
            } else if (row == 1) s.until("VENT_PURGE");
            else {
                var armor = s.part("armor-plate"); s.boss.damageAt(armor.bounds().rectangle(), armor.hp());
                s.until("DRILL_CHARGE");
            }
            for (int column = 0; column < 2; column++) {
                if (column == 1) {
                    if (row == 0) while (s.trail().warningTicks() > 0) s.tick();
                    else if (row == 3) {
                        var vent = s.part("heat-vent"); s.boss.damageAt(vent.bounds().rectangle(), s.part("armor-plate").maxHp());
                    } else { while (s.boss.getWarningTicks() > 0) s.tick(); s.tick(); }
                }
                BufferedImage frame = new BufferedImage(960, 600, BufferedImage.TYPE_INT_RGB); var g = frame.createGraphics();
                g.setColor(new Color(18, 21, 26)); g.fillRect(0, 0, 960, 600);
                ChapterArt.load().backdrop(g, 960, 600, 0, 3);
                new ChapterWorldRenderer().atmosphere(g, 3, 960, 480, 0, 0);
                var scene = new ChapterRouteController(3, 480);
                var player = new Player(160, 450); scene.beforeMove(player, true);
                new ChapterWorldRenderer().world(g, scene.snapshot(), 0, 960, 480, false);
                ChapterActorRenderer.boss(g, s.boss, 0, false, false);
                for (var shot : s.shots) shot.draw(g);
                String label = switch(row) {
                    case 0 -> column == 0 ? "HEAT TRAIL / CRACK WARNING" : "HEAT TRAIL / ACTIVE";
                    case 1 -> column == 0 ? "REAR VENT / PRESSURE WARNING" : "REAR VENT / ACTIVE PURGE";
                    case 2 -> column == 0 ? "BROKEN DRIVE / SHORT CHARGE WARNING" : "BROKEN DRIVE / SHORT CHARGE ACTIVE";
                    default -> column == 0 ? "HOT METAL / VENT TARGETABLE" : "OVERHEATED / TRAIL EXTINGUISHED";
                };
                g.setColor(Color.WHITE); g.setFont(new Font("SansSerif", Font.PLAIN, 18)); g.drawString(label, 20, 28); g.dispose();
                ImageIO.write(frame, "png", out.resolve("foundry-" + row + "-" + column + "-960.png").toFile());
                var small = new BufferedImage(600, 400, BufferedImage.TYPE_INT_RGB); var smallG = small.createGraphics();
                smallG.drawImage(frame, 0, 12, 600, 375, null); smallG.dispose();
                ImageIO.write(small, "png", out.resolve("foundry-" + row + "-" + column + "-600.png").toFile());
                sg.drawImage(frame, column * 600, row * 375, 600, 375, null);
            }
        }
        sg.dispose(); ImageIO.write(sheet, "png", out.resolve("contact-sheet.png").toFile());
        System.out.println(out.toAbsolutePath());
    }
}
