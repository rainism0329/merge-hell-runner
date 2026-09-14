package com.bigphil.mergehell.render;

import com.bigphil.mergehell.assets.AnimationClip;
import com.bigphil.mergehell.assets.AssetCatalog;
import com.bigphil.mergehell.assets.AssetStore;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Independent painted chapters, with eager sprite mips and six fixed-size panorama buffers. */
public final class ChapterArt {
    private static final int BACKDROP_WIDTH = 1200;
    private static final int BACKDROP_HEIGHT = 600;
    private static final double[] PARALLAX = {.14, .12, .105};
    private static final class Holder { private static final ChapterArt INSTANCE = new ChapterArt(); }

    private final AssetStore actors;
    private record Outline(BufferedImage image, int artWidth, int artHeight, int padding) { }
    private final Map<String, Outline> outlines;
    private final BufferedImage[][] panoramas = new BufferedImage[3][2];
    private final List<AssetStore.Diagnostic> diagnostics;

    private ChapterArt() {
        ClassLoader loader = ChapterArt.class.getClassLoader();
        actors = AssetStore.preload(new AssetCatalog(Map.of(
                "chapter3-actors", "game/art/chapter3-actors.properties",
                "chapter4-actors", "game/art/chapter4-actors.properties",
                "chapter5-actors", "game/art/chapter5-actors.properties",
                "chapter3-terrain", "game/art/chapter3-terrain.properties",
                "chapter4-terrain", "game/art/chapter4-terrain.properties",
                "chapter5-terrain", "game/art/chapter5-terrain.properties")), loader);
        Map<String, Outline> silhouettes = new HashMap<>();
        for (int level = 2; level <= 4; level++) for (String part : List.of("enemy-a", "enemy-b", "enemy-c")) {
            var sprite = actors.find("chapter" + (level + 1) + "-actors").orElse(null);
            var frame = frame(level, part).orElse(null);
            if (sprite != null && frame != null) silhouettes.put(level + "/" + part, outline(sprite, frame));
        }
        outlines = Map.copyOf(silhouettes);
        List<AssetStore.Diagnostic> issues = new ArrayList<>(actors.diagnostics());
        for (int world = 3; world <= 5; world++) {
            String id = "chapter" + world + "-background";
            // This temporary store is released after resampling; source panoramas and their mips
            // are not retained alongside the six final opaque buffers.
            AssetStore background = AssetStore.preload(new AssetCatalog(Map.of(
                    id, "game/art/" + id + ".properties")), loader);
            issues.addAll(background.diagnostics());
            var sprite = background.find(id).orElse(null);
            if (sprite == null) continue;
            var frame = sprite.definition().animations().get("backdrop").frames().get(0);
            for (int mirror = 0; mirror < 2; mirror++) {
                BufferedImage tile = new BufferedImage(BACKDROP_WIDTH, BACKDROP_HEIGHT, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = tile.createGraphics();
                try {
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    Graphics2D art = (Graphics2D) g.create();
                    try {
                        if (mirror != 0) { art.translate(BACKDROP_WIDTH, 0); art.scale(-1, 1); }
                        art.scale(BACKDROP_WIDTH / (double) frame.width(), BACKDROP_HEIGHT / (double) frame.height());
                        sprite.paintFrame(art, frame);
                    } finally { art.dispose(); }
                    // A subtle baked top shade supports HUD legibility without flattening the
                    // distinct bright cloud citadel, orange cavern and violet organic palette.
                    g.setComposite(AlphaComposite.SrcOver);
                    g.setPaint(new GradientPaint(0, 0, new Color(8, 13, 24, world == 3 ? 66 : 38),
                            0, 220, new Color(8, 13, 24, 0)));
                    g.fillRect(0, 0, BACKDROP_WIDTH, 220);
                } finally { g.dispose(); }
                panoramas[world - 3][mirror] = tile;
            }
        }
        diagnostics = List.copyOf(issues);
    }

    /** Call during renderer construction, before the first gameplay paint. Shared across panels. */
    public static ChapterArt load() { return Holder.INSTANCE; }
    public static void preload() { load(); }
    public List<AssetStore.Diagnostic> diagnostics() { return diagnostics; }

    /** Level is zero-based; other worlds deliberately return false instead of borrowing a theme. */
    public boolean hasLevel(int level) {
        return level >= 2 && level <= 4 && panoramas[level - 2][0] != null
                && actors.find("chapter" + (level + 1) + "-actors").isPresent();
    }

    public Optional<AnimationClip.Frame> frame(int level, String part) {
        if (level < 2 || level > 4 || part == null) return Optional.empty();
        return actors.find("chapter" + (level + 1) + "-actors")
                .map(sprite -> sprite.definition().animations().get(part))
                .filter(clip -> !clip.frames().isEmpty()).map(clip -> clip.frames().get(0));
    }

    public Optional<AnimationClip.Frame> terrainFrame(int level, String part) {
        if (level < 2 || level > 4 || part == null) return Optional.empty();
        return actors.find("chapter" + (level + 1) + "-terrain")
                .map(sprite -> sprite.definition().animations().get(part))
                .filter(clip -> !clip.frames().isEmpty()).map(clip -> clip.frames().get(0));
    }

    /** Painted foreground pieces; the caller keeps walking surfaces aligned to actual geometry. */
    public boolean terrain(Graphics2D target, int level, String part, double x, double y, double width, double height) {
        var frame = terrainFrame(level, part).orElse(null);
        if (frame == null || width <= 0 || height <= 0) return false;
        var sprite = actors.find("chapter" + (level + 1) + "-terrain").orElseThrow();
        Graphics2D g = (Graphics2D) target.create();
        try {
            g.translate(x, y); g.scale(width / frame.width(), height / frame.height());
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            sprite.paintFrame(g, frame);
        } finally { g.dispose(); }
        return true;
    }

    public boolean fitTerrain(Graphics2D target, int level, String part, double x, double y, double width, double height) {
        var frame = terrainFrame(level, part).orElse(null);
        if (frame == null || width <= 0 || height <= 0) return false;
        double scale = Math.min(width / frame.width(), height / frame.height());
        double w = frame.width() * scale, h = frame.height() * scale;
        return terrain(target, level, part, x + (width - w) / 2, y + height - h, w, h);
    }

    /** A thin actual-silhouette rim separates small pale actors from the sky; never a box or oval. */
    public boolean fitOutline(Graphics2D target, int level, String part, double x, double y, double width, double height) {
        var outline = outlines.get(level + "/" + part);
        var frame = frame(level, part).orElse(null);
        if (outline == null || frame == null || width <= 0 || height <= 0) return false;
        double scale = Math.min(width / frame.width(), height / frame.height());
        double w = frame.width() * scale, h = frame.height() * scale;
        Graphics2D g = (Graphics2D) target.create();
        try {
            g.translate(x + (width - w) / 2, y + height - h);
            g.scale(w / outline.artWidth(), h / outline.artHeight());
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(outline.image(), -outline.padding(), -outline.padding(), null);
        } finally { g.dispose(); }
        return true;
    }

    private static Outline outline(AssetStore.LoadedSprite sprite, AnimationClip.Frame frame) {
        int padding = 6;
        double scale = Math.min(1, 160.0 / Math.max(frame.width(), frame.height()));
        int artWidth = Math.max(1, (int) Math.round(frame.width() * scale));
        int artHeight = Math.max(1, (int) Math.round(frame.height() * scale));
        int width = artWidth + padding * 2, height = artHeight + padding * 2;
        BufferedImage mask = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = mask.createGraphics();
        try {
            g.translate(padding, padding); g.scale(artWidth / (double) frame.width(), artHeight / (double) frame.height());
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            sprite.paintFrame(g, frame);
        } finally { g.dispose(); }
        int[] pixels = mask.getRGB(0, 0, width, height, null, 0, width);
        BufferedImage rim = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int local = pixels[y * width + x] >>> 24;
            if (local >= 250) continue;
            int inner = 0, outer = 0;
            for (int dy = -5; dy <= 5; dy++) for (int dx = -5; dx <= 5; dx++) {
                int distance = dx * dx + dy * dy;
                if (distance > 25 || x + dx < 0 || x + dx >= width || y + dy < 0 || y + dy >= height) continue;
                int alpha = pixels[(y + dy) * width + x + dx] >>> 24;
                outer = Math.max(outer, alpha);
                if (distance <= 9) inner = Math.max(inner, alpha);
            }
            int darkAlpha = Math.max(0, inner - local) * 218 / 255;
            int lightAlpha = Math.max(0, outer - Math.max(inner, local)) * 54 / 255;
            if (darkAlpha > 0) rim.setRGB(x, y, darkAlpha << 24 | 0x0c1119);
            else if (lightAlpha > 0) rim.setRGB(x, y, lightAlpha << 24 | 0xf4e6b4);
        }
        return new Outline(rim, artWidth, artHeight, padding);
    }

