package com.bigphil.mergehell.world;

import com.bigphil.mergehell.model.*;
import com.bigphil.mergehell.progression.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CitadelPassageTest {
    private static final class Walk {
        final ExplorationRoute exploration=new ExplorationRoute(2);
        final ChapterRouteController route=new ChapterRouteController(2,480);
        final Player player;
        final List<ChapterRouteController.Event> terrainEvents=new ArrayList<>();
        final List<ExplorationRoute.Event> events=new ArrayList<>();
        Walk(CharacterId role,int x,int y) {
            player=new Player(x,y);player.getRunBuild().setIdentity(role,GameDifficulty.STANDARD);
        }
        void tick(boolean right,boolean interact) {
            tick(right,interact,List.of());
        }
        void tick(boolean right,boolean interact,List<java.awt.Rectangle> occupants) {
            route.syncExploration(exploration.visited());route.beforeMove(player,false);
            exploration.prepare(false);exploration.beforeMove(player,occupants);player.setSolids(exploration.solids());
            var platforms=new ArrayList<>(route.platforms());platforms.addAll(exploration.platforms());
            double x=player.getX();
            player.update(false,right,false,false,route.groundFor(x,30),12000,new ArrayList<>(),platforms);
            route.afterMove(player,x,0);exploration.update(player,interact);
            events.addAll(exploration.drainEvents());terrainEvents.addAll(route.drainEvents());
        }
        void activate(int id) {
            var point=exploration.snapshot().landmarks().stream().filter(p->p.id()==id).findFirst().orElseThrow();
            exploration.update(new Player(point.bounds().x,point.bounds().y),true);
            events.addAll(exploration.drainEvents());
        }
        double gateY(){return exploration.solids().stream().filter(b->b.x==9250).findFirst().orElseThrow().y;}
        long rescues(){return terrainEvents.stream().filter(e->e.key().equals("chapter.bridge.rescued")).count();}
    }
    @Test void winchMovesTheActualGateAndOpensWalkAndShotClearance() {
        Walk w=new Walk(CharacterId.REPAIR,8860,230);w.tick(false,true);
        assertEquals(Set.of(1),w.exploration.visited());assertEquals(320,w.gateY());
        Projectile blocked=new Projectile(9200,440,100,0,ProjectileType.COMMIT);blocked.update();
        assertTrue(w.exploration.consumeShot(blocked,Double.POSITIVE_INFINITY));
        for(int i=0;i<90;i++)w.tick(false,false);
        assertEquals(230,w.gateY());
        for(int i=0;i<100;i++)w.tick(false,false);
        assertEquals(140,w.gateY());
        Projectile open=new Projectile(9200,440,100,0,ProjectileType.COMMIT);open.update();
        assertFalse(w.exploration.consumeShot(open,Double.POSITIVE_INFINITY));
        w.player.setX(9190);w.player.setY(450);
        for(int i=0;i<60;i++)w.tick(true,false);
        assertTrue(w.player.getX()>9420);assertEquals(100,w.player.getHp());
        assertEquals(1,w.events.size());
    }
    @Test void risingGateCarriesRiderAndWaitsForAnAirborneBodyInsteadOfCrushingIt() {
        Walk rider=new Walk(CharacterId.WARDEN,9300,290);rider.tick(false,false);
        assertTrue(rider.player.isGrounded());rider.activate(1);
        for(int i=0;i<180;i++) {
            rider.tick(false,false);
            assertEquals(rider.gateY(),rider.player.getY()+30,.01);
            assertTrue(rider.player.isGrounded());
        }
        assertEquals(100,rider.player.getHp());assertEquals(140,rider.gateY());
        Walk airborne=new Walk(CharacterId.REPAIR,9300,289);airborne.activate(1);
        for(int i=0;i<12;i++)airborne.exploration.beforeMove(airborne.player);
        assertEquals(319,airborne.gateY(),"motor waits below an airborne player's feet");
        airborne.player.setX(9400+40);
        airborne.exploration.beforeMove(airborne.player);assertEquals(318,airborne.gateY());
    }
    @Test void allFourRolesCanTakeUnpoweredMainRouteWithoutRescueOrBridgeLock() {
        for(CharacterId role:CharacterId.values()) {
            Walk w=new Walk(role,9500,450);w.exploration.restore(Set.of(1));w.tick(false,false);
            while(w.player.getX()<9585)w.tick(true,false);
            w.player.requestJump();
            for(int frame=0;frame<90;frame++) {
                if(frame==17)w.player.requestJump();
                w.tick(w.player.getX()<9850,false);
            }
            assertTrue(w.player.getX()>=9800,role+" must land beyond the shaft");
            assertTrue(w.player.getX()<9900,role+" must land before the boss gate");
            assertTrue(w.player.isGrounded());assertEquals(450,w.player.getY(),.01);
            assertEquals(0,w.rescues());assertEquals(100,w.player.getHp());
            assertTrue(w.exploration.complete());assertFalse(w.exploration.visited().contains(2));
            assertEquals(0,w.route.snapshot().safeBridge().deployedWidth());
        }
    }
    @Test void optionalBridgeExtendsContinuouslyAndLetsEveryRoleWalkAcrossWithoutJumping() {
        for(CharacterId role:CharacterId.values()) {
            Walk w=new Walk(role,9570,280);w.exploration.restore(Set.of(1));w.tick(false,true);
            assertEquals(Set.of(1,2),w.exploration.visited());
            assertEquals(0,w.route.snapshot().safeBridge().deployedWidth());
            for(int i=1;i<=150;i++) {
                w.tick(false,false);
                assertEquals(i*2,w.route.snapshot().safeBridge().deployedWidth());
            }
            for(int i=0;i<130;i++)w.tick(w.player.getX()<9870,false);
            assertTrue(w.player.getX()>9800,role+" optional path reaches far bank");
            assertEquals(0,w.rescues());assertEquals(100,w.player.getHp());
            assertEquals(1,w.events.size(),"bridge gives its exploration reward once");
        }
    }
    @Test void allRolesReachBothConsolesThroughRealMovementFromTheApproach() {
        for(CharacterId role:CharacterId.values()) {
            Walk w=new Walk(role,8500,450);w.tick(false,false);
            for(int i=0;i<35;i++)w.tick(true,false);
            assertEquals(8570,w.player.getX(),.01,"walk must meet the approach wall");
            w.player.requestJump();
            for(int i=0;i<65;i++)w.tick(w.player.getX()<8660,false);
            assertTrue(w.player.isGrounded());assertEquals(345,w.player.getY(),.01,role+" reaches first step");
            w.player.requestJump();
            for(int i=0;i<90;i++) {
                if(i==16)w.player.requestJump();
                w.tick(w.player.getX()<8860,false);
            }
            assertTrue(w.player.isGrounded());assertEquals(230,w.player.getY(),.01,role+" reaches winch platform");
            w.tick(false,true);assertTrue(w.exploration.visited().contains(1));
            for(int i=0;i<185;i++)w.tick(w.player.getX()<9460,false);
            assertTrue(w.player.getX()>=9460,role+" passes opened gate");
            assertTrue(w.player.isGrounded());
            w.player.requestJump();
            for(int i=0;i<90;i++) {
                if(i==16)w.player.requestJump();
                w.tick(w.player.getX()<9570,false);
            }
            assertEquals(280,w.player.getY(),.01,role+" reaches optional skybridge control");
            w.tick(false,true);assertTrue(w.exploration.visited().contains(2));
            assertEquals(2,w.events.size());assertEquals(0,w.rescues());assertEquals(100,w.player.getHp());
        }
    }
    @Test void restoringEitherChoiceSettlesGeometryAndNeverReissuesRewards() {
        for(Set<Integer> flags:List.of(Set.of(1),Set.of(1,2),Set.of(0,1,2))) {
            Walk w=new Walk(CharacterId.ENGINEER,9810,450);
            w.exploration.restore(flags);w.route.syncExploration(w.exploration.visited());
            assertEquals(140,w.gateY());
            assertEquals(flags.contains(2)?300:0,w.route.snapshot().safeBridge().deployedWidth());
            for(int id:flags)w.activate(id);
            assertTrue(w.events.isEmpty());assertTrue(w.exploration.complete());
            for(int i=0;i<20;i++)w.tick(true,false);
            assertTrue(w.player.getX()>=9900);assertEquals(0,w.rescues());
        }
    }
    @Test void enemiesNavigateTheMovingGateUsingItsCurrentSolidBounds() {
        for(EntityType type:new EntityType[]{EntityType.SENTINEL,EntityType.WARDEN,EntityType.RIGGER}) {
            Walk w=new Walk(CharacterId.REPAIR,8860,230);w.tick(false,true);
            var manager=new ObstacleManager(25);manager.spawnEnemy(9490,480-type.height,type);
            var enemy=manager.getEnemies().get(0);boolean crossed=false;
            var shots=new ArrayList<Projectile>();
            for(int i=0;i<700;i++) {
                w.tick(false,false,List.of(enemy.getBounds()));manager.setSolids(w.exploration.solids());
                double before=enemy.getX();
                enemy.setMovementBounds(8965,9630);
                enemy.update(1,8940,450,8870,9800,480);manager.resolveSolidMotion(enemy,before);
                enemy.maybeShoot(450,shots);
                for(var shot:shots) {
                    shot.update();w.exploration.consumeShot(shot,Double.POSITIVE_INFINITY);
                    if(type==EntityType.SENTINEL && !shot.isDead() && shot.getX()<9250 && w.gateY()==140)crossed=true;
                }
                shots.removeIf(shot->shot.isDead()||shot.getX()<8900);
                for(var solid:w.exploration.solids())assertFalse(enemy.getBounds().intersects(solid),type+" intersects gate at "+i);
                if(enemy.getX()+type.width<9250)crossed=true;
                if(crossed)break;
            }
            assertTrue(crossed,type+" must pursue or regain a clear shot through the opened gate: "+enemy.getX()+","+enemy.getY());
        }
    }
    @Test void passageFlagsSurviveActualCheckpointXmlWithNoExtraResourceFields() {
        for(Set<Integer> flags:List.of(Set.of(1),Set.of(1,2))) {
            var session=new com.bigphil.mergehell.engine.GameSession(192);session.beginMission(2);
            session.tick(new com.bigphil.mergehell.engine.InputFrame(false,true,false,false,false,false));
            var player=new Player(9820,450);player.bindRunBuild(session.runBuild());
            var terrain=new ChapterRouteController(2,480);
            var nav=new com.bigphil.mergehell.persistence.CheckpointCodec.Navigation(9820,flags,
                    terrain.savedHealth(),terrain.savedHatches(),Set.of(),Set.of());
            var saved=com.bigphil.mergehell.persistence.CheckpointCodec.captureSegment(UUID.randomUUID().toString(),
                    1234,5000,session,player.checkpointForNextLevel(),nav);
            var xml=com.intellij.util.xmlb.XmlSerializer.serialize(saved);
            var decoded=com.intellij.util.xmlb.XmlSerializer.deserialize(xml,com.bigphil.mergehell.persistence.MergeHellState.ActiveRun.class);
            com.bigphil.mergehell.persistence.CheckpointCodec.validate(decoded);
            var restored=new ExplorationRoute(2);restored.restore(decoded.explorationVisited);
            terrain.restoreStructures(decoded.routeHealth,decoded.routeHatches);terrain.syncExploration(restored.visited());
            assertEquals(flags,restored.visited());assertEquals(180,restored.snapshot().gateLift());
            assertEquals(flags.contains(2)?300:0,terrain.snapshot().safeBridge().deployedWidth());
            assertTrue(restored.drainEvents().isEmpty());assertTrue(terrain.drainEvents().isEmpty());
            assertEquals(1234,decoded.score);
        }
    }
    @Test void otherChaptersAndBossArenaNeverGainPassageMechanisms() {
        for(int chapter:new int[]{0,1,3,4}) {
            var route=new ExplorationRoute(chapter);var before=route.solids();
            route.restore(Set.of(1));assertEquals(chapter>=2,route.complete());
            for(int i=0;i<200;i++)route.beforeMove(new Player(9250,450));
            assertEquals(before,route.solids());assertEquals(0,route.snapshot().gateLift());
            if(chapter>=3) {
                var terrain=new ChapterRouteController(chapter,480);terrain.syncExploration(Set.of(1,2));
                assertEquals(0,terrain.snapshot().safeBridge().deployedWidth());
                assertFalse(terrain.snapshot().surfaces().stream().anyMatch(s->s.kind()==ChapterRouteController.SurfaceKind.GAP));
            }
        }
        Walk w=new Walk(CharacterId.REPAIR,9650,450);w.activate(1);w.activate(2);
        w.exploration.prepare(true);w.exploration.beforeMove(w.player);
        assertTrue(w.exploration.solids().isEmpty());
        w.route.syncExploration(w.exploration.visited());w.route.beforeMove(w.player,true);
        assertEquals(480,w.route.groundFor(9650,30));
        assertTrue(w.route.platforms().stream().noneMatch(p->p.x==9560));
    }
}
