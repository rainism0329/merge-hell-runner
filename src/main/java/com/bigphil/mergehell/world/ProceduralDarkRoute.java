package com.bigphil.mergehell.world;

import com.bigphil.mergehell.LevelManager;
import com.bigphil.mergehell.model.Platform;

import java.util.List;
import java.util.Random;

/**
 * Builds readable arcade terrain ahead of the camera. The base floor deliberately
 * remains continuous; platforms change the run/jump rhythm without creating soft locks.
 */
public final class ProceduralDarkRoute {
    private final Random random;
    private double nextX;
    private int chunkIndex;

    public ProceduralDarkRoute(long seed, double startX) {
        this(new Random(seed), startX);
    }

    ProceduralDarkRoute(Random random, double startX) {
        this.random = random;
        this.nextX = startX;
    }

    public void extendTo(double worldX, int groundY, DarkBiome biome,
                         List<Platform> platforms, List<LevelManager.Coin> coins,
                         List<WorldScenery> scenery) {
        while (nextX < worldX) generateChunk(groundY, biome, platforms, coins, scenery);
    }

    public double nextX() { return nextX; }

    private void generateChunk(int groundY, DarkBiome biome,
                               List<Platform> platforms, List<LevelManager.Coin> coins,
                               List<WorldScenery> scenery) {
        double x = nextX;
        int variant = chunkIndex++;
        switch (biome) {
            case REPOSITORY_CITY -> city(x, groundY, variant, platforms, coins, scenery);
            case CI_FOUNDRY -> foundry(x, groundY, variant, platforms, coins, scenery);
            case DATA_CENTER -> dataCenter(x, groundY, variant, platforms, coins, scenery);
            case DEPENDENCY_DUMP -> dump(x, groundY, variant, platforms, coins, scenery);
            case BOSS_GATE -> bossGate(x, groundY, variant, platforms, coins, scenery);
        }
    }

    private void city(double x, int gy, int v, List<Platform> p,
                      List<LevelManager.Coin> c, List<WorldScenery> s) {
        add(p, x, gy - 86, 220, 24, Platform.Style.ROOFTOP);
        add(p, x + 286, gy - 152, 176, 20, Platform.Style.ROOFTOP);
        add(p, x + 520, gy - 104, 182, 22, Platform.Style.CATWALK);
        coin(c, x + 110, gy - 118); coinChance(c, x + 374, gy - 184, 0.68);
        s.add(new WorldScenery(x + 55, gy - 294, 142, 208,
                WorldScenery.Kind.CITY_TOWER, WorldScenery.Layer.BACK, v));
        s.add(new WorldScenery(x + 310, gy - 222, 124, 70,
                WorldScenery.Kind.NEON_SIGN, WorldScenery.Layer.MID, v));
        if ((v & 1) == 0) s.add(new WorldScenery(x + 585, gy - 164, 62, 60,
                WorldScenery.Kind.WATER_TANK, WorldScenery.Layer.MID, v));
        nextX = x + 760 + random.nextInt(55);
    }

    private void foundry(double x, int gy, int v, List<Platform> p,
                         List<LevelManager.Coin> c, List<WorldScenery> s) {
        add(p, x, gy - 96, 168, 24, Platform.Style.PIPE);
        add(p, x + 202, gy - 178, 278, 20, Platform.Style.CATWALK);
        add(p, x + 528, gy - 112, 164, 26, Platform.Style.PIPE);
        coin(c, x + 84, gy - 128); coin(c, x + 340, gy - 210);
        s.add(new WorldScenery(x + 18, gy - 260, 76, 164,
                WorldScenery.Kind.SMOKE_STACK, WorldScenery.Layer.BACK, v));
        s.add(new WorldScenery(x + 220, gy - 318, 310, 140,
                WorldScenery.Kind.CRANE, WorldScenery.Layer.BACK, v));
        s.add(new WorldScenery(x + 555, gy - 208, 112, 96,
                WorldScenery.Kind.FURNACE, WorldScenery.Layer.MID, v));
        s.add(new WorldScenery(x + 80, gy - 54, 310, 50,
                WorldScenery.Kind.PIPE_CLUSTER, WorldScenery.Layer.FRONT, v));
        nextX = x + 748 + random.nextInt(62);
    }

