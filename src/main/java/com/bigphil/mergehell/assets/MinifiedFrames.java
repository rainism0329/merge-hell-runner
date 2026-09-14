package com.bigphil.mergehell.assets;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Precomputed area-like reduction prevents large illustrated parts from shimmering at game size. */
final class MinifiedFrames {
    private record Region(int x, int y, int width, int height) {
        static Region of(AnimationClip.Frame frame) {
            return new Region(frame.x(), frame.y(), frame.width(), frame.height());
        }
    }

    // Added rasters are bounded even if metadata describes many overlapping atlas regions.
    private static final long MAX_PIXELS = 5_592_405;
    private final Map<Region, List<BufferedImage>> reductions;

    MinifiedFrames(SpriteDefinition definition, BufferedImage atlas) {
        Map<Region, List<BufferedImage>> prepared = new HashMap<>();
        long remaining = MAX_PIXELS;
        for (var clip : definition.animations().values()) for (var frame : clip.frames()) {
            Region region = Region.of(frame);
            if (prepared.containsKey(region)) continue;
            List<BufferedImage> levels = new ArrayList<>();
            int width = frame.width(), height = frame.height();
            BufferedImage previous = null;
            while (Math.min(width, height) >= 8 && Math.max(width, height) >= 32) {
                int nextWidth = Math.max(1, (width + 1) / 2), nextHeight = Math.max(1, (height + 1) / 2);
                long pixels = (long) nextWidth * nextHeight;
                if (pixels > remaining) break;
                BufferedImage next = new BufferedImage(nextWidth, nextHeight, BufferedImage.TYPE_INT_ARGB_PRE);
                Graphics2D g = next.createGraphics();
                try {
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    if (previous == null) g.drawImage(atlas, 0, 0, nextWidth, nextHeight,
                            frame.x(), frame.y(), frame.x() + width, frame.y() + height, null);
                    else g.drawImage(previous, 0, 0, nextWidth, nextHeight, null);
                } finally { g.dispose(); }
                levels.add(next); previous = next;
                remaining -= pixels; width = nextWidth; height = nextHeight;
            }
            prepared.put(region, List.copyOf(levels));
        }
        reductions = Map.copyOf(prepared);
    }

    BufferedImage select(AnimationClip.Frame frame, AffineTransform transform) {
        List<BufferedImage> levels = reductions.get(Region.of(frame));
        if (levels == null || levels.isEmpty()) return null;
        // Includes caller scale, rotation and reflection; use the larger axis to avoid upsampling.
        double scale = Math.max(Math.hypot(transform.getScaleX(), transform.getShearY()),
                Math.hypot(transform.getShearX(), transform.getScaleY()));
        BufferedImage chosen = null;
        for (BufferedImage level : levels) {
            if (level.getWidth() < frame.width() * scale || level.getHeight() < frame.height() * scale) break;
            chosen = level;
        }
        return chosen;
    }
}
