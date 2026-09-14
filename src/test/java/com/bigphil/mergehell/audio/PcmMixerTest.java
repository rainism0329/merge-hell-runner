package com.bigphil.mergehell.audio;

import org.junit.jupiter.api.Test;

import static com.bigphil.mergehell.audio.CombatFeedbackEvent.Cue.*;
import static org.junit.jupiter.api.Assertions.*;

class PcmMixerTest {
    private final SoundBank bank = SoundBank.synthesize();

    @Test
    void repeatedCueIsRateLimitedUntilItsAuthoredInterval() {
        for (var cue : new CombatFeedbackEvent.Cue[]{COMMIT_SHOT, FORCE_SHOT,
                RAPID_SHOT, GC_SHOT, FIREWALL_SHOT, BEAM_SHOT, DRONE_SHOT, BOSS_HIT, BOSS_BREAK, GC_PURGE}) {
            PcmMixer mixer = new PcmMixer(bank, 8);
            assertTrue(mixer.trigger(CombatFeedbackEvent.of(cue)));
            assertFalse(mixer.trigger(CombatFeedbackEvent.of(cue)));
            int interval = (int) Math.ceil(cue.minimumIntervalMillis() * SoundBank.SAMPLE_RATE / 1_000.0);
            mixer.render(new byte[interval * 2], interval - 1);
            assertFalse(mixer.trigger(CombatFeedbackEvent.of(cue)), cue + " cannot fire one frame early");
            mixer.render(new byte[2], 1);
            assertTrue(mixer.trigger(CombatFeedbackEvent.of(cue)), cue + " unlocks at the first allowed frame");
        }
    }

    @Test
    void voiceBudgetRejectsEqualPriorityAndMakesRoomForBossWarning() {
        PcmMixer mixer = new PcmMixer(bank, 2);
        assertTrue(mixer.trigger(CombatFeedbackEvent.of(COMMIT_SHOT)));
        assertTrue(mixer.trigger(CombatFeedbackEvent.of(HIT)));
        assertFalse(mixer.trigger(CombatFeedbackEvent.of(FORCE_SHOT)));
        assertEquals(2, mixer.activeVoiceCount());
        assertTrue(mixer.trigger(CombatFeedbackEvent.of(BOSS_PHASE)));
        assertEquals(2, mixer.activeVoiceCount());
        mixer.render(new byte[SoundBank.SAMPLE_RATE * 2], SoundBank.SAMPLE_RATE);
        assertEquals(0, mixer.activeVoiceCount());
    }

    @Test
    void droneUsesSpareVoicesAndYieldsToThePrimaryWeapon() {
        PcmMixer mixer = new PcmMixer(bank, 1);
        assertTrue(mixer.trigger(CombatFeedbackEvent.of(RAPID_SHOT)));
        assertFalse(mixer.trigger(CombatFeedbackEvent.of(DRONE_SHOT)),
                "Support fire cannot interrupt the primary weapon");
        mixer.clear();
        assertTrue(mixer.trigger(CombatFeedbackEvent.of(DRONE_SHOT)));
        assertTrue(mixer.trigger(CombatFeedbackEvent.of(RAPID_SHOT)),
                "The primary weapon may reclaim the support voice");
        assertEquals(1, mixer.activeVoiceCount());
    }

    @Test
    void fadingReachesExactSilenceAndClearResetsOldRateLimits() {
        PcmMixer mixer = new PcmMixer(bank, 8);
        mixer.setGain(0.35f, 0);
        assertTrue(mixer.trigger(CombatFeedbackEvent.of(SUDO)));
        byte[] audible = new byte[256 * 2];
        mixer.render(audible, 256);
        assertTrue(hasNonZero(audible));
        mixer.setGain(0, 882);
        mixer.render(new byte[882 * 2], 882);
        assertEquals(0, mixer.gain());
        byte[] silent = new byte[256 * 2];
        mixer.render(silent, 256);
        assertFalse(hasNonZero(silent));
        mixer.clear();
        assertEquals(0, mixer.activeVoiceCount());
        assertTrue(mixer.trigger(CombatFeedbackEvent.of(SUDO)));
    }

    @Test
    void maximumPolyphonyStaysInsidePcmRangeAndZeroIntensityUsesNoVoice() {
        PcmMixer mixer = new PcmMixer(bank, 8);
        mixer.setGain(1, 0);
        assertFalse(mixer.trigger(new CombatFeedbackEvent(COMMIT_SHOT, 0)));
        int rejected = 0;
        for (CombatFeedbackEvent.Cue cue : CombatFeedbackEvent.Cue.values()) {
            if (!mixer.trigger(CombatFeedbackEvent.of(cue))) rejected++;
            assertTrue(mixer.activeVoiceCount() <= 8);
        }
        assertEquals(8, mixer.activeVoiceCount());
        assertTrue(rejected > 0, "The four new equal-priority shots cannot exceed the existing eight voices");
        byte[] output = new byte[SoundBank.SAMPLE_RATE * 2];
        mixer.render(output, SoundBank.SAMPLE_RATE);
        int peak = 0;
        for (int i = 0; i < output.length; i += 2) {
            int signed = (short) ((output[i] & 255) | ((output[i + 1] & 255) << 8));
            peak = Math.max(peak, Math.abs(signed));
        }
        assertTrue(peak > 1_000);
        assertTrue(peak < 32_767);
        assertEquals(0, mixer.activeVoiceCount());
    }

    private static boolean hasNonZero(byte[] data) {
        for (byte value : data) if (value != 0) return true;
        return false;
    }
}
