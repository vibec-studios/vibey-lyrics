package dev.henny.lyrics.lyrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads .lrc files: {@code [mm:ss.xx]text}, several time tags per line, the tags ti/ar/al/length/offset and word
 * timing in the enhanced format {@code [00:12.00]<00:12.00>Hello <00:12.40>world}.
 */
public final class LrcParser {
    private static final Pattern TIME = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");
    private static final Pattern TAG = Pattern.compile("^\\[([a-zA-Z#]+):(.*)]\\s*$");
    private static final Pattern WORD_TIME = Pattern.compile("<(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?>");

    private LrcParser() {}

    private record Raw(long time, String text) {}

    public static Lyrics parse(String content) {
        String title = "";
        String artist = "";
        String album = "";
        long length = 0;
        long offset = 0;
        List<Raw> raw = new ArrayList<>();

        for (String line : content.replace("﻿", "").split("\\r?\\n")) {
            line = line.strip();
            if (line.isEmpty()) continue;
            Matcher tag = TAG.matcher(line);
            if (tag.matches() && !TIME.matcher(line).lookingAt()) {
                String key = tag.group(1).toLowerCase(Locale.ROOT);
                String value = tag.group(2).strip();
                switch (key) {
                    case "ti" -> title = value;
                    case "ar" -> artist = value;
                    case "al" -> album = value;
                    case "length" -> length = parseClock(value);
                    case "offset" -> {
                        try {
                            offset = Long.parseLong(value.replace("+", ""));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    default -> {
                    }
                }
                continue;
            }
            List<Long> times = new ArrayList<>();
            Matcher m = TIME.matcher(line);
            int pos = 0;
            while (m.find(pos) && m.start() == pos) {
                times.add(toMs(m.group(1), m.group(2), m.group(3)));
                pos = m.end();
            }
            if (times.isEmpty()) continue;
            String text = line.substring(pos).strip();
            for (long t : times) raw.add(new Raw(t, text));
        }

        raw.sort((a, b) -> Long.compare(a.time, b.time));
        // A positive [offset:] makes the lyrics appear sooner.
        List<Lyrics.Line> lines = new ArrayList<>();
        boolean wordSync = false;
        for (int i = 0; i < raw.size(); i++) {
            Raw r = raw.get(i);
            long start = Math.max(0, r.time - offset);
            long nextStart = i + 1 < raw.size() ? Math.max(0, raw.get(i + 1).time - offset) : -1;
            ParsedWords pw = words(r.text, start, nextStart, offset);
            wordSync |= pw.timed;
            long end = pw.words.isEmpty() ? (nextStart < 0 ? start : nextStart) : pw.words.get(pw.words.size() - 1).end();
            if (nextStart >= 0) end = Math.min(Math.max(end, start), nextStart);
            lines.add(new Lyrics.Line(start, end, pw.words));
        }
        return new Lyrics(title, artist, album, length, List.copyOf(lines), wordSync);
    }

    private record ParsedWords(List<Lyrics.Word> words, boolean timed) {}

    /** Splits a line into words with times: from {@code <mm:ss>} tags when there are some, estimated otherwise. */
    private static ParsedWords words(String text, long start, long nextStart, long offset) {
        List<Lyrics.Word> out = new ArrayList<>();
        Matcher m = WORD_TIME.matcher(text);
        if (m.find()) {
            // Enhanced LRC: each tag starts the text up to the next tag.
            String prefix = text.substring(0, m.start());
            List<Long> marks = new ArrayList<>();
            List<String> parts = new ArrayList<>();
            m.reset();
            int cursor = -1;
            while (m.find()) {
                if (cursor >= 0) parts.add(text.substring(cursor, m.start()));
                marks.add(Math.max(0, toMs(m.group(1), m.group(2), m.group(3)) - offset));
                cursor = m.end();
            }
            parts.add(text.substring(cursor));
            // Text in front of the first tag belongs to the first word.
            if (!prefix.isBlank()) parts.set(0, prefix + parts.get(0));
            long lastMark = marks.get(marks.size() - 1);
            long lineEnd = nextStart > 0 ? nextStart : lastMark + 1500;
            for (int i = 0; i < parts.size(); i++) {
                long ws = marks.get(i);
                long we = i + 1 < marks.size() ? marks.get(i + 1) : Math.min(lineEnd, ws + 1200);
                String chunk = parts.get(i);
                // A trailing tag with no text only marks where the line ends.
                if (chunk.isBlank()) {
                    if (!out.isEmpty() && i == parts.size() - 1) {
                        Lyrics.Word prev = out.remove(out.size() - 1);
                        out.add(new Lyrics.Word(prev.text(), prev.start(), Math.max(prev.start(), ws)));
                    }
                    continue;
                }
                // One tag can cover several words ("<00:01.00>la la"): share its time between them.
                String[] split = chunk.strip().split("\\s+");
                long span = Math.max(1, we - ws);
                for (int k = 0; k < split.length; k++) {
                    long a = ws + span * k / split.length;
                    long b = ws + span * (k + 1) / split.length;
                    out.add(new Lyrics.Word(split[k], a, b));
                }
            }
            return new ParsedWords(out, true);
        }

        String clean = text.strip();
        if (clean.isEmpty() || clean.equals("♪")) return new ParsedWords(out, false);
        String[] split = clean.split("\\s+");
        // Without word tags: spread the words over the line, by length, like most singers do.
        int chars = 0;
        for (String s : split) chars += s.length() + 1;
        long natural = 350L + chars * 70L;
        long available = nextStart > start ? nextStart - start : natural;
        long dur = Math.max(300, Math.min(available - Math.min(250, available / 8), natural));
        long t = start;
        for (String s : split) {
            long w = dur * (s.length() + 1) / chars;
            out.add(new Lyrics.Word(s, t, t + w));
            t += w;
        }
        return new ParsedWords(out, false);
    }

    private static long toMs(String min, String sec, String frac) {
        long ms = Long.parseLong(min) * 60_000L + Long.parseLong(sec) * 1000L;
        if (frac != null) {
            ms += switch (frac.length()) {
                case 1 -> Long.parseLong(frac) * 100;
                case 2 -> Long.parseLong(frac) * 10;
                default -> Long.parseLong(frac.substring(0, 3));
            };
        }
        return ms;
    }

    private static long parseClock(String value) {
        Matcher m = Pattern.compile("(\\d+):(\\d{1,2})(?:[.:](\\d{1,3}))?").matcher(value);
        if (m.find()) return toMs(m.group(1), m.group(2), m.group(3));
        return 0;
    }

    /** Formats milliseconds as an LRC time tag body, e.g. {@code 01:02.50}. */
    public static String clock(long ms) {
        long cs = Math.max(0, ms) / 10;
        return String.format(Locale.ROOT, "%02d:%02d.%02d", cs / 6000, (cs / 100) % 60, cs % 100);
    }
}
