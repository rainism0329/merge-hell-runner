package com.bigphil.mergehell.render;

import org.junit.jupiter.api.Test;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class ChapterArtTest {
    @Test void allEighteenTerrainPiecesAndNineSmallSilhouetteCachesRenderAtGameScale() {
        ChapterArt art = ChapterArt.load();
        assertTrue(art.residentOutlineBytes() > 0);
        assertTrue(art.residentOutlineBytes() < 1_000_000, "nine near-game-size outline masks stay bounded");
        for (int level = 2; level <= 4; level++) {
            for (String name : new String[]{"deck", "platform", "support", "prop-a", "prop-b", "trim"}) {
                var frame = art.terrainFrame(level, name).orElseThrow();
                assertTrue(frame.width() > 40 && frame.height() > 40);
                BufferedImage image = new BufferedImage(100, 110, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = image.createGraphics();
                try {
                    g.setColor(Color.CYAN);
                    assertTrue(art.terrain(g, level, name, 10, 10, 80, 90));
                    assertEquals(Color.CYAN, g.getColor());
                } finally { g.dispose(); }
                int painted = 0;
                for (int y = 10; y < 100; y++) for (int x = 10; x < 90; x++) {
                    if ((image.getRGB(x, y) >>> 24) > 100) painted++;
                }
                assertTrue(painted > 300, name + " must have solid material");
                assertEquals(0, image.getRGB(0, 0));
            }
            for (String name : new String[]{"enemy-a", "enemy-b", "enemy-c"}) {
                BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = image.createGraphics();
                try { assertTrue(art.fitOutline(g, level, name, 20, 20, 60, 60)); }
                finally { g.dispose(); }
                int painted = 0;
                for (int y = 0; y < 100; y++) for (int x = 0; x < 100; x++) if ((image.getRGB(x, y) >>> 24) > 20) painted++;
                assertTrue(painted > 20 && painted < 3000, "outline must follow sparse real silhouette");
                assertEquals(0, image.getRGB(0, 0));
            }
        }
        assertTrue(art.terrainFrame(1, "deck").isEmpty());
        assertTrue(art.terrainFrame(2, "absent").isEmpty());
    }

    @Test void allThreeDistinctChaptersLoadAllEighteenPartsAndBoundedBackgrounds() {
        ChapterArt art = ChapterArt.load();
        assertSame(art, ChapterArt.load());
        assertTrue(art.diagnostics().isEmpty(), art.diagnostics().toString());
        assertEquals(17_280_000, art.residentBackdropBytes());
        for (int level = 2; level <= 4; level++) {
            assertTrue(art.hasLevel(level));
            for (String name : new String[]{"enemy-a", "enemy-b", "enemy-c", "boss-shell", "boss-limb", "boss-core"}) {
                var frame = art.frame(level, name).orElseThrow();
                assertTrue(frame.width() > 100 && frame.height() > 100);
                assertFalse(frame.sockets().isEmpty());
                BufferedImage image = new BufferedImage(160, 180, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = image.createGraphics();
                try { assertTrue(art.part(g, level, name, 10, 10, 140, 160)); }
                finally { g.dispose(); }
                int solid = 0, clear = 0;
                for (int y = 10; y < 170; y++) for (int x = 10; x < 150; x++) {
                    int alpha = image.getRGB(x, y) >>> 24;
                    if (alpha >= 240) solid++;
                    if (alpha == 0) clear++;
                }
                assertTrue(solid > 1000, name + " has no opaque material");
                assertTrue(clear > 1000, name + " has no transparent silhouette");
            }
        }
        assertFalse(art.hasLevel(0)); assertFalse(art.hasLevel(1)); assertFalse(art.hasLevel(5));
        assertTrue(art.frame(0, "boss-shell").isEmpty());
        assertTrue(art.frame(2, "missing").isEmpty());
    }

    @Test void drawingPreservesCallerStateAndDoesNotReuseAnotherChaptersPicture() {
        ChapterArt art = ChapterArt.load();
        int[][] pixels = new int[3][];
        for (int level = 2; level <= 4; level++) {
            BufferedImage image = new BufferedImage(600, 400, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            try {
                g.setColor(Color.MAGENTA); g.setStroke(new BasicStroke(4));
                g.setComposite(AlphaComposite.SrcOver.derive(.75f)); g.translate(2, 1);
                g.clipRect(0, 0, 580, 390);
                AffineTransform tx = g.getTransform(); Shape clip = g.getClip();
                assertTrue(art.backdrop(g, 600, 400, -420, level));
                assertTrue(art.part(g, level, "boss-limb", 220, 200, 50, 80, .15, .5, .2));
                assertEquals(tx, g.getTransform()); assertEquals(clip.getBounds(), g.getClip().getBounds());
                assertEquals(Color.MAGENTA, g.getColor()); assertEquals(new BasicStroke(4), g.getStroke());
                assertEquals(AlphaComposite.SrcOver.derive(.75f), g.getComposite());
            } finally { g.dispose(); }
            pixels[level - 2] = image.getRGB(0, 0, 600, 400, null, 0, 600);
        }
        assertFalse(Arrays.equals(pixels[0], pixels[1]));
        assertFalse(Arrays.equals(pixels[0], pixels[2]));
        assertFalse(Arrays.equals(pixels[1], pixels[2]));
    }
}
