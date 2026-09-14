package com.bigphil.mergehell;

import com.bigphil.mergehell.model.*;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DropReplayTest {
    @Test
    void resettingALevelSeedReplaysTheSameDropTicksAndTypes() {
        CollisionSystem collision = new CollisionSystem(event -> { }, () -> false);
        var first = drops(collision, 42, 0);
        assertFalse(first.isEmpty());
        assertEquals(first, drops(collision, 42, 0));
        assertNotEquals(first, drops(collision, 77, 0));
    }

    @Test
    void decorativeGlobalRandomConsumptionCannotChangeEitherDropPool() {
        for (boolean phaseOne : new boolean[]{false, true}) {
            CollisionSystem collision = new CollisionSystem(event -> { }, () -> phaseOne);
            var first = drops(collision, 137, 0);
            assertFalse(first.isEmpty());
            assertEquals(first, drops(collision, 137, 80));
            if (phaseOne) assertTrue(first.stream().allMatch(drop ->
                    drop.endsWith("HEALTH") || drop.endsWith("POWERUP_SHIELD")));
        }
    }

    private List<String> drops(CollisionSystem collision, long seed, int extraDecorations) {
        collision.resetDropSeed(seed);
        CollisionSystem.Context ctx = new CollisionSystem.Context();
        ObstacleManager manager = new ObstacleManager(54);
        Player player = new Player(700, 450);
        List<String> trace = new ArrayList<>();
        for (int tick = 0; tick < 300; tick++) {
            manager.reset(54);
            manager.getEnemies().add(new ObstacleManager.Enemy(110, 195, EntityType.BUG, 7L));
            for (int i = 0; i < extraDecorations; i++) {
                // Floating text uses cosmetic randomness for velocity, as it does in the HUD.
                new FloatingText(0, 0, "decoration", Color.WHITE);
            }
            List<Projectile> shots = new ArrayList<>(List.of(new Projectile(100, 200, 0, 0, ProjectileType.COMMIT)));
            collision.process(ctx, shots, manager, null, player, GameState.RUNNING,
                    960, 600, 0, new ArrayList<>(), new ArrayList<>(), text -> { });
            for (var enemy : manager.getEnemies()) {
                if (!enemy.getType().isHostile()) trace.add(tick + ":" + enemy.getType());
            }
        }
        return trace;
    }
}
