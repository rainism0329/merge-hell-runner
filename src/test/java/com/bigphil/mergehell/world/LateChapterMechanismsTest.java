package com.bigphil.mergehell.world;

import com.bigphil.mergehell.model.*;
import com.bigphil.mergehell.progression.*;
import org.junit.jupiter.api.Test;
import java.awt.Rectangle;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LateChapterMechanismsTest {
    private static final class Walk {
        final int chapter;
        final ExplorationRoute exploration;
        final ChapterRouteController route;
        final Player player;
        final List<ChapterRouteController.Event> events=new ArrayList<>();
        final List<ExplorationRoute.Event> rewards=new ArrayList<>();
        Walk(int chapter,CharacterId role,int x,int y) {
            this.chapter=chapter;exploration=new ExplorationRoute(chapter);route=new ChapterRouteController(chapter,480);
            player=new Player(x,y);player.getRunBuild().setIdentity(role,GameDifficulty.STANDARD);
        }
        void tick(boolean right,boolean interact) {
            exploration.prepare(false);exploration.beforeMove(player);
            route.syncExploration(exploration.visited());route.beforeMove(player,false);
            player.setSolids(exploration.solids());
            var platforms=new ArrayList<>(route.platforms());platforms.addAll(exploration.platforms());
            double before=player.getX();
            player.update(false,right,false,false,480,12000,new ArrayList<>(),platforms);
            route.afterMove(player,before,0);exploration.update(player,interact);
            events.addAll(route.drainEvents());rewards.addAll(exploration.drainEvents());
        }
        void activate(int id) {
            var point=exploration.snapshot().landmarks().stream().filter(p->p.id()==id).findFirst().orElseThrow();
            exploration.update(new Player(point.bounds().x,point.bounds().y),true);
            rewards.addAll(exploration.drainEvents());route.syncExploration(exploration.visited());
        }
        void jumpTo(double x,int secondJump) {
            player.requestJump();
            for(int tick=0;tick<115;tick++) {
                if(tick==secondJump)player.requestJump();
                tick(player.getX()<x,false);
            }
        }
        ChapterRouteController.Prop prop(int x) {
            return route.snapshot().props().stream().filter(p->p.bounds().x==x).findFirst().orElseThrow();
        }
    }
    @Test void freightConsoleReversesOnlyLateConveyorsAndRestoredFlagsKeepThatDirection() {
        var route=new ChapterRouteController(3,480);var p=new Player(8700,450);
        p.update(false,false,false,false,480,12000,new ArrayList<>(),List.of());
        route.beforeMove(p,false);assertEquals(8698.95,p.getX(),.001);
        assertEquals(-1,route.snapshot().surfaces().stream().filter(s->s.x()==8650).findFirst().orElseThrow().phase());
        route.syncExploration(Set.of(1));route.beforeMove(p,false);assertEquals(8700,p.getX(),.001);
        assertEquals(1,route.snapshot().surfaces().stream().filter(s->s.x()==8650).findFirst().orElseThrow().phase());
        assertEquals(-1,route.snapshot().surfaces().stream().filter(s->s.x()==2980).findFirst().orElseThrow().phase());
        var restored=new ChapterRouteController(3,480);restored.syncExploration(Set.of(1));
        assertEquals(route.snapshot().mechanisms(),restored.snapshot().mechanisms());
    }
    @Test void exhaustControlCreatesAPermanentSafeHeatLaneWithoutRemovingEarlierHazards() {
        Walk w=new Walk(3,CharacterId.REPAIR,9540,450);w.tick(false,false);
        assertTrue(w.player.getHp()<100,"unvented heat damages a standing player");
        w.activate(2);int hp=w.player.getHp();
        for(int tick=0;tick<720;tick++)w.tick(false,false);
        assertEquals(hp,w.player.getHp());
        assertFalse(w.route.snapshot().surfaces().stream().filter(s->s.x()>=8800).anyMatch(ChapterRouteController.Surface::active));
        assertTrue(w.route.snapshot().surfaces().stream().anyMatch(s->s.x()==1120&&s.active()));
        assertEquals(-1,w.route.snapshot().surfaces().stream().filter(s->s.x()==8890).findFirst().orElseThrow().phase());
        assertEquals(1,w.rewards.size());w.activate(2);assertEquals(1,w.rewards.size());
    }
    @Test void everyRoleCanCrossUnventedHeatUsingPlatformsOrWalkSafelyAfterVenting() {
        for(CharacterId role:CharacterId.values()) {
            Walk main=new Walk(3,role,9450,450);main.exploration.restore(Set.of(1));main.tick(false,false);
            main.jumpTo(9790,18);
            assertTrue(main.player.getX()>9730,role+" main route crosses the full hot channel");
            assertEquals(100,main.player.getHp(),role+" must use platforms without taking heat damage");
            assertTrue(main.exploration.complete());assertFalse(main.exploration.visited().contains(2));
            Walk safe=new Walk(3,role,9450,450);safe.exploration.restore(Set.of(1,2));
            for(int i=0;i<100;i++)safe.tick(safe.player.getX()<9790,false);
            assertTrue(safe.player.getX()>9730);assertEquals(100,safe.player.getHp());
            assertEquals(450,safe.player.getY(),.01,"safe branch permits a grounded crossing");
        }
    }
    @Test void broodRelayStopsOnlyTheLinkedNestAndPreservesItsFiniteHatchBudget() {
        Walk w=new Walk(4,CharacterId.REPAIR,8900,450);
        for(int i=0;i<180;i++)w.tick(false,false);
        assertEquals(1,w.events.stream().filter(e->e.key().equals("chapter.nest.hatch")).count());
        int nestId=w.prop(8910).id();int hp=w.prop(8910).hp();
        w.activate(1);w.route.resetTransient();
        for(int i=0;i<1000;i++)w.tick(false,false);
        assertEquals(1,w.events.stream().filter(e->e.key().equals("chapter.nest.hatch")).count());
        assertEquals(1,w.route.savedHatches().get(nestId));assertEquals(hp,w.prop(8910).hp());
        assertEquals(0,w.prop(8910).warningTicks());
        var copy=new ChapterRouteController(4,480);copy.restoreStructures(w.route.savedHealth(),w.route.savedHatches());
        copy.syncExploration(w.exploration.visited());
        for(int i=0;i<800;i++){copy.beforeMove(w.player,false);copy.afterMove(w.player,w.player.getX(),0);}
        assertEquals(1,copy.savedHatches().get(nestId));
        assertTrue(copy.drainEvents().stream().noneMatch(e->e.key().equals("chapter.nest.hatch")));
        var early=new Player(6100,450);
        for(int i=0;i<150;i++){copy.beforeMove(early,false);copy.afterMove(early,early.getX(),0);}
        assertTrue(copy.drainEvents().stream().anyMatch(e->e.key().equals("chapter.nest.hatch")),"earlier nests retain their own behavior");
    }
    @Test void nerveRetractionShrinksTheSameRenderedAndCollidingMembraneWithoutRefunds() {
        Walk w=new Walk(4,CharacterId.REPAIR,9650,450);w.exploration.restore(Set.of(1));w.tick(false,false);
        Projectile blocked=new Projectile(9650,360,80,0,ProjectileType.COMMIT);blocked.update();
        assertTrue(w.route.consumeShot(blocked,Double.POSITIVE_INFINITY));
        int remaining=w.prop(9700).hp(),membraneId=w.prop(9700).id();w.activate(2);
        Rectangle prior=w.prop(9700).bounds();
        for(int i=0;i<119;i++) {
            w.tick(false,false);Rectangle now=w.prop(9700).bounds();
            assertTrue(prior.contains(now),"retraction can only free space, never expand into a body");prior=now;
        }
        w.tick(false,false);
        assertTrue(w.route.snapshot().props().stream().noneMatch(p->p.id()==membraneId),"harmless remains must not stay in the target list");
        assertEquals(remaining,w.route.savedHealth().get(membraneId));
        Projectile through=new Projectile(9650,470,80,0,ProjectileType.COMMIT);through.update();
        assertFalse(w.route.consumeShot(through,Double.POSITIVE_INFINITY));
        for(int i=0;i<50;i++)w.tick(w.player.getX()<9850,false);
        assertTrue(w.player.getX()>9796);assertEquals(100,w.player.getHp());
        assertEquals(1,w.rewards.size());assertTrue(w.events.stream().noneMatch(e->e.key().endsWith(".broken")));
    }
    @Test void everyRoleCanClearClosedMembraneOrUseTheGroundShortcutWithoutJumping() {
        for(CharacterId role:CharacterId.values()) {
            Walk main=new Walk(4,role,9640,450);main.exploration.restore(Set.of(1));main.tick(false,false);
            main.jumpTo(9850,18);assertTrue(main.player.getX()>9796,role+" can bypass the unopened membrane");
            assertEquals(100,main.player.getHp());assertEquals(210,main.prop(9700).hp());
            Walk safe=new Walk(4,role,9640,450);safe.exploration.restore(Set.of(1,2));
            for(int i=0;i<60;i++)safe.tick(safe.player.getX()<9850,false);
            assertTrue(safe.player.getX()>9796);assertEquals(450,safe.player.getY(),.01);assertEquals(100,safe.player.getHp());
        }
    }
    @Test void restoringOldFlagSetsDoesNotAddResourcesOrRequireTheNewOptionalRoute() {
        for(int chapter:new int[]{3,4})for(Set<Integer> flags:List.of(Set.<Integer>of(),Set.of(1),Set.of(1,2))) {
            Walk w=new Walk(chapter,CharacterId.ENGINEER,9810,450);
            var health=w.route.savedHealth();var hatches=w.route.savedHatches();
            assertEquals(chapter==3?4:10,health.size(),"previous release's structure IDs remain unchanged");
            w.exploration.restore(flags);w.route.restoreStructures(health,hatches);w.route.syncExploration(flags);
            assertEquals(health,w.route.savedHealth());assertEquals(hatches,w.route.savedHatches());
            assertEquals(flags.contains(1),w.exploration.complete());assertTrue(w.route.drainEvents().isEmpty());
            for(int id:flags)w.activate(id);assertTrue(w.rewards.isEmpty());
            if(chapter==4)assertEquals(flags.contains(2)?120:0,w.route.snapshot().mechanisms().membraneRetraction());
        }
    }
    @Test void allFourRolesReachMandatoryAndOptionalControlPlatformsThroughRealJumps() {
        for(CharacterId role:CharacterId.values())for(int chapter:new int[]{3,4}) {
            int wallStop=chapter==3?7970:8010,wallTop=chapter==3?330:290;
            Walk primary=new Walk(chapter,role,wallStop-60,450);primary.tick(false,false);
            for(int i=0;i<35;i++)primary.tick(true,false);
            assertEquals(wallStop,primary.player.getX(),.01);
            primary.jumpTo(chapter==3?8080:8110,18);assertEquals(wallTop,primary.player.getY(),.01);
            primary.jumpTo(chapter==3?8310:8350,18);
            assertEquals(chapter==3?260:230,primary.player.getY(),.01,chapter+" "+role+" mandatory console");
            primary.tick(false,true);assertTrue(primary.exploration.visited().contains(1));
            assertEquals(1,primary.rewards.size());
            Walk optional=new Walk(chapter,role,chapter==3?9200:9300,450);optional.tick(false,false);
            optional.jumpTo(chapter==3?9340:9460,18);
            assertEquals(chapter==3?240:255,optional.player.getY(),.01,chapter+" "+role+" optional console");
            optional.tick(false,true);assertTrue(optional.exploration.visited().contains(2));
            assertEquals(1,optional.rewards.size());
        }
    }
}
