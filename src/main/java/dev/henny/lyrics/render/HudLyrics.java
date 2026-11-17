package dev.henny.lyrics.render;

import dev.henny.lyrics.Compat;
import dev.henny.lyrics.LyricsConfig;
import dev.henny.lyrics.Playback;
import dev.henny.lyrics.client.ui.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Lyrics as a screen overlay (position "HUD"). */
public final class HudLyrics {
    /** Focal length of the fake perspective for 3D animations, in font pixels. */
    private static final float ROOM = 220f;

    private HudLyrics() {}

    public static void render(Gfx g) {
        NowPlayingBar.render(g);
        Minecraft mc = Minecraft.getInstance();
        LyricsConfig cfg = LyricsConfig.get();
        if (cfg.position != LyricsConfig.Position.HUD || Playback.song() == null || Compat.hudHidden()) return;
        int sw = g.screenWidth();
        int sh = g.screenHeight();
        // About twice the font size at 100 %, smaller on narrow windows.
        float scale = Math.max(1f, Math.min(sw / 220f, 2.4f)) * cfg.size / 100f;
        float maxWidth = Math.min(sw * 0.86f / scale, 240f);
        LyricLayout.Frame frame = LyricLayout.build(g.font, Playback.lyrics(), Playback.song().title(),
                Playback.song().artist(), Playback.lyricTimeMs(), maxWidth, ROOM);
        if (frame.isEmpty()) return;
        float cy = switch (cfg.hudPlace) {
            case TOP -> sh * 0.2f;
            case CENTER -> sh * 0.42f;
            case BOTTOM -> sh * 0.68f;
        };
        g.push();
        g.translate(sw / 2f, cy);
        g.scale(scale, scale);
        new Painter(g.font) {
            @Override
            protected void draw(Component c, LyricLayout.Run r, float ox, float oy, float oz, int argb, boolean back) {
                // The screen has no depth: words further away get smaller, turned words narrower.
                float s = r.scale();
                float p = ROOM / Math.max(ROOM * 0.15f, ROOM - r.z());
                g.push();
                g.translate(r.x() * p, (r.y() + LyricLayout.ROW * s / 2f) * p);
                if (r.roll() != 0f) g.rotate((float) Math.toRadians(r.roll()));
                float squash = Math.max(0.2f, Math.abs((float) Math.cos(Math.toRadians(r.yaw()))));
                g.scale(s * p * squash, s * p);
                g.translate(-font.width(c) / 2f + ox, -LyricLayout.ROW / 2f + oy);
                g.text(c, 0, 0, argb);
                g.pop();
            }

            @Override
            protected void box(float x1, float y1, float x2, float y2, int argb) {
                g.push();
                g.translate(x1, y1);
                // fill() takes whole numbers: draw in tenths of a font pixel.
                g.scale(0.1f, 0.1f);
                g.fill(0, 0, Math.round((x2 - x1) * 10), Math.round((y2 - y1) * 10), argb);
                g.pop();
            }
        }.paint(frame);
        g.pop();
    }
}
