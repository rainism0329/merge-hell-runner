package com.bigphil.mergehell;

import com.bigphil.mergehell.boss.BossArrivalController;
import com.bigphil.mergehell.boss.LegacyBossController;
import com.bigphil.mergehell.model.EntityType;
import com.bigphil.mergehell.model.ObstacleManager;
import com.bigphil.mergehell.model.Platform;
import com.bigphil.mergehell.model.Projectile;
import com.bigphil.mergehell.model.ProjectileType;
import com.bigphil.mergehell.persistence.MergeHellState;
import com.bigphil.mergehell.persistence.MergeHellStateService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Gate placement is a fixture; countdown, controls, damage, pause and restart use the real loop. */
class GamePanelArrivalTest {
    private final List<HeapGameHarness> games = new ArrayList<>();
    private final MergeHellStateService storage = MergeHellStateService.getInstance();
    private MergeHellState original;

    @BeforeEach void isolate() {
        original = storage.getState();
        storage.loadState(new MergeHellState());
    }

    @AfterEach void close() {
        games.forEach(HeapGameHarness::close);
        storage.loadState(original);
    }

    @Test void allFiveWorldsAllowTheEntirePreparationWindowBeforeCombat() throws Exception {
        for (int world = 0; world < 5; world++) {
            HeapGameHarness h = enterArrival(world);
            assertFalse(h.player().isDebugMode(), "Safety must not depend on invincibility practice");
            assertEquals(BossArrivalController.DURATION_TICKS, remaining(h));
            int hp = h.player().getHp();
            for (int tick = 0; tick < BossArrivalController.DURATION_TICKS - 1; tick++) {
                h.tick();
                assertEquals(GameState.BOSS_WARNING, h.state(), "World " + world + " must keep the full warning");
                assertEquals(hp, h.player().getHp());
                assertTrue(h.enemies().getEnemyBullets().isEmpty());
                if (world == 0) assertEquals(0, legacy(h).telegraphTicksRemaining());
                else assertFalse(h.boss().isActive());
            }
            h.tick();
            assertEquals(GameState.BOSS_FIGHT, h.state());
            assertEquals(0, remaining(h));
            assertEquals(hp, h.player().getHp());
            if (world > 0) assertTrue(h.boss().isActive());
            h.close();
        }
    }

    @Test void preparationAllowsMovingAndJumpingButBlocksWeaponsBombsAndOverlappingHostiles() throws Exception {
        HeapGameHarness h = enterArrival(0);
        h.player().setInvincibleTimer(0);
        h.player().takeDamage(27);
        h.player().setInvincibleTimer(0);
        int hp = h.player().getHp(), bombs = h.player().getBombs();
        long shots = h.player().getShotSequence();
        double startX = h.player().getX();
        h.key("RIGHT"); h.tick(); h.key("RIGHT_R"); h.tick();
        assertTrue(h.player().getX() > startX, "The player can prepare a position");
        h.player().setY(450);
        h.tick();
        double groundedY = h.player().getY();
        h.key("JUMP"); h.ticks(2);
        assertTrue(h.player().getY() < groundedY, "Jump input remains available during anticipation");

        ObstacleManager.Enemy hostile = new ObstacleManager.Enemy(h.player().getX(), h.player().getY(), EntityType.BUG, 19L);
        h.enemies().getEnemies().add(hostile);
        Projectile hostileShot = new Projectile(h.player().getX(), h.player().getY(), 8, 0, ProjectileType.CRITICAL);
        h.enemies().getEnemyBullets().add(hostileShot);
        double enemyX = hostile.getX(), enemyY = hostile.getY();
        double bulletX = hostileShot.getX(), bulletY = hostileShot.getY();
        int nodeHp = legacy(h).nodes().get(0).hp();
        h.key("SHOOT"); h.key("MELEE"); h.key("BOMB");
        for (int tick = 0; tick < 70; tick++) {
            h.tick();
            assertEquals(GameState.BOSS_WARNING, h.state());
            assertEquals(hp, h.player().getHp(), "Even overlapping dangerous fixtures cannot deal damage during preparation");
            assertEquals(bombs, h.player().getBombs(), "Bomb input must not spend a charge before combat");
            assertEquals(shots, h.player().getShotSequence());
            assertFalse(h.player().isMeleeActive());
            assertEquals(enemyX, hostile.getX()); assertEquals(enemyY, hostile.getY());
            assertEquals(bulletX, hostileShot.getX()); assertEquals(bulletY, hostileShot.getY());
            assertFalse(hostile.isDead()); assertFalse(hostileShot.isDead());
            assertEquals(nodeHp, legacy(h).nodes().get(0).hp());
            assertTrue(h.shots().isEmpty());
        }
    }

    @Test void pauseFreezesTheCountdownAndResumeUsesOnlyTheRemainingTicks() throws Exception {
        HeapGameHarness h = enterArrival(1);
        h.ticks(37);
        int before = remaining(h);
        h.key("PAUSE_P"); h.tick();
        assertEquals(GameState.PAUSED, h.state());
        long worldTick = h.panel.getSession().worldTick();
        var snapshot = arrival(h).snapshot();
        h.ticks(300);
        assertEquals(before, remaining(h));
        assertEquals(snapshot, arrival(h).snapshot());
        assertEquals(worldTick, h.panel.getSession().worldTick());
        h.key("PAUSE_P"); h.tick();
        assertEquals(GameState.BOSS_WARNING, h.state());
        assertEquals(before - 1, remaining(h));
        int resumed = remaining(h);
        h.ticks(resumed - 1);
        assertEquals(GameState.BOSS_WARNING, h.state());
        assertEquals(1, remaining(h));
        h.tick();
        assertEquals(GameState.BOSS_FIGHT, h.state());
    }