    public long residentOutlineBytes() {
        long count = 0;
        for (Outline outline : outlines.values()) count += (long) outline.image().getWidth() * outline.image().getHeight() * Integer.BYTES;
        return count;
    }

    /** Coordinates describe presentation geometry only; part metadata never changes collision. */
    public boolean part(Graphics2D target, int level, String part,
                        double x, double y, double width, double height) {
        return part(target, level, part, x, y, width, height, 0, 0, 0);
    }

    /** Rotation uses the caller-supplied normalized pivot, matching IndustrialArt.part. */
    public boolean part(Graphics2D target, int level, String part, double x, double y,
                        double width, double height, double radians, double pivotX, double pivotY) {
        if (level < 2 || level > 4 || width <= 0 || height <= 0) return false;
        var sprite = actors.find("chapter" + (level + 1) + "-actors").orElse(null);
        var frame = frame(level, part).orElse(null);
        if (sprite == null || frame == null) return false;
        Graphics2D g = (Graphics2D) target.create();
        try {
            g.translate(x, y); g.rotate(radians); g.translate(-pivotX * width, -pivotY * height);
            g.scale(width / frame.width(), height / frame.height());
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            sprite.paintFrame(g, frame);
        } finally { g.dispose(); }
        return true;
    }

    /** Keep the source silhouette's aspect ratio and anchor its feet to the requested box bottom. */
    public boolean fitPart(Graphics2D target, int level, String part,
                           double x, double y, double width, double height) {
        var frame = frame(level, part).orElse(null);
        if (frame == null || width <= 0 || height <= 0) return false;
        double scale = Math.min(width / frame.width(), height / frame.height());
        double w = frame.width() * scale, h = frame.height() * scale;
        return part(target, level, part, x + (width - w) / 2, y + height - h, w, h);
    }

