package com.bigphil.mergehell.assets;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * Visual geometry in atlas pixels; contains no collision or damage geometry.
 * Anchors and sockets are relative to each frame's top-left, not the atlas origin.
 * The anchor maps to the pose's logical foot position in either facing direction.
 * Metadata is UTF-8 properties, for example:
 * <pre>
 * format=1
 * atlas=game/actors/repair.png
 * pixelsPerUnit=2
 * layer=20
 * default=idle
 * clips=idle
 * clip.idle.loop=true
 * clip.idle.frames=idle0,idle1
 * frame.idle0.rect=0,0,128,128
 * frame.idle0.durationMs=120
 * frame.idle0.anchor=64,120
 * frame.idle0.sockets=muzzle
 * frame.idle0.socket.muzzle=110,58
 * frame.idle1.rect=128,0,128,128
 * frame.idle1.durationMs=120
 * frame.idle1.anchor=64,120
 * </pre>
 * Atlas paths start at the classpath root. Layer order is advisory for the caller's
 * draw order; separate body/weapon definitions can attach through frame sockets.
 */
public record SpriteDefinition(String id, String atlasResource, double pixelsPerUnit, int layerOrder,
                               String defaultAnimation, Map<String, AnimationClip> animations) {
    public SpriteDefinition {
        AssetCatalog.identifier(id, "sprite ID");
        atlasResource = AssetCatalog.resourcePath(atlasResource, ".png");
        if (!Double.isFinite(pixelsPerUnit) || pixelsPerUnit <= 0) {
            throw new IllegalArgumentException("pixelsPerUnit must be positive and finite");
        }
        Objects.requireNonNull(defaultAnimation, "defaultAnimation");
        animations = Collections.unmodifiableMap(new LinkedHashMap<>(animations));
        if (!animations.containsKey(defaultAnimation)) {
            throw new IllegalArgumentException("Missing default animation: " + defaultAnimation);
        }
        animations.forEach((name, clip) -> {
            if (!name.equals(clip.name())) throw new IllegalArgumentException("Animation name mismatch: " + name);
        });
    }

    public static SpriteDefinition read(String id, Reader reader) throws IOException {
        Properties properties = new Properties();
        properties.load(reader);
        AssetCatalog.requireVersion(properties);
        Map<String, AnimationClip.Frame> framesById = new LinkedHashMap<>();
        Map<String, AnimationClip> animations = new LinkedHashMap<>();
        for (String clipName : AssetCatalog.names(properties, "clips", false)) {
            String prefix = "clip." + clipName + ".";
            List<AnimationClip.Frame> frames = new ArrayList<>();
            for (String frameId : AssetCatalog.names(properties, prefix + "frames", false, true)) {
                frames.add(framesById.computeIfAbsent(frameId, key -> readFrame(properties, key)));
            }
            String loop = AssetCatalog.required(properties, prefix + "loop");
            if (!loop.equals("true") && !loop.equals("false")) {
                throw new IllegalArgumentException(prefix + "loop must be true or false");
            }
            animations.put(clipName, new AnimationClip(clipName, frames, Boolean.parseBoolean(loop)));
        }
        return new SpriteDefinition(id, AssetCatalog.required(properties, "atlas"),
                Double.parseDouble(AssetCatalog.required(properties, "pixelsPerUnit")),
                Integer.parseInt(properties.getProperty("layer", "0").trim()),
                AssetCatalog.required(properties, "default"), animations);
    }

    private static AnimationClip.Frame readFrame(Properties properties, String id) {
        String prefix = "frame." + id + ".";
        String[] rect = tuple(properties, prefix + "rect", 4);
        long duration;
        try {
            duration = Math.multiplyExact(Long.parseLong(AssetCatalog.required(properties, prefix + "durationMs")),
                    1_000_000L);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Frame duration is too long: " + id, exception);
        }
        AnimationClip.Point anchor = point(properties, prefix + "anchor");
        Map<String, AnimationClip.Point> sockets = new LinkedHashMap<>();
        for (String name : AssetCatalog.names(properties, prefix + "sockets", true)) {
            sockets.put(name, point(properties, prefix + "socket." + name));
        }
        return new AnimationClip.Frame(id, Integer.parseInt(rect[0]), Integer.parseInt(rect[1]),
                Integer.parseInt(rect[2]), Integer.parseInt(rect[3]), duration, anchor, sockets);
    }

    private static AnimationClip.Point point(Properties properties, String key) {
        String[] pair = tuple(properties, key, 2);
        return new AnimationClip.Point(Double.parseDouble(pair[0]), Double.parseDouble(pair[1]));
    }

    private static String[] tuple(Properties properties, String key, int size) {
        String[] values = AssetCatalog.required(properties, key).split(",", -1);
        if (values.length != size) throw new IllegalArgumentException("Expected " + size + " values: " + key);
        for (int i = 0; i < values.length; i++) values[i] = values[i].trim();
        return values;
    }

    public void validateAtlasSize(int width, int height) {
        for (AnimationClip clip : animations.values()) {
            for (AnimationClip.Frame frame : clip.frames()) {
                if ((long) frame.x() + frame.width() > width || (long) frame.y() + frame.height() > height) {
                    throw new IllegalArgumentException("Frame " + frame.id() + " exceeds " + width + "x" + height + " atlas");
                }
            }
        }
    }
}