    private void dataCenter(double x, int gy, int v, List<Platform> p,
                            List<LevelManager.Coin> c, List<WorldScenery> s) {
        add(p, x, gy - 94, 142, 26, Platform.Style.SERVER_BANK);
        add(p, x + 188, gy - 186, 184, 22, Platform.Style.CABLE);
        add(p, x + 414, gy - 186, 184, 22, Platform.Style.CABLE);
        add(p, x + 646, gy - 94, 142, 26, Platform.Style.SERVER_BANK);
        coin(c, x + 280, gy - 218); coinChance(c, x + 506, gy - 218, 0.76);
        s.add(new WorldScenery(x + 16, gy - 268, 118, 174,
                WorldScenery.Kind.SERVER_RACK, WorldScenery.Layer.MID, v));
        s.add(new WorldScenery(x + 656, gy - 268, 118, 174,
                WorldScenery.Kind.SERVER_RACK, WorldScenery.Layer.MID, v + 1));
        s.add(new WorldScenery(x + 300, gy - 298, 190, 112,
                WorldScenery.Kind.CABLE_BRIDGE, WorldScenery.Layer.BACK, v));
        s.add(new WorldScenery(x + 366, gy - 82, 76, 76,
                WorldScenery.Kind.COOLING_FAN, WorldScenery.Layer.MID, v));
        nextX = x + 836 + random.nextInt(48);
    }

    private void dump(double x, int gy, int v, List<Platform> p,
                      List<LevelManager.Coin> c, List<WorldScenery> s) {
        add(p, x, gy - 70, 136, 24, Platform.Style.RUBBLE);
        add(p, x + 142, gy - 108, 148, 25, Platform.Style.RUBBLE);
        add(p, x + 298, gy - 148, 166, 26, Platform.Style.RUBBLE);
        add(p, x + 484, gy - 98, 134, 24, Platform.Style.RUBBLE);
        add(p, x + 642, gy - 138, 118, 22, Platform.Style.RUBBLE);
        coin(c, x + 370, gy - 180); coinChance(c, x + 700, gy - 170, 0.60);
        s.add(new WorldScenery(x + 28, gy - 126, 180, 92,
                WorldScenery.Kind.SCRAP_HEAP, WorldScenery.Layer.MID, v));
        s.add(new WorldScenery(x + 310, gy - 236, 122, 88,
                WorldScenery.Kind.BROKEN_PACKAGE, WorldScenery.Layer.MID, v));
        s.add(new WorldScenery(x + 590, gy - 194, 70, 108,
                WorldScenery.Kind.DEAD_TREE, WorldScenery.Layer.BACK, v));
        nextX = x + 798 + random.nextInt(58);
    }

    private void bossGate(double x, int gy, int v, List<Platform> p,
                          List<LevelManager.Coin> c, List<WorldScenery> s) {
        add(p, x, gy - 108, 310, 28, Platform.Style.FORTIFICATION);
        add(p, x + 244, gy - 214, 196, 22, Platform.Style.CATWALK);
        add(p, x + 372, gy - 108, 310, 28, Platform.Style.FORTIFICATION);
        coinChance(c, x + 342, gy - 246, 0.55);
        s.add(new WorldScenery(x + 24, gy - 196, 52, 88,
                WorldScenery.Kind.WARNING_PYLON, WorldScenery.Layer.MID, v));
        s.add(new WorldScenery(x + 606, gy - 196, 52, 88,
                WorldScenery.Kind.WARNING_PYLON, WorldScenery.Layer.MID, v + 1));
        if ((v & 1) == 0) s.add(new WorldScenery(x + 220, gy - 388, 260, 280,
                WorldScenery.Kind.BLAST_DOOR, WorldScenery.Layer.BACK, v));
        else s.add(new WorldScenery(x + 270, gy - 330, 164, 222,
                WorldScenery.Kind.SEARCHLIGHT, WorldScenery.Layer.BACK, v));
        nextX = x + 760;
    }

    private static void add(List<Platform> platforms, double x, int y,
                            int width, int height, Platform.Style style) {
        platforms.add(new Platform(x, y, width, height, style));
    }

    private static void coin(List<LevelManager.Coin> coins, double x, double y) {
        coins.add(new LevelManager.Coin(x, y));
    }

    private void coinChance(List<LevelManager.Coin> coins, double x, double y, double chance) {
        if (random.nextDouble() < chance) coin(coins, x, y);
    }
}
