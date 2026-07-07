package com.bigphil.mergehell.model;

import org.junit.jupiter.api.Test;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;

class ParticleTest {

    @Test
    void particle_shouldStartWithFullLife() {
        Particle p = new Particle(0, 0, Color.RED, 5, -5, 0.04f);
        assertEquals(1.0f, p.getLife(), 0.001f);
    }

    @Test
    void particle_shouldDecayOnUpdate() {
        Particle p = new Particle(0, 0, Color.RED, 5, -5, 0.1f);
        p.update();
        assertEquals(0.9f, p.getLife(), 0.001f);
    }

    @Test
    void particle_shouldMoveAndDecayOnUpdate() {
        Particle p = new Particle(100, 200, Color.RED, 5, -5, 0.04f);
        float lifeBefore = p.getLife();
        p.update();
        assertTrue(p.getLife() < lifeBefore); // decayed
    }

    @Test
    void particle_life_shouldGoBelowZero() {
        Particle p = new Particle(0, 0, Color.RED, 0, 0, 0.5f);
        p.update();
        p.update();
        p.update();
        assertTrue(p.getLife() <= 0);
    }
}
