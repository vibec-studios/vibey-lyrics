package dev.henny.lyrics.audio;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.io.InputStream;

/**
 * Base for decoders that only read forward (MP3, WAV): seeking reopens the file and skips ahead.
 * The stream delivers 16-bit signed PCM in the given byte order.
 */
abstract class StreamDecoder implements Decoder {
    private InputStream in;
    private boolean bigEndian;
    protected int channels;
    protected int sampleRate;
    protected long length = -1;
    private byte[] bytes = new byte[0];

    protected abstract InputStream openStream() throws IOException;

    /** Called after {@link #openStream()} with the stream's format. */
    protected void format(AudioFormat f) throws IOException {
        if (f.getSampleSizeInBits() != 16) throw new IOException("Only 16-bit audio is supported");
        channels = f.getChannels();
        sampleRate = Math.round(f.getSampleRate());
        bigEndian = f.isBigEndian();
    }

    protected void start() throws IOException {
        in = openStream();
    }

    @Override
    public int sampleRate() {
        return sampleRate;
    }

    @Override
    public int channels() {
        return channels;
    }

    @Override
    public long lengthFrames() {
        return length;
    }

    @Override
    public int read(short[] buf) throws IOException {
        int frameBytes = 2 * channels;
        int want = (buf.length / channels) * frameBytes;
        if (bytes.length < want) bytes = new byte[want];
        int got = 0;
        while (got < frameBytes) {
            int n = in.read(bytes, got, want - got);
            if (n < 0) break;
            got += n;
            if (got >= want) break;
        }
        // Fill up to a whole frame so channels never get out of step.
        while (got % frameBytes != 0) {
            int n = in.read(bytes, got, frameBytes - got % frameBytes);
            if (n < 0) {
                got -= got % frameBytes;
                break;
            }
            got += n;
        }
        if (got <= 0) return -1;
        int samples = got / 2;
        for (int i = 0; i < samples; i++) {
            int a = bytes[i * 2] & 0xFF;
            int b = bytes[i * 2 + 1] & 0xFF;
            buf[i] = (short) (bigEndian ? (a << 8) | b : (b << 8) | a);
        }
        return samples / channels;
    }

    @Override
    public void seek(long frame) throws IOException {
        closeStream();
        in = openStream();
        long skip = Math.max(0, frame) * 2L * channels;
        byte[] tmp = new byte[16384];
        while (skip > 0) {
            int n = in.read(tmp, 0, (int) Math.min(tmp.length, skip));
            if (n < 0) break;
            skip -= n;
        }
    }

    private void closeStream() {
        try {
            if (in != null) in.close();
        } catch (IOException ignored) {
        }
        in = null;
    }

    @Override
    public void close() {
        closeStream();
    }
}
