package com.bigphil.mergehell.assets;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Eagerly decoded, read-only assets. Loading belongs outside painting and simulation ticks. */
public final class AssetStore {
    private static final int MAX_ATLAS_EDGE = 8_192;
    private static final long MAX_ATLAS_PIXELS = 16_777_216L;

    @FunctionalInterface
    public interface ResourceSource {
        /** Returns null for a missing classpath resource. The store closes every returned stream. */
        InputStream open(String path) throws IOException;
    }

    public record Diagnostic(String spriteId, String resource, String message) { }

    /** Owns a shared atlas without exposing its mutable pixel buffer. */
    public static final class LoadedSprite {
        private final SpriteDefinition definition;
        private final BufferedImage atlas;
        private final MinifiedFrames minified;

        private LoadedSprite(SpriteDefinition definition, BufferedImage atlas) {
            this.definition = definition;
            this.atlas = atlas;
            minified = new MinifiedFrames(definition, atlas);
        }

        public SpriteDefinition definition() { return definition; }

        /** Paints in local atlas-pixel coordinates, using source rectangles without creating subimages. */
        public void paintFrame(Graphics2D graphics, AnimationClip.Frame frame) {
            BufferedImage reduced = minified.select(frame, graphics.getTransform());
            if (reduced != null) {
                graphics.drawImage(reduced, 0, 0, frame.width(), frame.height(), null);
                return;
            }
            graphics.drawImage(atlas, 0, 0, frame.width(), frame.height(),
                    frame.x(), frame.y(), frame.x() + frame.width(), frame.y() + frame.height(), null);
        }
    }

    private final Map<String, LoadedSprite> sprites;
    private final List<Diagnostic> diagnostics;
    private final int atlasCount;

    private AssetStore(Map<String, LoadedSprite> sprites, List<Diagnostic> diagnostics, int atlasCount) {
        this.sprites = Map.copyOf(sprites);
        this.diagnostics = List.copyOf(diagnostics);
        this.atlasCount = atlasCount;
    }

    public static AssetStore empty() { return new AssetStore(Map.of(), List.of(), 0); }

    public static AssetStore preload(AssetCatalog catalog, ClassLoader classLoader) {
        Objects.requireNonNull(classLoader, "classLoader");
        return preload(catalog, classLoader::getResourceAsStream);
    }

    public static AssetStore preload(AssetCatalog catalog, ResourceSource source) {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(source, "source");
        Map<String, LoadedSprite> sprites = new HashMap<>();
        Map<String, BufferedImage> atlases = new HashMap<>();
        Set<String> failedAtlases = new HashSet<>();
        List<Diagnostic> diagnostics = new ArrayList<>();
        catalog.spriteResources().forEach((id, metadataPath) -> {
            String resource = metadataPath;
            try {
                SpriteDefinition definition;
                try (InputStream stream = requireResource(source, metadataPath);
                     InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    definition = SpriteDefinition.read(id, reader);
                }
                resource = definition.atlasResource();
                if (failedAtlases.contains(resource)) throw new IOException("Shared atlas previously failed to load");
                BufferedImage atlas = atlases.get(resource);
                if (atlas == null) {
                    try {
                        atlas = readPng(source, resource);
                        atlases.put(resource, atlas);
                    } catch (IOException | IllegalArgumentException exception) {
                        failedAtlases.add(resource);
                        throw exception;
                    }
                }
                definition.validateAtlasSize(atlas.getWidth(), atlas.getHeight());
                sprites.put(id, new LoadedSprite(definition, atlas));
            } catch (IOException | IllegalArgumentException exception) {
                diagnostics.add(new Diagnostic(id, resource, exception.getMessage()));
            }
        });
        // Release atlases used only by invalid metadata; retain only successfully registered sprites.
        Set<BufferedImage> usedAtlases = new HashSet<>();
        for (LoadedSprite sprite : sprites.values()) usedAtlases.add(sprite.atlas);
        return new AssetStore(sprites, diagnostics, usedAtlases.size());
    }

    private static InputStream requireResource(ResourceSource source, String path) throws IOException {
        InputStream stream = source.open(path);
        if (stream == null) throw new IOException("Resource not found: " + path);
        return stream;
    }

    private static BufferedImage readPng(ResourceSource source, String path) throws IOException {
        // Use memory caching explicitly: ImageIO must not create disk cache files during preload.
        try (InputStream stream = requireResource(source, path);
             MemoryCacheImageInputStream input = new MemoryCacheImageInputStream(stream)) {
            ImageReader reader = ImageIO.getImageReadersByFormatName("png").next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > MAX_ATLAS_EDGE || height > MAX_ATLAS_EDGE
                        || (long) width * height > MAX_ATLAS_PIXELS) {
                    throw new IOException("Atlas exceeds preload size limit: " + width + "x" + height);
                }
                BufferedImage decoded = reader.read(0);
                BufferedImage premultiplied = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
                Graphics2D graphics = premultiplied.createGraphics();
                try {
                    graphics.drawImage(decoded, 0, 0, null);
                } finally {
                    graphics.dispose();
                }
                return premultiplied;
            } finally {
                reader.dispose();
            }
        }
    }

    public Optional<LoadedSprite> find(String spriteId) { return Optional.ofNullable(sprites.get(spriteId)); }
    public List<Diagnostic> diagnostics() { return diagnostics; }
    public int spriteCount() { return sprites.size(); }
    public int atlasCount() { return atlasCount; }
}
