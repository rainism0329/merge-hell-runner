package com.bigphil.mergehell.combat;

import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class CombatRandomTest {
    @Test
    void matchesJavaRandomForCriticalRollsAndUpgradeSelection() {
        Random reference = new Random(947L);
        CombatRandom actual = new CombatRandom(947L);
        for (int i = 0; i < 500; i++) {
            assertEquals(reference.nextDouble(), actual.nextDouble());
            assertEquals(reference.nextInt(23), actual.nextInt(23));
        }
    }

    @Test
    void restoreContinuesTheExactStreamAndRejectsInvalidStates() {
        CombatRandom random = new CombatRandom(28L);
        random.nextDouble();
        long state = random.checkpointState();
        double first = random.nextDouble(), second = random.nextDouble();
        random.restoreState(state);
        assertEquals(first, random.nextDouble());
        assertEquals(second, random.nextDouble());
        assertThrows(IllegalArgumentException.class, () -> random.restoreState(-1));
        assertThrows(IllegalArgumentException.class, () -> random.restoreState(1L << 48));
    }
}
