package dev.henny.lyrics.client;

import dev.henny.lyrics.Compat;
import dev.henny.lyrics.LyricsConfig;
import dev.henny.lyrics.Playback;
import dev.henny.lyrics.VibeyLyrics;
import dev.henny.lyrics.client.ui.CardScreen;
import dev.henny.lyrics.client.ui.Gfx;
import dev.henny.lyrics.library.Song;
import dev.henny.lyrics.library.SongLibrary;
import dev.henny.lyrics.lyrics.LrcLib;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Finds synced lyrics on LRCLIB and saves them as .lrc into the song folder. Opened for a song without lyrics, the
 * file gets that song's name so it pairs with the audio file right away.
 */
public final class SearchScreen extends CardScreen {
    private final Song target;
    private EditBox query;
    private List<LrcLib.Result> results;
    private boolean searching;
    private String error;
    private String saved;
    private int requestId;

    public SearchScreen(Screen parent, Song target) {
        super(Component.translatable("vibeylyrics.search"), parent);
        this.target = target;
    }

    @Override
    protected void init() {
        super.init();
        if (query == null) {
            query = new EditBox(font, 0, 0, 200, 12, Component.translatable("vibeylyrics.search"));
            query.setMaxLength(120);
            query.setHint(Component.translatable("vibeylyrics.search.hint"));
            if (target != null) query.setValue(target.label().replace(" – ", " "));
        }
        input(query, this::search);
        setInitialFocus(query);
        if (target != null && results == null && !searching && LyricsConfig.get().onlineLookup) search();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected int cardWidth() {
        return 320;
    }

    @Override
    protected int headerHeight() {
        return 34;
    }

    @Override
    protected int footerHeight() {
        return 38;
    }

    @Override
    protected int fixedHeight() {
        return 330;
    }

    @Override
    protected void drawHeader(Gfx g, int x, int y, int w) {
        title(g, I18n.get("vibeylyrics.search"), x + PAD, y + 13);
        closeButton(g, x, y, w);
    }

    private void search() {
        if (!LyricsConfig.get().onlineLookup) {
            error = I18n.get("vibeylyrics.search.off");
            return;
        }
        String q = query.getValue().strip();
        if (q.isEmpty()) return;
        int id = ++requestId;
        searching = true;
        error = null;
        saved = null;
        LrcLib.search(q).whenComplete((list, ex) -> Minecraft.getInstance().execute(() -> {
            if (id != requestId) return;
            searching = false;
            if (ex != null) {
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                error = I18n.get("vibeylyrics.search.failed", cause.getMessage() == null ? cause.toString() : cause.getMessage());
                results = null;
            } else {
                results = list;
                scroll = 0;
            }
        }));
    }

    private void pick(LrcLib.Result r) {
        String base = target != null ? target.baseName() : (r.artist().isBlank() ? r.track() : r.artist() + " - " + r.track());
        try {
            SongLibrary.saveLrc(base, r.toLrc());
            SongLibrary.rescan();
            saved = I18n.get("vibeylyrics.search.saved", base + ".lrc");
            Song song = SongLibrary.find(base.toLowerCase(java.util.Locale.ROOT));
            if (song != null && target != null) {
                Playback.play(song);
            }
        } catch (Exception e) {
            error = e.getMessage();
            VibeyLyrics.LOG.warn("Could not save lyrics", e);
        }
    }

    @Override
    protected int drawBody(Gfx g, int x, int y, int w) {
        int bw = g.width(I18n.get("vibeylyrics.search.go")) + 16;
        inputBox(g, query, x, y, w - bw - 4);
        button(g, x + w - bw, y, bw, 16, I18n.get("vibeylyrics.search.go"), searching ? Style.DISABLED : Style.PRIMARY, this::search);
        y += 22;
        g.text(g.clip(I18n.get("vibeylyrics.search.source"), w), x, y, DIM);
        y += 14;

        if (saved != null) {
            y = note(g, x, y, w, saved, GREEN) + 6;
        }
        if (searching) return note(g, x, y, w, I18n.get("vibeylyrics.search.busy") + dots(), MUTED);
        if (error != null) {
            for (String l : wrap(g, error, w)) {
                g.text(l, x, y, RED);
                y += 10;
            }
            return y;
        }
        if (results == null) return note(g, x, y, w, I18n.get("vibeylyrics.search.intro"), MUTED);
        if (results.isEmpty()) return note(g, x, y, w, I18n.get("vibeylyrics.search.none"), MUTED);
        for (LrcLib.Result r : results) {
            int h = 26;
            boolean ok = r.hasSynced();
            boolean hov = ok && hover(x, y, w, h);
            g.rect(x, y, w, h, hov ? SURFACE_HOVER : SURFACE);
            String badge = ok ? I18n.get("vibeylyrics.search.synced") : I18n.get(r.instrumental() ? "vibeylyrics.search.instrumental" : "vibeylyrics.search.plain");
            int bwid = g.width(badge) + 8;
            g.rect(x + w - 6 - bwid, y + 7, bwid, 12, SURFACE_2);
            g.textCenter(badge, x + w - 6 - bwid / 2, y + 9, ok ? GREEN : DIM);
            int avail = w - bwid - 22;
            g.text(g.clip(r.track(), avail), x + 8, y + 4, ok ? TEXT : MUTED);
            String sub = r.artist() + (r.album().isBlank() ? "" : " · " + r.album()) + (r.durationMs() > 0 ? " · " + LyricsScreen.clock(r.durationMs()) : "");
            g.text(g.clip(sub, avail), x + 8, y + 15, DIM);
            if (ok) hit(x, y, w, h, () -> pick(r));
            y += h + 2;
        }
        return y;
    }

    @Override
    protected void drawFooter(Gfx g, int x, int bottom, int w) {
        int y = bottom + 8;
        button(g, x + PAD, y, w - PAD * 2, 20, I18n.get("vibeylyrics.back"), Style.SECONDARY, () -> Compat.openScreen(parent));
    }
}
