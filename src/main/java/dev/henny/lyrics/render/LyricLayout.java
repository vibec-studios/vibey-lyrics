package dev.henny.lyrics.render;

import dev.henny.lyrics.Compat;
import dev.henny.lyrics.LyricsConfig;
import dev.henny.lyrics.lyrics.Lyrics;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Works out what the lyrics look like at one point in time, independent of where they are drawn: a list of words
 * with position, scale, color and glow in "font pixels" around (0, 0). The HUD and the world renderer only place
 * and scale this frame.
 */
public final class LyricLayout {
    private static final long ENTER = 280;
    private static final long EXIT = 320;
    private static final float ROW = 10f;

    private LyricLayout() {}

    /** One word (or the title) to draw, centered on x. */
    public record Run(Component text, float x, float y, float scale, int color, int glow, float glowAlpha) {}

    /** Caption box behind a row. */
    public record Box(float x1, float y1, float x2, float y2, int color) {}

    public record Frame(List<Run> runs, List<Box> boxes, float width, float height) {
        public boolean isEmpty() {
            return runs.isEmpty();
        }
    }

    private static final Frame EMPTY = new Frame(List.of(), List.of(), 0, 0);

    public static Frame build(Font font, Lyrics lyrics, String title, String artist, long t, float maxWidth) {
        LyricsConfig cfg = LyricsConfig.get();
        Builder b = new Builder(font, cfg, maxWidth);
        if (lyrics == null || lyrics.lines().isEmpty()) {
            b.title(title, artist, 1f, 0);
            return b.finish();
        }
        List<Lyrics.Line> lines = lyrics.lines();
        int idx = lyrics.lineAt(t);
        Lyrics.Line first = firstSung(lines);

        if (idx < 0 || (first != null && t < first.start() && lines.get(Math.max(0, idx)).isBreak())) {
            // Intro: title card until the first line, fading out just before it.
            long until = first == null ? 4000 : first.start();
            float a = 1f;
            if (t > until - 600) a = clamp((until - t) / 600f);
            a *= clamp(t / 400f);
            if (a > 0.01f) b.title(title, artist, a, 0);
            if (first != null && cfg.showNext && until - t < 1600) {
                b.preview(first, clamp(1f - (until - t) / 1600f), 26);
            }
            return b.finish();
        }

        Lyrics.Line cur = lines.get(idx);
        Lyrics.Line prev = idx > 0 ? lines.get(idx - 1) : null;
        long sinceStart = t - cur.start();

        // The previous line animates out while the new one comes in.
        if (prev != null && !prev.isBreak() && sinceStart < EXIT) {
            b.line(prev, t, 1f, clamp(sinceStart / (float) EXIT), 0, true);
        }
        if (cur.isBreak()) {
            Lyrics.Line next = idx + 1 < lines.size() ? lines.get(idx + 1) : null;
            long gap = next == null ? Long.MAX_VALUE : next.start() - cur.start();
            if (gap > 4000 && sinceStart > 600) b.breakNotes(t, clamp((sinceStart - 600) / 500f) * clamp((next == null ? 3000 : next.start() - t) / 500f));
            if (next != null && cfg.showNext && next.start() - t < 1600) {
                b.preview(next, clamp(1f - (next.start() - t) / 1600f), 0);
            }
            return b.finish();
        }
        // Long silence after a line: let it fade away instead of hanging there.
        Lyrics.Line next = idx + 1 < lines.size() ? lines.get(idx + 1) : null;
        float fade = 1f;
        long hold = cur.end() + 2500;
        if (next == null || next.start() > hold) {
            if (t > hold) fade = clamp(1f - (t - hold) / 600f);
        }
        if (fade <= 0.01f) return b.finish();
        float enter = clamp(sinceStart / (float) ENTER);
        float y = b.line(cur, t, enter, 0f, fade, false);
        if (next != null && !next.isBreak() && cfg.showNext && cfg.reveal == LyricsConfig.Reveal.LINE) {
            b.preview(next, enter * fade, y);
        }
        return b.finish();
    }

    private static Lyrics.Line firstSung(List<Lyrics.Line> lines) {
        for (Lyrics.Line l : lines) if (!l.isBreak()) return l;
        return null;
    }

    // ================================================================================================ builder

    private static final class Builder {
        final Font font;
        final LyricsConfig cfg;
        final float maxWidth;
        final List<Run> runs = new ArrayList<>();
        final List<Box> boxes = new ArrayList<>();
        final Style style;
        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;

