package com.bigphil.mergehell;

import com.bigphil.mergehell.combat.WeaponId;
import com.bigphil.mergehell.model.EntityType;
import com.bigphil.mergehell.persistence.MergeHellState;
import com.bigphil.mergehell.persistence.MergeHellStateService;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Controlled alternatives to the finite route bot's repeated jumping above a low enemy. */
class CampaignEncounterInputTest {
    @TestFactory
    List<DynamicTest> normalInputAlternativesToTheRouteBot() {
        return List.of(
                DynamicTest.dynamicTest("Driller below the workshop ledge", () ->
                        leavingTheLedgeAndShootingAlongTheFloorCanDefeatTheObservedDriller(2916.15, 260, 2979.3, 3080)),
                DynamicTest.dynamicTest("Driller under the exhaust canopy", () ->
                        leavingTheLedgeAndShootingAlongTheFloorCanDefeatTheObservedDriller(8840, 370, 8744.5, 8950)));
    }

    private void leavingTheLedgeAndShootingAlongTheFloorCanDefeatTheObservedDriller(
            double startX, double startY, double enemyX, double walkUntil) throws Exception {
        var storage = MergeHellStateService.getInstance();
        var original = storage.getState();
        var fresh = new MergeHellState();
        fresh.settings.muted = true;
        storage.loadState(fresh);
        try (var h = new HeapGameHarness()) {
            // Chapter and relative positions are fixtures. From the first tick onward, only real
            // input and production simulation may change position, damage, enemies or resources.
            h.set("level", 3);
            h.invoke("advanceLevel");
            var level = (LevelManager) h.get("levelManager");
            level.restoreThrough(startX);
            h.player().setX(startX);
            h.player().setY(startY);
            h.set("cameraX", startX - 288);
            h.enemies().spawnEnemy((int) enemyX, 444, EntityType.DRILLER);
            var driller = h.enemies().getEnemies().get(0);
            int fullHealth = driller.getHp();
            assertTrue(fullHealth >= 200, "Use the full production chapter health, not the damaged probe target");
            assertFalse(h.player().isDebugMode());
            assertEquals(WeaponId.COMMIT_CANNON, h.panel.getSession().runBuild().weapon());
            h.key("RIGHT");
            h.key("SHOOT");
            int ticks = 0;
            while (h.player().getX() < walkUntil && ticks++ < 80) h.tick();
            h.key("RIGHT_R");
            assertTrue(h.player().getX() >= walkUntil, "A normal walk must reach the ledge's open end");
            h.key("AIM_LOCK");
            h.key("LEFT");
            int facing = -1;
            // Keep ordinary fire and the stationary aiming button held, turning if the real
            // conveyor carries the player past the target. No bombs, melee, upgrades,
            // healing, enemy deletion, timer overrides or further position changes are used.
            while (!driller.isDead() && ticks++ < 1000) {
                int wanted = driller.getX() + driller.getWidth() / 2 < h.player().getX() + 15 ? -1 : 1;
                if (wanted != facing) {
                    h.key(facing < 0 ? "LEFT_R" : "RIGHT_R");
                    h.key(wanted < 0 ? "LEFT" : "RIGHT");
                    facing = wanted;
                }
                h.tick();
            }
            assertTrue(driller.isDead(), "Horizontal fire after leaving the ledge must reach the real burrow/recovery cycle: "
                    + "player=" + h.player().getX() + "," + h.player().getY() + " facing=" + h.player().getFacingDir()
                    + " enemy=" + driller.getX() + "," + driller.getY() + " hp=" + driller.getHp()
                    + " state=" + h.state() + " world=" + h.panel.getSession().worldTick());
            assertTrue(h.player().getY() >= 440, "The player actually descended to the floor");
            assertEquals(GameState.RUNNING, h.state());
            assertTrue(h.player().getHp() > 0);
            assertTrue(h.panel.getSession().hostileKills() >= 1, "Collision settlement must award the kill");
            System.out.printf(java.util.Locale.ROOT,
                    "Driller input diagnostic: start=(%.2f,%.0f), enemy=%.1f, fullHp=%d, defeated in %d ticks, player=(%.1f,%.1f), hp=%d%n",
                    startX, startY, enemyX, fullHealth, ticks, h.player().getX(), h.player().getY(), h.player().getHp());
        } finally {
            storage.loadState(original);
        }
    }
}
