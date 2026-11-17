package dev.henny.lyrics.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** WAV (and anything else Java Sound reads), converted to 16-bit PCM. */
final class WavDecoder extends StreamDecoder {
    private final Path file;

    WavDecoder(Path file) throws IOException {
        this.file = file;
        start();
    }

    @Override
    protected InputStream openStream() throws IOException {
        try {
            AudioInputStream src = AudioSystem.getAudioInputStream(new BufferedInputStream(Files.newInputStream(file)));
            AudioFormat f = src.getFormat();
            AudioFormat pcm = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, f.getSampleRate(), 16,
                    f.getChannels(), f.getChannels() * 2, f.getSampleRate(), false);
            AudioInputStream conv = AudioSystem.getAudioInputStream(pcm, src);
            format(pcm);
            long frames = src.getFrameLength();
            length = frames == AudioSystem.NOT_SPECIFIED ? -1 : frames;
            return conv;
        } catch (UnsupportedAudioFileException | IllegalArgumentException e) {
            throw new IOException("Unsupported WAV file: " + e.getMessage(), e);
        }
    }
}
