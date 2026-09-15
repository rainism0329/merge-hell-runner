package com.bigphil.mergehell.world;
import com.bigphil.mergehell.model.*;
import com.bigphil.mergehell.progression.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ExplorationRouteTest {
    @Test void extendedGantryGroundSegmentsAlwaysContainAWholeEnemy() {
        var route=new ChapterRouteController(2,480);
        for(int x=7600;x<=10900;x+=10)for(int width:new int[]{40,58,60,82}) {
            var movement=route.movementBounds(x,width);
            assertTrue(movement.maxX()-movement.minX()>=width,"Invalid terrain interval at "+x);
        }
    }
    private void move(Player p,boolean right){p.update(false,right,false,false,480,12000,new ArrayList<>(),List.of());}
    @Test void everyCharacterMustJumpAndCanClearEveryChaptersAuthoredSolidRise() {
        for(int chapter=0;chapter<5;chapter++)for(var role:CharacterId.values()) {
            var route=new ExplorationRoute(chapter);
            var block=route.snapshot().blocks().stream().filter(b->!b.canopy()).findFirst().orElseThrow().bounds();
            Player p=new Player(block.x-70,450);p.getRunBuild().setIdentity(role,GameDifficulty.STANDARD);
            p.setSolids(List.of(block));move(p,false);
            for(int i=0;i<30;i++)move(p,true);
            assertTrue(p.getX()+30<=block.x+.01,chapter+" "+role);
            p.requestJump();
            for(int i=0;i<120;i++) {if(i==17)p.requestJump();move(p,true);}
            assertTrue(p.getX()>block.getMaxX(),chapter+" "+role+" x="+p.getX());
        }
    }
    @Test void crawlPassageStopsStandingAndKeepsCrouchUntilThereIsHeadroom() {
        var route=new ExplorationRoute(0);var roof=route.snapshot().blocks().stream().filter(ExplorationRoute.Block::canopy).findFirst().orElseThrow().bounds();
        Player p=new Player(roof.x-70,450);p.setSolids(List.of(roof));move(p,false);
        for(int i=0;i<25;i++)move(p,true);
        assertEquals(roof.x-30,p.getX(),.01);
        p.setAimInput(false,true,false,false,true);for(int i=0;i<35;i++)move(p,true);
        assertTrue(p.getX()>roof.x);assertTrue(p.isCrouching());
        p.clearAimInput();move(p,false);assertTrue(p.isCrouching());
        for(int i=0;i<100;i++)move(p,true);assertFalse(p.isCrouching());
    }
    @Test void noContactDoesNotSwallowShotsAndEachLandmarkRewardsOnlyOnce() {
        for(int chapter=0;chapter<5;chapter++) {
            var route=new ExplorationRoute(chapter);
            var shot=new Projectile(100,400,8,0,ProjectileType.COMMIT);
            assertFalse(route.consumeShot(shot,Double.POSITIVE_INFINITY));assertFalse(shot.isDead());
            for(var point:route.snapshot().landmarks()) {
                Player player=new Player(point.bounds().x,point.bounds().y);route.update(player,true);
                assertEquals(1,route.drainEvents().size());route.update(player,true);assertTrue(route.drainEvents().isEmpty());
            }
            assertTrue(route.complete());var copy=new ExplorationRoute(chapter);copy.restore(route.visited());assertTrue(copy.complete());
        }
    }
}
