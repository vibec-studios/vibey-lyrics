import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Random;

/**
 * Synthesizes the demo song "Pixel Skies" (music and words by Vibe Craft Studios; this
 * code is Apache-2.0, the song itself is an asset, see NOTICE)
 * and writes its word-timed .lrc. Every sung word is one lead note, so the karaoke timing is exact.
 *
 * java tools/DemoSong.java <out-dir>      then: ffmpeg -i pixel_skies.wav -c:a libvorbis -q:a 4 pixel_skies.ogg
 */
public class DemoSong {
    static final int SR = 44100;
    static final double BPM = 100;
    static final double BEAT = 60.0 / BPM;
    static final double BAR = BEAT * 4;
    static final double EIGHTH = BEAT / 2;
    static final int INTRO_BARS = 2;
    static final int LINE_BARS = 2;

    // word, start (eighths from line start), length (eighths), MIDI pitch
    static final Object[][][] LINES = {
            {{"Up", 0, 1, 69}, {"above", 1, 2, 72}, {"the", 3, 1, 71}, {"blocky", 4, 2, 69}, {"hills", 6, 4, 64}},
            {{"Every", 0, 2, 67}, {"chunk", 2, 1, 69}, {"is", 3, 1, 71}, {"glowing", 4, 2, 72}, {"still", 6, 4, 71}},
            {{"Build", 0, 1, 69}, {"it", 1, 1, 69}, {"high", 2, 2, 76}, {"and", 4, 1, 74}, {"let", 5, 1, 72}, {"it", 6, 1, 71}, {"shine", 7, 4, 69}},
            {{"Write", 0, 1, 67}, {"the", 1, 1, 69}, {"sky", 2, 2, 72}, {"in", 4, 1, 71}, {"neon", 5, 2, 69}, {"lines", 7, 5, 64}},
            {{"Pixel", 0, 2, 76}, {"skies,", 2, 2, 74}, {"pixel", 4, 2, 72}, {"skies", 6, 4, 69}},
            {{"Sing", 0, 1, 72}, {"it", 1, 1, 72}, {"loud", 2, 2, 74}, {"and", 4, 1, 72}, {"watch", 5, 1, 71}, {"it", 6, 1, 69}, {"rise", 7, 4, 71}},
            {{"Every", 0, 2, 69}, {"word", 2, 1, 72}, {"lights", 3, 1, 74}, {"up", 4, 1, 76}, {"the", 5, 1, 74}, {"night", 6, 4, 72}},
            {{"Pixel", 0, 2, 76}, {"skies,", 2, 2, 74}, {"we're", 4, 1, 72}, {"shining", 5, 2, 71}, {"bright", 7, 5, 69}},
    };
    // One chord per bar: Am F C G
    static final int[][] CHORDS = {{57, 60, 64}, {53, 57, 60}, {55, 60, 64}, {55, 59, 62}};
    static final int[] ROOTS = {45, 41, 48, 43};

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : ".");
        Files.createDirectories(out);
        int bars = INTRO_BARS + LINES.length * LINE_BARS + 2;
        double total = bars * BAR + 1.0;
        int n = (int) (total * SR);
        double[] mix = new double[n];
        double[] lead = new double[n];
        Random rnd = new Random(7);

        for (int bar = 0; bar < bars; bar++) {
            double t0 = bar * BAR;
            int c = bar % 4;
            boolean outro = bar >= bars - 2;
            boolean chorus = bar >= INTRO_BARS + 4 * LINE_BARS && !outro;
            boolean intro = bar < INTRO_BARS;
            // pad
            double padVol = outro ? 0.10 * (1 - (bar - (bars - 2)) / 2.0) : 0.085;
            for (int note : CHORDS[c]) pad(mix, t0, BAR, midi(note), padVol);
            // bass
            if (!intro && !outro) {
                for (int e = 0; e < 8; e++) {
                    int p = ROOTS[c] + (e % 4 == 3 ? 12 : 0);
                    bass(mix, t0 + e * EIGHTH, EIGHTH * 0.9, midi(p), chorus ? 0.22 : 0.17);
                }
            }
            // drums
            for (int b = 0; b < 4; b++) {
                double tb = t0 + b * BEAT;
                if (!intro && !outro && (b == 0 || b == 2 || (chorus && b == 3))) kick(mix, tb, 0.55);
                if (!intro && !outro && (b == 1 || b == 3)) snare(mix, tb, chorus ? 0.22 : 0.16, rnd);
                if (!outro) {
                    hat(mix, tb, 0.05, rnd);
                    hat(mix, tb + EIGHTH, intro ? 0.03 : 0.04, rnd);
                }
            }
        }

        StringBuilder lrc = new StringBuilder();
        lrc.append("[ti:Pixel Skies]\n[ar:Vibe Craft Studios]\n[al:Vibey Lyrics Demo]\n");
        lrc.append("[length:").append(clock(total - 1.0)).append("]\n");
        lrc.append("[re:Original demo song for Vibey Lyrics]\n\n");
        for (int i = 0; i < LINES.length; i++) {
            double lineStart = (INTRO_BARS + i * LINE_BARS) * BAR;
            StringBuilder line = new StringBuilder("[" + clock(lineStart) + "]");
            double end = lineStart;
            for (Object[] w : LINES[i]) {
                double ws = lineStart + (int) w[1] * EIGHTH;
                double wl = (int) w[2] * EIGHTH;
                leadNote(lead, ws, wl * 0.92, midi((int) w[3]), 0.16);
                line.append('<').append(clock(ws)).append('>').append(w[0]).append(' ');
                end = ws + wl;
            }
            line.setLength(line.length() - 1);
            line.append(" <").append(clock(end)).append('>');
            lrc.append(line).append('\n');
        }
        lrc.append("[").append(clock((INTRO_BARS + LINES.length * LINE_BARS) * BAR)).append("]\n");

        // lead with a dotted-eighth feedback echo
        int delay = (int) (EIGHTH * 1.5 * SR);
        for (int i = 0; i < n; i++) {
            if (i >= delay) lead[i] += lead[i - delay] * 0.32;
            mix[i] += lead[i];
        }
        // soft clip and write
        byte[] pcm = new byte[n * 2];
        for (int i = 0; i < n; i++) {
            double fade = Math.min(1, (n - i) / (0.5 * SR));
            double v = Math.tanh(mix[i] * 1.1) * 0.89 * fade;
            int s = (int) Math.round(v * 32767);
            pcm[i * 2] = (byte) s;
            pcm[i * 2 + 1] = (byte) (s >> 8);
        }
        AudioFormat fmt = new AudioFormat(SR, 16, 1, true, false);
        try (AudioInputStream ais = new AudioInputStream(new ByteArrayInputStream(pcm), fmt, n)) {
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, new File(out.toFile(), "pixel_skies.wav"));
        }
        Files.writeString(out.resolve("pixel_skies.lrc"), lrc.toString());
        System.out.println("Wrote " + out.toAbsolutePath() + " (" + String.format(Locale.ROOT, "%.1f", total) + " s)");
    }

    static double midi(int m) {
        return 440.0 * Math.pow(2, (m - 69) / 12.0);
    }

    static String clock(double s) {
        long cs = Math.round(s * 100);
        return String.format(Locale.ROOT, "%02d:%02d.%02d", cs / 6000, (cs / 100) % 60, cs % 100);
    }

    static void pad(double[] buf, double t0, double len, double f, double vol) {
        int a = (int) (t0 * SR);
        int b = Math.min(buf.length, (int) ((t0 + len + 0.4) * SR));
        double lp = 0;
        for (int i = a; i < b; i++) {
            double t = (i - a) / (double) SR;
            double env = Math.min(1, t / 0.25) * (t > len ? Math.max(0, 1 - (t - len) / 0.4) : 1);
            double s = 0;
            for (double det : new double[]{-0.004, 0, 0.004}) {
                double ph = (t * f * (1 + det)) % 1.0;
                s += (2 * ph - 1) / 3;
            }
            lp += (s - lp) * 0.06;
            buf[i] += lp * env * vol;
        }
    }

    static void bass(double[] buf, double t0, double len, double f, double vol) {
        int a = (int) (t0 * SR);
        int b = Math.min(buf.length, (int) ((t0 + len) * SR));
        for (int i = a; i < b; i++) {
            double t = (i - a) / (double) SR;
            double ph = (t * f) % 1.0;
            double tri = 4 * Math.abs(ph - 0.5) - 1;
            double env = Math.min(1, t / 0.005) * Math.exp(-t * 3) * Math.min(1, (len - t) / 0.01);
            buf[i] += tri * env * vol;
        }
    }

    static void kick(double[] buf, double t0, double vol) {
        int a = (int) (t0 * SR);
        int b = Math.min(buf.length, a + (int) (0.35 * SR));
        double ph = 0;
        for (int i = a; i < b; i++) {
            double t = (i - a) / (double) SR;
            double f = 45 + 90 * Math.exp(-t * 30);
            ph += f / SR;
            buf[i] += Math.sin(2 * Math.PI * ph) * Math.exp(-t * 9) * vol;
        }
    }

    static void snare(double[] buf, double t0, double vol, Random rnd) {
        int a = (int) (t0 * SR);
        int b = Math.min(buf.length, a + (int) (0.2 * SR));
        double prev = 0;
        for (int i = a; i < b; i++) {
            double t = (i - a) / (double) SR;
            double noise = rnd.nextDouble() * 2 - 1;
            double hp = noise - prev;
            prev = noise;
            buf[i] += (hp * 0.7 + Math.sin(2 * Math.PI * 190 * t) * 0.5) * Math.exp(-t * 22) * vol;
        }
    }

    static void hat(double[] buf, double t0, double vol, Random rnd) {
        int a = (int) (t0 * SR);
        int b = Math.min(buf.length, a + (int) (0.05 * SR));
        double prev = 0;
        for (int i = a; i < b; i++) {
            double t = (i - a) / (double) SR;
            double noise = rnd.nextDouble() * 2 - 1;
            double hp = noise - prev;
            prev = noise;
            buf[i] += hp * Math.exp(-t * 80) * vol;
        }
    }

    static void leadNote(double[] buf, double t0, double len, double f, double vol) {
        int a = (int) (t0 * SR);
        int b = Math.min(buf.length, (int) ((t0 + len + 0.12) * SR));
        double ph = 0;
        double lp = 0;
        for (int i = a; i < b; i++) {
            double t = (i - a) / (double) SR;
            double vib = t > 0.15 ? 1 + 0.006 * Math.sin(2 * Math.PI * 5.5 * t) : 1;
            ph += f * vib / SR;
            double p = ph % 1.0;
            double pulse = p < 0.32 ? 1 : -1;
            double sine = Math.sin(2 * Math.PI * ph);
            double s = pulse * 0.45 + sine * 0.55;
            lp += (s - lp) * 0.35;
            double env = Math.min(1, t / 0.012) * (t < len ? (0.75 + 0.25 * Math.exp(-t * 6)) : Math.max(0, 0.75 * (1 - (t - len) / 0.12)));
            buf[i] += lp * env * vol;
        }
    }
}
