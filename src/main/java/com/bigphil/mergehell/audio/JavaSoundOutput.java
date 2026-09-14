package com.bigphil.mergehell.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.io.IOException;

final class JavaSoundOutput implements PcmOutput {
    private SourceDataLine line;

    @Override
    public void open(int sampleRate, int bufferBytes) throws Exception {
        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
        line = AudioSystem.getSourceDataLine(format);
        line.open(format, bufferBytes);
        line.start();
    }

    @Override public int availableBytes() { return line.available(); }

    @Override
    public void write(byte[] pcm, int byteCount) throws IOException {
        if (line.write(pcm, 0, byteCount) != byteCount) throw new IOException("Audio output stopped during write");
    }

    @Override public void flush() { if (line != null && line.isOpen()) line.flush(); }

    @Override
    public void close() {
        if (line == null) return;
        try {
            line.stop();
            line.flush();
        } finally {
            line.close();
        }
    }
}
