package com.bigphil.mergehell.boss;

import com.bigphil.mergehell.model.*;
import com.bigphil.mergehell.engine.ProjectileBuffer;
import com.bigphil.mergehell.progression.CharacterId;
import com.bigphil.mergehell.progression.GameDifficulty;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ChapterBossDepthTest {
    private static final class Fight {
        final ChapterBossEncounter encounter;
        final ObstacleManager enemies = new ObstacleManager();
        final List<Projectile> shots = new ArrayList<>();
        int stage = 1;
        double playerX = 450;
        Fight(int chapter) {
            encounter = new ChapterBossEncounter(chapter, 16000, 900, 900);
            encounter.preview(1, 480);
            tick();
        }
        void tick() { encounter.update(480, playerX, 450, stage, enemies, shots); }
        void stage(int value) { stage = value; tick(); }
        void until(ChapterBossEncounter.Action action) {
            for (int i = 0; i < 5000 && encounter.action() != action; i++) tick();
            assertEquals(action, encounter.action());
        }
        Boss.PartView part(String id) { return encounter.parts(16000, 16000).stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow(); }
        void destroy(String id) { var p = part(id); encounter.hit(p.bounds().rectangle(), p.hp(), 16000, 16000); }
    }

    @Test void architectStagesIntroduceDifferentDecisionsAndKeepIntroductoryPattern() {
        var first = new Fight(2);
        for (int i = 0; i < 1200; i++) first.tick();
        assertEquals(Set.of("LEFT_STAMP", "RIGHT_STAMP", "CROSSBEAM", "GIRDER_FALL"), first.encounter.movesSeen());
        var second = new Fight(2); second.stage(2);
        second.until(ChapterBossEncounter.Action.LEFT_SWEEP);
        second.until(ChapterBossEncounter.Action.RIGHT_SWEEP);
        assertFalse(second.encounter.movesSeen().contains("GANTRY_LOCK"));
        var third = new Fight(2); third.stage(3);
        third.until(ChapterBossEncounter.Action.GANTRY_LOCK);
        assertEquals(2, third.encounter.telegraphs().size());
        assertEquals(110, third.encounter.warningTicks());
    }

    @Test void severingEitherArmCancelsItsSweepAndRemovesItsHalfOfTheLock() {
        for (int side = 0; side < 2; side++) {
            String id = side == 0 ? "left-arm" : "right-arm";
            var f = new Fight(2); f.stage(2);
            f.until(side == 0 ? ChapterBossEncounter.Action.LEFT_SWEEP : ChapterBossEncounter.Action.RIGHT_SWEEP);
            f.destroy(id);
            assertTrue(f.encounter.telegraphs().isEmpty());
            assertTrue(f.part(id).destroyed());
            f.stage(3); f.until(ChapterBossEncounter.Action.GANTRY_LOCK);
            var oneSide = f.encounter.telegraphs();
            assertEquals(1, oneSide.size());
            assertEquals(side == 0, oneSide.get(0).bounds().x() > f.playerX);
            for (int i = 0; i < 1800; i++) {
                f.tick();
                assertNotEquals(side == 0 ? ChapterBossEncounter.Action.LEFT_SWEEP : ChapterBossEncounter.Action.RIGHT_SWEEP,
                        f.encounter.action());
            }
        }
    }

    @Test void breakingAnArmDuringLockCancelsTheCommittedWholeHazardBeforeRewarning() {
        var f = new Fight(2); f.stage(3); f.until(ChapterBossEncounter.Action.GANTRY_LOCK);
        while (f.encounter.warningTicks() > 0) f.tick();
        assertTrue(f.encounter.telegraphs().stream().allMatch(t -> t.active() && t.damage() > 0));
        f.destroy("left-arm");
        assertTrue(f.encounter.telegraphs().isEmpty());
        assertEquals(ChapterBossEncounter.Action.READY, f.encounter.action());
    }

    @Test void lockDoesNotTrackAndItsCorridorIsReachableByTheSlowestCharacterFromBothEdges() {
        for (double origin : new double[]{260, 290, 450, 800, 1020}) {
            var f = new Fight(2); f.playerX = origin; f.stage(3);
            f.until(ChapterBossEncounter.Action.GANTRY_LOCK);
            var locked = f.encounter.telegraphs().stream().map(Boss.AttackTelegraph::bounds).toList();
            double destination = (locked.get(0).x() + locked.get(0).width() + locked.get(1).x()) / 2 - 15;
            Player player = player((int) origin, CharacterId.WARDEN);
            assertTrue(locked.stream().anyMatch(b -> b.intersects(player.getBounds())), "The new phase must ask for repositioning");
            int warning = f.encounter.warningTicks();
            for (int i = 0; i < warning; i++) {
                move(player, player.getX() > destination + 3, player.getX() < destination - 3, false);
                f.playerX = player.getX(); f.tick();
                assertEquals(locked, f.encounter.telegraphs().stream().map(Boss.AttackTelegraph::bounds).toList());
                if (i < warning - 1) assertTrue(f.encounter.telegraphs().stream().noneMatch(t -> t.damage() > 0));
            }
            assertTrue(f.encounter.telegraphs().stream().allMatch(t -> t.active()));
            assertTrue(locked.stream().noneMatch(b -> b.intersects(player.getBounds())), "Warden escape from " + origin);
            assertTrue(f.encounter.contacts().stream().noneMatch(b -> b.intersects(player.getBounds())));
        }
    }

    @Test void actualCrouchAvoidsSweepAndActualJumpClearsTheWholeRootSurge() {
        var gantry = new Fight(2); gantry.stage(2); gantry.until(ChapterBossEncounter.Action.LEFT_SWEEP);
        var sweep = gantry.encounter.telegraphs().get(0).bounds();
        Player duck = player((int) sweep.x() + 100, CharacterId.REPAIR);
        assertTrue(sweep.intersects(duck.getBounds()));
        duck.setAimInput(false, true, false, false, false); move(duck, false, false, false);
        assertTrue(duck.isCrouching()); assertFalse(sweep.intersects(duck.getBounds()));

        var heart = new Fight(4); heart.stage(3); heart.until(ChapterBossEncounter.Action.ROOT_SURGE);
        var roots = heart.encounter.telegraphs().get(0).bounds();
        Player jumper = player((int) roots.x() + 120, CharacterId.WARDEN);
        assertTrue(roots.intersects(jumper.getBounds()));
        while (heart.encounter.warningTicks() > 10) heart.tick();
        move(jumper, false, false, true); heart.tick();
        while (heart.encounter.warningTicks() > 0) { move(jumper, false, false, false); heart.tick(); }
        do {
            assertFalse(roots.intersects(jumper.getBounds()));
            move(jumper, false, false, false); heart.tick();
        } while (heart.encounter.action() == ChapterBossEncounter.Action.ROOT_SURGE);
    }

    @Test void finalHeartUsesThreeDistinctActionsWithoutReintroducingSeveredOrgansOrSummons() {
        var f = new Fight(4); f.stage(3);
        Set<ChapterBossEncounter.Action> attacks = new HashSet<>();
        for (int i = 0; i < 2500; i++) {
            f.tick();
            if (f.encounter.warningTicks() > 0) attacks.add(f.encounter.action());
            assertTrue(f.part("left-organ").destroyed()); assertTrue(f.part("right-organ").destroyed());
        }
        assertEquals(Set.of(ChapterBossEncounter.Action.HEART_CRAWL, ChapterBossEncounter.Action.HEART_PULSE,
                ChapterBossEncounter.Action.ROOT_SURGE), attacks);
        assertTrue(f.enemies.getEnemies().isEmpty());
    }

    @Test void pulseCommitsOriginsBeforeReleaseAndItsRealProjectilesLeaveCrouchingSpace() {
        var f = new Fight(4); f.stage(3); f.until(ChapterBossEncounter.Action.HEART_PULSE);
        var locked = f.encounter.predictedShots(); assertEquals(4, locked.size());
        for (int i = 0; i < 85; i++) {
            f.playerX += 3; f.tick();
            assertEquals(locked, f.encounter.predictedShots()); assertTrue(f.shots.isEmpty());
        }
        f.tick(); assertEquals(4, f.shots.size());
        Player standing = player(300, CharacterId.REPAIR), crouching = player(300, CharacterId.REPAIR);
        crouching.setAimInput(false, true, false, false, false); move(crouching, false, false, false);
        boolean threatenedStanding = false;
        for (int i = 0; i < 160; i++) for (Projectile shot : f.shots) {
            shot.update();
            threatenedStanding |= shot.hits(standing.getBounds());
            assertFalse(shot.hits(crouching.getBounds()));
        }
        assertTrue(threatenedStanding, "The low pulse should require a stance decision");
    }

    @Test void newHazardsRemainHarmlessForTheEntireWarningAndNeverRetarget() {
        for (var action : List.of(ChapterBossEncounter.Action.LEFT_SWEEP, ChapterBossEncounter.Action.RIGHT_SWEEP,
                ChapterBossEncounter.Action.GANTRY_LOCK, ChapterBossEncounter.Action.ROOT_SURGE)) {
            var f = new Fight(action == ChapterBossEncounter.Action.ROOT_SURGE ? 4 : 2);
            f.stage(action == ChapterBossEncounter.Action.LEFT_SWEEP || action == ChapterBossEncounter.Action.RIGHT_SWEEP ? 2 : 3);
            f.until(action);
            var locked = f.encounter.telegraphs().stream().map(Boss.AttackTelegraph::bounds).toList();
            assertTrue(f.encounter.warningTicks() >= 80);
            while (f.encounter.warningTicks() > 1) {
                f.playerX = f.playerX == 450 ? 1000 : 450; f.tick();
                assertEquals(locked, f.encounter.telegraphs().stream().map(Boss.AttackTelegraph::bounds).toList());
                assertTrue(f.encounter.telegraphs().stream().noneMatch(t -> t.active() || t.damage() > 0));
            }
            f.tick();
            assertEquals(locked, f.encounter.telegraphs().stream().map(Boss.AttackTelegraph::bounds).toList());
            assertTrue(f.encounter.telegraphs().stream().allMatch(t -> t.active() && t.damage() > 0));
        }
    }

    @Test void aSaturatedPulseIsRejectedWholeWithoutReplacingExistingShotsOrRetrying() {
        var f = new Fight(4); f.stage(3); f.until(ChapterBossEncounter.Action.HEART_PULSE);
        var bounded = new ProjectileBuffer(4);
        Projectile existing = new Projectile(10, 10, 1, 0, ProjectileType.ENEMY);
        bounded.add(existing);
        for (int i = 0; i < 86; i++) f.encounter.update(480, 200, 450, 3, f.enemies, bounded);
        assertEquals(List.of(existing), bounded); assertEquals(4, bounded.rejectedProjectiles());
        bounded.clear();
        f.encounter.update(480, 200, 450, 3, f.enemies, bounded);
        assertTrue(bounded.isEmpty(), "A refused salvo cannot launch late without a new warning");
    }

    @Test void exposedHeartContractsForTransitionButStillTakesNormalDamageAndImmediatelyWarnsAfterward() {
        var f = new Fight(4); f.stage(3);
        var core = f.part("core");
        assertFalse(core.weak()); assertTrue(core.targetable());
        assertEquals(100, f.encounter.hit(core.bounds().rectangle(), 100, 16000, 16000).coreDamage());
        assertEquals("chapter.boss.core.transition", f.encounter.hintKey());
        for (int i = 0; i < 89; i++) {
            f.tick(); assertEquals(ChapterBossEncounter.Action.RECONFIGURE, f.encounter.action());
            assertFalse(f.encounter.isContactDangerous()); assertTrue(f.encounter.telegraphs().isEmpty());
        }
        f.tick();
        assertEquals(ChapterBossEncounter.Action.HEART_CRAWL, f.encounter.action());
        assertEquals(90, f.encounter.warningTicks()); assertTrue(f.part("core").weak());
        assertEquals(220, f.encounter.hit(f.part("core").bounds().rectangle(), 100, 16000, 16000).coreDamage());
    }

    @Test void gantryTransitionPausesWeakBonusWithoutEatingEarnedExposureOrRestoringBrokenArms() {
        var f = new Fight(2); f.destroy("left-arm"); f.destroy("right-arm");
        assertEquals(240, f.encounter.exposureTicks());
        f.stage(3);
        assertFalse(f.part("core").weak()); assertEquals(0, f.encounter.exposureTicks());
        assertEquals(100, f.encounter.hit(f.part("core").bounds().rectangle(), 100, 16000, 16000).coreDamage());
        for (int i = 0; i < 90; i++) f.tick();
        assertEquals(ChapterBossEncounter.Action.EXPOSED, f.encounter.action());
        assertTrue(f.part("core").weak()); assertEquals(240, f.encounter.exposureTicks());
        assertTrue(f.part("left-arm").destroyed()); assertTrue(f.part("right-arm").destroyed());
        assertEquals(200, f.encounter.hit(f.part("core").bounds().rectangle(), 100, 16000, 16000).coreDamage());
    }

    private static Player player(int x, CharacterId role) {
        Player p = new Player(x, 450);
        p.getRunBuild().setIdentity(role, GameDifficulty.STANDARD);
        move(p, false, false, false); return p;
    }
    private static void move(Player player, boolean left, boolean right, boolean jump) {
        player.update(left, right, jump, false, 480, 1800, new ArrayList<>(), List.of());
    }
}
