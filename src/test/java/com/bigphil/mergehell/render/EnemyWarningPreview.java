package com.bigphil.mergehell.render;

import com.bigphil.mergehell.model.EntityType;
import com.bigphil.mergehell.model.ObstacleManager;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Real simulation warning poses, isolated for an equal-scale before/after readability review. */
public final class EnemyWarningPreview {
    static ActorVisuals.Hostile warning(EntityType type) {
        return warning(type, false);
    }

    static ActorVisuals.Hostile warning(EntityType type, boolean late) {
        var enemy = new ObstacleManager.Enemy(300, 195 - type.height, type, 128L);
        for (int tick = 0; tick < 400 && enemy.getTelegraphTicks() == 0; tick++) {
            enemy.update(1, 150, 165, 0, 480, 195);
            enemy.maybeShoot(165);
        }
        if (enemy.getTelegraphTicks() == 0) throw new IllegalStateException("No warning: " + type);
        if (late) while (enemy.getTelegraphTicks() > 5) {
            enemy.update(1, 150, 165, 0, 480, 195);
            enemy.maybeShoot(165);
        }
        var t = enemy.getTactics();
        return new ActorVisuals.Hostile(type, enemy.getX(), enemy.getY(), type.width, type.height,
                t.facing(), enemy.getHp(), enemy.getMaxHp(), enemy.getTelegraphTicks(), .6, 0, 0, t);
    }

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]); Files.createDirectories(output.getParent());
        var image = new BufferedImage(1440, 720, BufferedImage.TYPE_INT_RGB);
        var target = image.createGraphics();
        int cell = 0;
        for (EntityType type : List.of(EntityType.SENTINEL, EntityType.WARDEN, EntityType.RIGGER,
                EntityType.INTERRUPT, EntityType.DRILLER, EntityType.SLAG_SPITTER,
                EntityType.MIRROR, EntityType.SPORE_POD, EntityType.LURKER)) {
            var g = (Graphics2D) target.create();
            try {
                g.translate(cell % 3 * 480, cell / 3 * 240); g.setClip(0, 0, 480, 240);
                g.setColor(new Color(27, 36, 43)); g.fillRect(0, 0, 480, 240);
                g.setColor(new Color(64, 71, 71)); g.fillRect(0, 195, 480, 45);
                g.setColor(new Color(45, 55, 63));
                for (int x = 0; x < 480; x += 48) g.drawLine(x, 30, x, 195);
                var pose = warning(type, args.length > 1 && Boolean.parseBoolean(args[1]));
                ChapterActorRenderer.enemy(g, pose, 1, false);
                g.setColor(new Color(149, 177, 184)); g.drawRect(150, 165, 30, 30);
                g.setColor(new Color(228, 224, 211)); g.setFont(new Font("SansSerif", Font.BOLD, 14));
                g.drawString(type.name() + "  /  " + pose.warning() + " ticks", 14, 23);
            } finally { g.dispose(); }
            cell++;
        }
        target.dispose(); ImageIO.write(image, "png", output.toFile());
        System.out.println(output.toAbsolutePath());
    }
}
