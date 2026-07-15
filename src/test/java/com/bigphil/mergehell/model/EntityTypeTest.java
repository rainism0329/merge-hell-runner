package com.bigphil.mergehell.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class EntityTypeTest {

    @Test
    void powerupTypes_shouldReportAsPowerup() {
        assertTrue(EntityType.PICKUP_SPREAD.isPowerup());
        assertTrue(EntityType.POWERUP_SHIELD.isPowerup());
    }

    @Test
    void everyWeaponPickupIsNonHostile() {
        assertTrue(EntityType.PICKUP_FLAME.isPowerup());
        assertTrue(EntityType.PICKUP_LASER.isPowerup());
        assertFalse(EntityType.PICKUP_FLAME.isHostile());
        assertFalse(EntityType.PICKUP_LASER.isHostile());
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
        assertEquals(0, EntityType.PICKUP_SPREAD.damage);
        assertEquals(0, EntityType.POWERUP_SHIELD.damage);
    }

    @Test
    void everyLaterLevelAmbientPoolContainsItsSignatureEnemy() {
        assertTrue(Arrays.asList(ObstacleManager.ambientRoster(1)).contains(EntityType.LEAK));
        assertTrue(Arrays.asList(ObstacleManager.ambientRoster(2)).contains(EntityType.SENTINEL));
        assertTrue(Arrays.asList(ObstacleManager.ambientRoster(3)).contains(EntityType.INTERRUPT));
        assertTrue(Arrays.asList(ObstacleManager.ambientRoster(4)).contains(EntityType.MIRROR));
    }

    @Test
    void signatureEnemiesAreHostileAndDurable() {
        for (EntityType type : new EntityType[]{EntityType.LEAK, EntityType.SENTINEL,
                EntityType.INTERRUPT, EntityType.MIRROR}) {
            assertTrue(type.isHostile());
            assertTrue(type.maxHp > 1);
            assertTrue(type.pointValue >= 200);
        }
    }
}