        Builder(Font font, LyricsConfig cfg, float maxWidth) {
            this.font = font;
            this.cfg = cfg;
            this.maxWidth = maxWidth;
            this.style = Compat.font(Style.EMPTY.withBold(cfg.bold), cfg.font);
        }

        Component word(String s) {
            if (cfg.uppercase) s = s.toUpperCase(Locale.ROOT);
            return Component.literal(s).withStyle(style);
        }

        /** Gap between words; some fonts (Monoton) have a hair-thin space, so never less than half an "n". */
        float spaceWidth() {
            return Math.max(font.width(word(" ")), font.width(word("n")) * 0.6f);
        }

        /** Lays a line out in rows, adds its words; returns the y below it. */
        float line(Lyrics.Line line, long t, float enter, float exit, float fade, boolean leaving) {
            float lineScale = 1f;
            float alpha = fade;
            float dy = 0;
            float e = leaving ? 1f : enter;
            switch (cfg.anim) {
                case POP -> {
                    lineScale = 0.72f + 0.28f * backOut(e);
                    alpha *= clamp(e * 1.6f);
                    if (leaving) lineScale *= 1f + 0.12f * exit;
                }
                case FADE -> alpha *= e;
                case SLIDE, WAVE -> {
                    dy = 16f * (1f - cubicOut(e));
                    alpha *= e;
                    if (leaving) dy -= 16f * cubicOut(exit);
                }
                case ZOOM -> {
                    // Flies in from far away, shrinks away when it leaves.
                    lineScale = 1f + 1.4f * (1f - cubicOut(e));
                    alpha *= cubicOut(e);
                    if (leaving) lineScale *= 1f - 0.35f * cubicOut(exit);
                }
                case GLITCH -> {
                    if (e < 1f) alpha *= (hash(line.start(), t / 40) > 0.35f ? 1f : 0.25f) * e;
                    if (leaving) alpha *= hash(line.start(), t / 35) > 0.5f ? 1f : 0.3f;
                }
                case BOUNCE, TYPE, RAINBOW -> alpha *= clamp(e * 2f);
                case NONE -> {
                }
            }
            if (leaving) alpha *= 1f - exit;
            if (alpha <= 0.01f) return 0;
            float spacing = ROW * (1f + cfg.lineSpacing / 100f);

            List<List<Integer>> rows = wrap(line);
            float space = spaceWidth();
            float total = rows.size() * spacing;
            float top = -total / 2f + dy;
            if (leaving) top = -total / 2f + dy;
            for (int r = 0; r < rows.size(); r++) {
                List<Integer> row = rows.get(r);
                float w = -space;
                for (int i : row) w += font.width(word(line.words().get(i).text())) + space;
                float x = -w / 2f;
                float y = top + r * spacing;
                float rowW = w * lineScale;
                if (cfg.box) {
                    int a = Math.round(0x99 * alpha);
                    float pad = 4f * lineScale;
                    boxes.add(new Box(-rowW / 2f - pad, y + (ROW - ROW * lineScale) / 2f - pad * 0.6f,
                            rowW / 2f + pad, y + (ROW + ROW * lineScale) / 2f + pad * 0.4f, a << 24));
                }
                for (int i : row) {
                    Lyrics.Word word = line.words().get(i);
                    Component c = word(word.text());
                    float ww = font.width(c);
                    float cx = (x + ww / 2f) * lineScale;
                    x += ww + space;
                    addWord(c, word, i, t, cx, y, lineScale, alpha, leaving);
                    extend(cx - ww * lineScale / 2f, y, cx + ww * lineScale / 2f, y + ROW);
                }
            }
            return top + total + 4f;
        }

