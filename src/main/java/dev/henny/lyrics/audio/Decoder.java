package dev.henny.lyrics.audio;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

/** Turns an audio file into 16-bit PCM frames (interleaved shorts). Used from the audio thread only. */
public interface Decoder extends Closeable {
    int sampleRate();

    int channels();

    /** Length in frames, or -1 if not known (yet). */
    long lengthFrames();

    /** Fills {@code buf} with interleaved samples; returns the number of frames, -1 at the end. */
    int read(short[] buf) throws IOException;

    void seek(long frame) throws IOException;

    @Override
    void close();

    static boolean supported(String fileName) {
        String n = fileName.toLowerCase(Locale.ROOT);
        return n.endsWith(".ogg") || n.endsWith(".oga") || n.endsWith(".mp3") || n.endsWith(".wav");
    }

    static Decoder open(Path file) throws IOException {
        String n = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (n.endsWith(".ogg") || n.endsWith(".oga")) return new OggDecoder(file);
        if (n.endsWith(".mp3")) return new Mp3Decoder(file);
        if (n.endsWith(".wav")) return new WavDecoder(file);
        throw new IOException("Unsupported audio file: " + file.getFileName());
    }
}
