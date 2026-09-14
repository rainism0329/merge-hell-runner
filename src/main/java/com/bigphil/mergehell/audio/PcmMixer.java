package com.bigphil.mergehell.audio;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;

/** Single-worker software mixer; it has no device, clock, thread or filesystem access. */
public final class PcmMixer {
    private static final class Voice {
        final CombatFeedbackEvent event;
        final SoundBank.Sample sample;
        int cursor;
        Voice(CombatFeedbackEvent event, SoundBank.Sample sample) { this.event = event; this.sample = sample; }
    }

    private final SoundBank bank;
    private final int maxVoices;
    private final List<Voice> voices = new ArrayList<>();
    private final EnumMap<CombatFeedbackEvent.Cue, Long> lastStartFrames = new EnumMap<>(CombatFeedbackEvent.Cue.class);
    private long renderedFrames;
    private float gain;
    private float targetGain;
    private int gainRampFrames;
    private final AmbienceBank ambience;
    private static final AmbienceScene[] SCENES = AmbienceScene.values();
    private final float[] ambienceWeights = new float[SCENES.length];
    private final float[] ambienceTargets = new float[SCENES.length];
    private int ambienceRampFrames;
    private int ambienceCursor;

    public PcmMixer(SoundBank bank, int maxVoices) {
        this(bank, null, maxVoices);
    }

    public PcmMixer(SoundBank bank, AmbienceBank ambience, int maxVoices) {
        this.bank = Objects.requireNonNull(bank, "bank");
        this.ambience = ambience;
        if (maxVoices < 1) throw new IllegalArgumentException("maxVoices must be positive");
        this.maxVoices = maxVoices;
    }

    public boolean trigger(CombatFeedbackEvent event) {
        Objects.requireNonNull(event, "event");
        if (event.intensity() == 0) return false;
        Long previous = lastStartFrames.get(event.cue());
        long interval = (event.cue().minimumIntervalMillis() * (long) SoundBank.SAMPLE_RATE + 999) / 1_000;
        if (previous != null && renderedFrames - previous < interval) return false;
        if (voices.size() == maxVoices) {
            int candidate = 0;
            for (int i = 1; i < voices.size(); i++) {
                if (voices.get(i).event.cue().priority() < voices.get(candidate).event.cue().priority()) candidate = i;
            }
            if (event.cue().priority() <= voices.get(candidate).event.cue().priority()) return false;
            voices.remove(candidate);
        }
        voices.add(new Voice(event, bank.sample(event.cue())));
        lastStartFrames.put(event.cue(), renderedFrames);
        return true;
    }

    public void setGain(float gain, int fadeFrames) {
        if (!Float.isFinite(gain) || gain < 0 || gain > 1 || fadeFrames < 0) throw new IllegalArgumentException();
        targetGain = gain;
        gainRampFrames = fadeFrames;
        if (fadeFrames == 0) this.gain = gain;
    }

    /** A separate looping bed never consumes or steals combat voices. Scene changes crossfade. */
    public void setAmbience(AmbienceScene scene, int volumePercent, int fadeFrames) {
        Objects.requireNonNull(scene);
        if (volumePercent < 0 || volumePercent > 100 || fadeFrames < 0) throw new IllegalArgumentException();
        for (int i = 1; i < SCENES.length; i++) {
            ambienceTargets[i] = SCENES[i] == scene ? volumePercent / 100f : 0;
            if (fadeFrames == 0) ambienceWeights[i] = ambienceTargets[i];
        }
        ambienceRampFrames = fadeFrames;
    }

    /** Writes little-endian signed 16-bit mono PCM into a reusable output buffer. */
    public void render(byte[] output, int frames) {
        if (frames < 0 || frames > output.length / 2) throw new IllegalArgumentException("Output buffer is too small");
        for (int frame = 0; frame < frames; frame++) {
            if (gainRampFrames > 0) {
                gain += (targetGain - gain) / gainRampFrames;
                if (--gainRampFrames == 0) gain = targetGain;
            }
            double sum = 0;
            if (ambience != null) {
                for (int i = 1; i < SCENES.length; i++) {
                    if (ambienceRampFrames > 0) {
                        ambienceWeights[i] += (ambienceTargets[i] - ambienceWeights[i]) / ambienceRampFrames;
                        if (ambienceRampFrames == 1) ambienceWeights[i] = ambienceTargets[i];
                    }
                    if (ambienceWeights[i] != 0) sum += ambience.at(SCENES[i], ambienceCursor)
                            / 32_768.0 * ambienceWeights[i];
                }
                if (ambienceRampFrames > 0) ambienceRampFrames--;
                if (++ambienceCursor == AmbienceBank.LOOP_FRAMES) ambienceCursor = 0;
            }
            for (Voice voice : voices) {
                if (voice.cursor < voice.sample.frameCount()) {
                    sum += voice.sample.at(voice.cursor++) / 32_768.0 * voice.event.intensity();
                }
            }
            // Fixed headroom and a soft limiter keep coincident effects bounded without integer wrapping.
            int value = (int) Math.round(Math.tanh(sum * 0.65) * gain * 32_767);
            output[frame * 2] = (byte) value;
            output[frame * 2 + 1] = (byte) (value >> 8);
        }
        renderedFrames += frames;
        voices.removeIf(voice -> voice.cursor >= voice.sample.frameCount());
    }

    /** Drops tails and rate-limit history at a pause/resume boundary. */
    public void clear() {
        voices.clear(); lastStartFrames.clear();
        java.util.Arrays.fill(ambienceWeights, 0);
        ambienceRampFrames = SoundBank.SAMPLE_RATE * 600 / 1_000;
        ambienceCursor = 0;
    }
    public int activeVoiceCount() { return voices.size(); }
    public float gain() { return gain; }
}
