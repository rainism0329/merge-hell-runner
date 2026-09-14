package com.bigphil.mergehell.audio;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.nio.file.*;

/** Offline WAV export from the production mixer; never opens or plays through an audio device. */
public final class AudioPreview {
    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length == 0 ? "build/audio-preview" : args[0]); Files.createDirectories(out);
        for (AmbienceScene scene : AmbienceScene.values()) {
            if (scene == AmbienceScene.NONE) continue;
            PcmMixer mixer = new PcmMixer(SoundBank.synthesize(), AmbienceBank.synthesized(), 8);
            mixer.setGain(.35f, 0); mixer.setAmbience(scene, 20, SoundBank.SAMPLE_RATE * 600 / 1000);
            byte[] data = AmbienceTest.render(mixer, SoundBank.SAMPLE_RATE * 16);
            // Export tail fades for comfortable manual review; production loops continue seamlessly.
            mixer.setGain(0, SoundBank.SAMPLE_RATE / 2);
            byte[] tail = AmbienceTest.render(mixer, SoundBank.SAMPLE_RATE / 2);
            byte[] complete = java.util.Arrays.copyOf(data, data.length + tail.length);
            System.arraycopy(tail, 0, complete, data.length, tail.length);
            Path file = out.resolve(scene.name().toLowerCase(java.util.Locale.ROOT) + ".wav");
            try (var stream = new AudioInputStream(new ByteArrayInputStream(complete),
                    new AudioFormat(SoundBank.SAMPLE_RATE, 16, 1, true, false), complete.length / 2)) {
                AudioSystem.write(stream, AudioFileFormat.Type.WAVE, file.toFile());
            }
            System.out.println(file + " peak=" + AmbienceTest.peak(complete));
        }
    }
}
