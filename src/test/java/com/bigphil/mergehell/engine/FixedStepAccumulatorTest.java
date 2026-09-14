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
        assertThrows(IllegalArgumentException.class, () -> new FixedStepAccumulator(Long.MAX_VALUE, 5));
    }

    @Test
    void retainsSubTickRemainderAndResetDiscardsIt() {
        FixedStepAccumulator accumulator = new FixedStepAccumulator(10, 5);
        AtomicInteger ticks = new AtomicInteger();
        accumulator.reset(0);
        assertEquals(1, accumulator.consume(16, ticks::incrementAndGet));
        assertEquals(1, accumulator.consume(20, ticks::incrementAndGet));
        accumulator.consume(29, ticks::incrementAndGet);
        accumulator.reset(100);
        assertEquals(0, accumulator.consume(109, ticks::incrementAndGet));
        assertEquals(1, accumulator.consume(110, ticks::incrementAndGet));
        assertEquals(3, ticks.get());
    }

    @Test
    void backwardsClockDoesNotDuplicateAlreadyConsumedTime() {
        FixedStepAccumulator accumulator = new FixedStepAccumulator(10, 5);
        accumulator.reset(0);
        assertEquals(2, accumulator.consume(20, () -> { }));
        assertEquals(0, accumulator.consume(10, () -> { }));
        assertEquals(0, accumulator.consume(20, () -> { }));
        assertEquals(1, accumulator.consume(30, () -> { }));
    }

    @Test
    void supportsNegativeNanoTimeOriginWithoutSentinelCollision() {
        FixedStepAccumulator accumulator = new FixedStepAccumulator(10, 5);
        assertEquals(0, accumulator.consume(Long.MIN_VALUE, () -> { }));
        assertEquals(1, accumulator.consume(Long.MIN_VALUE + 10, () -> { }));
    }
}
