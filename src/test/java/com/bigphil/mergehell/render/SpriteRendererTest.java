package com.bigphil.mergehell.render;

import com.bigphil.mergehell.assets.AnimationClip;
import com.bigphil.mergehell.assets.AssetCatalog;
import com.bigphil.mergehell.assets.AssetStore;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SpriteRendererTest {
    @Test
    void mirrorUsesFootAnchorAndTheSameMuzzleTransformAsPixels() throws IOException {
        SpriteRenderer renderer = renderer(new AtomicInteger());
        VisualPose right = pose(true, 1);
        VisualPose left = pose(false, 1);
        assertEquals(new AnimationClip.Point(13, 18), renderer.socket(right, "muzzle").orElseThrow());
        assertEquals(new AnimationClip.Point(7, 18), renderer.socket(left, "muzzle").orElseThrow());
        assertEquals(new SpriteRenderer.Bounds(9, 16, 4, 4), renderer.bounds(right).orElseThrow());
        assertEquals(new SpriteRenderer.Bounds(7, 16, 4, 4), renderer.bounds(left).orElseThrow());

        BufferedImage image = new BufferedImage(30, 30, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        assertTrue(renderer.render(graphics, left));
        graphics.dispose();
        // The rightmost blue column becomes the leftmost column after reflecting about footX=10.
        assertEquals(Color.BLUE.getRGB(), image.getRGB(7, 17));
        assertEquals(Color.RED.getRGB(), image.getRGB(10, 17));
        assertEquals(0, image.getRGB(11, 17));
        assertEquals(0, image.getRGB(7, 20));
    }

    @Test
    void opacityComposesWithParentAndRenderingPreservesGraphicsStateWithoutLoading() throws IOException {
        AtomicInteger opens = new AtomicInteger();
        SpriteRenderer renderer = renderer(opens);
        int opensAfterPreload = opens.get();
        BufferedImage image = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.translate(2, 3);
        graphics.setComposite(AlphaComposite.SrcOver.derive(0.5f));
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        AffineTransform original = graphics.getTransform();
        assertTrue(renderer.render(graphics, pose(true, 0.5f)));
        assertEquals(original, graphics.getTransform());
        assertEquals(AlphaComposite.SrcOver.derive(0.5f), graphics.getComposite());
        assertEquals(RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR,
                graphics.getRenderingHint(RenderingHints.KEY_INTERPOLATION));
        assertEquals(64, (image.getRGB(11, 20) >>> 24), 1);
        assertEquals(opensAfterPreload, opens.get());
        graphics.dispose();
    }

    @Test
    void poseScaleUsesPixelsPerLogicalUnitWithoutEntityGeometry() throws IOException {
        SpriteRenderer renderer = renderer(new AtomicInteger());
        VisualPose enlarged = new VisualPose("repair", "idle", 0, 100, 80, true, 4, 1);
        assertEquals(new SpriteRenderer.Bounds(98, 72, 8, 8), renderer.bounds(enlarged).orElseThrow());
        assertEquals(new AnimationClip.Point(106, 76), renderer.socket(enlarged, "muzzle").orElseThrow());
    }

    @Test
    void opacityPreservesSourceReplacementAndDestinationMaskRules() throws IOException {
        SpriteRenderer renderer = renderer(new AtomicInteger());
        for (int rule : new int[]{AlphaComposite.SRC, AlphaComposite.DST_IN}) {
            BufferedImage image = new BufferedImage(30, 30, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            try {
                graphics.setColor(Color.GREEN);
                graphics.fillRect(0, 0, 30, 30);
                AlphaComposite inherited = AlphaComposite.getInstance(rule, 0.5f);
                graphics.setComposite(inherited);
                assertTrue(renderer.render(graphics, pose(true, 0.5f)));
                assertEquals(inherited, graphics.getComposite());
                int pixel = image.getRGB(9, 17);
                // SRC replaces the opaque destination with a quarter-alpha red sprite.
                // DST_IN retains the green destination and masks its alpha to one quarter.
                assertEquals(64, pixel >>> 24, 1, "Rule " + rule + " must keep its alpha behavior");
                int expectedRgb = rule == AlphaComposite.SRC ? Color.RED.getRGB() : Color.GREEN.getRGB();
                assertEquals(expectedRgb & 0x00ffffff, pixel & 0x00ffffff);
                assertEquals(Color.GREEN.getRGB(), image.getRGB(0, 0), "Pixels outside the sprite remain untouched");
            } finally {
                graphics.dispose();
            }
        }
    }

    @Test
    void missingAssetsAndInvalidFrameSelectionsReturnFallbackSignal() throws IOException {
        SpriteRenderer renderer = renderer(new AtomicInteger());
        Graphics2D graphics = new BufferedImage(30, 30, BufferedImage.TYPE_INT_ARGB).createGraphics();
        for (VisualPose pose : new VisualPose[]{
                new VisualPose("missing", "idle", 0, 0, 0, true, 1, 1),
                new VisualPose("repair", "missing", 0, 0, 0, true, 1, 1),
                new VisualPose("repair", "idle", 50, 0, 0, true, 1, 1)}) {
            assertFalse(renderer.render(graphics, pose));
            assertTrue(renderer.bounds(pose).isEmpty());
            assertTrue(renderer.socket(pose, "muzzle").isEmpty());
        }
        assertTrue(renderer.socket(pose(true, 1), "missing").isEmpty());
        assertTrue(renderer.render(graphics, pose(true, 0)));
        assertFalse(new SpriteRenderer(AssetStore.empty()).render(graphics, pose(true, 1)));
        graphics.dispose();
    }

    private static VisualPose pose(boolean facingRight, float opacity) {
        return new VisualPose("repair", "idle", 0, 10, 20, facingRight, 2, opacity);
    }

    private static SpriteRenderer renderer(AtomicInteger opens) throws IOException {
        BufferedImage atlas = new BufferedImage(8, 4, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = atlas.createGraphics();
        graphics.setColor(Color.GREEN);
        graphics.fillRect(0, 0, 4, 4);
        graphics.setColor(Color.RED);
        graphics.fillRect(4, 0, 4, 4);
        graphics.setColor(Color.BLUE);
        graphics.fillRect(7, 0, 1, 4);
        graphics.dispose();
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(atlas, "png", png);
        String metadata = """
                format=1
                atlas=game/repair.png
                pixelsPerUnit=2
                default=idle
                clips=idle
                clip.idle.loop=true
                clip.idle.frames=only
                frame.only.rect=4,0,4,4
                frame.only.durationMs=100
                frame.only.anchor=1,4
                frame.only.sockets=muzzle
                frame.only.socket.muzzle=4,2
                """;
        Map<String, byte[]> files = Map.of("game/repair.png", png.toByteArray(),
                "game/repair.properties", metadata.getBytes(StandardCharsets.UTF_8));
        AssetStore assets = AssetStore.preload(new AssetCatalog(Map.of("repair", "game/repair.properties")), path -> {
            opens.incrementAndGet();
            return new ByteArrayInputStream(files.get(path));
        });
        assertTrue(assets.diagnostics().isEmpty());
        return new SpriteRenderer(assets);
    }
}
