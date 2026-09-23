package com.bigphil.mergehell.boss;

import com.bigphil.mergehell.model.*;
import com.bigphil.mergehell.progression.CharacterId;
import com.bigphil.mergehell.progression.GameDifficulty;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FoundryBossDepthTest {
    private static final class Fight {
        final ChapterBossEncounter boss = new ChapterBossEncounter(3, 16000, 900, 900);
        final ObstacleManager enemies = new ObstacleManager();
        final List<Projectile> shots = new ArrayList<>();
        int stage = 1;
        double px = 450;
        Fight() { boss.preview(1, 480); tick(); }
        void tick() { boss.update(480, px, 450, stage, enemies, shots); }
        void stage(int value) { stage = value; tick(); }
        void until(ChapterBossEncounter.Action action) {
            for (int i = 0; i < 5000 && boss.action() != action; i++) tick();
            assertEquals(action, boss.action());
        }
        Boss.PartView part(String id) { return boss.parts(16000, 16000).stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow(); }
        Boss.AttackTelegraph trail() { return boss.telegraphs().stream().filter(t -> t.id().equals("slag-trail")).findFirst().orElse(null); }
        void breakArmor() { var plate = part("armor-plate"); boss.hit(plate.bounds().rectangle(), plate.hp(), 16000, 16000); }
        void finishCharge() {
            until(ChapterBossEncounter.Action.DRILL_CHARGE);
            while (boss.action() == ChapterBossEncounter.Action.DRILL_CHARGE) tick();
        }
    }

    @Test void phasesIntroduceScorchedGroundThenARearPressureAttackWithoutChangingTheIntro() {
        var first = new Fight(); first.finishCharge(); assertNull(first.trail());
        first.until(ChapterBossEncounter.Action.MAGMA_MORTAR); assertEquals(3, first.boss.telegraphs().size());
        var second = new Fight(); second.stage(2); second.finishCharge(); assertNotNull(second.trail());
        var third = new Fight(); third.stage(3); third.until(ChapterBossEncounter.Action.VENT_PURGE);
        assertEquals(92, third.boss.warningTicks()); assertTrue(third.part("heat-vent").weak());
    }

    @Test void armorBreakInterruptsCommittedChargeAndPermanentlyShortensAndSlowsLaterRuns() {
        var normal = new Fight(); normal.stage(2); normal.until(ChapterBossEncounter.Action.DRILL_CHARGE);
        while (normal.boss.warningTicks() > 0) normal.tick();
        double start = normal.boss.x(); normal.tick(); assertEquals(18, start - normal.boss.x());
        while (normal.boss.action() == ChapterBossEncounter.Action.DRILL_CHARGE) normal.tick();
        assertEquals(400, start - normal.boss.x());

        var broken = new Fight(); broken.stage(2); broken.until(ChapterBossEncounter.Action.DRILL_CHARGE);
        broken.breakArmor();
        assertEquals(ChapterBossEncounter.Action.COOLING, broken.boss.action());
        assertTrue(broken.boss.telegraphs().isEmpty()); assertTrue(broken.part("heat-vent").weak());
        broken.until(ChapterBossEncounter.Action.DRILL_CHARGE);
        while (broken.boss.warningTicks() > 0) broken.tick();
        start = broken.boss.x(); broken.tick(); assertEquals(10, Math.abs(start - broken.boss.x()));
        while (broken.boss.action() == ChapterBossEncounter.Action.DRILL_CHARGE) broken.tick();
        assertEquals(180, Math.abs(start - broken.boss.x())); assertNull(broken.trail());
        for (int i = 0; i < 1500; i++) { broken.tick(); assertNull(broken.trail()); }
    }

    @Test void theShortHeatStripHasItsOwnFullWarningAndExpiresWithoutAnInvisibleRemainder() {
        var f = new Fight(); f.stage(2); f.finishCharge();
        var trail = f.trail(); assertNotNull(trail);
        assertEquals(120, trail.bounds().width()); assertEquals(16, trail.bounds().height());
        assertEquals(36, trail.warningTicks()); assertEquals(0, trail.damage()); assertFalse(trail.active());
        var locked = trail.bounds();
        for (int i = 0; i < 35; i++) {
            f.px += 9; f.tick(); assertEquals(locked, f.trail().bounds());
            assertFalse(f.trail().active()); assertEquals(0, f.trail().damage());
        }
        f.tick(); assertTrue(f.trail().active()); assertEquals(12, f.trail().damage());
        for (int i = 0; i < 89; i++) { f.tick(); assertNotNull(f.trail()); }
        f.tick(); assertNull(f.trail());
    }

    @Test void wardenCanWalkAwayOrUseARealJumpBeforeTheWarnedMetalBecomesHot() {
        for (boolean jumping : new boolean[]{false, true}) {
            var f = new Fight(); f.stage(2); f.finishCharge();
            var strip = f.trail().bounds();
            Player player = new Player((int) strip.x() + 45, 450);
            player.getRunBuild().setIdentity(CharacterId.WARDEN, GameDifficulty.CHALLENGE);
            move(player, false, false);
            if (jumping) {
                while (f.trail().warningTicks() > 12) f.tick();
            }
            int steps = 0;
            while (f.trail() != null) {
                move(player, player.getX() < strip.x() + strip.width() + 4, jumping && steps == 0);
                f.tick(); steps++;
                if (f.trail() != null && f.trail().active()) {
                    assertFalse(strip.intersects(player.getBounds()), "Warden escape / jump=" + jumping);
                    assertTrue(f.boss.contacts().stream().noneMatch(b -> b.intersects(player.getBounds())));
                }
            }
            assertTrue(player.getX() >= strip.x() + strip.width());
        }
    }

    @Test void ventPurgeLocksRearBoundsAndGroundCrouchHasRealClearance() {
        var f = new Fight(); f.stage(3); f.until(ChapterBossEncounter.Action.VENT_PURGE);
        var beam = f.boss.telegraphs().get(0).bounds();
        Player player = new Player((int) beam.x() + 30, 450); move(player, false, false);
        assertTrue(beam.intersects(player.getBounds()));
        player.setAimInput(false, true, false, false, false); move(player, false, false);
        assertTrue(player.isCrouching()); assertEquals(6, player.getBounds().y - (beam.y() + beam.height()));
        for (int i = 0; i < 91; i++) {
            f.px = i % 2 == 0 ? 200 : 1200; f.tick();
            assertEquals(beam, f.boss.telegraphs().get(0).bounds());
            assertTrue(f.boss.telegraphs().stream().noneMatch(t -> t.active() || t.damage() > 0));
        }
        f.tick(); assertTrue(f.boss.telegraphs().get(0).active());
        do {
            assertFalse(beam.intersects(player.getBounds())); f.tick();
        } while (f.boss.action() == ChapterBossEncounter.Action.VENT_PURGE);
    }

    @Test void overheatingTheOpenVentCancelsPurgeAndAnyExistingHeatStrip() {
        var purge = new Fight(); purge.stage(3); purge.until(ChapterBossEncounter.Action.VENT_PURGE);
        overheat(purge);
        assertEquals(ChapterBossEncounter.Action.OVERHEATED, purge.boss.action());
        assertTrue(purge.boss.telegraphs().isEmpty()); assertTrue(purge.part("core").weak());
        assertFalse(purge.boss.isContactDangerous());

        var trail = new Fight(); trail.stage(2); trail.finishCharge();
        while (trail.trail().warningTicks() > 0) trail.tick();
        overheat(trail); assertNull(trail.trail()); assertTrue(trail.boss.telegraphs().isEmpty());
    }

    @Test void purgeFacesThePhysicalRearInBothDirectionsAndNeverExtendsOutsideTheArena() {
        for (boolean turned : new boolean[]{false, true}) {
            var f = new Fight();
            if (turned) {
                f.stage(2); f.finishCharge(); f.px = 1200;
                f.until(ChapterBossEncounter.Action.DRILL_CHARGE);
                assertEquals(1, f.boss.direction());
            }
            f.stage(3); f.until(ChapterBossEncounter.Action.VENT_PURGE);
            var beam = f.boss.telegraphs().get(0).bounds();
            var vent = f.part("heat-vent").bounds();
            double source = vent.x() + vent.width() / 2;
            assertTrue(beam.x() >= 265); assertTrue(beam.x() + beam.width() <= 1225);
            if (turned) assertEquals(source, beam.x() + beam.width()); else assertEquals(source, beam.x());
            assertTrue(beam.width() >= 30);
        }
    }

    @Test void armorBreakPhaseChangeAndRespawnEachRemoveLingeringHeat() {
        for (int reason = 0; reason < 3; reason++) {
            var f = new Fight(); f.stage(2); f.finishCharge(); assertNotNull(f.trail());
            if (reason == 0) f.breakArmor(); else if (reason == 1) f.stage(3); else f.boss.resetTransient();
            assertNull(f.trail()); assertTrue(f.boss.telegraphs().isEmpty());
        }
    }

    @Test void phaseChangePreservesEarnedOverheatWithoutExtraIdleOrWeakDamageDuringReconfigure() {
        var f = new Fight(); f.breakArmor(); overheat(f);
        assertEquals(190, f.boss.exposureTicks()); f.stage(3);
        assertFalse(f.part("core").weak()); assertEquals(0, f.boss.exposureTicks());
        assertEquals(100, f.boss.hit(f.part("core").bounds().rectangle(), 100, 16000, 16000).coreDamage());
        for (int i = 0; i < 90; i++) f.tick();
        assertEquals(ChapterBossEncounter.Action.OVERHEATED, f.boss.action());
        assertEquals(190, f.boss.exposureTicks()); assertTrue(f.part("armor-plate").destroyed());
    }

    private static void overheat(Fight f) {
        f.boss.hit(f.part("heat-vent").bounds().rectangle(), f.part("armor-plate").maxHp(), 16000, 16000);
    }
    private static void move(Player player, boolean right, boolean jump) {
        player.update(false, right, jump, false, 480, 1800, new ArrayList<>(), List.of());
    }
}
