package com.bigphil.mergehell;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LevelSeedTest {
    private record CoinState(double x, double y, boolean collected) { }
    private record PlatformState(double x, double y, int width, int height, com.bigphil.mergehell.model.Platform.Style style) { }

    @Test void seededLevelEntranceRebuildsPickupsAfterAnUnrelatedInstanceRuns() {
        for (int level = 0; level <= 4; level++) {
            LevelManager first = new LevelManager(level, 919L);
            List<CoinState> entrance = coins(first);
            first.getCoins().forEach(coin -> coin.collected = true);
            LevelManager unrelated = new LevelManager(level, 123L);
            unrelated.advanceToBossGateForTesting();
            LevelManager restored = new LevelManager(level, 919L);
            assertEquals(entrance, coins(restored));
            assertEquals(first.getCameraMaxX(), restored.getCameraMaxX());
            assertEquals(first.getPlatforms().size(), restored.getPlatforms().size());
            assertTrue(restored.getCoins().stream().noneMatch(coin -> coin.collected));
        }
    }

    @Test void differentLevelSeedsCanChangePickupPlacementWithoutChangingTerrain() {
        for (int chapter = 2; chapter <= 4; chapter++) {
            LevelManager first = new LevelManager(chapter, 1L);
            LevelManager second = new LevelManager(chapter, 2L);
            assertNotEquals(coins(first), coins(second));
            assertEquals(first.getCoins().size(), second.getCoins().size(), "Seed variation preserves authored reward count");
            assertEquals(terrain(first), terrain(second));
        }
    }

    private static List<CoinState> coins(LevelManager manager) {
        return manager.getCoins().stream().map(coin -> new CoinState(coin.x, coin.y, coin.collected)).toList();
    }
    private static List<PlatformState> terrain(LevelManager manager) {
        return manager.getPlatforms().stream().map(platform -> new PlatformState(
                platform.x, platform.y, platform.width, platform.height, platform.style)).toList();
    }
}
