package com.bigphil.mergehell.render;

import java.util.Objects;

/** Deeply immutable visual state, safe to include in a render snapshot without retaining an entity. */
public record VisualPose(String spriteId, String animation, int frameIndex,
                         double footX, double footY, boolean facingRight, double scale, float opacity) {
    public VisualPose {
        Objects.requireNonNull(spriteId, "spriteId");
        Objects.requireNonNull(animation, "animation");
        if (frameIndex < 0 || !Double.isFinite(footX) || !Double.isFinite(footY)
                || !Double.isFinite(scale) || scale <= 0 || !Float.isFinite(opacity)
                || opacity < 0 || opacity > 1) {
            throw new IllegalArgumentException("Invalid visual pose");
        }
    }
}