        void addWord(Component c, Lyrics.Word w, int index, long t, float cx, float y, float lineScale, float alpha, boolean leaving) {
            float since = t - w.start();
            float dur = Math.max(80, w.end() - w.start());
            boolean sung = since >= 0 || leaving;
            float lit = leaving ? 1f : clamp(since / 120f);
            float active = !leaving && since >= 0 && since < dur + 120 ? (float) Math.sin(Math.PI * clamp(since / 260f)) : 0f;
            float scale = lineScale * (1f + 0.10f * active);
            float a = alpha;
            float dx = 0;
            float dy = 0;
            int color;
            int glowColor = cfg.glowColor;
            boolean typing = cfg.anim == LyricsConfig.Anim.TYPE;
            if (cfg.reveal == LyricsConfig.Reveal.WORD || typing) {
                if (!sung) return;
                float in = clamp(since / 180f);
                if (!leaving && !typing) {
                    a *= in;
                    scale *= 0.65f + 0.35f * backOut(in);
                }
                color = cfg.reveal == LyricsConfig.Reveal.WORD ? mix(cfg.highlightColor, cfg.textColor, lit)
                        : mix(withAlpha(cfg.textColor, 0.74f), cfg.highlightColor, lit);
            } else {
                // Karaoke: words light up in the highlight color the moment they are sung.
                color = mix(withAlpha(cfg.textColor, 0.74f), cfg.highlightColor, lit);
            }

            switch (cfg.anim) {
                case WAVE -> dy = (float) Math.sin(t / 320.0 + index * 0.8) * 2.2f;
                case BOUNCE -> {
                    // A sung word hops and lands with two little bounces.
                    if (sung && !leaving && since < 700) {
                        float d = since / 1000f;
                        dy = -7f * (float) Math.exp(-6.5 * d) * Math.abs((float) Math.cos(11.0 * d));
                    }
                }
                case TYPE -> {
                    // Letters appear one after another while the word is sung.
                    if (!leaving && since < dur) {
                        String text = c.getString();
                        int n = Math.max(1, Math.min(text.length(), (int) Math.ceil(text.length() * (since + 40) / dur)));
                        if (n < text.length()) {
                            float full = font.width(c);
                            c = Component.literal(text.substring(0, n)).withStyle(c.getStyle());
                            float part = font.width(c);
                            dx = -(full - part) / 2f * scale;
                        }
                    }
                }
                case GLITCH -> {
                    if (!leaving && since >= 0 && since < 260) {
                        float p = 1f - since / 260f;
                        dx = (hash(w.start(), t / 30) - 0.5f) * 3f * p;
                        float split = 1.6f * p;
                        float yy = y + (ROW - ROW * scale) / 2f;
                        runs.add(new Run(c, cx + dx - split, yy, scale, withAlpha(0xFFFF2A6D, 0.7f * a * p), 0, 0f));
                        runs.add(new Run(c, cx + dx + split, yy, scale, withAlpha(0xFF05D9E8, 0.7f * a * p), 0, 0f));
                    }
                }
                case RAINBOW -> {
                    if (sung) {
                        float hue = (t / 2400f + index * 0.09f) % 1f;
                        int rgb = hsv(hue, 0.6f, 1f);
                        color = mix(color, (color & 0xFF000000) | (rgb & 0xFFFFFF), lit);
                        glowColor = hsv((hue + 0.08f) % 1f, 0.9f, 0.8f);
                    }
                }
                default -> {
                }
            }
            if (a <= 0.01f) return;
            float glow = cfg.glowStrength / 100f * (0.7f + 0.3f * lit + 0.4f * active);
            float wy = y + dy + (ROW - ROW * scale) / 2f;
            runs.add(new Run(c, cx + dx, wy, scale, withAlpha(color, a), glowColor, glow * a));
            if (cfg.sparkles && sung && !leaving && since < 650) sparkles(w, since, cx, wy + ROW * scale / 2f, scale, a);
        }

        /** Three little stars flying off a word that was just sung. Stateless: positions come from the word's time. */
        void sparkles(Lyrics.Word w, float since, float cx, float cy, float scale, float alpha) {
            float p = since / 650f;
            float fade = (1f - p) * clamp(since / 60f) * alpha;
            if (fade <= 0.02f) return;
            String[] glyphs = {"✦", "✧", "⋆"};
            for (int k = 0; k < 3; k++) {
                float angle = (float) (hash(w.start() + k * 7919L, k) * Math.PI * 2);
                float dist = (5f + 9f * cubicOut(p)) * scale;
                float sx = cx + (float) Math.cos(angle) * dist * 1.6f;
                float sy = cy + (float) Math.sin(angle) * dist - 4f * p;
                Component c = Component.literal(glyphs[k]);
                float s = 0.55f * scale * (1f - 0.4f * p);
                runs.add(new Run(c, sx, sy - ROW * s / 2f, s, withAlpha(cfg.highlightColor | 0xFF000000, fade), cfg.glowColor, 0.5f * fade));
            }
        }

        /** Next line, small and faint, under the current one. */
        void preview(Lyrics.Line line, float alpha, float y) {
            if (alpha <= 0.01f) return;
            List<List<Integer>> rows = wrap(line);
            List<Integer> row = rows.get(0);
            StringBuilder sb = new StringBuilder();
            for (int i : row) {
                if (!sb.isEmpty()) sb.append(' ');
                sb.append(line.words().get(i).text());
            }
            if (rows.size() > 1) sb.append(" …");
            Component c = word(sb.toString());
            float s = 0.62f;
            runs.add(new Run(c, 0, y + 3, s, withAlpha(cfg.textColor, 0.42f * alpha), cfg.glowColor, 0f));
            float w = font.width(c) * s;
            extend(-w / 2, y + 3, w / 2, y + 3 + ROW * s);
        }

