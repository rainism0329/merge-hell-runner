package com.bigphil.mergehell.render;

import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class LegacyWorldRendererTest {

    @Test
    void everyLaterLevelHasADistinctBackgroundSignature() {
        LegacyWorldRenderer renderer = new LegacyWorldRenderer();
        Set<Integer> signatures = new HashSet<>();
        Set<String> names = new HashSet<>();

        for (int level = 1; level <= 4; level++) {
            BufferedImage image = new BufferedImage(960, 480, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            renderer.drawBackground(g, level, 960, 480, 2_345, false);
            g.dispose();
            int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
            signatures.add(Arrays.hashCode(pixels));
            names.add(renderer.districtName(level));
            assertNotEquals(0, Arrays.hashCode(pixels));
        }

        assertEquals(4, signatures.size());
        assertEquals(4, names.size());
    }

    @Test
    void singularityRenderingPreservesTheCallerTransform() {
        LegacyWorldRenderer renderer = new LegacyWorldRenderer();
        BufferedImage image = new BufferedImage(960, 480, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.translate(17, 23);
        AffineTransform before = g.getTransform();

        renderer.drawBackground(g, 4, 900, 430, 7_200, true);

        assertEquals(before, g.getTransform());
        g.dispose();
    }
}
