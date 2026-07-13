package com.bigphil.mergehell.progression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverclockMeterTest {

    @Test
    void activatesAtCapacityForExactlyEightSeconds() {
        OverclockMeter meter = new OverclockMeter(100, 480);

        meter.addCharge(100);

        assertTrue(meter.isActive());
        assertEquals(480, meter.activeTicks());
        assertEquals(1.0, meter.ratio());
        for (int i = 0; i < 479; i++) {
            meter.tick();
        }
        assertTrue(meter.isActive());
        meter.tick();
        assertFalse(meter.isActive());
        assertEquals(0, meter.charge());
    }

    @Test
    void chargeIsClampedAndCannotBeExtendedWhileActive() {
        OverclockMeter meter = new OverclockMeter(100, 480);

        meter.addCharge(70);
        assertEquals(0.7, meter.ratio(), 0.0001);
        meter.addCharge(80);
        meter.tick();
        meter.addCharge(100);

        assertEquals(100, meter.charge());
        assertEquals(479, meter.activeTicks());
    }

    @Test
    void invalidConfigurationIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new OverclockMeter(0, 480));
        assertThrows(IllegalArgumentException.class, () -> new OverclockMeter(100, 0));
    }
}
