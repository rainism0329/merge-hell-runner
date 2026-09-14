package com.bigphil.mergehell.render;

import com.bigphil.mergehell.model.EntityType;
import com.bigphil.mergehell.world.HeapDistrictController;
import com.bigphil.mergehell.assets.AssetCatalog;
import com.bigphil.mergehell.assets.AssetStore;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HeapRendererTest {
    private final HeapWorldRenderer world = new HeapWorldRenderer();

    @Test void productionHeapResourcesLoadWithRequiredMotionSockets() {
        AssetStore assets = AssetStore.preload(new AssetCatalog(Map.of(
                "heap-background", "game/art/heap-background.properties",
                "heap-actors", "game/art/heap-actors.properties")), getClass().getClassLoader());
        assertTrue(assets.diagnostics().isEmpty(), assets.diagnostics().toString());
        assertEquals(2, assets.atlasCount());
        assertTrue(assets.find("heap-background").orElseThrow().definition().animations().containsKey("backdrop"));
        var actors = assets.find("heap-actors").orElseThrow();
        for (String name : List.of("boss-shell", "leak-body", "shutter")) {
            var frame = actors.definition().animations().get(name).frames().get(0);
            BufferedImage alpha = new BufferedImage(frame.width(), frame.height(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = alpha.createGraphics();
            try { actors.paintFrame(g, frame); } finally { g.dispose(); }
            assertEquals(0, alpha.getRGB(0, 0) >>> 24, name + " keeps a transparent guard");
            assertTrue(java.util.Arrays.stream(alpha.getRGB(0, 0, frame.width(), frame.height(), null, 0, frame.width()))
                    .anyMatch(pixel -> (pixel >>> 24) > 240), name + " retains substantial material opacity");
        }
        var boss = actors.definition().animations().get("boss-shell").frames().get(0);
        assertTrue(boss.sockets().keySet().containsAll(List.of("core", "core-left", "core-right", "fan")));
        assertTrue(boss.sockets().get("core-left").x() < boss.sockets().get("core").x());
        assertTrue(boss.sockets().get("core-right").x() > boss.sockets().get("core").x());
    }

    @Test void realRiskPoolKeepsItsGeometryWhenWarningBecomesActive() {
        HeapDistrictController controller = HeapDistrictController.standard(42);
        controller.update(route(true));
        assertTrue(controller.interact(HeapDistrictController.Choice.SALVAGE));
        var warning = controller.snapshot().pools().get(0);
        assertEquals(HeapDistrictController.PoolPhase.WARNING, warning.phase());
        BufferedImage before = paint(controller.snapshot());
        for (int i = 0; i < HeapDistrictController.POOL_WARNING_TICKS; i++) controller.update(route(true));
        var active = controller.snapshot().pools().get(0);
        assertEquals(warning.bounds(), active.bounds());
        assertEquals(HeapDistrictController.PoolPhase.ACTIVE, active.phase());
        BufferedImage after = paint(controller.snapshot());
        var b = active.bounds(); int x = (int) b.centerX(), y = (int) b.centerY();
        assertNotEquals(before.getRGB(x, y), after.getRGB(x, y));
        assertTrue((after.getRGB(x, y) >>> 24) > 230);
        // The active surface stays in the actual ground hurtbox, not a filled tall damage column.
        assertEquals(0, after.getRGB(x, (int) b.y() - 12) >>> 24);
        assertEquals(0, after.getRGB(x, (int) b.y() + b.height() + 5) >>> 24);
    }

    @Test void realChannelAndUsedStatesChangeStationWithoutMutatingTheController() {
        HeapDistrictController controller = HeapDistrictController.standard(42);
        controller.update(route(true));
        BufferedImage ready = paint(controller.snapshot());
        assertTrue(controller.interact(HeapDistrictController.Choice.PURGE));
        var channel = controller.snapshot();
        assertEquals(HeapDistrictController.NodeState.CHANNELING, channel.nodes().get(0).state());
        paint(channel); assertSame(channel, controller.snapshot());
        for (int i = 0; i < HeapDistrictController.CHANNEL_TICKS; i++) controller.update(route(true));
        assertEquals(HeapDistrictController.NodeState.USED, controller.snapshot().nodes().get(0).state());
        BufferedImage used = paint(controller.snapshot());
        assertNotEquals(ready.getRGB(750, 432), used.getRGB(750, 432));
    }

    @Test void inactiveControllerRendersIdenticallyIncludingBackgroundMotion() {
        HeapDistrictController controller = HeapDistrictController.standard(42);
        controller.update(route(true)); controller.interact(HeapDistrictController.Choice.PURGE);
        var snapshot = controller.snapshot();
        BufferedImage before = scene(snapshot);
        controller.update(route(false));
        assertSame(snapshot, controller.snapshot());
        assertArrayEquals(before.getRGB(0, 0, 960, 600, null, 0, 960),
                scene(controller.snapshot()).getRGB(0, 0, 960, 600, null, 0, 960));
    }

    @Test void leakRendererDeclinesOtherTypesAndKeepsAVisibleNativeBody() {
        BufferedImage image = new BufferedImage(160, 160, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            assertFalse(HeapActorRenderer.renderLeak(g, actor(EntityType.BUG)));
            assertEquals(0, image.getRGB(62, 62));
            assertTrue(HeapActorRenderer.renderLeak(g, actor(EntityType.LEAK)));
            assertTrue((image.getRGB(62, 62) >>> 24) > 200);
        } finally { g.dispose(); }
    }

    @Test void vulnerabilityOpensTheBossCoreAtTheSameBodyCoordinates() {
        BufferedImage closed = boss(0), open = boss(150);
        Color closedCore = new Color(closed.getRGB(100, 122), true);
        Color openCore = new Color(open.getRGB(100, 122), true);
        assertTrue(openCore.getRed() > closedCore.getRed() + 40);
        assertTrue(openCore.getGreen() > 160);
        // The side structure remains fixed while the central jaws reveal the real break window.
        assertEquals(closed.getRGB(55, 80), open.getRGB(55, 80));
    }

    @Test void allLayersPreserveCallerGraphicsState() {
        BufferedImage image = new BufferedImage(960, 600, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.translate(10, 12); g.setClip(0, 0, 700, 550); g.scale(.625, .625);
            g.setStroke(new BasicStroke(5)); g.setColor(Color.MAGENTA); g.setComposite(AlphaComposite.SrcOver.derive(.6f));
            AffineTransform transform = g.getTransform(); Rectangle clip = g.getClipBounds();
            Stroke stroke = g.getStroke(); Composite composite = g.getComposite();
            world.drawBackground(g, 960, 600, 480, 100, 4, true, true);
            world.drawWorld(g, new HeapDistrictController.Snapshot(0, 0, List.of(), List.of(), List.of(), "", "", 0, 0), 0, 960, true, false, true);
            HeapActorRenderer.renderLeak(g, actor(EntityType.LEAK));
            HeapActorRenderer.renderBoss(g, bossPose(100));
            assertEquals(transform, g.getTransform()); assertEquals(clip, g.getClipBounds());
            assertEquals(stroke, g.getStroke()); assertEquals(composite, g.getComposite()); assertEquals(Color.MAGENTA, g.getColor());
        } finally { g.dispose(); }
    }

    private static HeapDistrictController.Input route(boolean active) {
        return new HeapDistrictController.Input(new HeapDistrictController.Bounds(730, 416, 36, 64),
                0, 960, 480, active, false, null, 1);
    }
    private static ActorVisuals.Hostile actor(EntityType type) {
        return new ActorVisuals.Hostile(type, 40, 40, 44, 44, -1, 220, 220, 0, .4, 0, 0);
    }
    private static HeapActorRenderer.BossVisual bossPose(int vulnerable) {
        return new HeapActorRenderer.BossVisual(40, 40, 120, 150, 500, 800, 2, 0, vulnerable, 1, true, false);
    }
    private static BufferedImage boss(int vulnerable) {
        BufferedImage image = new BufferedImage(220, 220, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try { HeapActorRenderer.renderBoss(g, bossPose(vulnerable)); } finally { g.dispose(); }
        return image;
    }
    private BufferedImage paint(HeapDistrictController.Snapshot snapshot) {
        BufferedImage image = new BufferedImage(960, 600, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try { world.drawWorld(g, snapshot, 0, 960, false, false, false); } finally { g.dispose(); }
        return image;
    }
    private BufferedImage scene(HeapDistrictController.Snapshot snapshot) {
        BufferedImage image = new BufferedImage(960, 600, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            world.drawBackground(g, 960, 600, 480, 0, snapshot.tick() * .016, false, false);
            world.drawWorld(g, snapshot, 0, 960, false, true, false);
        } finally { g.dispose(); }
        return image;
    }
}
