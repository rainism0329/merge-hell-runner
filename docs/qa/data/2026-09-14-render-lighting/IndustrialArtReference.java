package com.bigphil.mergehell.render;

import com.bigphil.mergehell.assets.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Map;

/** Preloaded production art. No file access, cropping or decoding occurs during a draw. */
public final class IndustrialArtReference {
    private final AssetStore store;
    private final BufferedImage panorama;
    private final BufferedImage panoramaMirrored;

    public IndustrialArtReference(AssetStore store) {
        this.store = store;
        panorama = preloadPanorama(false);
        panoramaMirrored = preloadPanorama(true);
    }

    private BufferedImage preloadPanorama(boolean mirror) {
        var sprite = store.find("city").orElse(null);
        if (sprite == null) return null;
        var clip = sprite.definition().animations().get("backdrop");
        if (clip == null || clip.frames().isEmpty()) return null;
        var frame = clip.frames().get(0);
        int h = GameViewport.LOGICAL_HEIGHT, w = h * 2;
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            if (mirror) { g.translate(w, 0); g.scale(-1, 1); }
            g.scale(w / (double) frame.width(), h / (double) frame.height());
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            sprite.paintFrame(g, frame);
        } finally { g.dispose(); }
        return image;
    }

    public static IndustrialArtReference load() {
        return new IndustrialArtReference(AssetStore.preload(new AssetCatalog(Map.of(
                "repair", "game/art/repair.properties",
                "hostiles", "game/art/hostiles.properties",
                "first-wave", "game/art/first-wave.properties",
                "city", "game/art/city.properties")), IndustrialArtReference.class.getClassLoader()));
    }

    public boolean has(String id) { return store.find(id).isPresent(); }
    public java.util.List<AssetStore.Diagnostic> diagnostics() { return store.diagnostics(); }
    public java.util.Optional<AnimationClip.Frame> frame(String id, String part) {
        return store.find(id).map(sprite -> sprite.definition().animations().get(part))
                .filter(clip -> !clip.frames().isEmpty()).map(clip -> clip.frames().get(0));
    }

    /** The requested rectangle is visual geometry only; physics never uses it. */
    public boolean part(Graphics2D target, String id, String part, double x, double y,
                        double width, double height, double radians, double pivotX, double pivotY) {
        var loaded = store.find(id).orElse(null);
        if (loaded == null) return false;
        var clip = loaded.definition().animations().get(part);
        if (clip == null) return false;
        var frame = clip.frames().get(0);
        Graphics2D g = (Graphics2D) target.create();
        try {
            g.translate(x, y);
            g.rotate(radians);
            g.translate(-pivotX * width, -pivotY * height);
            g.scale(width / frame.width(), height / frame.height());
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            loaded.paintFrame(g, frame);
        } finally { g.dispose(); }
        return true;
    }

    public boolean part(Graphics2D g, String id, String part, double x, double y, double w, double h) {
        return part(g, id, part, x, y, w, h, 0, 0, 0);
    }

    /** Panoramas repeat by reflection, keeping edge pixels continuous without runtime image copies. */
    public boolean backdrop(Graphics2D original, int width, int height, double cameraX, int level) {
        if (!has("city")) return false;
        Graphics2D g = (Graphics2D) original.create();
        try {
            int tileW = height * 2;
            double travel = cameraX * 0.14;
            int tile = (int) Math.floor(travel / tileW);
            double offset = travel - tile * tileW;
            for (int i = -1; i <= width / tileW + 1; i++) {
                if (panorama != null && height == GameViewport.LOGICAL_HEIGHT) {
                    BufferedImage tileImage = Math.floorMod(tile + i, 2) == 0 ? panorama : panoramaMirrored;
                    g.drawImage(tileImage, (int) Math.floor(i * tileW - offset), 0, null);
                    continue;
                }
                Graphics2D cell = (Graphics2D) g.create();
                try {
                    cell.translate(i * tileW - offset, 0);
                    if (Math.floorMod(tile + i, 2) != 0) { cell.translate(tileW, 0); cell.scale(-1, 1); }
                    part(cell, "city", "backdrop", 0, 0, tileW, height);
                } finally { cell.dispose(); }
            }
            // World colors remain local lighting over the same warm industrial material family.
            Color[] light = {new Color(35, 24, 15, 35), new Color(38, 65, 42, 42),
                    new Color(41, 55, 85, 40), new Color(100, 32, 19, 45), new Color(32, 39, 85, 48)};
            g.setColor(light[Math.floorMod(level, light.length)]);
            g.fillRect(0, 0, width, height);
            g.setColor(new Color(6, 13, 19, 58));
            g.fillRect(0, 0, width, height);
            g.setPaint(new GradientPaint(0, 0, new Color(3, 7, 12, 105),
                    0, height * 0.5f, new Color(4, 8, 12, 10)));
            g.fillRect(0, 0, width, height / 2);
        } finally { g.dispose(); }
        return true;
    }
}
