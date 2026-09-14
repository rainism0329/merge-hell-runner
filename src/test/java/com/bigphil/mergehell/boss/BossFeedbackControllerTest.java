package com.bigphil.mergehell.boss;

import com.bigphil.mergehell.combat.CombatEvent;
import com.bigphil.mergehell.combat.WeaponId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BossFeedbackControllerTest {
    @Test void initialStatusAndRepeatedObservationsDoNotReplayPhaseAnnouncements() {
        BossFeedbackController controller = new BossFeedbackController();
        controller.observe(status(1000, 1, true, false));
        assertTrue(controller.drainSignals().isEmpty());
        controller.observe(status(1000, 2, false, false));
        assertEquals(List.of(BossFeedbackController.Kind.GUARD_BREAK), controller.drainSignals().stream().map(BossFeedbackController.Signal::kind).toList());
        controller.observe(status(1000, 2, false, false)); assertTrue(controller.drainSignals().isEmpty());
        controller.observe(status(400, 3, false, false));
        assertEquals(BossFeedbackController.Kind.PHASE, controller.drainSignals().get(0).kind());
        controller.observe(status(0, 3, false, false));
        assertEquals(BossFeedbackController.Kind.DEFEATED, controller.drainSignals().get(0).kind());
        controller.observe(status(0, 3, false, false)); assertTrue(controller.drainSignals().isEmpty());
    }

    @Test void everyDrainConsumesSignalsOnceAndBossHitsNeverRequestTheRedScreenFlash() {
        BossFeedbackController controller = new BossFeedbackController(); controller.observe(status(1000, 1, false, false));
        controller.accept(hit(1, 80, false, false, CombatEvent.DamageKind.DIRECT));
        var signal = controller.drainSignals().get(0);
        assertEquals(BossFeedbackController.Kind.HEAVY_HIT, signal.kind()); assertEquals(2, signal.hitstopTicks());
        assertEquals(0, signal.flashTicks()); assertTrue(controller.drainSignals().isEmpty());
    }

    @Test void rapidAndDroneStreamsCannotTrapTheSimulationInPermanentHitstop() {
        BossFeedbackController controller = new BossFeedbackController(); controller.observe(status(1000, 1, false, false));
        int stoppedTicks = 0;
        for (int tick = 0; tick < 100; tick++) {
            controller.tick(true);
            controller.accept(hit(tick * 2 + 1, 80, false, true, CombatEvent.DamageKind.DIRECT));
            controller.accept(hit(tick * 2 + 2, 30, false, false, CombatEvent.DamageKind.DRONE));
            for (var signal : controller.drainSignals()) {
                stoppedTicks += signal.hitstopTicks();
                if (signal.kind() == BossFeedbackController.Kind.DRONE_HIT) assertEquals(0, signal.hitstopTicks());
            }
        }
        assertEquals(20, stoppedTicks, "Even adversarial every-tick heavy hits are capped at two ticks per ten feedback ticks");
        assertTrue(controller.snapshot().impacts().size() <= 12);
    }

    @Test void blockedShotsDoNotFlashRecoilOrRemoveHealthAndTheirSoundIsRateLimited() {
        BossFeedbackController controller = new BossFeedbackController(); controller.observe(status(1000, 1, true, false));
        for (int i = 1; i <= 80; i++) controller.accept(hit(i, 0, true, false, CombatEvent.DamageKind.DIRECT));
        assertEquals(1, controller.drainSignals().size());
        assertEquals(0, controller.snapshot().bodyFlash()); assertEquals(0, controller.snapshot().recoil());
        assertEquals(1000, controller.snapshot().hp());
        assertTrue(controller.snapshot().impacts().stream().allMatch(BossFeedbackController.ImpactView::blocked));
    }

    @Test void duplicateRootHitsAreIgnoredButSeparateBossNodesCanBreakOnceEach() {
        BossFeedbackController controller = new BossFeedbackController();
        var hit = hit(99, 50, false, false, CombatEvent.DamageKind.DIRECT);
        controller.accept(hit); controller.accept(hit);
        assertEquals(50, controller.snapshot().impacts().get(0).damage()); controller.drainSignals();
        for (int node = 0; node < 3; node++) {
            var destroyed = new CombatEvent.BossImpact(CombatEvent.BossPart.NODE, node, 30, 0, 30,
                    600, 200 + node * 70, null, CombatEvent.DamageKind.BOMB, 100, false, false);
            controller.accept(destroyed); controller.accept(destroyed);
        }
        assertEquals(1, controller.drainSignals().size(), "Node break signals merge by kind, not additive screen freezes");
        assertEquals(4, controller.snapshot().impacts().size());
    }

    @Test void damageTrailKeepsTheOldHealthThenCatchesUpWithoutHealingOrPermanentStalling() {
        BossFeedbackController controller = new BossFeedbackController(); controller.observe(status(1000, 1, false, false));
        controller.observe(status(900, 1, false, false));
        assertEquals(1000, controller.snapshot().trailingHp());
        for (int tick = 0; tick < 50; tick++) {
            controller.observe(status(900 - tick, 1, false, false)); controller.tick(true);
        }
        assertTrue(controller.snapshot().trailingHp() < 900);
        for (int tick = 0; tick < 80; tick++) controller.tick(true);
        assertEquals(controller.snapshot().hp(), controller.snapshot().trailingHp());
        controller.observe(status(990, 1, false, false));
        assertEquals(990, controller.snapshot().trailingHp());
    }

    @Test void pauseFreezesImpactsTrailAndBannerWhileActiveHitstopTicksCanFinishTheFeedback() {
        BossFeedbackController controller = new BossFeedbackController(); controller.observe(status(1000, 1, true, false));
        controller.accept(hit(1, 80, false, true, CombatEvent.DamageKind.DIRECT));
        controller.observe(status(920, 2, false, false));
        var frozen = controller.snapshot();
        for (int i = 0; i < 200; i++) controller.tick(false);
        assertEquals(frozen, controller.snapshot());
        assertThrows(UnsupportedOperationException.class, () -> frozen.impacts().clear());
        for (int i = 0; i < 100; i++) controller.tick(true);
        assertTrue(controller.snapshot().impacts().isEmpty()); assertNull(controller.snapshot().banner());
        assertEquals(0, controller.snapshot().bodyFlash()); assertEquals(920, controller.snapshot().trailingHp());
    }

    @Test void vulnerabilityAnnouncesOnlyTheOpenEdgeAndResetDoesNotLeakOldBossState() {
        BossFeedbackController controller = new BossFeedbackController(); controller.observe(status(1000, 1, false, false));
        controller.observe(status(900, 1, false, true));
        assertEquals(BossFeedbackController.Kind.GUARD_BREAK, controller.drainSignals().get(0).kind());
        controller.observe(status(800, 1, false, true)); assertTrue(controller.drainSignals().isEmpty());
        controller.reset(); controller.observe(status(1000, 3, true, false));
        assertTrue(controller.drainSignals().isEmpty()); assertTrue(controller.snapshot().impacts().isEmpty());
        assertNull(controller.snapshot().banner()); assertEquals(1000, controller.snapshot().trailingHp());
    }

    private static BossFeedbackController.Status status(int hp, int stage, boolean shielded, boolean vulnerable) {
        return new BossFeedbackController.Status(hp, 1000, stage, "STAGE " + stage, shielded, vulnerable, 600, 180, 120, 150);
    }
    private static CombatEvent.BossImpact hit(long root, int damage, boolean blocked, boolean critical, CombatEvent.DamageKind source) {
        return new CombatEvent.BossImpact(CombatEvent.BossPart.BODY, 0, damage, 1000 - damage, 1000,
                605, 230, WeaponId.COMMIT_CANNON, source, root, critical, blocked);
    }
}
