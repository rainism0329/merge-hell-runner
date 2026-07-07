package com.bigphil.mergehell.model;

import org.junit.jupiter.api.Test;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;

class FloatingTextTest {

    @Test
    void floatingText_shouldReturnTrueInitially() {
        FloatingText ft = new FloatingText(100, 200, "Test", Color.WHITE);
        assertTrue(ft.update());
    }

    @Test
    void floatingText_shouldEventuallyExpire() {
        FloatingText ft = new FloatingText(100, 200, "Test", Color.WHITE);
        for (int i = 0; i < 100; i++) {
            if (!ft.update()) break;
        }
        assertFalse(ft.update());
    }
}
