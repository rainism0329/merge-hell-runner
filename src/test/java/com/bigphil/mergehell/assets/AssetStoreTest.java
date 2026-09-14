package com.bigphil.mergehell.assets;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AssetStoreTest {
    @Test
    void catalogPreservesStableIdsAndNormalizesClasspathPaths() throws IOException {
        AssetCatalog catalog = AssetCatalog.read(new StringReader("""
                format=1
                sprites=repair,bug
                sprite.repair=/game/repair.properties
                sprite.bug=game/bug.properties
                """));
        assertEquals("game/repair.properties", catalog.spriteResources().get("repair"));
        assertEquals("repair", catalog.spriteResources().keySet().iterator().next());
        assertThrows(UnsupportedOperationException.class, () -> catalog.spriteResources().clear());
        assertThrows(IllegalArgumentException.class,
                () -> new AssetCatalog(Map.of("bad", "../private.properties")));
    }

    @Test
    void repeatedFramesAreAllowedButDuplicateAnimationNamesAreRejected() throws IOException {
        SpriteDefinition repeated = SpriteDefinition.read("repair", new StringReader(metadata()
                .replace("clip.idle.frames=first,second", "clip.idle.frames=first,second,first")));
        assertEquals(3, repeated.animations().get("idle").frames().size());
        assertSame(repeated.animations().get("idle").frames().get(0), repeated.animations().get("idle").frames().get(2));
        assertThrows(IllegalArgumentException.class, () -> SpriteDefinition.read("repair",
                new StringReader(metadata().replace("clips=idle", "clips=idle,idle"))));
    }

    @Test
    void sharedAtlasLoadsOnceAndEveryStreamIsClosed() throws IOException {
        Map<String, byte[]> resources = resources();
        resources.put("game/bug.properties", utf8(metadata()));
        AtomicInteger opened = new AtomicInteger();
        AtomicInteger closed = new AtomicInteger();
        Map<String, Integer> reads = new HashMap<>();
        AssetStore store = AssetStore.preload(new AssetCatalog(Map.of(
                "repair", "game/repair.properties", "bug", "game/bug.properties")), path -> {
            opened.incrementAndGet();
            reads.merge(path, 1, Integer::sum);
            return new ByteArrayInputStream(resources.get(path)) {
                private boolean alreadyClosed;
                @Override public void close() throws IOException {
                    if (!alreadyClosed) { closed.incrementAndGet(); alreadyClosed = true; }
                    super.close();
                }
            };
        });
        assertEquals(2, store.spriteCount());
        assertEquals(1, store.atlasCount());
        assertEquals(1, reads.get("game/repair.png"));
        assertEquals(opened.get(), closed.get());
        assertTrue(store.diagnostics().isEmpty());
    }

    @Test
    void missingMetadataAndOutOfBoundsFrameLeaveOtherSpritesAvailable() throws IOException {
        Map<String, byte[]> resources = resources();
        resources.put("game/bad.properties", utf8(metadata().replace("4,0,4,4", "7,0,4,4")));
        LinkedHashMap<String, String> entries = new LinkedHashMap<>();
        entries.put("missing", "game/missing.properties");
        entries.put("bad", "game/bad.properties");
        entries.put("repair", "game/repair.properties");
        AssetStore store = AssetStore.preload(new AssetCatalog(entries), path -> stream(resources.get(path)));
        assertTrue(store.find("repair").isPresent());
        assertTrue(store.find("missing").isEmpty());
        assertTrue(store.find("bad").isEmpty());
        assertEquals(2, store.diagnostics().size());
        assertTrue(store.diagnostics().stream().anyMatch(d -> d.message().contains("exceeds")));
    }

    @Test
    void corruptSharedAtlasIsAttemptedOnceAndProducesADiagnosticForEverySprite() {
        Map<String, byte[]> resources = new HashMap<>();
        resources.put("game/repair.properties", utf8(metadata()));
        resources.put("game/bug.properties", utf8(metadata()));
        resources.put("game/repair.png", new byte[]{1, 2, 3});
        AtomicInteger imageReads = new AtomicInteger();
        AssetStore store = AssetStore.preload(new AssetCatalog(Map.of(
                "repair", "game/repair.properties", "bug", "game/bug.properties")), path -> {
            if (path.endsWith(".png")) imageReads.incrementAndGet();
            return stream(resources.get(path));
        });
        assertEquals(0, store.spriteCount());
        assertEquals(1, imageReads.get());
        assertEquals(2, store.diagnostics().size());
    }

    @Test
    void metadataRejectsUnsafeTimesAndMalformedCoordinatesBeforeRendering() {
        for (String bad : new String[]{
                metadata().replace("durationMs=100", "durationMs=0"),
                metadata().replace("durationMs=100", "durationMs=9223372036854775807"),
                metadata().replace("pixelsPerUnit=2", "pixelsPerUnit=NaN"),
                metadata().replace("loop=true", "loop=maybe"),
                metadata().replace("anchor=1,4", "anchor=NaN,4")}) {
            assertThrows(IllegalArgumentException.class,
                    () -> SpriteDefinition.read("repair", new StringReader(bad)));
        }
    }

    private static Map<String, byte[]> resources() throws IOException {
        BufferedImage atlas = new BufferedImage(8, 4, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = atlas.createGraphics();
        graphics.setColor(Color.RED);
        graphics.fillRect(0, 0, 4, 4);
        graphics.setColor(Color.BLUE);
        graphics.fillRect(4, 0, 4, 4);
        graphics.dispose();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(atlas, "png", output);
        Map<String, byte[]> resources = new HashMap<>();
        resources.put("game/repair.properties", utf8(metadata()));
        resources.put("game/repair.png", output.toByteArray());
        return resources;
    }

    private static ByteArrayInputStream stream(byte[] bytes) {
        return bytes == null ? null : new ByteArrayInputStream(bytes);
    }
    private static byte[] utf8(String value) { return value.getBytes(StandardCharsets.UTF_8); }

    private static String metadata() {
        return """
                format=1
                atlas=game/repair.png
                pixelsPerUnit=2
                layer=20
                default=idle
                clips=idle
                clip.idle.loop=true
                clip.idle.frames=first,second
                frame.first.rect=0,0,4,4
                frame.first.durationMs=100
                frame.first.anchor=1,4
                frame.first.sockets=muzzle
                frame.first.socket.muzzle=4,2
                frame.second.rect=4,0,4,4
                frame.second.durationMs=200
                frame.second.anchor=1,4
                """;
    }
}
