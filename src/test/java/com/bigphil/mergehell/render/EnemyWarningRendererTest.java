package com.bigphil.mergehell.render;

import com.bigphil.mergehell.model.EntityType;
import com.bigphil.mergehell.model.ObstacleManager;
import com.bigphil.mergehell.model.Projectile;
import org.junit.jupiter.api.Test;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class EnemyWarningRendererTest {
    @Test void ordinaryVolleysStayAttachedToTheActorAndDoNotRevealTheirFlightPaths() {
        for (EntityType type : List.of(EntityType.RIGGER, EntityType.MIRROR,
                EntityType.SLAG_SPITTER, EntityType.SPORE_POD)) {
            var pose = EnemyWarningPreview.warning(type);
            var image = render(pose);
            assertTrue(pixels(image, new Rectangle(0, 0, 480, 240)) > 0, type + " must still charge visibly");
            var actor = new Rectangle((int) pose.x() - 10, (int) pose.y() - 10,
                    (int) pose.width() + 20, (int) pose.height() + 20);
            for (int y = 0; y < 240; y++) for (int x = 0; x < 480; x++)
                if (!actor.contains(x, y)) assertEquals(0, image.getRGB(x, y), type + " must not paint a remote trajectory");
        }
    }

    @Test void leapsRetainTheirCommittedLandingFootprintWithoutDrawingAnAerialArc() {
        for (EntityType type : List.of(EntityType.INTERRUPT, EntityType.LURKER)) {
            var pose = EnemyWarningPreview.warning(type); var t = pose.tactics();
            var image = render(pose);
            int bottom = (int) (t.targetY() + pose.height() - 2);
            assertTrue(pixels(image, new Rectangle((int) t.targetX(), bottom - 6,
                    (int) pose.width(), 8)) > 0, "Landing remains readable");
            int middle = (int) ((t.originX() + t.targetX()) / 2 + pose.width() / 2);
            assertEquals(0, pixels(image, new Rectangle(middle - 6, 20, 12, 120)), "No full leap arc");
        }
    }

    @Test void piercingShotUsesAShortDirectionCueAndBurrowWarningStaysOnTheSurface() {
        var sniper = EnemyWarningPreview.warning(EntityType.SENTINEL);
        var image = render(sniper);
        assertTrue(pixels(image, new Rectangle(250, 130, 50, 70)) > 0);
        assertEquals(0, pixels(image, new Rectangle(0, 0, 225, 240)), "Sniper guide cannot reach the target");
        var buried = EnemyWarningPreview.warning(EntityType.DRILLER);
        image = render(buried);
        assertTrue(pixels(image, new Rectangle(0, 180, 480, 20)) > 0);
        assertEquals(0, pixels(image, new Rectangle(0, 0, 480, 175)), "No floating glow above the hidden body");
    }

    @Test void absentWarningsAndDeadActorsCannotPaintDangerAndFullHealthBarsStayHidden() {
        var pose = EnemyWarningPreview.warning(EntityType.SENTINEL);
        assertFalse(EnemyWarningRenderer.showHealth(pose));
        for (double death : new double[]{0, .5}) {
            var quiet = new ActorVisuals.Hostile(pose.type(), pose.x(), pose.y(), pose.width(), pose.height(),
                    pose.facing(), pose.hp(), pose.maxHp(), death == 0 ? 0 : 20, pose.phase(), 0, death, pose.tactics());
            assertEquals(0, pixels(render(quiet), new Rectangle(0, 0, 480, 240)));
        }
    }

    @Test void repeatedPaintDoesNotAdvanceTheWarningOrChangeTheVolleyCommitment() {
        for (EntityType type : List.of(EntityType.SENTINEL, EntityType.RIGGER, EntityType.SLAG_SPITTER,
                EntityType.MIRROR, EntityType.SPORE_POD)) {
            var observed = new ObstacleManager.Enemy(300, 195 - type.height, type, 128L);
            var control = new ObstacleManager.Enemy(300, 195 - type.height, type, 128L);
            var observedShots = new ArrayList<Projectile>(); var controlShots = new ArrayList<Projectile>();
            var image = new BufferedImage(480, 240, BufferedImage.TYPE_INT_ARGB); var g = image.createGraphics();
            try {
                for (int tick = 0; tick < 180; tick++) {
                    observed.update(1, 150, 165, 0, 480, 195); control.update(1, 150, 165, 0, 480, 195);
                    observed.maybeShoot(165, observedShots); control.maybeShoot(165, controlShots);
                    var t = observed.getTactics();
                    var pose = new ActorVisuals.Hostile(type, observed.getX(), observed.getY(), type.width, type.height,
                            t.facing(), observed.getHp(), observed.getMaxHp(), observed.getTelegraphTicks(), 0, 0, 0, t);
                    for (int paint = 0; paint < 4; paint++) EnemyWarningRenderer.render(g, pose, Color.ORANGE);
                    assertEquals(control.getTactics(), observed.getTactics());
                    assertEquals(control.getTelegraphTicks(), observed.getTelegraphTicks());
                    assertEquals(controlShots.size(), observedShots.size(), "Emission tick remains unchanged");
                }
            } finally { g.dispose(); }
            assertFalse(observedShots.isEmpty());
            for (int i = 0; i < observedShots.size(); i++) {
                assertEquals(controlShots.get(i).getX(), observedShots.get(i).getX());
                assertEquals(controlShots.get(i).getY(), observedShots.get(i).getY());
                assertEquals(controlShots.get(i).getVx(), observedShots.get(i).getVx());
                assertEquals(controlShots.get(i).getVy(), observedShots.get(i).getVy());
            }
        }
    }

    private static BufferedImage render(ActorVisuals.Hostile pose) {
        var image = new BufferedImage(480, 240, BufferedImage.TYPE_INT_ARGB); var g = image.createGraphics();
        try { EnemyWarningRenderer.render(g, pose, Color.ORANGE); } finally { g.dispose(); }
        return image;
    }
    private static int pixels(BufferedImage image, Rectangle region) {
        int count = 0;
        for (int y = region.y; y < region.y + region.height; y++)
            for (int x = region.x; x < region.x + region.width; x++)
                if ((image.getRGB(x, y) >>> 24) > 0) count++;
        return count;
    }
}
