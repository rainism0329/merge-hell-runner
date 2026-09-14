package com.bigphil.mergehell.engine;

import com.bigphil.mergehell.GameState;
import com.bigphil.mergehell.combat.CombatEvent;
import com.bigphil.mergehell.model.EntityType;
import com.bigphil.mergehell.progression.UpgradeDefinition;
import com.bigphil.mergehell.progression.UpgradeId;
import com.bigphil.mergehell.mission.DirectorInput;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameSessionTest {

    @Test
    void pendingUpgradePausesWorldUntilChoice() {
        GameSession session = new GameSession(4L);

        session.awardBuildXp(100);

        assertEquals(GameState.UPGRADE_SELECTION, session.state());
        assertEquals(3, session.upgradeChoices().size());
        long before = session.worldTick();
        session.tick(InputFrame.NONE);
        assertEquals(before, session.worldTick());

        UpgradeDefinition selected = session.upgradeChoices().get(0);
        session.chooseUpgrade(0);

        assertEquals(GameState.RUNNING, session.state());
        assertEquals(1, session.runBuild().rank(selected.id()));
        assertEquals(0, session.buildProgress().pendingChoices());
    }

    @Test
    void multipleEarnedLevelsOpenDraftsOneAtATime() {
        GameSession session = new GameSession(11L);

        session.awardBuildXp(275);
        session.chooseUpgrade(0);

        assertEquals(GameState.UPGRADE_SELECTION, session.state());
        assertEquals(1, session.buildProgress().pendingChoices());
        assertEquals(3, session.upgradeChoices().size());

        session.chooseUpgrade(0);
        assertEquals(GameState.RUNNING, session.state());
        assertEquals(0, session.buildProgress().pendingChoices());
    }

    @Test
    void choiceAndRerollAreOnlyAcceptedDuringUpgradeSelection() {
        GameSession session = new GameSession(23L);

        assertThrows(IllegalStateException.class, () -> session.chooseUpgrade(0));
        assertThrows(IllegalStateException.class, session::rerollUpgrades);
        session.awardBuildXp(100);
        List<UpgradeId> original = ids(session.upgradeChoices());

        session.rerollUpgrades();

        assertFalse(original.equals(ids(session.upgradeChoices())));
        assertThrows(IllegalStateException.class, session::rerollUpgrades);
        assertThrows(IndexOutOfBoundsException.class, () -> session.chooseUpgrade(3));
    }

    @Test
    void hostileKillAwardsBuildXpAndOverclockCharge() {
        GameSession session = new GameSession(31L);

        session.accept(new CombatEvent.EnemyKilled(EntityType.BUG, 100, 12, 34));

        assertEquals(7, session.buildProgress().currentXp());
        assertEquals(10, session.overclockCharge());
    }

    @Test
    void firstUpgradeArrivesAfterAReadableNumberOfBasicKills() {
        GameSession session = new GameSession(35L);
        for (int i = 0; i < 14; i++) {
            session.accept(new CombatEvent.EnemyKilled(EntityType.BUG, 100, 0, 0));
        }
        assertEquals(GameState.RUNNING, session.state());
        assertEquals(98, session.buildProgress().currentXp());
        session.accept(new CombatEvent.EnemyKilled(EntityType.BUG, 100, 0, 0));
        assertEquals(GameState.UPGRADE_SELECTION, session.state());
    }

    @Test
    void nonHostileKillEventDoesNotAwardProgress() {
        GameSession session = new GameSession(37L);

        session.accept(new CombatEvent.EnemyKilled(EntityType.HEALTH, 500, 12, 34));

        assertEquals(0, session.buildProgress().currentXp());
        assertEquals(0, session.overclockCharge());
    }

    @Test
    void overclockOnlyTicksDuringGameplayAndGameplayStateResumesAfterUpgrade() {
        GameSession session = new GameSession(41L);
        for (int i = 0; i < 10; i++) {
            session.accept(new CombatEvent.EnemyKilled(EntityType.BUG, 100, 0, 0));
        }
        assertTrue(session.isOverclocked());
        while (session.state() == GameState.UPGRADE_SELECTION) {
            session.chooseUpgrade(0);
        }

        session.setGameplayState(GameState.BOSS_FIGHT);
        long before = session.worldTick();
        int activeBefore = session.overclockActiveTicks();
        session.tick(InputFrame.NONE);
        assertEquals(before + 1, session.worldTick());
        assertEquals(activeBefore - 1, session.overclockActiveTicks());

        session.awardBuildXp(session.buildProgress().nextThreshold());
        session.tick(InputFrame.NONE);
        assertEquals(before + 1, session.worldTick());
        session.chooseUpgrade(0);
        assertEquals(GameState.BOSS_FIGHT, session.state());
    }

    @Test
    void onlyGameplayStatesCanBeSynchronizedDirectly() {
        GameSession session = new GameSession(43L);

        assertThrows(IllegalArgumentException.class,
                () -> session.setGameplayState(GameState.UPGRADE_SELECTION));
        assertThrows(NullPointerException.class, () -> session.tick(null));
    }

    @Test void upgradeSelectionFreezesTheRouteTimerAndStageProgress() {
        GameSession session = new GameSession(61L);
        session.tick(InputFrame.NONE);
        session.directMission(new DirectorInput(session.worldTick(), 0, .5, 0));
        var before = session.routeProgress();
        session.awardBuildXp(100);
        for (int i = 0; i < 500; i++) {
            session.tick(InputFrame.NONE);
            assertTrue(session.directMission(new DirectorInput(session.worldTick(), 0, .5, 0)).isEmpty());
        }
        assertEquals(before, session.routeProgress());
        session.chooseUpgrade(0);
        session.tick(InputFrame.NONE);
        session.directMission(new DirectorInput(session.worldTick(), 0, .5, 0));
        assertEquals(before.stageTicksRemaining() - 1, session.routeProgress().stageTicksRemaining());
    }

    private static List<UpgradeId> ids(List<UpgradeDefinition> definitions) {
        return definitions.stream().map(UpgradeDefinition::id).toList();
    }
}
