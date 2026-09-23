package com.bigphil.mergehell.world;

import com.bigphil.mergehell.model.Player;
import org.junit.jupiter.api.Test;
import java.awt.Rectangle;
import java.lang.reflect.Field;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NestHostileBudgetTest {
    @Test void fullBattleBudgetDefersHatchingWithoutConsumingAChargeOrDroppingTheRequest() {
        var route=new ChapterRouteController(4,480);
        var player=new Player(550,450);
        int id=route.snapshot().props().stream().filter(p->p.bounds().x==550).findFirst().orElseThrow().id();
        for(int tick=0;tick<600;tick++) {
            route.beforeMove(player,false);route.afterMove(player,player.getX(),3,3);
            assertTrue(route.drainEvents().stream().noneMatch(ChapterRouteController.Event::spawn));
        }
        assertEquals(0,route.savedHatches().get(id));
        route.beforeMove(player,false);route.afterMove(player,player.getX(),2,3);
        assertEquals(1,route.drainEvents().stream().filter(ChapterRouteController.Event::spawn).count());
        assertEquals(1,route.savedHatches().get(id));
        for(int tick=0;tick<600;tick++) {
            route.beforeMove(player,false);route.afterMove(player,player.getX(),3,3);
            assertTrue(route.drainEvents().stream().noneMatch(ChapterRouteController.Event::spawn));
        }
        assertEquals(1,route.savedHatches().get(id));
    }

    @Test void freeRouteOverloadKeepsTheSixHostileLimit() {
        var route=new ChapterRouteController(4,480);
        var player=new Player(550,450);
        for(int tick=0;tick<200;tick++) {
            route.beforeMove(player,false);route.afterMove(player,player.getX(),6);
        }
        assertTrue(route.drainEvents().stream().noneMatch(ChapterRouteController.Event::spawn));
        assertEquals(0,route.savedHatches().values().stream().mapToInt(Integer::intValue).sum());
        route.beforeMove(player,false);route.afterMove(player,player.getX(),5);
        assertEquals(1,route.drainEvents().stream().filter(ChapterRouteController.Event::spawn).count());
        assertEquals(1,route.savedHatches().values().stream().mapToInt(Integer::intValue).sum());
    }

    @Test void twoReadyNestsReserveTheSameRemainingSlotOnlyOnce() throws Exception {
        var route=new ChapterRouteController(4,480);
        var player=new Player(550,450);
        // Current authored nests are spaced apart. Bring two actual structures into view to
        // guard simultaneous readiness if a future encounter puts adjacent nests in one arena.
        Field structures=ChapterRouteController.class.getDeclaredField("structures");structures.setAccessible(true);
        var entries=(List<?>)structures.get(route);
        int nearby=0;
        for(Object structure:entries) {
            Field kind=structure.getClass().getDeclaredField("kind");kind.setAccessible(true);
            if(kind.get(structure)!=ChapterRouteController.Kind.NEST)continue;
            Field bounds=structure.getClass().getDeclaredField("bounds");bounds.setAccessible(true);
            ((Rectangle)bounds.get(structure)).x=550+nearby*100;
            Field hatch=structure.getClass().getDeclaredField("hatch");hatch.setAccessible(true);hatch.setInt(structure,0);
            if(++nearby==2)break;
        }
        route.beforeMove(player,false);route.afterMove(player,player.getX(),2,3);
        assertEquals(1,route.drainEvents().stream().filter(ChapterRouteController.Event::spawn).count());
        assertEquals(1,route.savedHatches().values().stream().mapToInt(Integer::intValue).sum());
        route.beforeMove(player,false);route.afterMove(player,player.getX(),3,3);
        assertTrue(route.drainEvents().stream().noneMatch(ChapterRouteController.Event::spawn));
        route.beforeMove(player,false);route.afterMove(player,player.getX(),2,3);
        assertEquals(1,route.drainEvents().stream().filter(ChapterRouteController.Event::spawn).count());
        assertEquals(2,route.savedHatches().values().stream().mapToInt(Integer::intValue).sum());
    }
}
