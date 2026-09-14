package com.bigphil.mergehell.model;

import org.junit.jupiter.api.Test;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ArchitectBossTest {
    private static final class Fight {
        final Boss boss = new Boss("Gantry", 16000, "A", 960, 2, 8L);
        final ObstacleManager enemies = new ObstacleManager();
        final List<Projectile> bullets = new ArrayList<>();
        Fight() { boss.previewArrival(1, 480); boss.activate(); tick(200); }
        void tick(double px) { boss.update(enemies,480,px,450,bullets); }
        void until(String action) {
            for(int i=0;i<1800 && !boss.getEncounterAction().equals(action);i++) tick(200);
            assertEquals(action,boss.getEncounterAction());
        }
        Boss.PartView part(String id) { return boss.getParts().stream().filter(p->p.id().equals(id)).findFirst().orElseThrow(); }
        void destroy(String id) { var p=part(id); boss.damageAt(p.bounds().rectangle(),p.hp()); }
    }
    @Test void gantryHasIndependentArmsAndGroundReachablePartsInsteadOfOneBodyBox() {
        var f=new Fight(); assertEquals(280,f.boss.getWidth()); assertEquals(170,f.boss.getHeight());
        assertEquals(3,f.boss.getParts().size());
        for(String id:List.of("left-arm","right-arm")) {
            var p=f.part(id); assertTrue(p.bounds().intersects(new Rectangle((int)p.bounds().x(),448,58,5)));
        }
        var original=f.boss.getParts(); int left=f.part("left-arm").hp(), right=f.part("right-arm").hp();
        assertEquals(5,f.boss.damageAt(f.part("left-arm").bounds().rectangle(),20));
        assertEquals(left-20,f.part("left-arm").hp()); assertEquals(right,f.part("right-arm").hp());
        assertEquals(left,original.get(0).hp());
        assertThrows(UnsupportedOperationException.class,()->original.clear());
        assertFalse(f.boss.canHit(new Rectangle((int)f.boss.getX()+65,(int)f.boss.getY()+145,15,15)));
    }
    @Test void stampIsLockedBeforeDamageAndDestroyingItsArmCancelsOnlyItsStrike() {
        var f=new Fight(); f.until("LEFT_STAMP"); var locked=f.boss.getAttackTelegraphs();
        assertEquals(72,f.boss.getWarningTicks());
        for(int i=0;i<71;i++) {f.tick(1300); assertTrue(f.boss.getActiveHazards().isEmpty()); assertEquals(locked.get(0).bounds(),f.boss.getAttackTelegraphs().get(0).bounds());}
        f.tick(1300); assertEquals(locked.get(0).bounds(),f.boss.getActiveHazards().get(0).bounds());
        f.destroy("left-arm"); assertTrue(f.boss.getActiveHazards().isEmpty()); assertFalse(f.boss.isVulnerable());
        f.until("CROSSBEAM"); assertEquals(1,f.boss.getAttackTelegraphs().size());
        assertEquals(276,f.boss.getAttackTelegraphs().get(0).bounds().y());
    }
    @Test void destroyingBothArmsOpensCoreAndReconstructionHasAnEntireHarmlessWarning() {
        var f=new Fight(); f.destroy("left-arm"); f.destroy("right-arm");
        assertEquals("EXPOSED",f.boss.getEncounterAction()); assertEquals(240,f.boss.getVulnerabilityTicks());
        assertEquals(20,f.boss.damageAt(f.part("core").bounds().rectangle(),10));
        assertFalse(f.boss.isContactDangerous()); assertTrue(f.boss.getAttackTelegraphs().isEmpty());
        for(int i=0;i<240;i++)f.tick(200);
        assertEquals("REBUILD",f.boss.getEncounterAction());
        for(int i=0;i<89;i++){f.tick(200);assertTrue(f.part("left-arm").destroyed());assertFalse(f.boss.isContactDangerous());}
        f.tick(200);assertFalse(f.part("left-arm").destroyed());assertFalse(f.part("right-arm").destroyed());
    }
    @Test void areaDamageAffectsEachArmOnceAndNeverMultipliesCoreDamageByPartCount() {
        var f=new Fight(); int hp=f.boss.getHp(),arm=f.part("left-arm").hp();
        assertEquals(120,f.boss.damage(120)); assertEquals(hp-120,f.boss.getHp());
        assertEquals(arm-40,f.part("left-arm").hp());assertEquals(arm-40,f.part("right-arm").hp());
        f.boss.interruptBlueprint(180);assertFalse(f.boss.isVulnerable());
        assertEquals(0,f.boss.damageAt(new Rectangle(-500,-500,10,10),100));
    }
    @Test void twoBeamHeightsLeaveARealJumpableCorridorAndAllHazardsHaveFullPriorWarnings() {
        var f=new Fight();f.until("CROSSBEAM");
        var tell=f.boss.getAttackTelegraphs();assertEquals(2,tell.size());
        var low=tell.get(0).bounds();var high=tell.get(1).bounds();
        assertTrue(low.y()-(high.y()+high.height())>=100);
        for(int i=0;i<77;i++){f.tick(200);assertTrue(f.boss.getActiveHazards().isEmpty());}
        f.tick(200);assertEquals(tell.stream().map(Boss.AttackTelegraph::bounds).toList(),f.boss.getActiveHazards().stream().map(Boss.AttackTelegraph::bounds).toList());
        for(var hazard:f.boss.getActiveHazards())assertTrue(hazard.damage()>0);
    }
    @Test void zeroNegativeAndDeadPartHitsDoNotGenerateGhostImpacts() {
        var f=new Fight();var left=f.part("left-arm");long seq=f.boss.getDamageSequence();
        assertEquals(0,f.boss.damageAt(left.bounds().rectangle(),0));assertEquals(seq,f.boss.getDamageSequence());
        f.destroy("left-arm");seq=f.boss.getDamageSequence();
        assertFalse(f.boss.canHit(left.bounds().rectangle()));
        assertEquals(0,f.boss.damageAt(left.bounds().rectangle(),40));assertEquals(seq,f.boss.getDamageSequence());
    }
}
