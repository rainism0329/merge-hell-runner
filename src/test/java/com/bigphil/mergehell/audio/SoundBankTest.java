package com.bigphil.mergehell.audio;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.Map;

import static com.bigphil.mergehell.audio.CombatFeedbackEvent.Cue.*;

import static org.junit.jupiter.api.Assertions.*;

class SoundBankTest {
    @Test
    void everyCueHasDeterministicDistinctPcmWithQuietEndpoints() {
        SoundBank first = SoundBank.synthesize();
        SoundBank second = SoundBank.synthesize();
        Set<Long> signatures = new HashSet<>();
        for (CombatFeedbackEvent.Cue cue : CombatFeedbackEvent.Cue.values()) {
            SoundBank.Sample sample = first.sample(cue);
            assertSame(sample, first.sample(cue), "Playback reuses the cached sample");
            assertTrue(sample.frameCount() > SoundBank.SAMPLE_RATE / 20);
            assertTrue(sample.frameCount() < SoundBank.SAMPLE_RATE);
            assertEquals(0, sample.at(0));
            assertEquals(0, sample.at(sample.frameCount() - 1));
            long signature = 1;
            long sum = 0;
            int peak = 0;
            for (int i = 0; i < sample.frameCount(); i++) {
                assertEquals(sample.at(i), second.sample(cue).at(i));
                signature = 31 * signature + sample.at(i);
                sum += sample.at(i);
                peak = Math.max(peak, Math.abs((int) sample.at(i)));
            }
            assertTrue(peak > 1_000, "A registered cue must contain audible PCM");
            assertTrue(peak <= 27_852, "The authored sample keeps at least 15% headroom before mixing");
            assertTrue(Math.abs(sum / (double) sample.frameCount()) < 1,
                    cue + " must have less than one PCM step of DC offset");
            signatures.add(signature);
        }
        assertEquals(CombatFeedbackEvent.Cue.values().length, signatures.size());
    }

    @Test
    void fourNewWeaponVoicesHaveShortAuthoredLengthsAndModerateEnergy() {
        SoundBank bank = SoundBank.synthesize();
        Map<CombatFeedbackEvent.Cue, Double> durations = Map.of(
                RAPID_SHOT, 0.060, GC_SHOT, 0.28, FIREWALL_SHOT, 0.17, BEAM_SHOT, 0.24);
        durations.forEach((cue, seconds) -> {
            var sample = bank.sample(cue);
            assertEquals((int) Math.ceil(seconds * SoundBank.SAMPLE_RATE), sample.frameCount());
            double squares = 0;
            for (int i = 0; i < sample.frameCount(); i++) squares += sample.at(i) * (double) sample.at(i);
            double rms = Math.sqrt(squares / sample.frameCount());
            assertTrue(rms > 1_000 && rms < 9_000, cue + " keeps useful energy without an excessively loud tail: " + rms);
        });
    }

    @Test
    void rapidIsCrispGcIsLowFirewallIsNoisyAndBeamHasAChargeThenBrightRelease() {
        SoundBank bank = SoundBank.synthesize();
        double heavy = crossingsPerSecond(bank.sample(GC_SHOT));
        assertTrue(heavy < 400, "GC is dominated by its low mechanical body");
        assertTrue(crossingsPerSecond(bank.sample(RAPID_SHOT)) > heavy * 5);
        assertTrue(crossingsPerSecond(bank.sample(FIREWALL_SHOT)) > heavy * 10);
        assertTrue(crossingsPerSecond(bank.sample(BEAM_SHOT)) > heavy * 5);
        assertTrue(peakFrame(bank.sample(RAPID_SHOT)) < SoundBank.SAMPLE_RATE * 0.020);
        assertTrue(peakFrame(bank.sample(BEAM_SHOT)) > SoundBank.SAMPLE_RATE * 0.032);
    }

    @Test
    void droneIsAShortQuieterLowerPulseThanTheRapidPrimaryWeapon() {
        SoundBank bank = SoundBank.synthesize();
        var drone = bank.sample(DRONE_SHOT);
        assertEquals((int) Math.ceil(0.075 * SoundBank.SAMPLE_RATE), drone.frameCount());
        double droneRms = rms(drone);
        assertTrue(droneRms > 500, "The support shot remains audible");
        assertTrue(droneRms < rms(bank.sample(RAPID_SHOT)) * 0.60,
                "The support pulse leaves the primary gun in front");
        assertTrue(crossingsPerSecond(drone) < crossingsPerSecond(bank.sample(RAPID_SHOT)) * 0.65,
                "The lower tonal pulse remains distinct from Rapid's bright crack");
        assertTrue(peakFrame(drone) < SoundBank.SAMPLE_RATE * 0.015);
    }

    private static double rms(SoundBank.Sample sample) {
        double squares = 0;
        for (int i = 0; i < sample.frameCount(); i++) squares += sample.at(i) * (double) sample.at(i);
        return Math.sqrt(squares / sample.frameCount());
    }

    @Test void bossImpactBreakAndGcPurgeHaveDistinctBoundedShortSamples() {
        SoundBank bank = SoundBank.synthesize();
        Map<CombatFeedbackEvent.Cue, Double> durations = Map.of(BOSS_HIT, .13, BOSS_BREAK, .34, GC_PURGE, .48);
        durations.forEach((cue, duration) -> {
            var sample = bank.sample(cue);
            assertEquals((int) Math.ceil(duration * SoundBank.SAMPLE_RATE), sample.frameCount());
            assertTrue(rms(sample) > 1000 && rms(sample) < 9000, cue.name());
        });
        assertTrue(crossingsPerSecond(bank.sample(BOSS_HIT)) < crossingsPerSecond(bank.sample(GC_PURGE)),
                "The low plated impact is distinct from the rising purge airflow");
    }

    private static int peakFrame(SoundBank.Sample sample) {
        int peak = 0;
        for (int i = 1; i < sample.frameCount(); i++)
            if (Math.abs((int) sample.at(i)) > Math.abs((int) sample.at(peak))) peak = i;
        return peak;
    }

    private static double crossingsPerSecond(SoundBank.Sample sample) {
        int crossings = 0;
        for (int i = 1; i < sample.frameCount(); i++)
            if ((sample.at(i) < 0) != (sample.at(i - 1) < 0)) crossings++;
        return crossings * (double) SoundBank.SAMPLE_RATE / sample.frameCount();
    }
}
