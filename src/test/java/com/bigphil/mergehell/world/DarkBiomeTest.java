package com.bigphil.mergehell.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DarkBiomeTest {

    @Test
    void mapsMissionProgressToFiveActs() {
        assertEquals(DarkBiome.REPOSITORY_CITY, DarkBiome.forProgress(0));
        assertEquals(DarkBiome.REPOSITORY_CITY, DarkBiome.forProgress(0.1799));
        assertEquals(DarkBiome.CI_FOUNDRY, DarkBiome.forProgress(0.18));
        assertEquals(DarkBiome.DATA_CENTER, DarkBiome.forProgress(0.38));
        assertEquals(DarkBiome.DEPENDENCY_DUMP, DarkBiome.forProgress(0.60));
        assertEquals(DarkBiome.BOSS_GATE, DarkBiome.forProgress(0.82));
        assertEquals(DarkBiome.BOSS_GATE, DarkBiome.forProgress(1));
    }

    @Test
    void safelyClampsBadProgress() {
        assertEquals(DarkBiome.REPOSITORY_CITY, DarkBiome.forProgress(-50));
        assertEquals(DarkBiome.BOSS_GATE, DarkBiome.forProgress(50));
        assertEquals(DarkBiome.REPOSITORY_CITY, DarkBiome.forProgress(Double.NaN));
    }

    @Test
    void everyActHasACompleteVisualIdentity() {
        for (DarkBiome biome : DarkBiome.values()) {
            assertFalse(biome.displayName.isBlank());
            assertFalse(biome.subtitle.isBlank());
            assertNotNull(biome.skyTop);
            assertNotNull(biome.skyBottom);
            assertNotNull(biome.ground);
            assertNotNull(biome.accent);
        }
    }
}
