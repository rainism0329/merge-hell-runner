package com.bigphil.mergehell.world;

import com.bigphil.mergehell.LevelManager;
import com.bigphil.mergehell.model.Platform;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProceduralDarkRouteTest {
    private static final int GROUND_Y = 480;

    @Test
    void sameSeedProducesTheSameRoute() {
        RouteData first = generate(42, DarkBiome.REPOSITORY_CITY, 4_000);
        RouteData second = generate(42, DarkBiome.REPOSITORY_CITY, 4_000);

        assertEquals(first.scenery, second.scenery);
        assertEquals(first.platforms.size(), second.platforms.size());
        assertEquals(first.coins.size(), second.coins.size());
        for (int i = 0; i < first.platforms.size(); i++) {
            Platform a = first.platforms.get(i), b = second.platforms.get(i);
            assertAll(() -> assertEquals(a.x, b.x), () -> assertEquals(a.y, b.y),
                    () -> assertEquals(a.width, b.width), () -> assertEquals(a.height, b.height),
                    () -> assertEquals(a.style, b.style));
        }
        for (int i = 0; i < first.coins.size(); i++) {
            assertEquals(first.coins.get(i).x, second.coins.get(i).x);
            assertEquals(first.coins.get(i).y, second.coins.get(i).y);
        }
    }

    @Test
    void allTerrainStaysReadableAndBounded() {
        for (DarkBiome biome : DarkBiome.values()) {
            RouteData route = generate(77, biome, 4_000);
            assertTrue(route.platforms.size() < 40);
            assertTrue(route.scenery.size() < 30);
            assertFalse(route.platforms.isEmpty());

            Platform previous = null;
            for (Platform platform : route.platforms) {
                assertTrue(platform.x >= 360);
                assertTrue(platform.y > 100 && platform.y < GROUND_Y);
                assertTrue(platform.width >= 110);
                assertTrue(platform.height >= 18 && platform.height <= 30);
                if (previous != null) {
                    double edgeGap = platform.x - (previous.x + previous.width);
                    assertTrue(edgeGap <= 170, "unreadable platform gap: " + edgeGap);
                }
                previous = platform;
            }
        }
    }

    @Test
    void everyActHasItsOwnTerrainAndLandmarks() {
        assertSignature(DarkBiome.REPOSITORY_CITY, Platform.Style.ROOFTOP,
                WorldScenery.Kind.CITY_TOWER);
        assertSignature(DarkBiome.CI_FOUNDRY, Platform.Style.PIPE,
                WorldScenery.Kind.CRANE);
        assertSignature(DarkBiome.DATA_CENTER, Platform.Style.SERVER_BANK,
                WorldScenery.Kind.SERVER_RACK);
        assertSignature(DarkBiome.DEPENDENCY_DUMP, Platform.Style.RUBBLE,
                WorldScenery.Kind.BROKEN_PACKAGE);
        assertSignature(DarkBiome.BOSS_GATE, Platform.Style.FORTIFICATION,
                WorldScenery.Kind.WARNING_PYLON);
    }

    private static void assertSignature(DarkBiome biome, Platform.Style platformStyle,
                                        WorldScenery.Kind landmark) {
        RouteData route = generate(9, biome, 1_300);
        assertTrue(route.platforms.stream().anyMatch(p -> p.style == platformStyle));
        assertTrue(route.scenery.stream().anyMatch(s -> s.kind() == landmark));
        assertTrue(EnumSet.copyOf(route.scenery.stream().map(WorldScenery::kind).toList()).size() >= 2);
    }

    private static RouteData generate(long seed, DarkBiome biome, double toX) {
        List<Platform> platforms = new ArrayList<>();
        List<LevelManager.Coin> coins = new ArrayList<>();
        List<WorldScenery> scenery = new ArrayList<>();
        new ProceduralDarkRoute(seed, 360).extendTo(toX, GROUND_Y, biome,
                platforms, coins, scenery);
        return new RouteData(platforms, coins, scenery);
    }

    private record RouteData(List<Platform> platforms, List<LevelManager.Coin> coins,
                             List<WorldScenery> scenery) {}
}
