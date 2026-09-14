package com.bigphil.mergehell.render;

import com.bigphil.mergehell.boss.BossFeedbackController;
import com.bigphil.mergehell.combat.CombatEvent;
import com.bigphil.mergehell.combat.WeaponId;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;

class BossFeedbackRendererTest {
    private final BossFeedbackRenderer renderer = new BossFeedbackRenderer();

    @Test void pauseAndRepeatedPaintsHaveIdenticalPixelsWithoutChangingTheSnapshot() {
        BossFeedbackController controller = controller("CORE EXPOSED");
        controller.accept(new CombatEvent.BossImpact(CombatEvent.BossPart.CORE, 0, 80, 920, 1000,
                620, 240, WeaponId.GARBAGE_COLLECTOR, CombatEvent.DamageKind.DIRECT, 1, true, false));
        var before = controller.snapshot(); BufferedImage first = world(before, true);
        for (int i = 0; i < 80; i++) controller.tick(false);
        assertEquals(before, controller.snapshot());
        assertArrayEquals(first.getRGB(0, 0, 960, 600, null, 0, 960), world(before, true).getRGB(0, 0, 960, 600, null, 0, 960));
        assertNotEquals(first.getRGB(620, 240), world(before, false).getRGB(620, 240), "Flashes off removes the brief white impact light");
    }

    @Test void longCompactStageLabelsLeaveTheHealthColumnClearAndHudStaysAbove185() {
        var longStatus = controller("HEAP DISTRICT // OUT OF MEMORY // VERY LONG BOSS IDENTITY AND PHASE").snapshot();
        var emptyStatus = controller("").snapshot();
        BufferedImage longFrame = overlay(longStatus), emptyFrame = overlay(emptyStatus);
        for (int y = 108; y < 130; y++) for (int x = 705; x < 780; x++)
            assertEquals(emptyFrame.getRGB(x, y), longFrame.getRGB(x, y), "State text must not intrude on numeric HP");
        for (int y = 185; y < 600; y++) for (int x = 0; x < 960; x++) assertEquals(0, longFrame.getRGB(x, y));
    }

    @Test void rendererKeepsParentTransformClipPaintStrokeFontAndCompositeRule() {
        BufferedImage image = new BufferedImage(960, 600, BufferedImage.TYPE_INT_ARGB); Graphics2D g = image.createGraphics();
        g.translate(5, 7); g.setClip(0, 0, 900, 550); g.setColor(Color.MAGENTA); g.setStroke(new BasicStroke(7));
        g.setComposite(AlphaComposite.DstIn.derive(.6f)); g.setFont(new Font(Font.MONOSPACED, Font.ITALIC, 19));
        var transform = g.getTransform(); var clip = g.getClipBounds(); var paint = g.getPaint();
        var stroke = g.getStroke(); var font = g.getFont(); var composite = g.getComposite();
        renderer.renderWorld(g, controller("PHASE 2").snapshot(), true);
        renderer.renderOverlay(g, controller("PHASE 2").snapshot(), 960, true);
        assertEquals(transform, g.getTransform()); assertEquals(clip, g.getClipBounds()); assertEquals(paint, g.getPaint());
        assertEquals(stroke, g.getStroke()); assertEquals(font, g.getFont()); assertEquals(composite, g.getComposite()); g.dispose();
    }

    private BossFeedbackController controller(String label) {
        BossFeedbackController controller = new BossFeedbackController();
        controller.observe(new BossFeedbackController.Status(920, 1000, 2, label, false, false, 600, 180, 120, 150));
        return controller;
    }
    private BufferedImage world(BossFeedbackController.Snapshot snapshot, boolean flashes) {
        BufferedImage image = new BufferedImage(960, 600, BufferedImage.TYPE_INT_ARGB); Graphics2D g = image.createGraphics();
        renderer.renderWorld(g, snapshot, flashes); g.dispose(); return image;
    }
    private BufferedImage overlay(BossFeedbackController.Snapshot snapshot) {
        BufferedImage image = new BufferedImage(960, 600, BufferedImage.TYPE_INT_ARGB); Graphics2D g = image.createGraphics();
        renderer.renderOverlay(g, snapshot, 960, true); g.dispose(); return image;
    }
}
