package com.bigphil.mergehell;

import com.bigphil.mergehell.model.*;
import com.bigphil.mergehell.persistence.MergeHellState;
import com.bigphil.mergehell.persistence.MergeHellStateService;
import com.bigphil.mergehell.world.ChapterRouteController;
import org.junit.jupiter.api.*;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Replaces the retired three-switch integration fixtures with the actual authored campaign. */
class GamePanelChapterCampaignTest {
    private final List<HeapGameHarness> games=new ArrayList<>();
    private MergeHellState original;
    @BeforeEach void isolate(){ original=MergeHellStateService.getInstance().getState();MergeHellStateService.getInstance().loadState(new MergeHellState()); }
    @AfterEach void close(){games.forEach(HeapGameHarness::close);MergeHellStateService.getInstance().loadState(original);}

    @TestFactory java.util.stream.Stream<DynamicTest> eachChapterOwnsPhysicalGeometryAndNoRetiredSwitchSystemCases() {
        return java.util.stream.IntStream.rangeClosed(2,4).mapToObj(level ->
                DynamicTest.dynamicTest("chapter " + (level+1), () -> runChapterCase(() -> eachChapterOwnsPhysicalGeometryAndNoRetiredSwitchSystem(level))));
    }
    private void eachChapterOwnsPhysicalGeometryAndNoRetiredSwitchSystem(int level)throws Exception {
        var h=world(level);var route=route(h);
        assertEquals(level,route.snapshot().level());
        assertNull(h.get("blueprint"));assertNull(h.get("kernel"));assertNull(h.get("singularity"));
        assertNull(h.get("environment"));
        @SuppressWarnings("unchecked")var terrain=(List<Platform>)h.get("combatPlatforms");
        assertEquals(route.platforms(),terrain);
        assertTrue(terrain.size()>12);assertFalse(h.player().isDebugMode());
    }
    @TestFactory java.util.stream.Stream<DynamicTest> pauseAndDraftFreezePhysicalRouteAndRejectInteractionCases() {
        return java.util.stream.IntStream.rangeClosed(2,4).mapToObj(level ->
                DynamicTest.dynamicTest("chapter " + (level+1), () -> runChapterCase(() -> pauseAndDraftFreezePhysicalRouteAndRejectInteraction(level))));
    }
    private void pauseAndDraftFreezePhysicalRouteAndRejectInteraction(int level)throws Exception {
        var h=world(level);long before=route(h).snapshot().tick();
        h.key("PAUSE_P");h.tick();long paused=route(h).snapshot().tick();
        h.key("HEAP_PURGE");h.ticks(16);assertEquals(paused,route(h).snapshot().tick());
        h.key("PAUSE_P");h.tick();assertTrue(route(h).snapshot().tick()>before);
        h.panel.getSession().awardBuildXp(h.panel.getSession().buildProgress().nextThreshold()-3);
        @SuppressWarnings("unchecked") var coins=(List<LevelManager.Coin>)h.get("coins");
        coins.add(new LevelManager.Coin(h.player().getX()+15,h.player().getY()+15));h.tick();
        assertEquals(GameState.UPGRADE_SELECTION,h.state());long draft=route(h).snapshot().tick();
        h.key("SHOOT");h.key("HEAP_PURGE");h.ticks(16);assertEquals(draft,route(h).snapshot().tick());
        h.key("SHOOT_R");h.key("UPGRADE_1");h.tick();assertEquals(GameState.RUNNING,h.state());
    }
    @Test void aRealSweptShotDropsTheSkyBridgeAndTheRewardCannotRepeat()throws Exception {
        var h=world(2);h.place(680,450);quiet(h,2);
        var counter=route(h).snapshot().props().get(0);int score=h.context().score;
        for(int i=0;i<3;i++){fireInto(h,counter.bounds());quiet(h,1);}
        assertEquals(0,route(h).snapshot().props().get(0).hp());assertEquals(score+200,h.context().score);
        assertTrue(route(h).platforms().stream().anyMatch(p->p.x==860&&p.y==432&&p.width==165));
        fireInto(h,counter.bounds());quiet(h,2);assertEquals(score+200,h.context().score);
    }
    @Test void conveyorCarriesAnIdleGroundedPlayerButDoesNotCarryAnAirborneOne()throws Exception {
        var h=world(3);h.place(720,450);quiet(h,2);double start=h.player().getX();
        quiet(h,20);assertTrue(h.player().getX()>start+15);
        h.key("JUMP");h.tick();double airborne=h.player().getX();quiet(h,8);
        assertEquals(airborne,h.player().getX(),.01);assertTrue(h.player().getY()<430);
    }
    @Test void coolantIsAnImmediatePhysicalCounterAndHasNoRepeatableScoreReward()throws Exception {
        var h=world(3);h.place(930,450);quiet(h,2);int score=h.context().score;
        h.key("HEAP_PURGE");quiet(h,1);var pump=route(h).snapshot().props().get(0);
        assertTrue(pump.effectTicks()>400);assertFalse(route(h).snapshot().surfaces().stream()
                .anyMatch(s->s.kind()==ChapterRouteController.SurfaceKind.MOLTEN&&s.x()<1500&&s.active()));
        h.key("HEAP_PURGE");quiet(h,5);assertEquals(score,h.context().score);
        h.key("PAUSE_P");h.tick();int remaining=route(h).snapshot().props().get(0).effectTicks();
        h.ticks(20);assertEquals(remaining,route(h).snapshot().props().get(0).effectTicks());
    }
    @Test void livingMembraneStopsWalkingUntilRealShotsBreakIt()throws Exception {
        var h=world(4);h.place(1500,450);quiet(h,2);h.key("RIGHT");
        quiet(h,25);h.key("RIGHT_R");h.tick();assertTrue(h.player().getX()<=1550.01);
        var membrane=route(h).snapshot().props().stream().filter(p->p.kind()==ChapterRouteController.Kind.MEMBRANE).findFirst().orElseThrow();
        for(int i=0;i<7;i++){fireInto(h,membrane.bounds());quiet(h,1);}
        assertEquals(0,route(h).snapshot().props().get(membrane.id()).hp());
        h.key("RIGHT");quiet(h,28);h.key("RIGHT_R");h.tick();assertTrue(h.player().getX()>1625);
    }
    @TestFactory java.util.stream.Stream<DynamicTest> bossWarningPrecedesCombatAndEHasNoGenericExposeShortcutCases() {
        return java.util.stream.IntStream.rangeClosed(2,4).mapToObj(level ->
                DynamicTest.dynamicTest("chapter " + (level+1), () -> runChapterCase(() -> bossWarningPrecedesCombatAndEHasNoGenericExposeShortcut(level))));
    }
    private void bossWarningPrecedesCombatAndEHasNoGenericExposeShortcut(int level)throws Exception {
        var h=bossWorld(level);assertTrue(h.boss().hasMultipartEncounter());
        var parts=h.boss().getParts();assertEquals(3,parts.size());
        double camera=(double)h.get("cameraX");
        assertTrue(parts.stream().allMatch(p->p.bounds().x()>=camera-1));
        assertTrue(parts.stream().allMatch(p->p.bounds().x()+p.bounds().width()<camera+960));
        h.key("HEAP_PURGE");quiet(h,1);assertFalse(h.boss().isVulnerable());
        var target=parts.stream().filter(p->!p.id().equals("core")).findFirst().orElseThrow();
        int before=h.boss().getHp();fireInto(h,target.bounds().rectangle());quiet(h,1);
        assertTrue(h.boss().getHp()<before);assertTrue(h.boss().getDamageSequence()>0);
    }
    @TestFactory java.util.stream.Stream<DynamicTest> checkpointRestartsTheCorrectChapterWithoutReusingDestroyedRouteStateCases() {
        return java.util.stream.IntStream.rangeClosed(2,4).mapToObj(level ->
                DynamicTest.dynamicTest("chapter " + (level+1), () -> runChapterCase(() -> checkpointRestartsTheCorrectChapterWithoutReusingDestroyedRouteState(level))));
    }
    private void checkpointRestartsTheCorrectChapterWithoutReusingDestroyedRouteState(int level)throws Exception {
        var h=world(level);var old=route(h);
        h.key("PAUSE_P");h.tick();h.key("MENU");h.tick();h.key("UPGRADE_REROLL");h.tick();
        assertEquals(GameState.RUNNING,h.state());assertEquals(level,h.get("level"));
        assertNotSame(old,route(h));assertTrue(route(h).snapshot().props().stream().allMatch(p->p.hp()==p.maxHp()));
        assertFalse(h.player().isDebugMode());
    }
    @Test void finalBossDeathSettlesOnceAndAdvancesToVictory()throws Exception {
        var h=bossWorld(4);h.boss().damage(Integer.MAX_VALUE);quiet(h,120);
        assertEquals(GameState.MISSION_COMPLETE,h.state());int score=h.context().score;
        quiet(h,30);assertEquals(score,h.context().score);h.key("START");h.tick();
        assertEquals(GameState.VICTORY,h.state());
    }
    private void runChapterCase(org.junit.jupiter.api.function.Executable test) throws Throwable { try { test.execute(); } finally { games.forEach(HeapGameHarness::close); games.clear(); MergeHellStateService.getInstance().loadState(new MergeHellState()); } }
    private HeapGameHarness world(int level)throws Exception {
        var h=new HeapGameHarness();games.add(h);h.set("level",level);h.invoke("advanceLevel");h.tick();return h;
    }
    private HeapGameHarness bossWorld(int level)throws Exception {
        var h=world(level);((LevelManager)h.get("levelManager")).advanceToBossGateForTesting();
        h.place(7600,450);assertEquals(GameState.BOSS_WARNING,h.state());
        for(int i=0;i<260&&h.state()!=GameState.BOSS_FIGHT;i++)quiet(h,1);
        assertEquals(GameState.BOSS_FIGHT,h.state());quiet(h,18);return h;
    }
    private static ChapterRouteController route(HeapGameHarness h)throws Exception{return (ChapterRouteController)h.get("chapterRoute");}
    private static void quiet(HeapGameHarness h,int ticks)throws Exception {
        for(int i=0;i<ticks;i++){h.enemies().clearHostiles();h.enemies().getEnemyBullets().clear();h.tick();}
    }
    private static void fireInto(HeapGameHarness h,Rectangle bounds)throws Exception {
        h.shots().add(new Projectile(bounds.x-35,bounds.y+bounds.height*.5,90,0,ProjectileType.COMMIT));
    }
}
