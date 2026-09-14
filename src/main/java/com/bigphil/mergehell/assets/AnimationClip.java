package com.bigphil.mergehell.assets;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable animation data. All time is supplied by the simulation, never by painting. */
public final class AnimationClip {
    public record Point(double x, double y) {
        public Point {
            if (!Double.isFinite(x) || !Double.isFinite(y)) {
                throw new IllegalArgumentException("Anchor and socket coordinates must be finite");
            }
        }
    }

    public record Frame(String id, int x, int y, int width, int height, long durationNanos,
                        Point anchor, Map<String, Point> sockets) {
        public Frame {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(anchor, "anchor");
            sockets = Map.copyOf(sockets);
            if (x < 0 || y < 0 || width <= 0 || height <= 0 || durationNanos <= 0) {
                throw new IllegalArgumentException("Invalid region or duration for frame " + id);
            }
        }
    }

    private final String name;
    private final List<Frame> frames;
    private final boolean loop;
    private final long durationNanos;

    public AnimationClip(String name, List<Frame> frames, boolean loop) {
        this.name = Objects.requireNonNull(name, "name");
        this.frames = List.copyOf(frames);
        this.loop = loop;
        if (this.frames.isEmpty()) throw new IllegalArgumentException("Animation has no frames: " + name);
        long duration = 0;
        try {
            for (Frame frame : this.frames) duration = Math.addExact(duration, frame.durationNanos());
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Animation is too long: " + name, exception);
        }
        durationNanos = duration;
    }

    public String name() { return name; }
    public List<Frame> frames() { return frames; }
    public boolean loop() { return loop; }
    public long durationNanos() { return durationNanos; }

    public int frameIndexAt(long elapsedNanos) {
        if (elapsedNanos < 0) throw new IllegalArgumentException("Elapsed time must not be negative");
        long position = loop ? elapsedNanos % durationNanos : Math.min(elapsedNanos, durationNanos - 1);
        for (int i = 0; i < frames.size(); i++) {
            long duration = frames.get(i).durationNanos();
            if (position < duration) return i;
            position -= duration;
        }
        throw new IllegalStateException("Animation duration does not match its frames");
    }
}
