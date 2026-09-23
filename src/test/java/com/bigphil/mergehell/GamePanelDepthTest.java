package com.bigphil.mergehell;
import com.bigphil.mergehell.progression.*;
import com.bigphil.mergehell.persistence.*;
import com.bigphil.mergehell.world.ExplorationRoute;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
class GamePanelDepthTest {
    private MergeHellState original;
    @BeforeEach void isolate(){original=MergeHellStateService.getInstance().getState();MergeHellStateService.getInstance().loadState(new MergeHellState());}
    @AfterEach void restore(){MergeHellStateService.getInstance().loadState(original);}
    @Test void realMenuKeysChooseCharacterAndDifficultyAndPauseClearsAiming()throws Exception {
        try(var h=new HeapGameHarness()) {
            h.set("state",GameState.MENU);h.key("CHARACTER");h.key("DIFFICULTY");h.tick();
            h.key("NEW_RANKED_RUN");h.tick();h.key("START");h.tick();
            assertEquals(CharacterId.SCOUT,h.player().getRunBuild().character());
            assertEquals(GameDifficulty.CHALLENGE,h.player().getRunBuild().difficulty());
            h.key("MENU_UP");h.key("SHOOT");h.ticks(8);
            assertEquals(-1,h.player().getAim().y());assertTrue(h.shots().stream().anyMatch(s->s.getVy()<0));
            h.key("PAUSE_P");h.tick();assertFalse((boolean)h.get("keyUp"));assertFalse((boolean)h.get("keyShoot"));
            h.key("MENU_UP_R");h.key("SHOOT_R");h.key("PAUSE_P");h.tick();assertEquals(0,h.player().getAim().y());
            assertTrue(MergeHellStateService.getInstance().getState().settings.muted);
        }
    }
    @Test void rankedGateWaitsForBothRouteInteractionsAndPracticeStillCanSkip()throws Exception {
        try(var h=new HeapGameHarness()) {
            h.set("level",2);h.invoke("advanceLevel");h.tick();
            h.panel.getLevelManager().advanceToBossGateForTesting();h.place(h.panel.getLevelManager().getBossGateX(),450);
            assertEquals(GameState.RUNNING,h.state());h.finishExplorationFixture();h.tick();assertEquals(GameState.BOSS_WARNING,h.state());
        }
    }
    @Test void theOldOfficeUnlocksEngineerUsingTheActualInteractionKeyExactlyOnce()throws Exception {
        try(var h=new HeapGameHarness()) {
            h.set("state",GameState.MENU);h.key("NEW_RANKED_RUN");h.tick();h.key("START");h.tick();
            var route=(ExplorationRoute)h.get("exploration");
            var office=route.snapshot().landmarks().get(0);
            h.place(office.bounds().x,462);h.key("MENU_DOWN");h.tick();
            h.key("HEAP_PURGE");h.tick();
            assertTrue(MergeHellStateService.getInstance().getState().unlockedCharacters.contains(CharacterId.ENGINEER));
            int score=h.context().score;h.key("HEAP_PURGE");h.tick();assertEquals(score,h.context().score);
            h.key("MENU_DOWN_R");
        }
    }
    @Test void quietSegmentContinuesThroughTheMenuWithSpentResourcesAndRouteFlags()throws Exception {
        try(var h=new HeapGameHarness()) {
            h.set("level",2);h.invoke("advanceLevel");h.tick();
            h.player().getRunBuild().setIdentity(CharacterId.WARDEN,GameDifficulty.CHALLENGE);
            ((ExplorationRoute)h.get("exploration")).restore(java.util.Set.of(0,1));
            h.player().takeDamage(20);h.player().useBomb();
            h.place(7660,450);h.enemies().clearHostiles();h.quietTicks(4);
            var saved=MergeHellStateService.getInstance().readCheckpoint().orElseThrow();
            assertEquals(CheckpointCodec.SAFE_SEGMENT,saved.checkpointKind);
            assertEquals(7660,saved.checkpointX,.01);
            assertEquals(java.util.Set.of(0,1),saved.explorationVisited);
            assertEquals(2,saved.player.bombs);assertTrue(saved.player.hp<100);
            h.set("state",GameState.MENU);h.key("UPGRADE_REROLL");h.tick();
            assertEquals(GameState.RUNNING,h.state());assertEquals(7660,h.player().getX(),.01);
            assertEquals(saved.player.hp,h.player().getHp());assertEquals(2,h.player().getBombs());
            assertEquals(CharacterId.WARDEN,h.player().getRunBuild().character());
            assertEquals(GameDifficulty.CHALLENGE,h.player().getRunBuild().difficulty());
            assertEquals(java.util.Set.of(0,1),((ExplorationRoute)h.get("exploration")).visited());
        }
    }
}
