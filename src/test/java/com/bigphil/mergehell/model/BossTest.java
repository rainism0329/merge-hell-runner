package com.bigphil.mergehell.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BossTest {

    private static final int PANEL_WIDTH = 960;

    @Test
    void boss_shouldStartInactive() {
        Boss boss = new Boss("Test Boss", 1000, "⚠️", PANEL_WIDTH);
        assertFalse(boss.isActive());
    }

    @Test
    void boss_shouldStartWithCorrectHp() {
        Boss boss = new Boss("Test Boss", 1000, "⚠️", PANEL_WIDTH);
        assertEquals(500, boss.getHp());
        assertEquals(500, boss.getMaxHp());
    }

    @Test
    void activate_shouldSetActive() {
        Boss boss = new Boss("Test Boss", 1000, "⚠️", PANEL_WIDTH);
        boss.activate();
        assertTrue(boss.isActive());
    }

    @Test
    void takeDamage_shouldReduceHp() {
        Boss boss = new Boss("Test Boss", 1000, "⚠️", PANEL_WIDTH);
        boss.activate();
        int initialHp = boss.getHp();
        boss.takeDamage(100);
        assertEquals(initialHp - 100, boss.getHp());
    }

    @Test
    void takeDamage_shouldNotWorkWhenInactive() {
        Boss boss = new Boss("Test Boss", 1000, "⚠️", PANEL_WIDTH);
        boss.takeDamage(100);
        assertEquals(boss.getMaxHp(), boss.getHp());
    }

    @Test
    void isDashing_shouldStartFalse() {
        Boss boss = new Boss("Test Boss", 1000, "⚠️", PANEL_WIDTH);
        assertFalse(boss.isDashing());
    }

    @Test
    void boss_shouldHaveCorrectName() {
        Boss boss = new Boss("LEGACY CODE", 1000, "⚠️", PANEL_WIDTH);
        assertEquals("LEGACY CODE", boss.getName());
    }

    @Test
    void getBounds_shouldReturnCorrectSize() {
        Boss boss = new Boss("Test Boss", 1000, "⚠️", PANEL_WIDTH);
        var bounds = boss.getBounds();
        assertEquals(120, bounds.width);
        assertEquals(150, bounds.height);
    }
}
