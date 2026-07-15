package com.bigphil.mergehell.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BossTest {

    private static final int PANEL_WIDTH = 960;

    @Test
    void boss_shouldStartInactive() {
        Boss boss = new Boss("Test Boss", 1000, "⚠️", PANEL_WIDTH, 0);
        assertFalse(boss.isActive());
    }

    @Test
    void boss_shouldStartWithCorrectHp() {
        Boss boss = new Boss("Test Boss", 1000, "⚠️", PANEL_WIDTH, 0);
        assertEquals(500, boss.getHp());
        assertEquals(500, boss.getMaxHp());
    }

    @Test
    void activate_shouldSetActive() {
        Boss boss = new Boss("Test Boss", 1000, "⚠️", PANEL_WIDTH, 0);
        boss.activate();
        assertTrue(boss.isActive());
    }

    @Test
    void takeDamage_shouldReduceHp() {
        Boss boss = new Boss("Test Boss", 1000, "⚠️", PANEL_WIDTH, 0);
        boss.activate();
        int initialHp = boss.getHp();
        boss.takeDamage(100);
        assertEquals(initialHp - 100, boss.getHp());
    }

    @Test
    void takeDamage_shouldNotWorkWhenInactive() {
        Boss boss = new Boss("Test Boss", 1000, "⚠️", PANEL_WIDTH, 0);
        boss.takeDamage(100);
        assertEquals(boss.getMaxHp(), boss.getHp());
    }

    @Test
    void isDashing_shouldStartFalse() {
        Boss boss = new Boss("Test Boss", 1000, "⚠️", PANEL_WIDTH, 0);
        assertFalse(boss.isDashing());
    }

    @Test
    void boss_shouldHaveCorrectName() {
        Boss boss = new Boss("LEGACY CODE", 1000, "⚠️", PANEL_WIDTH, 0);
        assertEquals("LEGACY CODE", boss.getName());
    }

    @Test
    void getBounds_shouldReturnCorrectSize() {
        Boss boss = new Boss("Test Boss", 1000, "⚠️", PANEL_WIDTH, 0);
        var bounds = boss.getBounds();
        assertEquals(120, bounds.width);
        assertEquals(150, bounds.height);
    }

    @Test
    void laterCampaignBossesHaveDistinctPersonalitiesAndPhaseNames() {
        List<Boss> bosses = List.of(
                bossAtLevel(1), bossAtLevel(2), bossAtLevel(3), bossAtLevel(4));

        assertEquals(4, bosses.stream().map(Boss::getPersonalityName).distinct().count());
        assertEquals(List.of("ALLOCATING", "DRAFT", "BOOT", "OBSERVE"),
                bosses.stream().map(Boss::getStageName).toList());

        for (Boss boss : bosses) {
            boss.activate();
            boss.takeDamage(500);
            assertEquals(2, boss.getCombatStage());
            boss.takeDamage(400);
            assertEquals(3, boss.getCombatStage());
        }
    }

    @Test
    void eachLaterBossExecutesItsOwnSignatureMoveSet() {
        Boss memory = simulateBoss(1, 1_100);
        Boss architect = simulateBoss(2, 1_100);
        Boss kernel = simulateBoss(3, 1_300);
        Boss singularity = simulateBoss(4, 1_700);

        assertTrue(memory.movesSeenForTesting().containsAll(Set.of("HEAP_SPRAY", "GC_STORM")));
        assertTrue(architect.movesSeenForTesting().containsAll(Set.of("BLUEPRINT_GRID", "RUNTIME_WALL")));
        assertTrue(kernel.movesSeenForTesting().containsAll(Set.of("CORE_DUMP", "PANIC_LANES")));
        assertTrue(singularity.movesSeenForTesting().containsAll(Set.of(
                "MEMORY_ECHO", "BLUEPRINT_ECHO", "KERNEL_ECHO", "EVENT_HORIZON")));
    }

    private static Boss bossAtLevel(int level) {
        return new Boss("Boss " + level, 2_400, "!", PANEL_WIDTH, level, new Random(level));
    }

    private static Boss simulateBoss(int level, int ticks) {
        Boss boss = bossAtLevel(level);
        boss.activate();
        ObstacleManager obstacles = new ObstacleManager();
        List<Projectile> bullets = new ArrayList<>();
        for (int i = 0; i < ticks; i++) {
            boss.update(obstacles, 480, 260, 330, bullets);
            if (bullets.size() > 300) bullets.clear();
        }
        return boss;
    }
}