    @Test void preparationPreservesActualSupportOnAnExistingVisiblePlatform() throws Exception {
        HeapGameHarness h = world(0);
        @SuppressWarnings("unchecked") var originalPlatforms = (List<Platform>) h.get("platforms");
        Platform bridge = originalPlatforms.stream().filter(platform -> platform.x == 500).findFirst().orElseThrow();
        h.place(bridge.x + 20, bridge.y - 100);
        h.quietTicks(45);
        assertTrue(h.player().isGrounded());
        assertEquals(bridge.y, h.player().getY() + h.player().getBounds().height, 0.001);
        h.panel.getSession().advanceToBossGateForTesting();
        h.enemies().clearHostiles(); h.tick();
        assertEquals(GameState.BOSS_WARNING, h.state());
        @SuppressWarnings("unchecked") var visibleTerrain = (List<Platform>) h.get("combatPlatforms");
        assertTrue(visibleTerrain.contains(bridge), "This old bridge remains part of the rendered arena");
        for (int tick = 0; tick < BossArrivalController.DURATION_TICKS; tick++) {
            h.tick();
            assertEquals(bridge.y, h.player().getY() + h.player().getBounds().height, 0.001,
                    "Visible old terrain must still support the player during the entire preparation window");
            assertTrue(h.player().isGrounded());
        }
        assertEquals(GameState.BOSS_FIGHT, h.state());
        h.tick();
        assertEquals(bridge.y, h.player().getY() + h.player().getBounds().height, 0.001,
                "The transition into combat must not change the platform contact");
    }

    @Test void restartRemovesTheOldCountdownAndTheNextBossGetsAFreshWindow() throws Exception {
        HeapGameHarness h = enterArrival(0);
        h.ticks(50);
        h.key("NEW_RANKED_RUN"); h.tick();
        assertEquals(GameState.RUNNING, h.state());
        assertFalse(arrival(h).active());
        assertEquals(0, remaining(h));
        assertNull(h.get("legacyBoss")); assertNull(h.boss());
        h.panel.getSession().advanceToBossGateForTesting();
        h.enemies().clearHostiles(); h.tick();
        assertEquals(GameState.BOSS_WARNING, h.state());
        assertEquals(BossArrivalController.DURATION_TICKS, remaining(h));
    }

    @Test void arrivalClearsOldCombatAndNearbyRewardsCannotOpenAnUpgradeDuringPreparation() throws Exception {
        HeapGameHarness h = world(1);
        h.panel.getLevelManager().advanceToBossGateForTesting();
        h.player().setX(h.panel.getLevelManager().getBossGateX());
        h.set("cameraX", h.player().getX() - 288);
        h.enemies().getEnemies().add(new ObstacleManager.Enemy(h.player().getX(), h.player().getY(), EntityType.BUG, 71L));
        h.enemies().getEnemyBullets().add(new Projectile(h.player().getX(), h.player().getY(), 0, 0, ProjectileType.CRITICAL));
        h.shots().add(new Projectile(h.player().getX(), h.player().getY(), 0, 0, ProjectileType.COMMIT));
        h.tick();
        assertEquals(GameState.BOSS_WARNING, h.state());
        assertTrue(h.enemies().getEnemies().stream().noneMatch(enemy -> !enemy.isDead() && enemy.getType().isHostile()));
        assertTrue(h.enemies().getEnemyBullets().isEmpty()); assertTrue(h.shots().isEmpty());
        var progress = h.panel.getSession().buildProgress();
        h.panel.getSession().awardBuildXp(progress.nextThreshold() - progress.currentXp() - 1);
        LevelManager.Coin nearby = new LevelManager.Coin(h.player().getX() + 12, 465);
        h.set("coins", new ArrayList<>(List.of(nearby)));
        int xp = progress.currentXp(), hp = h.player().getHp();
        h.ticks(BossArrivalController.DURATION_TICKS - 1);
        assertEquals(GameState.BOSS_WARNING, h.state());
        assertEquals(hp, h.player().getHp());
        assertEquals(xp, progress.currentXp());
        assertEquals(0, progress.pendingChoices());
        assertFalse(nearby.collected, "Collection resumes when actual combat does, rather than interrupting the countdown");
    }

    private HeapGameHarness enterArrival(int world) throws Exception {
        HeapGameHarness h = world(world);
        if (world == 0) h.panel.getSession().advanceToBossGateForTesting();
        else {
            h.panel.getLevelManager().advanceToBossGateForTesting();
            h.player().setX(h.panel.getLevelManager().getBossGateX());
            h.set("cameraX", h.player().getX() - 288);
        }
        h.enemies().clearHostiles();
        h.tick();
        assertEquals(GameState.BOSS_WARNING, h.state());
        return h;
    }

    private HeapGameHarness world(int world) throws Exception {
        HeapGameHarness h = new HeapGameHarness(); games.add(h);
        if (world != 1) { h.set("level", world); h.invoke("advanceLevel"); }
        return h;
    }

    private int remaining(HeapGameHarness h) throws Exception {
        int value = (int) h.get("bossWarningTimer");
        assertEquals(arrival(h).remainingTicks(), value);
        return value;
    }

    private BossArrivalController arrival(HeapGameHarness h) throws Exception { return (BossArrivalController) h.get("bossArrival"); }
    private LegacyBossController legacy(HeapGameHarness h) throws Exception { return (LegacyBossController) h.get("legacyBoss"); }
}
