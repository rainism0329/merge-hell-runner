package com.bigphil.mergehell.audio;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

/** Original, deterministic short effects: mono signed 16-bit PCM at 22,050 Hz. */
public final class SoundBank {
    public static final int SAMPLE_RATE = 22_050;
    private static final double[] UPGRADE_NOTES = {523.25, 659.25, 783.99, 1_046.5};

    public static final class Sample {
        private final short[] pcm;
        private Sample(short[] pcm) { this.pcm = pcm; }
        public int frameCount() { return pcm.length; }
        public short at(int index) { return pcm[index]; }
    }

    private final Map<CombatFeedbackEvent.Cue, Sample> samples;

    private SoundBank(Map<CombatFeedbackEvent.Cue, Sample> samples) { this.samples = Map.copyOf(samples); }

    /** Precompute once on the audio worker; playback never generates or decodes samples. */
    public static SoundBank synthesize() {
        EnumMap<CombatFeedbackEvent.Cue, Sample> samples = new EnumMap<>(CombatFeedbackEvent.Cue.class);
        for (CombatFeedbackEvent.Cue cue : CombatFeedbackEvent.Cue.values()) samples.put(cue, synthesize(cue));
        return new SoundBank(samples);
    }

    public Sample sample(CombatFeedbackEvent.Cue cue) { return samples.get(cue); }

    private static Sample synthesize(CombatFeedbackEvent.Cue cue) {
        double seconds = switch (cue) {
            case COMMIT_SHOT -> 0.095;
            case FORCE_SHOT -> 0.21;
            case RAPID_SHOT -> 0.060;
            case DRONE_SHOT -> 0.075;
            case GC_SHOT -> 0.28;
            case FIREWALL_SHOT -> 0.17;
            case BEAM_SHOT -> 0.24;
            case HIT -> 0.065;
            case PLAYER_HURT -> 0.26;
            case DASH -> 0.15;
            case UPGRADE -> 0.46;
            case SUDO -> 0.64;
            case BOSS_PHASE -> 0.58;
            case BOSS_HIT -> 0.13;
            case BOSS_BREAK -> 0.34;
            case GC_PURGE -> 0.48;
        };
        short[] pcm = new short[(int) Math.ceil(seconds * SAMPLE_RATE)];
        double[] waveform = new double[pcm.length];
        double[] weights = new double[pcm.length];
        double sampleSum = 0, weightSum = 0;
        Random random = new Random(0x4D45524745484C4CL + cue.ordinal() * 97L);
        double filteredNoise = 0;
        for (int frame = 0; frame < pcm.length; frame++) {
            double t = frame / (double) SAMPLE_RATE;
            double progress = frame / (double) (pcm.length - 1);
            double noise = random.nextDouble() * 2 - 1;
            filteredNoise += 0.16 * (noise - filteredNoise);
            double envelope = Math.min(1, t / 0.004)
                    * Math.pow(1 - progress, cue == CombatFeedbackEvent.Cue.FIREWALL_SHOT ? 0.7 : 1.6);
            double value = switch (cue) {
                case COMMIT_SHOT -> 0.58 * chirp(t, 1_400, -8_000)
                        + 0.30 * noise * Math.exp(-t * 65);
                case FORCE_SHOT -> 0.68 * chirp(t, 175, -450)
                        + 0.48 * filteredNoise + 0.18 * noise * Math.exp(-t * 38);
                case RAPID_SHOT -> (0.60 * chirp(t, 2_200, -24_000)
                        + 0.22 * (noise - filteredNoise)) * Math.exp(-t * 24);
                // A soft, lower mechanical pulse leaves the bright rapid-fire voice in front.
                case DRONE_SHOT -> (0.22 * chirp(t, 780, -3_600)
                        + 0.055 * tone(t, 1_560) * Math.exp(-t * 30)
                        + 0.035 * filteredNoise * Math.exp(-t * 65)) * Math.exp(-t * 12);
                case GC_SHOT -> 0.64 * chirp(t, 105, -180) + 0.14 * tone(t, 65)
                        + 0.20 * chirp(t, 820, -1_400) * Math.exp(-t * 40)
                        + 0.30 * filteredNoise * Math.exp(-Math.pow((t - 0.034) / 0.009, 2));
                case FIREWALL_SHOT -> (0.66 * filteredNoise + 0.27 * noise
                        + 0.08 * chirp(t, 230, 700)) * (0.78 + 0.22 * tone(t, 43));
                case BEAM_SHOT -> beam(t);
                case HIT -> 0.45 * tone(t, 1_780) + 0.25 * tone(t, 2_530) + 0.25 * noise;
                case PLAYER_HURT -> (0.65 * chirp(t, 510, -1_250) + 0.20 * noise)
                        * (0.55 + 0.45 * Math.cos(2 * Math.PI * 13 * t));
                case DASH -> (0.65 * filteredNoise + 0.32 * chirp(t, 420, 3_800))
                        * Math.sin(Math.PI * progress);
                case UPGRADE -> arpeggio(t, UPGRADE_NOTES, 0.115);
                case SUDO -> 0.38 * chirp(t, 220, 660) + 0.24 * chirp(t, 330, 990)
                        + 0.18 * tone(t, 880) * Math.min(1, t * 4);
                case BOSS_PHASE -> (0.55 * tone(t, 146.83) + 0.30 * tone(t, 155.56))
                        * (0.5 + 0.5 * Math.pow(Math.sin(Math.PI * t / 0.19), 2));
                case BOSS_HIT -> 0.38 * chirp(t, 210, -660)
                        + 0.22 * tone(t, 1240) * Math.exp(-t * 35) + 0.13 * noise * Math.exp(-t * 50);
                case BOSS_BREAK -> 0.45 * chirp(t, 140, -210) + 0.32 * filteredNoise
                        + 0.21 * tone(t, 1960) * Math.exp(-t * 17);
                case GC_PURGE -> (0.28 * filteredNoise + 0.31 * chirp(t, 160, 1450))
                        * (0.65 + 0.35 * Math.sin(Math.PI * progress));
            };
            waveform[frame] = value * envelope * 0.78;
            weights[frame] = envelope;
            sampleSum += waveform[frame];
            weightSum += envelope;
        }
        // Remove the window-weighted DC component without introducing clicks at either zero endpoint.
        double dc = sampleSum / weightSum;
        double peak = 0;
        for (int frame = 0; frame < pcm.length; frame++) {
            waveform[frame] -= dc * weights[frame];
            peak = Math.max(peak, Math.abs(waveform[frame]));
        }
        double headroom = peak > 0.85 ? 0.85 / peak : 1;
        for (int frame = 0; frame < pcm.length; frame++)
            pcm[frame] = (short) Math.round(waveform[frame] * headroom * 32_767);
        return new Sample(pcm);
    }

