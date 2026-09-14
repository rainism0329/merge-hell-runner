package com.bigphil.mergehell.assets;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AnimatorTest {
    private static final long MS = 1_000_000L;

    @Test
    void frameBoundariesUseAuthoredDurationsAndLoopExactly() {
        Animator animator = new Animator(definition());
        animator.advanceNanos(100 * MS - 1);
        assertEquals(0, animator.frameIndex());
        animator.advanceNanos(1);
        assertEquals(1, animator.frameIndex());
        animator.advanceNanos(200 * MS);
        assertEquals(0, animator.frameIndex());
        assertEquals(0, animator.elapsedNanos());
        assertFalse(animator.finished());
    }

    @Test
    void singleShotHoldsTheLastFrameAfterLongDelta() {
        Animator animator = new Animator(definition());
        animator.play("land", false);
        animator.advanceNanos(Long.MAX_VALUE);
        animator.advanceNanos(Long.MAX_VALUE);
        assertTrue(animator.finished());
        assertEquals(1, animator.frameIndex());
        assertEquals(300 * MS, animator.elapsedNanos());
        assertEquals("second", animator.frame().id());
    }

    @Test
    void largeLoopDeltasDoNotOverflowOrRequireOneIterationPerLoop() {
        Animator animator = new Animator(definition());
        animator.advanceNanos(Long.MAX_VALUE);
        animator.advanceNanos(Long.MAX_VALUE);
        assertEquals(((Long.MAX_VALUE % (300 * MS)) * 2) % (300 * MS), animator.elapsedNanos());
    }

    @Test
    void chunkingSimulationTimeDoesNotChangeTheSelectedFrame() {
        Animator smallSteps = new Animator(definition());
        Animator largeStep = new Animator(definition());
        for (int i = 0; i < 83; i++) smallSteps.advanceNanos(16_666_667);
        largeStep.advanceNanos(83 * 16_666_667L);
        assertEquals(largeStep.elapsedNanos(), smallSteps.elapsedNanos());
        assertEquals(largeStep.frameIndex(), smallSteps.frameIndex());
    }

    @Test
    void sameAnimationDoesNotRestartUnlessExplicitlyRequested() {
        Animator animator = new Animator(definition());
        animator.advanceNanos(150 * MS);
        animator.play("idle", false);
        assertEquals(150 * MS, animator.elapsedNanos());
        animator.play("idle", true);
        assertEquals(0, animator.elapsedNanos());
        animator.advanceNanos(150 * MS);
        animator.play("land", false);
        assertEquals(0, animator.elapsedNanos());
    }

    @Test
    void invalidInputDoesNotCorruptPlayback() {
        Animator animator = new Animator(definition());
        animator.advanceNanos(150 * MS);
        assertThrows(IllegalArgumentException.class, () -> animator.advanceNanos(-1));
        assertThrows(IllegalArgumentException.class, () -> animator.play("missing", true));
        assertEquals("idle", animator.animation());
        assertEquals(150 * MS, animator.elapsedNanos());
    }

    @Test
    void clipsDefensivelyCopyDataAndRejectOverflowingDurations() {
        AnimationClip.Frame frame = new AnimationClip.Frame("too-long", 0, 0, 2, 2,
                Long.MAX_VALUE, new AnimationClip.Point(1, 2), Map.of());
        assertThrows(IllegalArgumentException.class, () -> new AnimationClip("bad", List.of(frame, frame), true));
        assertThrows(UnsupportedOperationException.class, () -> definition().animations().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> definition().animations().get("idle").frames().clear());
    }

    private static SpriteDefinition definition() {
        List<AnimationClip.Frame> frames = List.of(
                new AnimationClip.Frame("first", 0, 0, 4, 4, 100 * MS,
                        new AnimationClip.Point(2, 4), Map.of()),
                new AnimationClip.Frame("second", 4, 0, 4, 4, 200 * MS,
                        new AnimationClip.Point(2, 4), Map.of()));
        return new SpriteDefinition("repair", "game/repair.png", 2, 20, "idle",
                Map.of("idle", new AnimationClip("idle", frames, true),
                        "land", new AnimationClip("land", frames, false)));
    }
}
