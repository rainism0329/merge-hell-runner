package com.bigphil.mergehell.render;

import com.bigphil.mergehell.assets.AssetStore;
import com.bigphil.mergehell.boss.BossAction;
import com.bigphil.mergehell.boss.BossPhase;
import com.bigphil.mergehell.boss.BossSnapshot;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LegacyBossRendererTest {
    private final IndustrialArt missingArt = new IndustrialArt(AssetStore.empty());
    private final LegacyBossRenderer renderer = new LegacyBossRenderer();

    @Test void disablingFlashesKeepsLaserLaneWarningSteadyAndVisible() {
        var snapshot = new BossSnapshot(BossPhase.DEPENDENCIES, 2400, 2400, List.of(), 0,
                new BossAction.Laser(40, 350, 46, 24));
        BufferedImage start = render(snapshot, 40), end = render(snapshot, 1);
        // Inspect the danger lane itself, away from labels, body and status HUD.
        for (int y = 326; y <= 374; y++) for (int x = 400; x < 550; x++)
            assertEquals(start.getRGB(x, y), end.getRGB(x, y));
        assertNotEquals(0, start.getRGB(460, 350) >>> 24, "lane fill must remain visible");
        assertTrue((start.getRGB(460, 327) >>> 24) > 100, "lane boundary must remain distinct");
        assertEquals(0, start.getRGB(460, 310) >>> 24, "safe lane must not be covered by danger fill");
    }

    @Test void phaseRenderingLeavesCallerGraphicsAndImmutableStateUntouched() {
        var node = new BossSnapshot.NodeView(0, 595, 208, 120, 240, true);
        for (BossPhase phase : BossPhase.values()) {
            var snapshot = new BossSnapshot(phase, 600, 2400, List.of(node), 30, null);
            Graphics2D g = new BufferedImage(960, 600, BufferedImage.TYPE_INT_ARGB).createGraphics();
            try {
                g.translate(3, 4); g.setColor(Color.MAGENTA); g.setStroke(new BasicStroke(7));
                g.setFont(new Font(Font.SERIF, Font.ITALIC, 17));
                g.setComposite(AlphaComposite.SrcOver.derive(0.4f));
                var transform = g.getTransform(); var composite = g.getComposite();
                var stroke = g.getStroke(); var font = g.getFont();
                renderer.render(g, missingArt, visual(snapshot, 0), 960, 480);
                assertEquals(transform, g.getTransform()); assertEquals(composite, g.getComposite());
                assertEquals(stroke, g.getStroke()); assertEquals(font, g.getFont());
                assertEquals(Color.MAGENTA, g.getColor());
                assertEquals(600, snapshot.coreHp()); assertEquals(120, snapshot.nodes().get(0).hp());
            } finally { g.dispose(); }
        }
    }

    private BufferedImage render(BossSnapshot snapshot, int warningTicks) {
        BufferedImage image = new BufferedImage(960, 600, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try { renderer.render(g, missingArt, visual(snapshot, warningTicks), 960, 480); }
        finally { g.dispose(); }
        return image;
    }

    private LegacyBossRenderer.Visual visual(BossSnapshot snapshot, int warningTicks) {
        return new LegacyBossRenderer.Visual(snapshot, 760, 190, 160, 280, warningTicks,
                0, 0, 0, 0.4, 1.5, 0, false, false);
    }
}
