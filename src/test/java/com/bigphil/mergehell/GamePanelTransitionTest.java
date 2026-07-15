package com.bigphil.mergehell;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GamePanelTransitionTest {

    @Test
    void missionCompleteScreenStaysUncoveredBeforeItsFinalFade() {
        assertEquals(0, GamePanel.missionCompleteTransitionAlpha(180));
        assertEquals(0, GamePanel.missionCompleteTransitionAlpha(60));
    }

    @Test
    void missionCompleteScreenOnlyFadesNearTheTransition() {
        assertEquals(0, GamePanel.missionCompleteTransitionAlpha(45));
        assertTrue(GamePanel.missionCompleteTransitionAlpha(20) > 0);
        assertEquals(255, GamePanel.missionCompleteTransitionAlpha(0));
    }
}
