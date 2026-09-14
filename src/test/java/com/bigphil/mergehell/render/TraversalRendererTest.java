package com.bigphil.mergehell.render;

import com.bigphil.mergehell.assets.AssetCatalog;
import com.bigphil.mergehell.assets.AssetStore;
import com.bigphil.mergehell.world.TraversalEnvironment;
import org.junit.jupiter.api.Test;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TraversalRendererTest {
    private final TraversalRenderer renderer = new TraversalRenderer();
    private final IndustrialArt art = new IndustrialArt(AssetStore.preload(new AssetCatalog(Map.of()),
            getClass().getClassLoader()));

    @Test void puddlesStayOnTheFloorWithoutFloatingPlumbingOrAnOpaqueDeckFace() {
        var pool = new TraversalEnvironment.WaterView(4, 100, 260, 480, 24);
        var scene = new TraversalEnvironment.Snapshot(0, List.of(pool), List.of(), List.of(),
                List.of(), List.of(), false);
        var frame = new BufferedImage(640, 600, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = frame.createGraphics();
        try { renderer.ground(g, scene, 0, 640, 480, 0, 1.25); }
        finally { g.dispose(); }
        assertEquals(0, painted(frame, 80, 400, 300, 76), "A puddle cannot invent a disconnected pipe above the floor");
        assertEquals(0, painted(frame, 80, 492, 300, 40), "The solid deck face must remain available to the underlying scenery");
        assertTrue(painted(frame, 100, 479, 260, 12) > 200);
        assertEquals(0, frame.getRGB(100, 488) >>> 24, "The wet edge tapers instead of forming a rectangular tank");
    }

    @Test void zeroParticleDensityHidesEveryDropletButPreservesContactRings() {
        var scene = effects();
        BufferedImage none = foreground(scene, 0);
        BufferedImage all = foreground(scene, 100);
        assertEquals(0, painted(none, 340, 280, 230, 40), "The separated droplet region stays fully transparent at 0%");
        assertTrue(painted(all, 340, 280, 230, 40) > 100);
        assertTrue(painted(none, 160, 385, 80, 30) > 0, "Ground contact remains readable even with decorative particles disabled");
        assertArrayEquals(region(none, 160, 385, 80, 30), region(all, 160, 385, 80, 30));
        var ringsOnly = new TraversalEnvironment.Snapshot(scene.tick(), scene.water(), scene.platforms(), scene.props(),
                scene.ripples(), List.of(), scene.bossArena());
        assertArrayEquals(pixels(foreground(ringsOnly, 100)), pixels(none));
        assertEquals(10, scene.droplets().size(), "Rendering settings filter the presentation, not simulation snapshots");
    }

    @Test void reducedParticleDensityIsDeterministicAndKeepsAnIntermediateNumberOfDrops() {
        var scene = effects();
        BufferedImage reduced = foreground(scene, 50);
        long reducedPixels = painted(reduced, 340, 280, 230, 40);
        assertTrue(reducedPixels > 0);
        assertTrue(reducedPixels < painted(foreground(scene, 100), 340, 280, 230, 40));
        assertArrayEquals(pixels(reduced), pixels(foreground(scene, 50)),
                "Repainting a paused scene must not randomly change the visible particle subset");
    }

    @Test void everyLayerPreservesTheCallerGraphicsAndSimulationSnapshot() {
        var environment = new TraversalEnvironment(1, 42, 480);
        environment.update(new TraversalEnvironment.Step(330, 450, 350, 480,
                30, 0, 960, true, false, true, false));
        var snapshot = environment.snapshot();
        int snapshotHash = snapshot.hashCode();
        var image = new BufferedImage(960, 600, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.translate(12, 8); g.scale(.75, .75); g.setClip(4, 6, 800, 570);
            g.setStroke(new BasicStroke(5)); g.setComposite(AlphaComposite.SrcOver.derive(.6f));
            g.setColor(Color.MAGENTA); g.setPaint(new GradientPaint(0, 0, Color.RED, 10, 10, Color.BLUE));
            g.setFont(new Font(Font.SERIF, Font.ITALIC, 23));
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            var transform = g.getTransform(); var clip = g.getClipBounds(); var stroke = g.getStroke();
            var composite = g.getComposite(); var paint = g.getPaint(); var color = g.getColor();
            var font = g.getFont(); var hints = g.getRenderingHints();
            renderer.backdrop(g, 960, 480, 0, 1.25, 1, false);
            renderer.ground(g, snapshot, 0, 960, 480, 1, 1.25);
            renderer.foreground(g, snapshot, art, null, 0, 960, false, 35);
            assertEquals(transform, g.getTransform()); assertEquals(clip, g.getClipBounds());
            assertEquals(stroke, g.getStroke()); assertEquals(composite, g.getComposite());
            assertEquals(paint, g.getPaint()); assertEquals(color, g.getColor());
            assertEquals(font, g.getFont()); assertEquals(hints, g.getRenderingHints());
            assertSame(snapshot, environment.snapshot()); assertEquals(snapshotHash, snapshot.hashCode());
        } finally { g.dispose(); }
    }

    private TraversalEnvironment.Snapshot effects() {
        var droplets = java.util.stream.IntStream.range(0, 10)
                .mapToObj(index -> new TraversalEnvironment.DropletView(355 + index * 20, 300, 2.5, .75f)).toList();
        return new TraversalEnvironment.Snapshot(30, List.of(), List.of(), List.of(),
                List.of(new TraversalEnvironment.RippleView(200, 400, 26, .7f, 1.2f)), droplets, false);
    }

    private BufferedImage foreground(TraversalEnvironment.Snapshot scene, int density) {
        var image = new BufferedImage(640, 500, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try { renderer.foreground(g, scene, art, null, 0, 640, false, density); }
        finally { g.dispose(); }
        return image;
    }

    private static long painted(BufferedImage image, int x, int y, int width, int height) {
        return Arrays.stream(region(image, x, y, width, height)).filter(pixel -> (pixel >>> 24) != 0).count();
    }

    private static int[] region(BufferedImage image, int x, int y, int width, int height) {
        return image.getRGB(x, y, width, height, null, 0, width);
    }

    private static int[] pixels(BufferedImage image) {
        return region(image, 0, 0, image.getWidth(), image.getHeight());
    }
}
