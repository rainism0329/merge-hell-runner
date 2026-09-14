package com.bigphil.mergehell.audio;

/** Device operations; AudioService invokes every method exclusively on its audio worker. */
public interface PcmOutput extends AutoCloseable {
    void open(int sampleRate, int bufferBytes) throws Exception;
    int availableBytes() throws Exception;
    void write(byte[] pcm, int byteCount) throws Exception;
    void flush() throws Exception;
    @Override void close() throws Exception;
}
