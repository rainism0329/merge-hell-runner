package com.bigphil.mergehell.render;

import org.junit.jupiter.api.Test;

import java.awt.Rectangle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameViewportTest {
    @Test
    void letterboxesWithoutChangingAspectRatio() {
        ViewportTransform transform = GameViewport.fit(1200, 600);
        assertEquals(1.0, transform.scale(), 0.0001);
        assertEquals(120, transform.offsetX());
        assertEquals(0, transform.offsetY());
        assertEquals(960, transform.drawWidth());
    }

    @Test
    void upgradeCardsStayInsideLogicalCanvas() {
        UpgradeOverlayRenderer renderer = new UpgradeOverlayRenderer();
        Rectangle canvas = new Rectangle(0, 0, 960, 600);
        for (int i = 0; i < 3; i++) assertTrue(canvas.contains(renderer.cardBounds(i)));
    }
}