    private static double beam(double seconds) {
        double charge = 0.032;
        if (seconds < charge) {
            double rise = seconds / charge;
            return 0.28 * chirp(seconds, 540, 14_000) * Math.sin(Math.PI * rise / 2);
        }
        double release = seconds - charge;
        double onset = Math.min(1, release / 0.002);
        double discharge = (0.62 * chirp(release, 2_600, -6_800) + 0.17 * tone(release, 3_300))
                * onset * Math.exp(-release * 8);
        // A two-millisecond crossfade avoids a discontinuity between charge and release.
        double chargeTail = 0.28 * chirp(seconds, 540, 14_000) * Math.max(0, 1 - release / 0.002);
        return discharge + chargeTail;
    }

    private static double tone(double seconds, double frequency) { return Math.sin(2 * Math.PI * frequency * seconds); }
    private static double chirp(double seconds, double startFrequency, double slope) {
        return Math.sin(2 * Math.PI * (startFrequency * seconds + 0.5 * slope * seconds * seconds));
    }
    private static double arpeggio(double seconds, double[] notes, double noteLength) {
        int index = Math.min(notes.length - 1, (int) (seconds / noteLength));
        double local = seconds - index * noteLength;
        double gate = Math.min(1, local / 0.004) * Math.min(1, Math.max(0, noteLength - local) / 0.015);
        return gate * (0.62 * tone(local, notes[index]) + 0.16 * tone(local, notes[index] * 2));
    }
}
