package dev.henny.lyrics.lyrics;

import java.util.List;

/**
 * Time-synced lyrics. Every line knows when each of its words is sung, either from word tags in the .lrc file
 * (enhanced LRC, {@code <00:12.50>word}) or estimated from the line's length.
 */
public record Lyrics(String title, String artist, String album, long lengthMs, List<Line> lines, boolean wordSync) {
    /** A sung line. Empty lines ({@link #isBreak()}) mark instrumental parts. */
    public record Line(long start, long end, List<Word> words) {
        public boolean isBreak() {
            return words.isEmpty();
        }

        public String text() {
            StringBuilder sb = new StringBuilder();
            for (Word w : words) {
                if (!sb.isEmpty()) sb.append(' ');
                sb.append(w.text());
            }
            return sb.toString();
        }
    }

    public record Word(String text, long start, long end) {}

    /** Index of the line being sung at {@code t}, or -1 before the first line. */
    public int lineAt(long t) {
        int lo = 0;
        int hi = lines.size() - 1;
        int found = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (lines.get(mid).start() <= t) {
                found = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return found;
    }

    /** Where the last line ends, a stand-in for the song length when nothing better is known. */
    public long lastTime() {
        if (lengthMs > 0) return lengthMs;
        return lines.isEmpty() ? 0 : lines.get(lines.size() - 1).end() + 3000;
    }
}
