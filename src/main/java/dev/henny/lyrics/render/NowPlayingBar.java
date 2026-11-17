package dev.henny.lyrics.render;

import dev.henny.lyrics.Compat;
import dev.henny.lyrics.LyricsConfig;
import dev.henny.lyrics.Playback;
import dev.henny.lyrics.client.ui.Gfx;
import dev.henny.lyrics.library.Song;
import dev.henny.lyrics.lyrics.Lyrics;
import dev.henny.lyrics.sky.SkyLook;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The small bar at the top of the screen: a note, the line that is sung right now (words light up as they are sung),
 * a little equalizer and the song's progress.
 */
public final class NowPlayingBar {
    private static final int H = 17;
    private static final int BG = 0xD8121218;
    private static final int EDGE = 0x30FFFFFF;
    private static final int ACCENT = 0xFFD946EF;
    private static final int DIM = 0xFF8E8E9E;

    private NowPlayingBar() {}

    public static void render(Gfx g) {
        LyricsConfig cfg = LyricsConfig.get();
        Song song = Playback.song();
        if (!cfg.pill || song == null || Compat.hudHidden()) return;
        if (cfg.position == LyricsConfig.Position.HUD && cfg.hudPlace == LyricsConfig.HudPlace.TOP) return;
        int sw = g.screenWidth();
        int high = cfg.matchSky ? SkyLook.lyricColors()[1] : cfg.highlightColor;
        high |= 0xFF000000;

        long t = Playback.lyricTimeMs();
        Lyrics lyrics = Playback.lyrics();
        Lyrics.Line line = null;
        if (lyrics != null && !lyrics.lines().isEmpty()) {
            int idx = lyrics.lineAt(t);
            if (idx >= 0 && !lyrics.lines().get(idx).isBreak() && t < lyrics.lines().get(idx).end() + 2500) line = lyrics.lines().get(idx);
        }

        int maxW = Math.min(250, sw - 40);
        int textW;
        String fallback = null;
        if (line != null) {
            textW = g.width(lineText(line.words()));
        } else {
            fallback = song.artist().isBlank() ? song.title() : song.title() + " · " + song.artist();
            textW = g.width(fallback);
        }
        int w = Math.max(130, Math.min(maxW, textW + 52));
        int x = (sw - w) / 2;
        int y = 4;

        // pill: a box with its corners cut off
        g.fill(x + 2, y, x + w - 2, y + H, BG);
        g.fill(x, y + 2, x + 2, y + H - 2, BG);
        g.fill(x + w - 2, y + 2, x + w, y + H - 2, BG);
        g.fill(x + 1, y + 1, x + 2, y + 2, BG);
        g.fill(x + w - 2, y + 1, x + w - 1, y + 2, BG);
        g.fill(x + 1, y + H - 2, x + 2, y + H - 1, BG);
        g.fill(x + w - 2, y + H - 2, x + w - 1, y + H - 1, BG);
        g.fill(x + 2, y, x + w - 2, y + 1, EDGE);

        // note in a little disc
        g.fill(x + 5, y + 3, x + 16, y + 14, 0xFF2A1F33);
        g.text(Component.literal("♪"), x + 7, y + 5, ACCENT);

        // equalizer
        boolean playing = Playback.isPlaying();
        long now = System.currentTimeMillis();
        int ex = x + w - 18;
        for (int i = 0; i < 4; i++) {
            float k = playing ? (float) Math.abs(Math.sin(now / 160.0 + i * 1.7) * Math.cos(now / 410.0 + i)) : 0.15f;
            int bh = 2 + Math.round(7 * k);
            g.fill(ex + i * 3, y + 12 - bh, ex + i * 3 + 2, y + 12, i % 2 == 0 ? ACCENT : 0xFFB98CF5);
        }

        // the line, scrolled so the word being sung stays in view
        int tx = x + 21;
        int avail = ex - 5 - tx;
        g.scissor(tx, y, tx + avail, y + H);
        if (line == null) {
            g.text(g.clip(fallback, avail), tx, y + 5, 0xFFE8E8F0);
        } else {
            List<Lyrics.Word> words = line.words();
            int curEnd = 0;
            int pos = 0;
            for (Lyrics.Word word : words) {
                pos += g.width(word.text() + " ");
                if (t >= word.start()) curEnd = pos;
            }
            int shift = Math.max(0, Math.min(textW - avail, curEnd - avail + 8));
            if (textW <= avail) shift = -(avail - textW) / 2;
            int wx = tx - shift;
            for (Lyrics.Word word : words) {
                int color;
                if (t < word.start()) color = DIM;
                else if (t < word.end()) color = high;
                else color = 0xFFFFFFFF;
                g.text(Component.literal(word.text()), wx, y + 5, color);
                wx += g.width(word.text() + " ");
            }
        }
        g.endScissor();

        // progress
        long dur = Playback.durationMs();
        if (dur > 0) {
            int pw = Math.round((w - 8) * Math.min(1f, Playback.positionMs() / (float) dur));
            g.fill(x + 4, y + H - 2, x + w - 4, y + H - 1, 0x30FFFFFF);
            g.fill(x + 4, y + H - 2, x + 4 + pw, y + H - 1, ACCENT);
        }
    }

    private static String lineText(List<Lyrics.Word> words) {
        StringBuilder sb = new StringBuilder();
        for (Lyrics.Word w : words) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(w.text());
        }
        return sb.toString();
    }
}
