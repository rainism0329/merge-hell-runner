package com.bigphil.mergehell.assets;

import java.util.Objects;

/** Simulation-owned playback cursor. A paused world simply does not advance it. */
public final class Animator {
    private final SpriteDefinition definition;
    private AnimationClip clip;
    private long elapsedNanos;

    public Animator(SpriteDefinition definition) {
        this.definition = Objects.requireNonNull(definition, "definition");
        clip = definition.animations().get(definition.defaultAnimation());
    }

    public void play(String animation, boolean restart) {
        AnimationClip next = definition.animations().get(animation);
        if (next == null) throw new IllegalArgumentException("Unknown animation: " + animation);
        if (clip != next || restart) {
            clip = next;
            elapsedNanos = 0;
        }
    }

    public void advanceNanos(long deltaNanos) {
        if (deltaNanos < 0) throw new IllegalArgumentException("Animation delta must not be negative");
        long duration = clip.durationNanos();
        if (clip.loop()) {
            long remainder = deltaNanos % duration;
            // Modular addition without overflowing even for an arbitrarily long suspended frame.
            elapsedNanos = elapsedNanos >= duration - remainder
                    ? elapsedNanos - (duration - remainder) : elapsedNanos + remainder;
        } else {
            elapsedNanos = deltaNanos >= duration - elapsedNanos ? duration : elapsedNanos + deltaNanos;
        }
    }

    public String spriteId() { return definition.id(); }
    public String animation() { return clip.name(); }
    public int frameIndex() { return clip.frameIndexAt(elapsedNanos); }
    public AnimationClip.Frame frame() { return clip.frames().get(frameIndex()); }
    public long elapsedNanos() { return elapsedNanos; }
    public boolean finished() { return !clip.loop() && elapsedNanos == clip.durationNanos(); }
}
