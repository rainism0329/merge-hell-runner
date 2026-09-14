package com.bigphil.mergehell.model;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ArchitectDisruptionTest {
    @Test void breakingRealArmsStopsAllAttacksUntilExposureAndVisibleReconstructionFinish() {
        Boss boss=architect();var enemies=new ObstacleManager();var bullets=new ArrayList<Projectile>();
        for(int i=0;i<220&&boss.getActiveHazards().isEmpty();i++)boss.update(enemies,480,150,450,bullets);
        assertFalse(boss.getActiveHazards().isEmpty());
        breakArms(boss);int initialTick=boss.getCombatTick();double x=boss.getX(),y=boss.getY();
        assertEquals(240,boss.getVulnerabilityTicks());assertEquals("EXPOSED",boss.getEncounterAction());
        for(int i=0;i<240;i++) {
            boss.update(enemies,480,350,380,bullets);
            assertTrue(boss.getActiveHazards().isEmpty());assertTrue(bullets.isEmpty());
            assertFalse(boss.isContactDangerous());assertEquals(x,boss.getX());assertEquals(y,boss.getY());
        }
        assertTrue(boss.getCombatTick()>initialTick);assertFalse(boss.isVulnerable());
        assertEquals("REBUILD",boss.getEncounterAction());
        for(int i=0;i<90;i++)boss.update(enemies,480,150,450,bullets);
        assertTrue(boss.getParts().stream().filter(p->!p.id().equals("core")).allMatch(p->p.hp()==p.maxHp()));
        for(int i=0;i<500&&boss.getActiveHazards().isEmpty();i++)boss.update(enemies,480,150,450,bullets);
        assertFalse(boss.getActiveHazards().isEmpty(),"The authored encounter resumes after its bounded recovery");
    }
    @Test void respawnDuringExposureCannotLeaveBothDestroyedArmsAndAClosedPermanentCore() {
        Boss boss=architect();breakArms(boss);int hp=boss.getHp(),stage=boss.getCombatStage();
        boss.clearVulnerability();boss.clearVulnerability();assertFalse(boss.isVulnerable());
        assertEquals("RECONFIGURE",boss.getEncounterAction());assertFalse(boss.isContactDangerous());
        var enemies=new ObstacleManager();var bullets=new ArrayList<Projectile>();
        for(int i=0;i<90;i++)boss.update(enemies,480,150,450,bullets);
        assertEquals("REBUILD",boss.getEncounterAction());assertTrue(boss.getParts().get(0).destroyed());
        for(int i=0;i<90;i++)boss.update(enemies,480,150,450,bullets);
        assertFalse(boss.getParts().get(0).destroyed());assertFalse(boss.getParts().get(1).destroyed());
        assertEquals(hp,boss.getHp());assertEquals(stage,boss.getCombatStage());assertTrue(bullets.isEmpty());
        breakArms(boss);assertEquals(240,boss.getVulnerabilityTicks(),"Reconstructed arms can open a fresh legitimate weakpoint");
    }
    @Test void legacySupportHooksCannotOpenRefreshOrExtendMultipartWindows() {
        Boss boss=architect();boss.interruptBlueprint(Integer.MAX_VALUE);assertFalse(boss.isVulnerable());
        breakArms(boss);var enemies=new ObstacleManager();var bullets=new ArrayList<Projectile>();
        for(int i=0;i<40;i++)boss.update(enemies,480,150,450,bullets);
        assertEquals(200,boss.getVulnerabilityTicks());boss.interruptBlueprint(Integer.MAX_VALUE);
        assertEquals(200,boss.getVulnerabilityTicks());
        for(int level:List.of(3,4)) {
            var other=new Boss("Other",12000,"!",960,level,7);other.previewArrival(1,480);other.activate();
            other.interruptBlueprint(180);assertFalse(other.isVulnerable());
        }
        boss.damage(Integer.MAX_VALUE);boss.interruptBlueprint(180);assertFalse(boss.isVulnerable());
    }
    private static void breakArms(Boss boss) {
        for(var part:boss.getParts())if(part.id().endsWith("-arm"))boss.damageAt(part.bounds().rectangle(),part.hp());
    }
    private static Boss architect() {
        Boss boss=new Boss("Architect",10000,"!",960,2,17);
        boss.previewArrival(1,480);boss.activate();boss.update(new ObstacleManager(),480,150,450,new ArrayList<>());return boss;
    }
}