        void title(String title, String artist, float alpha, float y) {
            if (title == null || title.isBlank()) return;
            Component t = word(title);
            float s = 1.25f;
            runs.add(new Run(t, 0, y - ROW * s, s, withAlpha(cfg.highlightColor, alpha), cfg.glowColor, cfg.glowStrength / 100f * alpha));
            float w = font.width(t) * s;
            extend(-w / 2, y - ROW * s, w / 2, y);
            if (artist != null && !artist.isBlank()) {
                Component a = word(artist);
                float sa = 0.75f;
                runs.add(new Run(a, 0, y + 4, sa, withAlpha(cfg.textColor, 0.8f * alpha), cfg.glowColor, 0.5f * cfg.glowStrength / 100f * alpha));
                float wa = font.width(a) * sa;
                extend(-wa / 2, y + 4, wa / 2, y + 4 + ROW * sa);
            }
        }

        void breakNotes(long t, float alpha) {
            if (alpha <= 0.01f) return;
            String[] notes = {"♪", "♫", "♪"};
            for (int i = 0; i < 3; i++) {
                float bounce = (float) Math.sin(t / 260.0 + i * 1.3) * 2.5f;
                Component c = word(notes[i]);
                runs.add(new Run(c, (i - 1) * 14f, -ROW / 2f + bounce, 1.1f, withAlpha(cfg.highlightColor, 0.85f * alpha), cfg.glowColor, cfg.glowStrength / 100f * alpha));
            }
            extend(-24, -ROW, 24, ROW);
        }

        /** Greedy word wrap at {@link #maxWidth}. */
        List<List<Integer>> wrap(Lyrics.Line line) {
            List<List<Integer>> rows = new ArrayList<>();
            List<Integer> row = new ArrayList<>();
            float space = spaceWidth();
            float w = 0;
            for (int i = 0; i < line.words().size(); i++) {
                float ww = font.width(word(line.words().get(i).text()));
                if (!row.isEmpty() && w + space + ww > maxWidth) {
                    rows.add(row);
                    row = new ArrayList<>();
                    w = 0;
                }
                w += (row.isEmpty() ? 0 : space) + ww;
                row.add(i);
            }
            if (!row.isEmpty()) rows.add(row);
            if (rows.isEmpty()) rows.add(List.of());
            return rows;
        }

        void extend(float x1, float y1, float x2, float y2) {
            minX = Math.min(minX, x1);
            maxX = Math.max(maxX, x2);
            minY = Math.min(minY, y1);
            maxY = Math.max(maxY, y2);
        }

        Frame finish() {
            if (runs.isEmpty()) return EMPTY;
            return new Frame(List.copyOf(runs), List.copyOf(boxes), maxX - minX, maxY - minY);
        }
    }

    // ================================================================================================ math

    static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    static float cubicOut(float t) {
        float u = 1f - t;
        return 1f - u * u * u;
    }

    static float backOut(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        float u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }

    /** Deterministic pseudo random number in [0, 1) for two inputs. */
    static float hash(long a, long b) {
        long h = a * 0x9E3779B97F4A7C15L + b * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return (h >>> 40) / (float) (1L << 24);
    }

    static int hsv(float h, float s, float v) {
        float c = v * s;
        float hp = (h * 6f) % 6f;
        float x = c * (1 - Math.abs(hp % 2 - 1));
        float r = 0, g = 0, b = 0;
        if (hp < 1) { r = c; g = x; } else if (hp < 2) { r = x; g = c; } else if (hp < 3) { g = c; b = x; }
        else if (hp < 4) { g = x; b = c; } else if (hp < 5) { r = x; b = c; } else { r = c; b = x; }
        float m = v - c;
        return 0xFF000000 | (Math.round((r + m) * 255) << 16) | (Math.round((g + m) * 255) << 8) | Math.round((b + m) * 255);
    }

    public static int withAlpha(int argb, float a) {
        int base = argb >>> 24;
        int na = Math.max(0, Math.min(255, Math.round(base * a)));
        return (na << 24) | (argb & 0xFFFFFF);
    }

    public static int mix(int a, int b, float t) {
        t = clamp(t);
        int r = 0;
        for (int s = 0; s <= 24; s += 8) {
            int ca = (a >>> s) & 0xFF;
            int cb = (b >>> s) & 0xFF;
            r |= (Math.round(ca + (cb - ca) * t) & 0xFF) << s;
        }
        return r;
    }
}
