package dev.henny.lyrics.audio;

import dev.henny.lyrics.VibeyLyrics;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

/**
 * Plays one decoder on its own thread through Java Sound. The playback position comes from the sound card
 * ({@link SourceDataLine#getLongFramePosition()}), so lyrics stay in sync with what you actually hear.
 */
public final class AudioPlayer {
    private final Decoder decoder;
    private final Thread thread;
    private final Object lock = new Object();
    private volatile boolean paused;
    private volatile boolean stopped;
    private volatile boolean ended;
    private volatile long seekTo = -1;
    private volatile float volume = 1f;
    private volatile String error;
    private SourceDataLine line;
    private long baseFrame;
    private long lineFrameAtBase;

    public AudioPlayer(Decoder decoder) {
        this.decoder = decoder;
        this.thread = new Thread(this::run, "Vibey Lyrics audio");
        thread.setDaemon(true);
        thread.start();
    }

    public void setPaused(boolean p) {
        paused = p;
    }

    public boolean isPaused() {
        return paused;
    }

    public boolean isEnded() {
        return ended;
    }

    public String error() {
        return error;
    }

    public void setVolume(float v) {
        volume = Math.max(0f, Math.min(1f, v));
    }

    public void seekMs(long ms) {
        ended = false;
        seekTo = Math.max(0, ms) * decoder.sampleRate() / 1000L;
    }

    public long lengthMs() {
        long f = decoder.lengthFrames();
        return f < 0 ? -1 : f * 1000L / decoder.sampleRate();
    }

    public long positionMs() {
        synchronized (lock) {
            long frames = baseFrame;
            if (line != null) frames += Math.max(0, line.getLongFramePosition() - lineFrameAtBase);
            long pending = seekTo;
            if (pending >= 0) frames = pending;
            return frames * 1000L / decoder.sampleRate();
        }
    }

    public void stop() {
        stopped = true;
        thread.interrupt();
    }

    private void run() {
        int ch = decoder.channels();
        AudioFormat format = new AudioFormat(decoder.sampleRate(), 16, ch, true, false);
        short[] samples = new short[2048 * ch];
        byte[] bytes = new byte[samples.length * 2];
        try {
            SourceDataLine l = AudioSystem.getSourceDataLine(format);
            // ~150 ms buffer: small enough for quick pause/seek, big enough not to stutter.
            l.open(format, Math.max(bytes.length * 2, Math.round(decoder.sampleRate() * 0.15f) * ch * 2));
            synchronized (lock) {
                line = l;
                lineFrameAtBase = l.getLongFramePosition();
            }
            while (!stopped) {
                long seek = seekTo;
                if (seek >= 0) {
                    l.stop();
                    l.flush();
                    decoder.seek(seek);
                    synchronized (lock) {
                        baseFrame = seek;
                        lineFrameAtBase = l.getLongFramePosition();
                        seekTo = -1;
                    }
                    ended = false;
                }
                if (paused || ended) {
                    if (l.isRunning()) l.stop();
                    Thread.sleep(10);
                    continue;
                }
                if (!l.isRunning()) l.start();
                int frames = decoder.read(samples);
                if (frames < 0) {
                    l.drain();
                    ended = true;
                    continue;
                }
                float v = volume;
                // Perceived loudness is roughly logarithmic, a squared slider feels linear.
                float gain = v * v;
                int n = frames * ch;
                for (int i = 0; i < n; i++) {
                    int s = Math.round(samples[i] * gain);
                    bytes[i * 2] = (byte) s;
                    bytes[i * 2 + 1] = (byte) (s >> 8);
                }
                l.write(bytes, 0, n * 2);
            }
        } catch (InterruptedException ignored) {
        } catch (Exception e) {
            error = e.getMessage() == null ? e.toString() : e.getMessage();
            ended = true;
            VibeyLyrics.LOG.warn("Audio playback failed", e);
        } finally {
            synchronized (lock) {
                if (line != null) {
                    baseFrame += Math.max(0, line.getLongFramePosition() - lineFrameAtBase);
                    line.stop();
                    line.close();
                    line = null;
                }
            }
            decoder.close();
        }
    }
}
