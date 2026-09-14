package com.bigphil.mergehell.persistence;

import com.bigphil.mergehell.combat.CombatEvent;
import com.bigphil.mergehell.combat.WeaponId;
import com.bigphil.mergehell.engine.GameSession;
import com.bigphil.mergehell.engine.InputFrame;
import com.bigphil.mergehell.model.EntityType;
import com.bigphil.mergehell.model.Player;
import com.bigphil.mergehell.model.WeaponType;
import com.bigphil.mergehell.progression.UpgradeCatalog;
import com.bigphil.mergehell.progression.UpgradeDefinition;
import com.bigphil.mergehell.progression.UpgradeId;
import com.bigphil.mergehell.progression.UpgradeTag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class CheckpointCodecTest {
    @Test
    void completeCheckpointRoundTripsThroughTheIdeXmlSerializer() {
        GameSession session = evolvedSession(WeaponId.FORCE_PUSH);
        Player player = survivor(session);
        MergeHellState state = new MergeHellState();
        state.activeRun = CheckpointCodec.capture(UUID.randomUUID().toString(), 9_321, 15_000,
                session, player.checkpoint());
        org.jdom.Element xml = com.intellij.util.xmlb.XmlSerializer.serialize(state);
        MergeHellState decoded = com.intellij.util.xmlb.XmlSerializer.deserialize(xml, MergeHellState.class);
        MergeHellState migrated = new StateMigrator(() -> null).migrate(decoded);
        assertNotNull(migrated.activeRun);
        assertEquals(state.activeRun.runId, migrated.activeRun.runId);
        assertEquals(9_321, migrated.activeRun.score);
        assertEquals(15_000, migrated.activeRun.nextLifeThreshold);
        assertEquals(CheckpointCodec.sessionCheckpoint(state.activeRun),
                CheckpointCodec.sessionCheckpoint(migrated.activeRun));
        assertEquals(player.checkpoint(), CheckpointCodec.playerCheckpoint(migrated.activeRun));
    }

    @Test
    void everyEvolvedWeaponRestoresResourcesBuildProgressAndFutureDraftRandomness() {
        for (WeaponId weapon : WeaponId.values()) {
            GameSession original = evolvedSession(weapon);
            Player player = survivor(original);
            MergeHellState.ActiveRun run = CheckpointCodec.capture(UUID.randomUUID().toString(), 8_500, 15_000,
                    original, player.checkpoint());
            GameSession restored = CheckpointCodec.restoreSession(run);
            Player restoredPlayer = new Player(0, 0);
            restoredPlayer.restoreCheckpoint(CheckpointCodec.playerCheckpoint(run), restored.runBuild(),
                    run.checkpointX, run.checkpointY);

            assertEquals(original.checkpointAtMissionStart(), restored.checkpointAtMissionStart());
            assertEquals(original.runBuild().buildStats(), restored.runBuild().buildStats());
            assertEquals(player.checkpoint(), restoredPlayer.checkpoint());
            assertEquals(1, restoredPlayer.getShieldRebootsRemaining());
            assertEquals(25, restoredPlayer.getHp());
            assertEquals(2, restoredPlayer.getBombs());
            assertEquals(2, restoredPlayer.getLives());
            assertEquals(17, restoredPlayer.getWeaponAmmo());
            assertEquals(0, restored.missionProgress(), "Director resumes at the declared level entry");

            int xpToNextChoice = original.buildProgress().nextThreshold() - original.buildProgress().currentXp();
            original.awardBuildXp(xpToNextChoice);
            restored.awardBuildXp(xpToNextChoice);
            assertEquals(ids(original), ids(restored), "Upgrade RNG continues instead of replaying the initial seed");
            original.rerollUpgrades(); restored.rerollUpgrades();
            assertEquals(ids(original), ids(restored));
            original.chooseUpgrade(0); restored.chooseUpgrade(0);
            assertEquals(original.runBuild().buildStats(), restored.runBuild().buildStats());
            original.tick(InputFrame.NONE); restored.tick(InputFrame.NONE);
            assertEquals(original.worldTick(), restored.worldTick());
            assertEquals(original.overclockActiveTicks(), restored.overclockActiveTicks());
        }
    }

    @Test
    void captureRejectsPlayedMissionAndUnresolvedUpgradeSelection() {
        GameSession played = new GameSession(1);
        played.tick(InputFrame.NONE);
        assertThrows(IllegalStateException.class, played::checkpointAtMissionStart);
        GameSession selection = new GameSession(2);
        selection.awardBuildXp(100);
        assertThrows(IllegalStateException.class, selection::checkpointAtMissionStart);
        assertThrows(IllegalStateException.class, () -> selection.beginMission(1));
        selection.chooseUpgrade(0);
        selection.beginMission(1);
        assertEquals(1, selection.checkpointAtMissionStart().mission());
    }

    @Test
    void corruptCheckpointIsRejectedAndMigrationKeepsLongTermProgress() {
        List<Consumer<MergeHellState.ActiveRun>> corruptions = new ArrayList<>();
        corruptions.add(run -> run.player = null);
        corruptions.add(run -> run.session = null);
        corruptions.add(run -> run.runId = "unknown");
        corruptions.add(run -> run.mission = 5);
        corruptions.add(run -> run.checkpointX = Double.NaN);
        corruptions.add(run -> run.checkpointX = 1_000);
        corruptions.add(run -> run.session.ranks.put(UpgradeId.SUPPLY_BOMB, 1));
        corruptions.add(run -> run.session.ranks.put(UpgradeId.SHIELD_REBOOT, 999));
        corruptions.add(run -> run.session.ranks.put(UpgradeId.FORCE_KNOCKBACK, 1));
        corruptions.add(run -> run.session.evolved = true);
        corruptions.add(run -> run.session.pendingChoices = 1);
        corruptions.add(run -> run.session.draftRandomState = -1);
        corruptions.add(run -> run.session.overclockCharge = 100);
        corruptions.add(run -> run.player.shieldRebootsUsed = 1);
        corruptions.add(run -> run.player.weapon = WeaponType.HEAVY);
        corruptions.add(run -> run.player.lives = 0);
        for (Consumer<MergeHellState.ActiveRun> corrupt : corruptions) {
            MergeHellState state = new MergeHellState();
            state.topScores.add(6_000);
            state.activeRun = freshRun();
            corrupt.accept(state.activeRun);
            assertThrows(RuntimeException.class, () -> CheckpointCodec.validate(state.activeRun));
            MergeHellState migrated = new StateMigrator(() -> null).migrate(state);
            assertNull(migrated.activeRun);
            assertNotNull(migrated.legacyActiveRunBackup);
            assertEquals(List.of(6_000), migrated.topScores);
            assertFalse(migrated.checkpointNotice.isBlank());
        }
    }

    static MergeHellState.ActiveRun freshRun() {
        GameSession session = new GameSession(123);
        Player player = new Player(100, 480);
        player.bindRunBuild(session.runBuild());
        return CheckpointCodec.capture(UUID.randomUUID().toString(), 0, 5_000, session, player.checkpoint());
    }

    private static GameSession evolvedSession(WeaponId weapon) {
        GameSession session = new GameSession(3_927, weapon);
        for (int i = 0; i < 10; i++) session.accept(new CombatEvent.EnemyKilled(EntityType.BUG, 100, 0, 0));
        List<UpgradeDefinition> weaponUpgrades = UpgradeCatalog.all().stream()
                .filter(definition -> definition.tag() == UpgradeTag.WEAPON && definition.isWeaponRelevant(weapon))
                .sorted(java.util.Comparator.comparing(UpgradeDefinition::id)).toList();
        int ranks = 0;
        for (UpgradeDefinition upgrade : weaponUpgrades) {
            for (int i = 0; i < upgrade.maxRank() && ranks < 4; i++, ranks++) session.runBuild().apply(upgrade);
        }
        UpgradeDefinition core = UpgradeCatalog.all().stream()
                .filter(definition -> definition.tag() == UpgradeTag.EVOLUTION_CORE && definition.isWeaponRelevant(weapon))
                .findFirst().orElseThrow();
        session.runBuild().apply(core);
        session.runBuild().apply(UpgradeCatalog.definition(UpgradeId.SHIELD_REBOOT));
        session.runBuild().apply(UpgradeCatalog.definition(UpgradeId.SHIELD_REBOOT));
        assertTrue(session.runBuild().tryEvolve());
        session.awardBuildXp(100);
        while (session.state() == com.bigphil.mergehell.GameState.UPGRADE_SELECTION) session.chooseUpgrade(0);
        session.tick(InputFrame.NONE);
        session.beginMission(2);
        return session;
    }

    private static Player survivor(GameSession session) {
        Player player = new Player(100, 480);
        player.bindRunBuild(session.runBuild());
        player.takeDamage(200); // Spend a shield reboot once; restoration must not replenish it.
        player.loseLife(); player.useBomb();
        player.setSudoTimer(session.overclockActiveTicks());
        player.giveWeapon(WeaponType.HEAVY, 17);
        player.beginNextLevel(100, 480);
        return player;
    }

    private static List<UpgradeId> ids(GameSession session) { return session.upgradeChoices().stream().map(UpgradeDefinition::id).toList(); }
}
