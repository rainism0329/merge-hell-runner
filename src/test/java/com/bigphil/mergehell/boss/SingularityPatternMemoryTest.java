package com.bigphil.mergehell.boss;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;
import static com.bigphil.mergehell.boss.SingularityPatternMemory.Pattern.*;

class SingularityPatternMemoryTest {
    @Test void onlyRecentHistoryChoosesTheFirstCounterAndEveryCycleContainsAllFour() {
        var stationary = new SingularityPatternMemory();
        for (int i = 0; i < 900; i++) stationary.observe(100, 440, false);
        var reading = stationary.select(0, 0);
        assertEquals(MEMORY_ECHO, reading.pattern()); assertEquals(90, reading.samples());
        assertEquals(100, reading.targetX()); assertEquals(440, reading.targetY());
        var types = new HashSet<>(); types.add(reading.pattern());
        for (int i = 0; i < 3; i++) types.add(stationary.select(0, 0).pattern());
        assertEquals(4, types.size());
        var firing = new SingularityPatternMemory();
        for (int i = 0; i < 90; i++) firing.observe(i * 4, 300, i % 10 == 0);
        assertEquals(BLUEPRINT_ECHO, firing.select(0, 0).pattern());
        var moving = new SingularityPatternMemory();
        for (int i = 0; i < 90; i++) moving.observe(i * 4, 300, false);
        assertEquals(KERNEL_ECHO, moving.select(0, 0).pattern());
    }

    @Test void oldSamplesExpireAndSingleInputCannotReplaceAnEntireHistory() {
        var memory = new SingularityPatternMemory();
        for (int i = 0; i < 300; i++) memory.observe(i * 10, 100, true);
        for (int i = 0; i < 90; i++) memory.observe(700, 430, false);
        memory.observe(900, 10, true);
        var reading = memory.select(0, 0);
        assertEquals(1, reading.shots());
        assertEquals((700 * 89 + 900) / 90.0, reading.targetX());
        assertTrue(reading.targetY() > 420);
        memory.reset();
        reading = memory.select(12, 34);
        assertEquals(0, reading.samples()); assertEquals(12, reading.targetX());
        assertEquals(EVENT_HORIZON, reading.pattern());
    }
}
