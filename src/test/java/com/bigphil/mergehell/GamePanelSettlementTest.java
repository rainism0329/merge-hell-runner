package com.bigphil.mergehell;

import com.bigphil.mergehell.boss.LegacyBossController;
import com.bigphil.mergehell.engine.InputCommandBuffer;
import com.bigphil.mergehell.engine.TickScheduler;
import com.bigphil.mergehell.model.Boss;
import com.bigphil.mergehell.model.Player;
import com.bigphil.mergehell.persistence.CheckpointCodec;
import com.bigphil.mergehell.persistence.MergeHellState;
import com.bigphil.mergehell.persistence.MergeHellStateService;
import org.junit.jupiter.api.*;

import javax.swing.SwingUtilities;
import java.awt.event.ActionEvent;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GamePanelSettlementTest {
    private final List<Harness> windows = new ArrayList<>();
    private final MergeHellStateService storage = MergeHellStateService.getInstance();
    private MergeHellState original;

    @BeforeEach void isolate() {
        original = storage.getState();
        storage.loadState(new MergeHellState());
    }
    @AfterEach void cleanup() {
        windows.forEach(h -> h.panel.dispose());
        storage.loadState(original);
    }

    @Test void firstWorldBossPublishesItsRewardAndSafeBoundary() throws Exception { verifyBossSettlement(0); }
    @Test void secondWorldBossPublishesItsRewardAndSafeBoundary() throws Exception { verifyBossSettlement(1); }
    @Test void thirdWorldBossPublishesItsRewardAndSafeBoundary() throws Exception { verifyBossSettlement(2); }
    @Test void fourthWorldBossPublishesItsRewardAndSafeBoundary() throws Exception { verifyBossSettlement(3); }
    @Test void fifthWorldBossPublishesItsRewardAndSafeBoundary() throws Exception { verifyBossSettlement(4); }

    private void verifyBossSettlement(int level) throws Exception {
        Harness h = window(level);
        h.finishBoss();
        assertEquals(GameState.MISSION_COMPLETE, get(h.panel, "state"));
        assertEquals(level, get(h.panel, "level"));
        assertTrue(h.panel.getSession().metrics().completionTick() >= 0);
        assertEquals(Set.of(level), storage.getState().completedMissions);
        assertEquals(250, storage.getState().refactorPoints);
        if (level < 4) {
            var next = storage.readCheckpoint().orElseThrow();
            assertEquals(level + 1, next.mission);
            assertEquals(get(h.panel, "runId"), next.runId);
            assertTrue(storage.getState().rankedScores.getOrDefault(com.bigphil.mergehell.progression.GameDifficulty.STANDARD,java.util.List.of()).isEmpty());
        } else {
            assertTrue(storage.readCheckpoint().isEmpty());
            assertEquals(1, storage.getState().rankedScores.getOrDefault(com.bigphil.mergehell.progression.GameDifficulty.STANDARD,java.util.List.of()).size());
            assertFalse((boolean) get(h.panel, "ownsCheckpoint"));
        }
    }

    @Test void completionWaitsForEnterAndDoesNotRepeatBonusOrRewards() throws Exception {
        Harness h = window(1); h.finishBoss();
        int score = h.score();
        long world = h.panel.getSession().worldTick();
        invoke(h.panel, "completeCurrentMission");
        for (int i = 0; i < 600; i++) h.tick();
        assertEquals(GameState.MISSION_COMPLETE, get(h.panel, "state"));
        assertEquals(1, get(h.panel, "level"));
        assertEquals(world, h.panel.getSession().worldTick());
        assertEquals(score, h.score());
        assertEquals(250, storage.getState().refactorPoints);
        h.action("START");
        assertEquals(GameState.RUNNING, get(h.panel, "state"));
        assertEquals(2, get(h.panel, "level"));
        assertEquals(score, h.score());
    }

    @Test void allFiveCampaignFirstClearsAreRewardedOnceAcrossARepeatRun() throws Exception {
        for (int run = 0; run < 2; run++) {
            Harness h = window(0);
            for (int level = 0; level < 5; level++) {
                h.finishBoss();
                assertTrue(storage.getState().completedMissions.contains(level));
                h.action("START");
                assertEquals(level < 4 ? GameState.RUNNING : GameState.VICTORY, get(h.panel, "state"));
            }
            h.panel.dispose();
            assertEquals(Set.of(0, 1, 2, 3, 4), storage.getState().completedMissions);
            assertEquals(1250, storage.getState().refactorPoints);
            assertEquals(run + 1, storage.getState().rankedScores.getOrDefault(com.bigphil.mergehell.progression.GameDifficulty.STANDARD,java.util.List.of()).size());
        }
    }

    @Test void aCoinOnTheFinalTickCannotReplaceTheSettledBoundaryWithAnUpgrade() throws Exception {
        Harness h = window(0);
        var coin = new LevelManager.Coin(115, 465);
        set(h.panel, "coins", new ArrayList<>(List.of(coin)));
        h.panel.getSession().awardBuildXp(h.panel.getSession().buildProgress().nextThreshold() - 1);
        h.finishBoss();
        assertEquals(GameState.MISSION_COMPLETE, get(h.panel, "state"));
        assertFalse(coin.collected);
        assertEquals(0, storage.readCheckpoint().orElseThrow().session.pendingChoices);
    }

    @Test void closingOnTheCompletedScreenRestoresTheNextEntranceWithTheSameResources() throws Exception {
        Harness first = window(0);
        first.player().takeDamage(31); first.player().useBomb();
        first.player().setShieldTimer(121);
        first.finishBoss();
        var saved = storage.readCheckpoint().orElseThrow();
        first.panel.dispose();
        Harness restored = emptyWindow(); restored.action("UPGRADE_REROLL");
        assertEquals(GameState.RUNNING, get(restored.panel, "state"));
        assertEquals(1, get(restored.panel, "level"));
        assertEquals(CheckpointCodec.playerCheckpoint(saved), restored.player().checkpoint());
        assertEquals(CheckpointCodec.sessionCheckpoint(saved), restored.panel.getSession().checkpointAtMissionStart());
        assertEquals(saved.score, restored.score());
        assertEquals(saved.nextLifeThreshold, get(restored.panel, "extraLifeScore"));
    }

    @Test void enterAndSafeQuitUseExactlyTheSameSavedNextEntrance() throws Exception {
        Harness first = window(2); first.finishBoss();
        var saved = storage.readCheckpoint().orElseThrow();
        first.action("START");
        assertEquals(CheckpointCodec.playerCheckpoint(saved), first.player().checkpoint());
        assertEquals(CheckpointCodec.sessionCheckpoint(saved), first.panel.getSession().checkpointAtMissionStart());
        first.panel.dispose();

        Harness second = emptyWindow(); second.action("UPGRADE_REROLL");
        second.finishBoss();
        var afterSecond = storage.readCheckpoint().orElseThrow();
        second.action("MENU");
        assertEquals(GameState.MENU, get(second.panel, "state"));
        assertFalse((boolean) get(second.panel, "ownsCheckpoint"));
        second.action("UPGRADE_REROLL");
        assertEquals(4, get(second.panel, "level"));
        assertEquals(CheckpointCodec.playerCheckpoint(afterSecond), second.player().checkpoint());
        assertEquals(CheckpointCodec.sessionCheckpoint(afterSecond), second.panel.getSession().checkpointAtMissionStart());
    }

    @Test void finalClearIsSavedBeforeClosingAndEnterDoesNotRecordItAgain() throws Exception {
        Harness h = window(4); h.finishBoss();
        int finalScore = h.score();
        invoke(h.panel, "completeCurrentMission");
        h.action("START");
        assertEquals(GameState.VICTORY, get(h.panel, "state"));
        assertEquals(List.of(finalScore), storage.getState().rankedScores.getOrDefault(com.bigphil.mergehell.progression.GameDifficulty.STANDARD,java.util.List.of()));
        h.action("MENU"); h.panel.dispose();
        assertTrue(storage.readCheckpoint().isEmpty());
        assertEquals(Set.of(4), storage.getState().completedMissions);
        assertEquals(250, storage.getState().refactorPoints);
        assertEquals(List.of(finalScore), storage.getState().rankedScores.getOrDefault(com.bigphil.mergehell.progression.GameDifficulty.STANDARD,java.util.List.of()));
    }

    @Test void anotherWindowsFinalClearCannotDeleteOrReleaseTheOwnersCheckpoint() throws Exception {
        Harness owner = window(0);
        var before = storage.readCheckpoint().orElseThrow();
        Harness other = window(4); other.finishBoss();
        assertEquals(before.runId, storage.readCheckpoint().orElseThrow().runId);
        assertEquals(before.mission, storage.readCheckpoint().orElseThrow().mission);
        assertTrue(storage.ownedByAnotherWindow((String) get(other.panel, "storageOwner")));
        other.action("MENU");
        assertEquals(before.runId, storage.readCheckpoint().orElseThrow().runId);
        owner.panel.dispose();
    }

    @Test void anotherWindowCanContinueLocallyAfterAClearWithoutReplacingTheOwnersEntrance() throws Exception {
        Harness owner = window(0);
        var before = storage.readCheckpoint().orElseThrow();
        Harness other = window(1); other.finishBoss();
        assertEquals(GameState.MISSION_COMPLETE, get(other.panel, "state"));
        other.action("START");
        assertEquals(GameState.RUNNING, get(other.panel, "state"));
        assertEquals(2, get(other.panel, "level"));
        assertEquals(before.runId, storage.readCheckpoint().orElseThrow().runId);
        assertEquals(before.mission, storage.readCheckpoint().orElseThrow().mission);
        owner.panel.dispose();
    }

    @Test void labInvalidatesOnlyItsOwnedCheckpointAndNeverAwardsAClear() throws Exception {
        Harness h = window(0); h.action("LAB_TOGGLE");
        assertTrue(storage.readCheckpoint().isEmpty());
        h.finishBoss();
        assertEquals(GameState.MISSION_COMPLETE, get(h.panel, "state"));
        assertTrue(storage.getState().completedMissions.isEmpty());
        assertEquals(0, storage.getState().refactorPoints);
        assertTrue(storage.getState().rankedScores.getOrDefault(com.bigphil.mergehell.progression.GameDifficulty.STANDARD,java.util.List.of()).isEmpty());
        h.action("START");
        assertEquals(1, get(h.panel, "level"));
        assertTrue(storage.readCheckpoint().isEmpty());
    }

    private Harness window(int level) throws Exception {
        Harness h = emptyWindow(); h.action("START");
        if (level > 0) { set(h.panel, "level", level); invoke(h.panel, "advanceLevel"); }
        return h;
    }
    private Harness emptyWindow() throws Exception {
        Harness h = new Harness(); windows.add(h); return h;
    }
    private static Object get(Object target, String name) throws Exception {
        Field value = target.getClass().getDeclaredField(name); value.setAccessible(true); return value.get(target);
    }
    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    private static void invoke(GamePanel panel, String name) throws Exception {
        var method = GamePanel.class.getDeclaredMethod(name); method.setAccessible(true); method.invoke(panel);
    }
    private static final class Harness {
        GamePanel panel;
        Harness() throws Exception {
            SwingUtilities.invokeAndWait(() -> panel = new GamePanel(new TickScheduler() {
                public void scheduleAtFixedRate(Runnable tick, long period) { }
                public void dispose() { }
            }, () -> 0L));
        }
        Player player() throws Exception { return (Player) get(panel, "player"); }
        int score() throws Exception { return ((CollisionSystem.Context) get(panel, "ctx")).score; }
        void action(String name) throws Exception {
            SwingUtilities.invokeAndWait(() -> {
                panel.getActionMap().get(name).actionPerformed(new ActionEvent(panel, 0, name));
                var release = panel.getActionMap().get(name + "_RELEASE");
                if (release != null) release.actionPerformed(new ActionEvent(panel, 0, name));
            });
            ((InputCommandBuffer) get(panel, "inputCommands")).drain();
        }
        void tick() throws Exception { invoke(panel, "advanceSimulation"); }
        void finishBoss() throws Exception {
            int level = (int) get(panel, "level");
            if (level == 0) {
                LegacyBossController boss = new LegacyBossController(new Random(7), 2400);
                boss.damageAllNodes(240); boss.damageCore(2400);
                set(panel, "legacyBoss", boss); set(panel, "legacyBossDying", true);
            } else {
                Boss boss = new Boss("fixture", 100, "#", 700, level, 7L);
                boss.activate(); boss.takeDamage(100);
                set(panel, "boss", boss);
            }
            set(panel, "bossDeathTimer", 1);
            set(panel, "hitstop", 0);
            set(panel, "state", GameState.BOSS_FIGHT);
            panel.getSession().setGameplayState(GameState.BOSS_FIGHT);
            tick();
        }
    }
}
