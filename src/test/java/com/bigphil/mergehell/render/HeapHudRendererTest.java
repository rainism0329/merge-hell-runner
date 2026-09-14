package com.bigphil.mergehell.render;

import com.bigphil.mergehell.world.HeapDistrictController;
import org.junit.jupiter.api.Test;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class HeapHudRendererTest {
    @Test void compactChapterCardCannotCoverTheExistingHealthPanel() {
        BufferedImage image = new BufferedImage(960, 600, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            var snapshot = new HeapDistrictController.Snapshot(1, 100, List.of(), List.of(), List.of(),
                    "E 净化（安全） / F 回收（奖励更高、泄漏风险）", "", 0, 0);
            new HeapHudRenderer().render(g, snapshot, true, false, false);
            for (int x = 14; x <= 340; x++) for (int y = 12; y <= 108; y++)
                assertEquals(0, image.getRGB(x, y), "Existing compact HP/weapon/lives area must stay untouched");
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
            var lines = HeapHudRenderer.wrap(g.getFontMetrics(), snapshot.objective(), 300);
            assertTrue(lines.size() <= 3);
            assertEquals(snapshot.objective().replace(" ", ""), String.join("", lines).replace(" ", ""));
            assertTrue(lines.stream().allMatch(line -> g.getFontMetrics().stringWidth(line) <= 300));
        } finally { g.dispose(); }
    }
}
