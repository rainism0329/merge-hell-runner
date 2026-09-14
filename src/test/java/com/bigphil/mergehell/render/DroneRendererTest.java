package com.bigphil.mergehell.render;

import com.bigphil.mergehell.combat.DroneController;
import com.bigphil.mergehell.combat.WeaponId;
import com.bigphil.mergehell.model.Projectile;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DroneRendererTest {
    private final DroneRenderer renderer = new DroneRenderer();

    @Test void emittedProjectileAndPaintedFlashShareTheAuthoritativeMuzzleInEveryDirection() {
        for (double[] target : new double[][]{{250, 102}, {20, 102}, {112, 20}, {112, 250}}) {
            DroneController controller = new DroneController();
            List<Projectile> shots = controller.update(input(target[0], target[1], true));
            assertEquals(1, shots.size());
            DroneController.Pose pose = controller.snapshot().drones().get(0);
            Projectile shot = shots.get(0);
            assertEquals(pose.muzzleX(), shot.getX() + shot.getType().width / 2.0, 1e-9);
            assertEquals(pose.muzzleY(), shot.getY() + shot.getType().height / 2.0, 1e-9);
            BufferedImage frame = paint(controller.snapshot());
            boolean brightAtEmission = false;
            int mx = (int) Math.round(pose.muzzleX()), my = (int) Math.round(pose.muzzleY());
            for (int y = my - 1; y <= my + 1; y++) for (int x = mx - 1; x <= mx + 1; x++) {
                Color color = new Color(frame.getRGB(x, y), true);
                brightAtEmission |= color.getAlpha() > 180 && color.getRed() > 175
                        && color.getGreen() > 220 && color.getBlue() > 210;
            }
            assertTrue(brightAtEmission, "Flash must begin at the emitted projectile center");
        }
    }

    @Test void pausedSnapshotProducesExactlyTheSamePixelsAndRemainsUnchanged() {
        DroneController controller = new DroneController();
        controller.update(input(250, 102, true));
        DroneController.Snapshot before = controller.snapshot();
        BufferedImage activeFrame = paint(before);
        assertTrue(controller.update(input(20, 250, false)).isEmpty());
        assertSame(before, controller.snapshot());
        assertArrayEquals(activeFrame.getRGB(0, 0, 300, 300, null, 0, 300),
                paint(controller.snapshot()).getRGB(0, 0, 300, 300, null, 0, 300));
    }

    @Test void rendererPreservesCallerGraphicsState() {
        BufferedImage image = new BufferedImage(300, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.translate(12, 20); g.scale(.625, .625); g.setClip(0, 0, 250, 250);
            g.setColor(Color.MAGENTA); g.setStroke(new BasicStroke(4));
            g.setComposite(AlphaComposite.SrcOver.derive(.7f));
            AffineTransform transform = g.getTransform(); Rectangle clip = g.getClipBounds();
            Stroke stroke = g.getStroke(); Composite composite = g.getComposite();
            renderer.render(g, new DroneController.Snapshot(1, List.of(pose(3))));
            assertEquals(transform, g.getTransform()); assertEquals(clip, g.getClipBounds());
            assertEquals(Color.MAGENTA, g.getColor()); assertEquals(stroke, g.getStroke());
            assertEquals(composite, g.getComposite());
        } finally { g.dispose(); }
    }

    @Test void topRankHasVisibleArmorOutsideStandardBodyAtNativeSize() {
        BufferedImage standard = paint(new DroneController.Snapshot(1, List.of(pose(2))));
        BufferedImage reinforced = paint(new DroneController.Snapshot(1, List.of(pose(3))));
        int extraVisiblePixels = 0;
        for (int y = 80; y <= 118; y++) for (int x = 79; x <= 120; x++) {
            if ((standard.getRGB(x, y) >>> 24) < 30 && (reinforced.getRGB(x, y) >>> 24) > 100)
                extraVisiblePixels++;
        }
        assertTrue(extraVisiblePixels > 40, "Armor and ring must change the native-size silhouette");
    }

    private static DroneController.Pose pose(int rank) {
        return new DroneController.Pose(0, rank, 100, 100, 0, 118, 100, 0, .5, 0, 100, 100);
    }

    private static DroneController.Input input(double x, double y, boolean active) {
        return new DroneController.Input(150, 150, 1, 1, WeaponId.COMMIT_CANNON, 50,
                new DroneController.Bounds(0, 0, 300, 300),
                List.of(new DroneController.Target(1, x, y, 500, false, true)), 20, active);
    }

    private BufferedImage paint(DroneController.Snapshot snapshot) {
        BufferedImage image = new BufferedImage(300, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try { renderer.render(g, snapshot); } finally { g.dispose(); }
        return image;
    }
}
