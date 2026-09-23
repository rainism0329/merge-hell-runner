package com.bigphil.mergehell.render;

import com.bigphil.mergehell.i18n.GameLanguage;
import com.bigphil.mergehell.i18n.GameText;
import com.bigphil.mergehell.model.Boss;
import com.bigphil.mergehell.model.EntityType;
import com.bigphil.mergehell.model.ObstacleManager;
import com.bigphil.mergehell.model.Projectile;
import org.junit.jupiter.api.Test;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChapterActorRendererTest {
    @Test void newDepthActionsDrawAtSmallSizeWithFlashesOffWithoutAdvancingTheEncounter() {
        for (String action : List.of("LEFT_SWEEP", "RIGHT_SWEEP", "GANTRY_LOCK", "HEART_PULSE", "ROOT_SURGE")) {
            int chapter = action.startsWith("HEART") || action.startsWith("ROOT") ? 4 : 2;
            var boss = new Boss("Depth", 16000, "!", 960, chapter, 4);
            var enemies = new ObstacleManager(); var shots = new ArrayList<Projectile>();
            boss.previewArrival(1, 480); boss.activate(); boss.update(enemies, 480, 180, 450, shots);
            boss.damageAt(boss.getParts().get(2).bounds().rectangle(), action.endsWith("SWEEP") ? 10000 : chapter == 2 ? 20000 : 30000);
            for (int i = 0; i < 5000 && !boss.getEncounterAction().equals(action); i++) boss.update(enemies, 480, 180, 450, shots);
            assertEquals(action, boss.getEncounterAction());
            for (int active = 0; active < 2; active++) {
                if (active == 1) while (boss.getWarningTicks() > 0) boss.update(enemies, 480, 180, 450, shots);
                int tick = boss.getCombatTick(); var warnings = boss.getAttackTelegraphs(); var parts = boss.getParts();
                var image = new BufferedImage(600, 400, BufferedImage.TYPE_INT_ARGB); var g = image.createGraphics();
                g.scale(.625, .625); var before = g.getTransform();
                ChapterActorRenderer.boss(g, boss, 0, false, true);
                assertEquals(before, g.getTransform()); g.dispose();
                assertEquals(tick, boss.getCombatTick()); assertEquals(warnings, boss.getAttackTelegraphs()); assertEquals(parts, boss.getParts());
                assertTrue(Arrays.stream(image.getRGB(0, 0, 600, 400, null, 0, 600)).filter(p -> p >>> 24 > 100).count() > 1000);
            }
        }
    }

    @Test void bothLanguagesRenderRealMultipartWarningsWithoutChangingAnyPhysicsOrCallerGraphics() {
        for (GameLanguage language : GameLanguage.values()) try (var scope = GameText.use(language)) {
            for (int level = 2; level <= 4; level++) {
                var boss = new Boss("Chapter", 16000, "!", 960, level, 5);
                boss.previewArrival(1, 480); boss.activate();
                var enemies = new ObstacleManager(); var shots = new ArrayList<Projectile>();
                for (int i = 0; i < 200 && boss.getWarningTicks() == 0; i++) boss.update(enemies, 480, 170, 450, shots);
                assertTrue(boss.getWarningTicks() > 0);
                var parts = boss.getParts(); var bounds = boss.getBounds(); int tick = boss.getCombatTick();
                var warnings = boss.getAttackTelegraphs(); var predictions = boss.getSingularityPredictedShots();
                var image = new BufferedImage(960, 600, BufferedImage.TYPE_INT_ARGB);
                var g = image.createGraphics();
                g.translate(2, 3); g.setClip(new Rectangle(0, 0, 940, 580)); g.setColor(Color.MAGENTA);
                g.setStroke(new BasicStroke(5)); g.setFont(new Font("Serif", Font.PLAIN, 17));
                g.setComposite(AlphaComposite.SrcOver.derive(.7f));
                var transform = g.getTransform(); var clip = g.getClip().getBounds(); var stroke = g.getStroke();
                var composite = g.getComposite(); var font = g.getFont(); var paint = g.getPaint();
                ChapterActorRenderer.boss(g, boss, 50, false, true);
                assertEquals(transform, g.getTransform()); assertEquals(clip, g.getClip().getBounds());
                assertEquals(stroke, g.getStroke()); assertEquals(composite, g.getComposite());
                assertEquals(font, g.getFont()); assertEquals(paint, g.getPaint()); g.dispose();
                assertEquals(parts, boss.getParts()); assertEquals(bounds, boss.getBounds()); assertEquals(tick, boss.getCombatTick());
                assertEquals(warnings, boss.getAttackTelegraphs()); assertEquals(predictions, boss.getSingularityPredictedShots());
                assertTrue(Arrays.stream(image.getRGB(0, 0, 960, 600, null, 0, 960)).anyMatch(p -> (p >>> 24) != 0));
            }
        }
    }

    @Test void nineChapterSpecialistsUseTheirOwnPaintedFamiliesAndLegacyActorsAreNotReplaced() {
        var image = new BufferedImage(940, 140, BufferedImage.TYPE_INT_ARGB); var g = image.createGraphics();
        int index = 0;
        for (EntityType type : List.of(EntityType.SENTINEL, EntityType.WARDEN, EntityType.RIGGER,
                EntityType.INTERRUPT, EntityType.DRILLER, EntityType.SLAG_SPITTER,
                EntityType.MIRROR, EntityType.SPORE_POD, EntityType.LURKER)) {
            int x = 5 + index++ * 100;
            var pose = new ActorVisuals.Hostile(type, x, 120 - type.height, type.width, type.height,
                    index % 2 == 0 ? 1 : -1, type.maxHp, type.maxHp, 0, .5, 0, 0);
            assertTrue(ChapterActorRenderer.enemy(g, pose, 1, false));
            long occupied = Arrays.stream(image.getRGB(x, 15, 90, 110, null, 0, 90)).filter(p -> (p >>> 24) > 100).count();
            assertTrue(occupied > 250, type + " must have actual painted pixels");
        }
        var legacy = new ActorVisuals.Hostile(EntityType.BUG, 0, 0, 40, 40, -1, 1, 1, 0, 0, 0, 0);
        assertFalse(ChapterActorRenderer.enemy(g, legacy, 1, true)); g.dispose();
    }

    @Test void disabledImpactFlashesStillShowTheAuthoritativeActiveHazard() {
        var boss = new Boss("Gantry", 16000, "!", 960, 2, 4);
        boss.previewArrival(1, 480); boss.activate(); var enemies = new ObstacleManager(); var shots = new ArrayList<Projectile>();
        for (int i = 0; i < 220 && boss.getActiveHazards().isEmpty(); i++) boss.update(enemies, 480, 180, 450, shots);
        var hazard = boss.getActiveHazards().get(0).bounds().rectangle();
        var image = new BufferedImage(960, 600, BufferedImage.TYPE_INT_ARGB); var g = image.createGraphics();
        ChapterActorRenderer.boss(g, boss, 1, false, false); g.dispose();
        assertTrue((image.getRGB(hazard.x + 3, hazard.y + 3) >>> 24) > 0,
                "Warning/active geometry must survive the accessibility flash preference");
    }
}
