package com.bigphil.mergehell.engine;

public record SessionMetrics(
        int hostiles, int projectiles, int enemyProjectiles, int particles,
        int floatingTexts, int maxHostiles, int rejectedProjectiles,
        long worldTick, int upgradeCount, long bossSpawnTick, long completionTick) { }
