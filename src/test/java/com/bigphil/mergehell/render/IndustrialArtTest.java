package com.bigphil.mergehell.render;

import com.bigphil.mergehell.assets.AssetCatalog;
import com.bigphil.mergehell.assets.AssetStore;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.IdentityHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class IndustrialArtTest {
    private static final AssetStore STORE = AssetStore.preload(new AssetCatalog(Map.of(
            "city", "game/art/city.properties")), IndustrialArtTest.class.getClassLoader());

    @Test void bakedLightingMatchesCompositionAcrossPaletteChangesAndReflectedSeams() {
        var art = new IndustrialArt(STORE);
        // An explicit full-frame clip selects the original separate lighting passes.
        // The cached path must preserve their pixels after both camera and palette changes.
        for (int level : new int[]{0, 3, 2, 4, 1, 0, -1}) {
            for (double camera : new double[]{-13.2, 80.5, 8571.4, 8571.5}) {
                for (int width : new int[]{600, 960}) {
                    var cached = render(art, width, camera, level, false);
                    var composed = render(art, width, camera, level, true);
                    assertArrayEquals(composed.getRGB(0, 0, width, 600, null, 0, width),
                            cached.getRGB(0, 0, width, 600, null, 0, width),
                            "Lighting at level " + level + ", camera " + camera + ", width " + width);
                    cached.flush(); composed.flush();
                }
            }
        }
    }

    @Test void repeatedPaletteChangesReuseTheSameBoundedRasterBuffers() throws Exception {
        var art = new IndustrialArt(STORE);
        var initial = rasters(art);
        long rasterBytes = initial.keySet().stream().mapToLong(image ->
                (long) image.getWidth() * image.getHeight() * Integer.BYTES).sum();
        assertTrue(rasterBytes <= 11_520_000L, "Only the base and lit panorama pairs may be retained");
        for (int level = 0; level < 25; level++) art.prepareBackdrop(level);
        var after = rasters(art);
        assertEquals(initial.size(), after.size());
        assertTrue(initial.keySet().stream().allMatch(after::containsKey),
                "Changing light must repaint existing buffers instead of retaining a texture per world or camera");
    }

    private static IdentityHashMap<BufferedImage, Boolean> rasters(IndustrialArt art) throws Exception {
        var result = new IdentityHashMap<BufferedImage, Boolean>();
        for (var field : IndustrialArt.class.getDeclaredFields()) {
            if (field.getType() != BufferedImage.class) continue;
            field.setAccessible(true);
            var image = (BufferedImage) field.get(art);
            if (image != null) result.put(image, true);
        }
        return result;
    }

    private static BufferedImage render(IndustrialArt art, int width, double camera, int level, boolean clipped) {
        var result = new BufferedImage(width, 600, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = result.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (clipped) g.setClip(0, 0, width, 600);
            assertTrue(art.backdrop(g, width, 600, camera, level));
        } finally { g.dispose(); }
        return result;
    }
}
