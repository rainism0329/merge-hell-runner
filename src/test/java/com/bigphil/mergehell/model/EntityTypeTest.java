package com.bigphil.mergehell.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EntityTypeTest {

    @Test
    void powerupTypes_shouldReportAsPowerup() {
        assertTrue(EntityType.POWERUP_SUDO.isPowerup());
        assertTrue(EntityType.POWERUP_SHIELD.isPowerup());
    }

    @Test
    void nonPowerupTypes_shouldNotReportAsPowerup() {
        assertFalse(EntityType.BUG.isPowerup());
        assertFalse(EntityType.CRASH.isPowerup());
        assertFalse(EntityType.CONFLICT.isPowerup());
        assertFalse(EntityType.TECHDEBT.isPowerup());
        assertFalse(EntityType.LOCK.isPowerup());
        assertFalse(EntityType.FIREWALL.isPowerup());
    }

    @Test
    void allTypes_shouldHaveNonNullProperties() {
        for (EntityType t : EntityType.values()) {
            assertNotNull(t.color, t + " color should not be null");
            assertNotNull(t.symbol, t + " symbol should not be null");
            assertTrue(t.width > 0, t + " width should be positive");
            assertTrue(t.height > 0, t + " height should be positive");
            assertTrue(t.vx > 0, t + " vx should be positive");
        }
    }

    @Test
    void powerups_shouldHaveZeroDamage() {
        assertEquals(0, EntityType.POWERUP_SUDO.damage);
        assertEquals(0, EntityType.POWERUP_SHIELD.damage);
    }
}
