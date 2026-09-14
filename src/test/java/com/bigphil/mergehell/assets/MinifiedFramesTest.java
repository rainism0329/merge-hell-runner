package com.bigphil.mergehell.assets;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MinifiedFramesTest {
    @Test void tinyDrawAveragesFineTextureWithoutBleedingAdjacentAtlasPartsOrReadingResources() throws Exception {
        // Half-transparent white grille beside a solid red part. Red must never enter its reduction.
        BufferedImage atlas = new BufferedImage(128, 64, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 64; y++) for (int x = 0; x < 128; x++)
            atlas.setRGB(x, y, x >= 64 ? 0xffff0000 : ((x + y) % 2 == 0 ? 0xffffffff : 0));
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(atlas, "png", png);
        byte[] metadata = """
                format=1
                atlas=grille.png
                pixelsPerUnit=1
                default=idle
                clips=idle
                clip.idle.loop=true
                clip.idle.frames=one
                frame.one.rect=0,0,64,64
                frame.one.durationMs=100
                frame.one.anchor=32,64
                """.getBytes(StandardCharsets.UTF_8);
        AtomicInteger reads = new AtomicInteger();
        AssetStore store = AssetStore.preload(new AssetCatalog(Map.of("grille", "grille.properties")), path -> {
            reads.incrementAndGet();
            return new ByteArrayInputStream(path.endsWith(".png") ? png.toByteArray() : metadata);
        });
        assertTrue(store.diagnostics().isEmpty());
        var sprite = store.find("grille").orElseThrow();
        var frame = sprite.definition().animations().get("idle").frames().get(0);
        int readsAfterLoad = reads.get();
        for (int direction : new int[]{1, -1}) {
            BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
            var g = image.createGraphics();
            try {
                if (direction < 0) g.translate(4, 0);
                g.scale(direction / 16.0, 1 / 16.0);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                sprite.paintFrame(g, frame);
            } finally { g.dispose(); }
            for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) {
                int rgba = image.getRGB(x, y);
                assertTrue((rgba >>> 24) >= 126 && (rgba >>> 24) <= 129, "coverage must average to half");
                assertEquals(0x00ffffff, rgba & 0x00ffffff, "adjacent red must not bleed into white grille");
            }
        }
        assertEquals(readsAfterLoad, reads.get());
    }
}
