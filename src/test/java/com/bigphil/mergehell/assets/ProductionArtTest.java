package com.bigphil.mergehell.assets;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Contract for the shipped resources, rather than synthetic loader fixtures. */
class ProductionArtTest {
    private static final Map<String, String> RESOURCES = Map.of(
            "repair", "game/art/repair.properties",
            "hostiles", "game/art/hostiles.properties",
            "first-wave", "game/art/first-wave.properties",
            "city", "game/art/city.properties");

    @Test
    void allProductionAtlasesLoadThroughTheRealStoreWithoutDiagnostics() {
        AssetStore store = load();
        assertTrue(store.diagnostics().isEmpty(), () -> store.diagnostics().toString());
        assertEquals(RESOURCES.size(), store.spriteCount());
        assertEquals(RESOURCES.size(), store.atlasCount());
        assertNotNull(frame(store, "city", "backdrop"));
    }

    @Test
    void mechanicalPartsHaveRealTransparentOpaqueAndAntialiasedPixels() throws IOException {
        AssetStore store = load();
        for (String id : Set.of("repair", "hostiles", "first-wave")) {
            SpriteDefinition definition = store.find(id).orElseThrow().definition();
            try (InputStream input = ProductionArtTest.class.getClassLoader()
                    .getResourceAsStream(definition.atlasResource())) {
                assertNotNull(input, definition.atlasResource());
                BufferedImage atlas = ImageIO.read(input);
                assertNotNull(atlas, definition.atlasResource());
                assertTrue(atlas.getColorModel().hasAlpha(), id + " must not revert to RGB");
                for (AnimationClip clip : definition.animations().values()) {
                    for (AnimationClip.Frame frame : clip.frames()) {
                        boolean transparent = false, opaque = false, soft = false;
                        for (int y = frame.y(); y < frame.y() + frame.height(); y++) {
                            for (int x = frame.x(); x < frame.x() + frame.width(); x++) {
                                int alpha = atlas.getRGB(x, y) >>> 24;
                                transparent |= alpha == 0;
                                opaque |= alpha == 255;
                                soft |= alpha > 0 && alpha < 255;
                            }
                        }
                        String part = id + "/" + frame.id();
                        assertTrue(transparent, part + " needs a cutout, not a baked background");
                        assertTrue(opaque, part + " must contain visible material");
                        assertTrue(soft, part + " needs antialiased boundaries");
                    }
                }
            }
        }
    }

    @Test
    void requiredRigPartsAndAttachmentSocketsRemainAvailableAndInsideTheirFrames() {
        AssetStore store = load();
        Map<String, Set<String>> repair = Map.of(
                "head", Set.of("neck", "visor", "visor-top-left", "visor-bottom-right"),
                "torso", Set.of("neck", "shoulder", "hip"),
                "upper-arm", Set.of("distal"), "forearm", Set.of("distal"),
                "thigh", Set.of("distal"), "shin", Set.of("distal", "ankle", "heel", "toe"),
                "cannon", Set.of("muzzle", "grip"), "scarf", Set.of("tail"));
        Map<String, Set<String>> hostiles = Map.of(
                "bug-body", Set.of(), "bug-upper", Set.of("distal"),
                "bug-lower", Set.of("distal"),
                "debt-body", Set.of("hip", "shoulder-left", "shoulder-right"),
                "debt-arm", Set.of("distal"), "debt-leg", Set.of("distal"),
                "legacy-core", Set.of("reactor"), "node", Set.of("socket"));
        Map<String, Set<String>> firstWave = Map.of(
                "conflict", Set.of("core"), "crash", Set.of("core", "wheel", "wheel-rim"),
                "lock", Set.of("core", "aperture"), "firewall", Set.of("core"));
        Map.of("repair", repair, "hostiles", hostiles, "first-wave", firstWave).forEach((id, parts) ->
                parts.forEach((part, requiredSockets) -> {
                    AnimationClip.Frame frame = frame(store, id, part);
                    inside(frame.anchor(), frame, id + "/" + part + " anchor");
                    for (String socket : requiredSockets) {
                        AnimationClip.Point point = frame.sockets().get(socket);
                        assertNotNull(point, id + "/" + part + " missing " + socket);
                        inside(point, frame, id + "/" + part + " " + socket);
                    }
                }));
        AnimationClip.Frame cannon = frame(store, "repair", "cannon");
        assertNotEquals(cannon.sockets().get("grip"), cannon.sockets().get("muzzle"),
                "Weapon grip and firing origin must be distinct attachment points");
    }

    private static AssetStore load() {
        return AssetStore.preload(new AssetCatalog(RESOURCES), ProductionArtTest.class.getClassLoader());
    }

    private static AnimationClip.Frame frame(AssetStore store, String id, String part) {
        AnimationClip clip = store.find(id).orElseThrow().definition().animations().get(part);
        assertNotNull(clip, id + " missing " + part);
        assertFalse(clip.frames().isEmpty(), id + "/" + part + " has no frames");
        return clip.frames().get(0);
    }

    private static void inside(AnimationClip.Point point, AnimationClip.Frame frame, String name) {
        assertTrue(Double.isFinite(point.x()) && Double.isFinite(point.y()), name + " must be finite");
        assertTrue(point.x() >= 0 && point.x() < frame.width()
                && point.y() >= 0 && point.y() < frame.height(), name + " outside frame");
    }
}