    /** No image allocation, decoding, crop or lighting pass occurs while drawing the background. */
    public boolean backdrop(Graphics2D target, int width, int height, double cameraX, int level) {
        if (!hasLevel(level) || width <= 0 || height <= 0 || !Double.isFinite(cameraX)) return false;
        Graphics2D g = (Graphics2D) target.create();
        try {
            int tileWidth = height * 2;
            double travel = cameraX * PARALLAX[level - 2];
            long first = (long) Math.floor(travel / tileWidth);
            double offset = travel - first * tileWidth;
            int count = width / tileWidth + 2;
            for (int i = 0; i < count; i++) {
                BufferedImage tile = panoramas[level - 2][(int) Math.floorMod(first + i, 2)];
                int x = (int) Math.floor(i * tileWidth - offset);
                if (height == BACKDROP_HEIGHT) g.drawImage(tile, x, 0, null);
                else {
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g.drawImage(tile, x, 0, tileWidth, height, null);
                }
            }
        } finally { g.dispose(); }
        return true;
    }

    public long residentBackdropBytes() {
        long count = 0;
        for (BufferedImage[] pair : panoramas) for (BufferedImage tile : pair) {
            if (tile != null) count += (long) tile.getWidth() * tile.getHeight() * Integer.BYTES;
        }
        return count;
    }
}
