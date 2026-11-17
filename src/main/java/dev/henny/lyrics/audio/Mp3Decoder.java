package dev.henny.lyrics.audio;

import dev.henny.lyrics.VibeyLyrics;
import fr.delthas.javamp3.Sound;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** MP3 through JavaMP3 (pure Java, MIT). The length is found by a quick second decode in the background. */
final class Mp3Decoder extends StreamDecoder {
    private final Path file;
    private volatile long scanned = -1;

    Mp3Decoder(Path file) throws IOException {
        this.file = file;
        start();
        Thread t = new Thread(this::scanLength, "Vibey Lyrics MP3 length");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    @Override
    protected InputStream openStream() throws IOException {
        Sound s = new Sound(new BufferedInputStream(Files.newInputStream(file), 1 << 16));
        format(s.getAudioFormat());
        return s;
    }

    @Override
    public long lengthFrames() {
        return scanned;
    }

    private void scanLength() {
        try (Sound s = new Sound(new BufferedInputStream(Files.newInputStream(file), 1 << 16))) {
            int channels = s.isStereo() ? 2 : 1;
            long[] bytes = {0};
            s.decodeFullyInto(new OutputStream() {
                @Override
                public void write(int b) {
                    bytes[0]++;
                }

                @Override
                public void write(byte[] b, int off, int len) {
                    bytes[0] += len;
                }
            });
            scanned = bytes[0] / (2L * channels);
        } catch (Exception e) {
            VibeyLyrics.LOG.debug("Could not measure {}: {}", file.getFileName(), e.toString());
        }
    }
}
