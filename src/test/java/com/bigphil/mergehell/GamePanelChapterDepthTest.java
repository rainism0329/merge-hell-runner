package com.bigphil.mergehell;

import com.bigphil.mergehell.persistence.*;
import com.bigphil.mergehell.world.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** E input, safe-save capture and menu Continue exercise the production controller boundary. */
class GamePanelChapterDepthTest {
    private final MergeHellStateService storage=MergeHellStateService.getInstance();
    private MergeHellState original;
    @BeforeEach void isolate(){original=storage.getState();storage.loadState(new MergeHellState());}
    @AfterEach void restore(){storage.loadState(original);}

    @Test void foundryChoicesPersistThroughRealSafeSaveAndContinueWithoutRewardingAgain()throws Exception {
        try(var h=world(3)) {
            interact(h,8300,260);assertTrue(route(h).snapshot().mechanisms().freightForward());
            assertTrue(exploration(h).complete(),"The upper exhaust route is optional");
            h.place(8680,450);h.quietTicks(3);double before=h.player().getX();h.quietTicks(10);
            assertTrue(h.player().getX()>before+8,"Restored freight power drives the actual grounded player");
            interact(h,9340,240);assertTrue(route(h).snapshot().mechanisms().exhaustOpen());
            assertFalse(route(h).snapshot().surfaces().stream().anyMatch(s->s.x()>=8800&&s.active()));
            saveAndContinue(h,3);
            assertTrue(route(h).snapshot().mechanisms().freightForward());
            assertTrue(route(h).snapshot().mechanisms().exhaustOpen());
            int score=h.context().score;interact(h,9340,240);assertEquals(score,h.context().score);
        }
    }

    @Test void hiveNerveRestoresOpenWithoutRefillingOrAwardingTheLivingProps()throws Exception {
        try(var h=world(4)) {
            interact(h,8340,230);assertTrue(route(h).snapshot().mechanisms().broodQuiet());
            assertTrue(exploration(h).complete(),"The final nerve shortcut is optional");
            interact(h,9460,255);h.quietTicks(130);
            assertEquals(120,route(h).snapshot().mechanisms().membraneRetraction());
            var health=route(h).savedHealth();var hatches=route(h).savedHatches();
            saveAndContinue(h,4);
            assertTrue(route(h).snapshot().mechanisms().broodQuiet());
            assertEquals(120,route(h).snapshot().mechanisms().membraneRetraction());
            assertEquals(health,route(h).savedHealth());assertEquals(hatches,route(h).savedHatches());
            int score=h.context().score;interact(h,9460,255);assertEquals(score,h.context().score);
        }
    }

    @Test void previousPreviewStructureMapsRemainValidForAllThreeLateChapters()throws Exception {
        for(int level=2;level<=4;level++)try(var h=world(level)) {
            // Captured from the previous maturity-preview's compiled controller, not this run's constructor.
            int[] oldHp=switch(level) {
                case 2 -> new int[]{60,60,60,60,90};
                case 3 -> new int[]{45,45,45,60};
                default -> new int[]{160,160,160,160,110,110,110,110,145,210};
            };
            var initialHealth=new HashMap<Integer,Integer>();var initialHatches=new HashMap<Integer,Integer>();
            for(int id=0;id<oldHp.length;id++){initialHealth.put(id,id==0?0:oldHp[id]);initialHatches.put(id,0);}
            if(level==4)initialHatches.put(8,2);
            route(h).restoreStructures(initialHealth,initialHatches);
            h.place(9800,450);h.quietTicks(3);h.enemies().getEnemyBullets().clear();h.invoke("trySafeCheckpoint");
            var checkpoint=storage.readCheckpoint().orElseThrow();
            assertEquals(level,checkpoint.mission);assertEquals(CheckpointCodec.SAFE_SEGMENT,checkpoint.checkpointKind);
            assertEquals(level==2?5:level==3?4:10,checkpoint.routeHealth.size());
            var fresh=new ChapterRouteController(level,480);fresh.restoreStructures(initialHealth,initialHatches);
            CheckpointCodec.validate(checkpoint);
            h.key("PAUSE_P");h.tick();h.key("MENU");h.tick();h.key("START");h.tick();
            assertEquals(GameState.RUNNING,h.state());assertEquals(level,h.get("level"));
            assertEquals(initialHealth,route(h).savedHealth());assertEquals(initialHatches,route(h).savedHatches());
        }
    }

    private void saveAndContinue(HeapGameHarness h,int level)throws Exception {
        h.place(9800,450);h.quietTicks(3);h.enemies().getEnemyBullets().clear();h.invoke("trySafeCheckpoint");
        var checkpoint=storage.readCheckpoint().orElseThrow();
        assertEquals(level,checkpoint.mission);assertEquals(CheckpointCodec.SAFE_SEGMENT,checkpoint.checkpointKind);
        assertEquals(Set.of(1,2),checkpoint.explorationVisited);
        var savedHealth=Map.copyOf(checkpoint.routeHealth);int score=checkpoint.score;
        h.key("PAUSE_P");h.tick();h.key("MENU");h.tick();h.key("START");h.tick();
        assertEquals(GameState.RUNNING,h.state());assertEquals(Set.of(1,2),exploration(h).visited());
        assertEquals(savedHealth,route(h).savedHealth());assertEquals(score,h.context().score);
        assertFalse(h.player().isDebugMode());
    }
    private static HeapGameHarness world(int level)throws Exception {
        var h=new HeapGameHarness();h.set("level",level);h.invoke("advanceLevel");h.tick();return h;
    }
    private static void interact(HeapGameHarness h,double x,double y)throws Exception {
        h.enemies().clearHostiles();h.enemies().getEnemyBullets().clear();h.place(x,y);
        h.key("HEAP_PURGE");h.tick();h.quietTicks(2);
    }
    private static ChapterRouteController route(HeapGameHarness h)throws Exception {return (ChapterRouteController)h.get("chapterRoute");}
    private static ExplorationRoute exploration(HeapGameHarness h)throws Exception {return (ExplorationRoute)h.get("exploration");}
}
