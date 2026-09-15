package com.bigphil.mergehell.combat;

import com.bigphil.mergehell.model.*;
import com.bigphil.mergehell.progression.*;
import org.junit.jupiter.api.Test;
import java.awt.Rectangle;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CombatDepthTest {
    @Test void allSixWeaponsRotateAllEightDirectionsAndInheritTravelOnlyOnce() {
        for(var weapon:WeaponId.values())for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++) {
            if(x==0&&y==0)continue;
            Aim aim=new Aim(x,y);var build=new RunBuild(weapon);
            var stationary=new WeaponFireController().fire(new FireRequest(weapon,300,220,x<0?-1:1,
                    build.effectiveStats(),false,new Random(7),false,0,aim));
            var moving=new WeaponFireController().fire(new FireRequest(weapon,300,220,x<0?-1:1,
                    build.effectiveStats(),false,new Random(7),false,6,aim));
            assertEquals(build.effectiveStats().pellets(),moving.size());
            for(int i=0;i<moving.size();i++) {
                var a=stationary.get(i);var b=moving.get(i);
                assertEquals(6,b.getVx()-a.getVx(),1e-8);assertEquals(a.getVy(),b.getVy(),1e-8);
                assertTrue(a.getVx()*aim.x()+a.getVy()*aim.y()>0);
                assertEquals(build.effectiveStats().speed(),Math.hypot(a.getVx(),a.getVy()),1e-8);
                assertEquals(300,a.getBounds().getCenterX(),.6);assertEquals(220,a.getBounds().getCenterY(),.6);
            }
        }
    }
    @Test void verticalBeamUsesANarrowTallSweepAndCannotHitAnAdjacentParallelLane() {
        var build=new RunBuild(WeaponId.REFACTOR_BEAM);
        var beam=new WeaponFireController().fire(new FireRequest(build.weapon(),200,300,1,build.effectiveStats(),
                false,new Random(2),false,0,new Aim(0,-1))).get(0);
        assertTrue(beam.getBounds().height>beam.getBounds().width);
        beam.update();
        assertTrue(beam.hits(new Rectangle(195,265,10,10)));
        assertFalse(beam.hits(new Rectangle(225,270,5,5)));
    }
    @Test void aimingDownMeansCrouchOnlyOnGroundAndAimLockKeepsFeetStill() {
        Player player=new Player(100,450);var shots=new ArrayList<Projectile>();
        player.update(false,false,false,false,480,1200,shots,List.of());
        player.setAimInput(false,true,true,false,true);
        player.update(false,true,false,true,480,1200,shots,List.of());
        assertTrue(player.isCrouching());assertEquals(480,player.getY()+player.getBounds().height);
        assertEquals(100,player.getX());assertEquals(new Aim(1,0),player.getAim());
        player.requestJump();player.update(false,true,false,false,480,1200,shots,List.of());
        assertFalse(player.isGrounded());assertFalse(player.isCrouching());assertEquals(new Aim(1,1),player.getAim());
        player.clearAimInput();assertEquals(new Aim(1,0),player.getAim());
    }
    @Test void bombCooldownCannotBeBypassedByRepeatedUseOrCheckpointRestore() {
        Player player=new Player(100,450);player.setCombatSeed(1);
        assertTrue(player.useBomb());assertFalse(player.useBomb());
        Player copy=new Player(100,450);copy.restoreCheckpoint(player.checkpointForNextLevel(),player.getRunBuild(),100,450);
        assertFalse(copy.useBomb());
        for(int i=0;i<60;i++)copy.update(false,false,false,false,480,1200,new ArrayList<>(),List.of());
        assertTrue(copy.useBomb());assertEquals(1,copy.getBombs());
    }
    @Test void productionDifficultyScalesAllRostersAndKeepsWarningsSeparateFromRecovery() {
        for(var type:EntityType.values())if(type.isHostile()) {
            int low=CombatBalance.enemyHealth(type,3,GameDifficulty.RELAXED),normal=CombatBalance.enemyHealth(type,3,GameDifficulty.STANDARD);
            assertTrue(low<normal);assertTrue(normal<CombatBalance.enemyHealth(type,3,GameDifficulty.CHALLENGE));
        }
        for(int chapter=0;chapter<5;chapter++)assertTrue(CombatBalance.bossHealth(chapter,GameDifficulty.STANDARD)>=2650);
        var manager=new ObstacleManager(1);manager.configureCombat(2,GameDifficulty.CHALLENGE);manager.spawnEnemy(500,402,EntityType.WARDEN);
        assertEquals(CombatBalance.enemyHealth(EntityType.WARDEN,2,GameDifficulty.CHALLENGE),manager.getEnemies().get(0).getHp());
    }
}
