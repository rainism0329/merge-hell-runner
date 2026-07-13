package com.bigphil.mergehell.render;

import com.bigphil.mergehell.world.DarkBiome;
import com.bigphil.mergehell.world.WorldScenery;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DarkWorldRendererTest {

    @Test
    void rendersEveryBiomeAtLargeCameraPositions() {
        DarkWorldRenderer renderer = new DarkWorldRenderer();
        for (DarkBiome biome : DarkBiome.values()) {
            BufferedImage image = new BufferedImage(960, 600, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            g.setColor(Color.MAGENTA);
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
            assertDoesNotThrow(() -> renderer.drawBackground(g, 960, 480, 125_000, biome, false));
            assertNotEquals(Color.MAGENTA.getRGB(), image.getRGB(10, 10));
            g.dispose();
        }
    }

    @Test
    void cullsSceneryAndPreservesTheCallerTransform() {
        DarkWorldRenderer renderer = new DarkWorldRenderer();
        BufferedImage image = new BufferedImage(960, 600, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.translate(17, 23);
        AffineTransform before = g.getTransform();
        List<WorldScenery> scenery = List.of(
                new WorldScenery(-5_000, 100, 100, 100,
                        WorldScenery.Kind.CITY_TOWER, WorldScenery.Layer.BACK, 0),
                new WorldScenery(260, 180, 140, 200,
                        WorldScenery.Kind.CITY_TOWER, WorldScenery.Layer.BACK, 1),
                new WorldScenery(8_000, 100, 100, 100,
                        WorldScenery.Kind.CITY_TOWER, WorldScenery.Layer.BACK, 2));

        assertDoesNotThrow(() -> renderer.drawScenery(g, scenery, WorldScenery.Layer.BACK,
                0, 960, DarkBiome.REPOSITORY_CITY));
        assertEquals(before, g.getTransform());
        g.dispose();
    }
}
