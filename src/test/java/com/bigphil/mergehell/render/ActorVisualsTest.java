package com.bigphil.mergehell.render;

import com.bigphil.mergehell.assets.AssetStore;
import com.bigphil.mergehell.model.EntityType;
import com.bigphil.mergehell.model.Player;
import com.bigphil.mergehell.model.ObstacleManager;
import org.junit.jupiter.api.Test;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ActorVisualsTest {
    @Test void buriedDrillerRetainsItsFloorAnchoredFullVisualBody() {
        var driller = new ObstacleManager.Enemy(300, 444, EntityType.DRILLER, -1, 25);
        assertEquals(12, driller.getBounds().height);
        assertEquals(480, driller.getBounds().getMaxY());
        var visuals = new ActorVisuals();
        visuals.update(new Player(100, 480), List.of(driller), 0.016);
        var pose = visuals.snapshot().enemies().get(0);
        assertTrue(pose.tactics().burrowed());
        assertEquals(36, pose.height());
        assertEquals(480, pose.y() + pose.height());
    }

    @Test void newVisualSessionDoesNotInventAShotFromAnOldSequence() {
        Player player = new Player(100, 480);
        player.update(false, false, false, true, 480, 3_000, new ArrayList<>(), List.of());
        assertEquals(1, player.getShotSequence());
        ActorVisuals visuals = new ActorVisuals();
        visuals.update(player, List.of(), 0.016);
        assertEquals(0, visuals.snapshot().hero().shot());
        visuals.reset();
        visuals.update(player, List.of(), 0.016);
        assertEquals(0, visuals.snapshot().hero().shot());
    }

    @Test void rendererKeepsTheSamePoseWhileWorldTimeDoesNotAdvance() {
        var renderer = new com.bigphil.mergehell.GameRenderer();
        Player player = new Player(100, 480);
        renderer.updateVisuals(player, List.of(), 0);
        // Let the initial contact with the floor finish its landing transition.
        for (int i = 0; i < 12; i++) {
            player.update(false, true, false, false, 480, 3_000, new ArrayList<>(), List.of());
            renderer.updateVisuals(player, List.of(), 0.016);
        }
        var moving = renderer.actorSnapshot();
        assertEquals(ActorVisuals.Action.RUN, moving.hero().action());
        for (int i = 0; i < 20; i++) renderer.updateVisuals(player, List.of(), 0);
        assertSame(moving, renderer.actorSnapshot());
    }

    @Test void actorDrawingDoesNotLeakPaintStrokeFontCompositeOrTransform() {
        Graphics2D g = new BufferedImage(160, 120, BufferedImage.TYPE_INT_ARGB).createGraphics();
        try {
            g.setColor(Color.MAGENTA); g.setStroke(new BasicStroke(7));
            g.setFont(new Font(Font.SERIF, Font.ITALIC, 17)); g.translate(3, 4);
            g.setComposite(AlphaComposite.DstIn.derive(0.4f));
            var transform = g.getTransform(); var composite = g.getComposite(); var stroke = g.getStroke(); var font = g.getFont();
            IndustrialArt missing = new IndustrialArt(AssetStore.empty());
            ActorVisuals.hero(g, missing, new ActorVisuals.Hero(40, 100, 1, ActorVisuals.Action.DASH,
                    0, 0.3, 0, true, true, 0.5f));
            ActorVisuals.enemy(g, missing, new ActorVisuals.Hostile(EntityType.CONFLICT, 60, 60, 40, 40,
                    -1, 1, 1, 10, 0, 0, 0));
            assertEquals(Color.MAGENTA, g.getColor()); assertEquals(stroke, g.getStroke());
            assertEquals(font, g.getFont()); assertEquals(composite, g.getComposite()); assertEquals(transform, g.getTransform());
        } finally { g.dispose(); }
    }
}
