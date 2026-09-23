package com.bigphil.mergehell.render;

import com.bigphil.mergehell.model.*;
import org.junit.jupiter.api.Test;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class FoundryBossRendererTest {
    @Test void activeAndWarningHeatRemainDistinctWithFlashesOffAndRenderingDoesNotAdvanceTheHazard() {
        Boss boss = new Boss("Foundry", 16000, "!", 960, 3, 9);
        var enemies = new ObstacleManager(); var shots = new ArrayList<Projectile>();
        boss.previewArrival(1, 480); boss.activate(); boss.update(enemies, 480, 180, 450, shots);
        boss.damageAt(boss.getParts().get(2).bounds().rectangle(), 10000);
        for (int i = 0; i < 1000 && boss.getAttackTelegraphs().stream().noneMatch(t -> t.id().equals("slag-trail")); i++)
            boss.update(enemies, 480, 180, 450, shots);
        var trail = boss.getAttackTelegraphs().stream().filter(t -> t.id().equals("slag-trail")).findFirst().orElseThrow();
        var warning = paint(boss);
        while (boss.getAttackTelegraphs().stream().anyMatch(t -> t.id().equals("slag-trail") && t.warningTicks() > 0))
            boss.update(enemies, 480, 180, 450, shots);
        var active = paint(boss);
        Rectangle r = trail.bounds().rectangle();
        long warningLight = luminance(warning, r), activeLight = luminance(active, r);
        assertTrue(activeLight > warningLight * 1.3, "Hot metal must remain visibly brighter than safe warning cracks");
    }

    @Test void rearPurgeCanBePaintedRepeatedlyAtSmallSizeWithoutChangingItsCommittedGeometry() {
        Boss boss = new Boss("Foundry", 16000, "!", 960, 3, 9);
        var enemies = new ObstacleManager(); var shots = new ArrayList<Projectile>();
        boss.previewArrival(1, 480); boss.activate(); boss.update(enemies, 480, 180, 450, shots);
        boss.damageAt(boss.getParts().get(2).bounds().rectangle(), 20000);
        for (int i = 0; i < 500 && !boss.getEncounterAction().equals("VENT_PURGE"); i++) boss.update(enemies, 480, 180, 450, shots);
        assertEquals("VENT_PURGE", boss.getEncounterAction());
        for (int state = 0; state < 2; state++) {
            if (state == 1) while (boss.getWarningTicks() > 0) boss.update(enemies, 480, 180, 450, shots);
            var tells = boss.getAttackTelegraphs(); int tick = boss.getCombatTick();
            var image = new BufferedImage(600, 400, BufferedImage.TYPE_INT_ARGB); var g = image.createGraphics(); g.scale(.625, .625);
            var before = g.getTransform();
            ChapterActorRenderer.boss(g, boss, 0, false, true); ChapterActorRenderer.boss(g, boss, 0, false, true);
            assertEquals(before, g.getTransform()); g.dispose();
            assertEquals(tick, boss.getCombatTick()); assertEquals(tells, boss.getAttackTelegraphs());
        }
    }

    private static BufferedImage paint(Boss boss) {
        var before = boss.getAttackTelegraphs(); int tick = boss.getCombatTick(); var parts = boss.getParts();
        var image = new BufferedImage(960, 600, BufferedImage.TYPE_INT_RGB); var g = image.createGraphics();
        ChapterActorRenderer.boss(g, boss, 0, false, false); g.dispose();
        assertEquals(before, boss.getAttackTelegraphs()); assertEquals(tick, boss.getCombatTick()); assertEquals(parts, boss.getParts());
        return image;
    }
    private static long luminance(BufferedImage image, Rectangle bounds) {
        long total = 0;
        for (int y = bounds.y; y < bounds.y + bounds.height; y++) for (int x = bounds.x; x < bounds.x + bounds.width; x++) {
            int rgb = image.getRGB(x, y); total += (rgb >> 16 & 255) * 3 + (rgb >> 8 & 255) * 6 + (rgb & 255);
        }
        return total;
    }
}
