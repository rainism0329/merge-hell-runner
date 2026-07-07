package com.bigphil.mergehell.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProjectileTest {

    @Test
    void projectile_shouldMoveOnUpdate() {
        Projectile p = new Projectile(100, 100, 10, 0, ProjectileType.COMMIT);
        p.update();
        assertEquals(110, p.getX(), 0.01);
        assertEquals(100, p.getY(), 0.01);
    }

    @Test
    void commitProjectile_shouldHaveCorrectProperties() {
        Projectile p = new Projectile(0, 0, 10, 0, ProjectileType.COMMIT);
        assertEquals(ProjectileType.COMMIT, p.getType());
        assertEquals(25, p.getDamage());
        assertEquals(30, p.getBounds().width);
        assertEquals(10, p.getBounds().height);
    }

    @Test
    void sudoProjectile_shouldHaveHigherDamage() {
        Projectile p = new Projectile(0, 0, 12, 0, ProjectileType.SUDO);
        assertEquals(ProjectileType.SUDO, p.getType());
        assertEquals(100, p.getDamage());
        assertEquals(60, p.getBounds().width);
        assertEquals(15, p.getBounds().height);
    }

    @Test
    void projectile_shouldStartAlive() {
        Projectile p = new Projectile(0, 0, 10, 0, ProjectileType.COMMIT);
        assertFalse(p.isDead());
    }

    @Test
    void setDead_shouldMarkAsDead() {
        Projectile p = new Projectile(0, 0, 10, 0, ProjectileType.COMMIT);
        p.setDead(true);
        assertTrue(p.isDead());
    }

    @Test
    void projectile_shouldHandleVerticalMovement() {
        Projectile p = new Projectile(100, 100, 11, -1.5, ProjectileType.SUDO);
        p.update();
        assertEquals(111, p.getX(), 0.01);
        assertEquals(98.5, p.getY(), 0.01);
    }
}
