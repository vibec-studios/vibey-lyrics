package dev.henny.lyrics.client;

import dev.henny.lyrics.Compat;
import dev.henny.lyrics.LyricsConfig;
import dev.henny.lyrics.Playback;
import dev.henny.lyrics.VibeyLyrics;
import dev.henny.lyrics.client.ui.CardScreen;
import dev.henny.lyrics.client.ui.Gfx;
import dev.henny.lyrics.library.Song;
import dev.henny.lyrics.library.SongLibrary;
import dev.henny.lyrics.pc.NowPlaying;
import dev.henny.lyrics.pc.PcLyrics;
import dev.henny.lyrics.render.LyricLayout;
import dev.henny.lyrics.sky.SkyTheme;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;

/**
 * The main menu, kept small: what is playing, the song list, where the lyrics show up and one of a few looks.
 * Fonts, colors, sizes and sync live in {@link AdvancedScreen}. In a world the card sits on the left so the lyrics
 * stay visible while you change things.
 */
public final class LyricsScreen extends CardScreen {
    public LyricsScreen(Screen parent) {
        super(Component.translatable("vibeylyrics.title"), parent);
        SongLibrary.rescan();
    }

    @Override
    public void removed() {
        LyricsConfig.get().save();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private boolean inWorld() {
        return minecraft != null && minecraft.level != null;
    }

    @Override
    protected int dimColor() {
        return inWorld() ? 0x40000000 : DIM_BG;
    }

    @Override
    protected int cardX(int cardWidth) {
        return inWorld() && width >= cardWidth * 2 ? 16 : super.cardX(cardWidth);
    }

    @Override
    protected int cardWidth() {
        return 300;
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
    protected void drawHeader(Gfx g, int x, int y, int w) {
        g.text(Component.literal("♪ ").withStyle(ChatFormatting.LIGHT_PURPLE), x + PAD, y + 13, ACCENT);
        title(g, I18n.get("vibeylyrics.title"), x + PAD + 10, y + 13);
        closeButton(g, x, y, w);
    }

    @Override
    protected int drawBody(Gfx g, int x, int y, int w) {
        y = nowPlaying(g, x, y, w);
        y += 12;
        y = songs(g, x, y, w);
        y += 12;

        y = section(g, I18n.get("vibeylyrics.position"), x, y);
        LyricsConfig cfg = LyricsConfig.get();
        LyricsConfig.Position[] positions = LyricsConfig.Position.values();
        String[] labels = new String[positions.length];
        for (int i = 0; i < positions.length; i++) labels[i] = I18n.get("vibeylyrics.position." + positions[i].name().toLowerCase(Locale.ROOT));
        y = segmented(g, x, y, w, labels, cfg.position.ordinal(), i -> {
            cfg.position = positions[i];
            if (cfg.position == LyricsConfig.Position.WORLD && !cfg.anchorSet) placeHere();
            cfg.save();
        });
        String hint = I18n.get("vibeylyrics.position." + cfg.position.name().toLowerCase(Locale.ROOT) + ".hint");
        y += 5;
        if (cfg.position == LyricsConfig.Position.WORLD || cfg.position == LyricsConfig.Position.SKY) {
            String label = I18n.get(cfg.position == LyricsConfig.Position.WORLD ? "vibeylyrics.place" : "vibeylyrics.aim");
            int bw = g.width(label) + 14;
            boolean can = inWorld();
            button(g, x + w - bw, y, bw, 16, label, can ? Style.SECONDARY : Style.DISABLED, () -> {
                if (cfg.position == LyricsConfig.Position.WORLD) placeHere();
                else aimSky();
            });
            g.text(g.clip(hint, w - bw - 8), x, y + 4, DIM);
            y += 16;
        } else {
            g.text(g.clip(hint, w), x, y, DIM);
            y += 10;
        }
        y += 10;

        y = section(g, I18n.get("vibeylyrics.style"), x, y);
        LyricsConfig.Preset[] presets = LyricsConfig.Preset.values();
        String[] names = new String[presets.length];
        for (int i = 0; i < presets.length; i++) names[i] = I18n.get("vibeylyrics.preset." + presets[i].name().toLowerCase(Locale.ROOT));
        for (int row = 0; row * 4 < presets.length; row++) {
            int from = row * 4;
            int n = Math.min(4, presets.length - from);
            String[] part = java.util.Arrays.copyOfRange(names, from, from + n);
            int sel = cfg.preset.ordinal() - from;
            y = segmented(g, x, y, w, part, sel >= 0 && sel < n ? sel : -1, i -> {
                cfg.applyPreset(presets[from + i]);
                cfg.save();
            }) + 2;
        }
        y += 10;
        y = skies(g, x, y, w);
        return y + 2;
    }

    /** Custom skies: a grid of buttons, each with a little gradient of its colors. */
    private int skies(Gfx g, int x, int y, int w) {
        y = section(g, I18n.get("vibeylyrics.sky"), x, y);
        LyricsConfig cfg = LyricsConfig.get();
        SkyTheme[] skies = SkyTheme.values();
        int cols = 3;
        int gap = 2;
        int ch = 20;
        for (int i = 0; i < skies.length; i++) {
            SkyTheme s = skies[i];
            int cw = (w - gap * (cols - 1)) / cols;
            int cx = x + (i % cols) * (cw + gap);
            int cy = y + (i / cols) * (ch + gap);
            if (i % cols == cols - 1) cw = x + w - cx;
            boolean sel = s == SkyTheme.of(cfg.sky);
            boolean hov = hover(cx, cy, cw, ch);
            g.rect(cx, cy, cw, ch, sel ? 0xFF2A1F33 : (hov ? SURFACE_HOVER : SURFACE));
            int top = s == SkyTheme.VANILLA ? 0x6E9BEF : s.high;
            int bottom = s == SkyTheme.VANILLA ? 0xC4DCFF : s.horizon;
            int sh = ch - 6;
            for (int k = 0; k < sh; k++) {
                g.rect(cx + 3, cy + 3 + k, 7, 1, LyricLayout.mix(0xFF000000 | top, 0xFF000000 | bottom, k / (float) (sh - 1)));
            }
            if (sel) g.outline(cx, cy, cw, ch, ACCENT);
            String name = I18n.get("vibeylyrics.sky." + s.id());
            g.text(g.clip(name, cw - 16), cx + 13, cy + 6, sel ? TEXT : MUTED);
            hit(cx, cy, cw, ch, () -> {
                cfg.sky = s.id();
                cfg.save();
            });
        }
        y += ((skies.length + cols - 1) / cols) * (ch + gap) + 3;
        g.text(g.clip(I18n.get("vibeylyrics.sky.hint"), w), x, y, DIM);
        return y + 10;
    }

    private int nowPlaying(Gfx g, int x, int y, int w) {
        Song song = Playback.song();
        int h = 54;
        g.rect(x, y, w, h, SURFACE);
        int bs = 26;
        int bx = x + w - 8 - bs;
        int by = y + 8;

        // Play / pause
        boolean hov = hover(bx, by, bs, bs);
        g.rect(bx, by, bs, bs, hov ? ACCENT_HOVER : ACCENT);
        if (Playback.isPlaying()) {
            g.rect(bx + 9, by + 8, 3, 10, 0xFFFFFFFF);
            g.rect(bx + 14, by + 8, 3, 10, 0xFFFFFFFF);
        } else {
            for (int i = 0; i < 6; i++) g.rect(bx + 10 + i, by + 7 + i, 1, 12 - i * 2, 0xFFFFFFFF);
        }
        hit(bx, by, bs, bs, Playback::toggle);

        // Restart
        int rx = bx - 4 - 20;
        boolean pc = Playback.isPc();
        if (song != null && !pc) {
            boolean rh = hover(rx, by + 3, 20, 20);
            g.rect(rx, by + 3, 20, 20, rh ? SURFACE_2 : 0xFF25252B);
            g.rect(rx + 6, by + 9, 2, 8, rh ? TEXT : MUTED);
            for (int i = 0; i < 4; i++) g.rect(rx + 9 + i, by + 13 - i, 1, 1 + i * 2, rh ? TEXT : MUTED);
            hit(rx, by + 3, 20, 20, Playback::restart);
        }

        int textW = (song != null && !pc ? rx : bx) - x - 18;
        if (pc) {
            NowPlaying.Track t = NowPlaying.track();
            String head = t == null ? I18n.get("vibeylyrics.pc.idle") : t.title();
            g.text(Component.literal(g.clip(head, textW - 8)).withStyle(ChatFormatting.BOLD), x + 10, y + 9, t == null ? MUTED : TEXT);
            String sub;
            int color = MUTED;
            if (NowPlaying.state() == NowPlaying.State.UNSUPPORTED) {
                sub = I18n.get("vibeylyrics.pc.unsupported." + osKey());
                color = AMBER;
            } else if (t == null) {
                sub = I18n.get(NowPlaying.state() == NowPlaying.State.STARTING ? "vibeylyrics.pc.starting" : "vibeylyrics.pc.idle.hint");
                color = DIM;
            } else {
                String app = t.appName().isEmpty() ? "" : t.appName() + " · ";
                switch (PcLyrics.status()) {
                    case LOADING -> sub = app + I18n.get("vibeylyrics.pc.loading") + dots();
                    case NOT_FOUND -> {
                        sub = app + I18n.get("vibeylyrics.pc.notfound");
                        color = AMBER;
                    }
                    case OFFLINE -> {
                        sub = app + I18n.get("vibeylyrics.pc.offline");
                        color = AMBER;
                    }
                    case ERROR -> {
                        sub = app + I18n.get("vibeylyrics.pc.error");
                        color = RED;
                    }
                    default -> sub = app + t.artist();
                }
            }
            g.text(g.clip(sub, textW), x + 10, y + 21, color);
        } else if (song == null) {
            Song next = SongLibrary.find(LyricsConfig.get().song);
            g.text(I18n.get("vibeylyrics.nothing"), x + 10, y + 10, MUTED);
            String sub = next != null ? I18n.get("vibeylyrics.resume", next.label()) : I18n.get("vibeylyrics.pick");
            g.text(g.clip(sub, textW), x + 10, y + 22, DIM);
        } else {
            g.text(Component.literal(g.clip(song.title(), textW - 8)).withStyle(ChatFormatting.BOLD), x + 10, y + 9, TEXT);
            String sub = song.artist().isBlank() ? "" : song.artist();
            if (!song.hasAudio()) sub = I18n.get("vibeylyrics.external");
            else if (!song.hasLyrics()) sub = I18n.get("vibeylyrics.nolyrics.playing");
            g.text(g.clip(sub, textW), x + 10, y + 21, song.hasLyrics() ? MUTED : AMBER);
        }

        // Progress
        long pos = Playback.positionMs();
        long dur = Playback.durationMs();
        int py = y + h - 12;
        String time = clock(pos) + " / " + clock(dur);
        int tw = g.width(time);
        int barW = w - 20 - tw - 8;
        bar(g, x + 10, py + 2, barW, 3, dur > 0 ? (float) pos / dur : 0f,
                song == null || dur <= 0 || pc ? null : f -> Playback.seek(Math.round(f * dur)));
        g.text(time, x + w - 10 - tw, py - 1, DIM);

        String err = Playback.error();
        if (err != null && song != null) {
            g.text(g.clip(I18n.get("vibeylyrics.error", err), w), x, y + h + 3, RED);
            return y + h + 12;
        }
        // Lyrics without audio: line them up with music that plays somewhere else.
        if ((song != null && !song.hasAudio()) || (pc && PcLyrics.lyrics() != null)) {
            int ny = y + h + 4;
            g.text(g.clip(I18n.get("vibeylyrics.nudge"), w - 120), x, ny + 4, DIM);
            int b2 = buttonRight(g, x + w, ny, 16, "+0.5s", Style.SECONDARY, () -> Playback.nudge(500));
            buttonRight(g, b2 - 4, ny, 16, "−0.5s", Style.SECONDARY, () -> Playback.nudge(-500));
            return ny + 16;
        }
        return y + h;
    }

    private int songs(Gfx g, int x, int y, int w) {
        y = section(g, I18n.get("vibeylyrics.songs"), x, y);
        {
            int h = 30;
            boolean on = Playback.isPc();
            boolean hov = hover(x, y, w, h);
            g.rect(x, y, w, h, on ? 0xFF2A1F33 : (hov ? SURFACE_HOVER : SURFACE));
            if (on) g.rect(x, y, 2, h, ACCENT);
            g.text(Component.literal("♫ " + I18n.get("vibeylyrics.pc")).withStyle(ChatFormatting.BOLD), x + 9, y + 5, TEXT);
            g.text(g.clip(I18n.get(on ? "vibeylyrics.pc.on" : "vibeylyrics.pc.desc"), w - 60), x + 9, y + 17, DIM);
            toggle(g, "pc", x + w - 8, y + h / 2, on, () -> Playback.setPc(!Playback.isPc()));
            hit(x, y, w - 40, h, () -> Playback.setPc(!Playback.isPc()));
            y += h + 2;
        }
        List<Song> list = SongLibrary.songs();
        if (list.isEmpty()) return note(g, x, y, w, I18n.get("vibeylyrics.songs.empty"), MUTED);
        Song current = Playback.song();
        for (Song s : list) {
            int h = 22;
            boolean cur = current != null && current.id().equals(s.id());
            boolean hov = hover(x, y, w, h);
            g.rect(x, y, w, h, cur ? 0xFF2A1F33 : (hov ? SURFACE_HOVER : SURFACE));
            if (cur) g.rect(x, y, 2, h, ACCENT);

            int right = x + w - 6;
            // Songs without lyrics get a direct way to find them.
            if (!s.hasLyrics()) {
                right = buttonRight(g, right, y + 3, 16, I18n.get("vibeylyrics.find"), Style.PRIMARY,
                        () -> Compat.openScreen(new SearchScreen(this, s))) - 6;
            } else {
                String badge = s.hasAudio() ? "♪" : I18n.get("vibeylyrics.badge.lrc");
                int bw = g.width(badge) + 8;
                g.rect(right - bw, y + 5, bw, 12, SURFACE_2);
                g.textCenter(badge, right - bw / 2, y + 7, s.hasAudio() ? MUTED : AMBER);
                right -= bw + 6;
            }
            int tx = x + 9;
            int avail = right - tx;
            String title = g.clip(s.title(), avail);
            g.text(title, tx, y + 7, cur ? TEXT : TEXT);
            int used = g.width(title) + 6;
            if (!s.artist().isBlank() && used < avail - 20) {
                g.text(g.clip(s.artist(), avail - used), tx + used, y + 7, DIM);
            }
            hit(x, y, right - x, h, () -> Playback.play(s));
            y += h + 2;
        }
        return y - 2;
    }

    @Override
    protected void drawFooter(Gfx g, int x, int bottom, int w) {
        int y = bottom + 8;
        int bx = x + PAD;
        int bw = w - PAD * 2;
        int third = (bw - 8) / 3;
        button(g, bx, y, third, 20, I18n.get("vibeylyrics.folder"), Style.SECONDARY,
                () -> Compat.openFolder(VibeyLyrics.songsDir()));
        button(g, bx + third + 4, y, third, 20, I18n.get("vibeylyrics.find"), Style.SECONDARY,
                () -> Compat.openScreen(new SearchScreen(this, null)));
        button(g, bx + (third + 4) * 2, y, bw - (third + 4) * 2, 20, I18n.get("vibeylyrics.advanced"), Style.SECONDARY,
                () -> Compat.openScreen(new AdvancedScreen(this)));
    }

    /** Pins the lyrics a few blocks in front of you, a bit above eye level. */
    public static void placeHere() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return;
        LyricsConfig cfg = LyricsConfig.get();
        Vec3 eye = mc.player.getEyePosition();
        Vec3 look = mc.player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1e-4) flat = new Vec3(0, 0, 1);
        Vec3 p = eye.add(flat.normalize().scale(8)).add(0, 2.5, 0);
        cfg.anchorX = p.x;
        cfg.anchorY = p.y;
        cfg.anchorZ = p.z;
        cfg.anchorYaw = mc.player.getYRot();
        cfg.anchorSet = true;
        cfg.save();
    }

    static void aimSky() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return;
        LyricsConfig cfg = LyricsConfig.get();
        cfg.skyYaw = mc.player.getYRot();
        cfg.skyFollow = false;
        cfg.save();
    }

    private static String osKey() {
        String n = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return n.contains("win") ? "windows" : n.contains("mac") ? "mac" : "linux";
    }

    static String clock(long ms) {
        long s = Math.max(0, ms) / 1000;
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }
}
