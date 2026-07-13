package com.bigphil.mergehell.engine;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FixedStepAccumulatorTest {
    @Test
    void capsCatchUpAtFiveTicks() {
        FixedStepAccumulator accumulator = new FixedStepAccumulator(16_666_667L, 5);
        AtomicInteger ticks = new AtomicInteger();
        accumulator.consume(0, ticks::incrementAndGet);
        int consumed = accumulator.consume(1_000_000_000L, ticks::incrementAndGet);
        assertEquals(5, consumed);
        assertEquals(5, ticks.get());
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new FixedStepAccumulator(0, 5));
        assertThrows(IllegalArgumentException.class, () -> new FixedStepAccumulator(1, 0));
    }
}
