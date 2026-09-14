package com.bigphil.mergehell.audio;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class AmbienceTest {
    private final AmbienceBank bank = AmbienceBank.synthesized();

    @Test void loopsAreDistinctQuietBoundedAndJoinWithoutASpike() {
        var hashes = new HashSet<Integer>();
        for (var scene : AmbienceScene.values()) {
            if (scene == AmbienceScene.NONE) continue;
            short[] loop = new short[AmbienceBank.LOOP_FRAMES];
            long squares = 0, sum = 0; int peak = 0;
            for (int i = 0; i < loop.length; i++) {
                loop[i] = bank.at(scene, i);
                squares += (long) loop[i] * loop[i]; sum += loop[i];
                peak = Math.max(peak, Math.abs((int) loop[i]));
            }
            double rms = Math.sqrt(squares / (double) loop.length);
            assertTrue(rms > 1_000 && rms < 5_000, scene + " RMS " + rms);
            assertTrue(peak < 13_000, scene + " peak " + peak);
            assertTrue(Math.abs(sum / (double) loop.length) < 2, scene + " DC offset");
            assertTrue(Math.abs(loop[0] - loop[loop.length - 1]) < 500, scene + " loop seam");
            assertTrue(hashes.add(Arrays.hashCode(loop)), "Each environment has a distinct motif");
        }
        assertSame(bank, AmbienceBank.synthesized(), "The immutable cache is shared across windows");
    }

    @Test void ambienceRepeatsExactlyWithoutConsumingCombatVoices() {
        PcmMixer mixer = mixer();
        mixer.setAmbience(AmbienceScene.FOUNDRY, 20, 0);
        byte[] first = render(mixer, AmbienceBank.LOOP_FRAMES);
        assertArrayEquals(first, render(mixer, AmbienceBank.LOOP_FRAMES));
        assertEquals(0, mixer.activeVoiceCount());
        assertTrue(peak(first) > 100 && peak(first) < 1_000);
        assertTrue(mixer.trigger(CombatFeedbackEvent.of(CombatFeedbackEvent.Cue.BOSS_BREAK)));
        assertEquals(1, mixer.activeVoiceCount());
        assertTrue(peak(render(mixer, SoundBank.SAMPLE_RATE)) > peak(first));
    }

    @Test void sceneChangesCrossfadeAndBackgroundZeroLeavesEffectsAudible() {
        PcmMixer mixer = mixer();
        mixer.setAmbience(AmbienceScene.HEAP, 100, 0);
        byte[] before = render(mixer, 12345);
        mixer.setAmbience(AmbienceScene.BOSS, 100, 13230);
        byte[] transition = render(mixer, 13230);
        assertTrue(Math.abs(sample(before, before.length / 2 - 1) - sample(transition, 0)) < 500);
        assertTrue(peak(transition) > 100);
        mixer.setAmbience(AmbienceScene.BOSS, 0, 13230);
        render(mixer, 13230);
        assertEquals(0, peak(render(mixer, 1024)));
        assertTrue(mixer.trigger(CombatFeedbackEvent.of(CombatFeedbackEvent.Cue.COMMIT_SHOT)));
        assertTrue(peak(render(mixer, 1024)) > 100);
    }

    @Test void muteGainSilencesMusicAndFullCombatPolyphonyTogether() {
        PcmMixer mixer = mixer();
        mixer.setGain(1, 0); mixer.setAmbience(AmbienceScene.BOSS, 100, 0);
        for (var cue : CombatFeedbackEvent.Cue.values()) mixer.trigger(CombatFeedbackEvent.of(cue));
        assertEquals(8, mixer.activeVoiceCount());
        int peak = peak(render(mixer, 512)); assertTrue(peak > 1000 && peak < 32767);
        mixer.setGain(0, 882); render(mixer, 882);
        assertEquals(0, peak(render(mixer, 512)));
        mixer.clear(); assertEquals(0, mixer.activeVoiceCount());
    }

    @Test void missionMappingUsesAllFiveWorldsAndBossOverrides() {
        var expected = new AmbienceScene[]{AmbienceScene.FOUNDRY, AmbienceScene.HEAP,
                AmbienceScene.BLUEPRINT, AmbienceScene.KERNEL, AmbienceScene.SINGULARITY};
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], AmbienceScene.forMission(i, false));
            assertEquals(AmbienceScene.BOSS, AmbienceScene.forMission(i, true));
        }
        assertEquals(AmbienceScene.NONE, AmbienceScene.forMission(-1, false));
    }

    private PcmMixer mixer() {
        PcmMixer mixer = new PcmMixer(SoundBank.synthesize(), bank, 8);
        mixer.setGain(.35f, 0); return mixer;
    }
    static byte[] render(PcmMixer mixer, int frames) {
        byte[] data = new byte[frames * 2]; mixer.render(data, frames); return data;
    }
    static int sample(byte[] data, int index) {
        return (short) ((data[index * 2] & 255) | ((data[index * 2 + 1] & 255) << 8));
    }
    static int peak(byte[] data) {
        int peak = 0;
        for (int i = 0; i < data.length / 2; i++) peak = Math.max(peak, Math.abs(sample(data, i)));
        return peak;
    }
}
