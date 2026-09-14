package com.bigphil.mergehell.render;

import com.bigphil.mergehell.assets.AnimationClip;
import com.bigphil.mergehell.assets.AssetStore;
import com.bigphil.mergehell.assets.SpriteDefinition;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Objects;
import java.util.Optional;

/** Paints cached sprites at a logical foot anchor; missing sprites leave fallback policy to the caller. */
public final class SpriteRenderer {
    public record Bounds(double x, double y, double width, double height) { }
    private record Selection(AssetStore.LoadedSprite sprite, AnimationClip.Frame frame) { }

    private final AssetStore assets;

    public SpriteRenderer(AssetStore assets) { this.assets = Objects.requireNonNull(assets, "assets"); }

    /** Returns false when the resource, animation or frame is unavailable. Does not load resources. */
    public boolean render(Graphics2D graphics, VisualPose pose) {
        Selection selection = select(pose);
        if (selection == null) return false;
        if (pose.opacity() == 0) return true;
        SpriteDefinition definition = selection.sprite().definition();
        AnimationClip.Frame frame = selection.frame();
        double unitScale = pose.scale() / definition.pixelsPerUnit();
        Graphics2D local = (Graphics2D) graphics.create();
        try {
            local.translate(pose.footX(), pose.footY());
            local.scale(pose.facingRight() ? unitScale : -unitScale, unitScale);
            local.translate(-frame.anchor().x(), -frame.anchor().y());
            local.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            if (pose.opacity() < 1) {
                AlphaComposite composite = local.getComposite() instanceof AlphaComposite inherited
                        ? inherited : AlphaComposite.SrcOver;
                local.setComposite(composite.derive(composite.getAlpha() * pose.opacity()));
            }
            selection.sprite().paintFrame(local, frame);
        } finally {
            local.dispose();
        }
        return true;
    }

    /** Uses the same transform as rendering, including frame-specific anchors and horizontal mirroring. */
    public Optional<AnimationClip.Point> socket(VisualPose pose, String name) {
        Selection selection = select(pose);
        if (selection == null) return Optional.empty();
        AnimationClip.Point point = selection.frame().sockets().get(name);
        if (point == null) return Optional.empty();
        double scale = pose.scale() / selection.sprite().definition().pixelsPerUnit();
        AnimationClip.Point anchor = selection.frame().anchor();
        return Optional.of(new AnimationClip.Point(
                pose.footX() + (point.x() - anchor.x()) * scale * (pose.facingRight() ? 1 : -1),
                pose.footY() + (point.y() - anchor.y()) * scale));
    }

    /** Visual bounds only; these must not replace the actor's collision or attack rectangles. */
    public Optional<Bounds> bounds(VisualPose pose) {
        Selection selection = select(pose);
        if (selection == null) return Optional.empty();
        AnimationClip.Frame frame = selection.frame();
        double scale = pose.scale() / selection.sprite().definition().pixelsPerUnit();
        double left = pose.facingRight() ? -frame.anchor().x() : frame.anchor().x() - frame.width();
        return Optional.of(new Bounds(pose.footX() + left * scale,
                pose.footY() - frame.anchor().y() * scale, frame.width() * scale, frame.height() * scale));
    }

    private Selection select(VisualPose pose) {
        Objects.requireNonNull(pose, "pose");
        AssetStore.LoadedSprite sprite = assets.find(pose.spriteId()).orElse(null);
        if (sprite == null) return null;
        AnimationClip clip = sprite.definition().animations().get(pose.animation());
        if (clip == null || pose.frameIndex() >= clip.frames().size()) return null;
        return new Selection(sprite, clip.frames().get(pose.frameIndex()));
    }
}
