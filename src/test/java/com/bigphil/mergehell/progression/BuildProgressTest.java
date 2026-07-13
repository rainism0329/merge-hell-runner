package com.bigphil.mergehell.progression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildProgressTest {

    @Test
    void xpCarriesAcrossMultipleThresholds() {
        BuildProgress progress = new BuildProgress();

        assertEquals(2, progress.addXp(275));

        assertEquals(2, progress.pendingChoices());
        assertEquals(25, progress.currentXp());
        assertEquals(200, progress.nextThreshold());
        assertEquals(3, progress.level());
        assertTrue(progress.currentXp() < progress.nextThreshold());
    }

    @Test
    void xpAccumulatesUntilTheNextThreshold() {
        BuildProgress progress = new BuildProgress();

        assertEquals(0, progress.addXp(40));
        assertEquals(0, progress.addXp(59));
        assertEquals(1, progress.addXp(1));

        assertEquals(2, progress.level());
        assertEquals(0, progress.currentXp());
        assertEquals(1, progress.pendingChoices());
        assertEquals(150, progress.nextThreshold());
    }

    @Test
    void negativeXpIsRejectedWithoutChangingProgress() {
        BuildProgress progress = new BuildProgress();

        assertThrows(IllegalArgumentException.class, () -> progress.addXp(-1));

        assertEquals(1, progress.level());
        assertEquals(0, progress.currentXp());
        assertEquals(0, progress.pendingChoices());
    }
}
