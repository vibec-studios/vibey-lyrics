package dev.henny.lyrics.sky;

import dev.henny.lyrics.Compat;
import dev.henny.lyrics.LyricsConfig;
import dev.henny.lyrics.render.LyricLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;

/** What the sky looks like right now: the custom sky in use (if any), fog color and the colors lyrics take from it. */
public final class SkyLook {
    private SkyLook() {}

    /** The custom sky to draw now, or null (vanilla sky, not in the overworld, no world). */
    public static SkyTheme active() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        SkyTheme t = SkyTheme.of(LyricsConfig.get().sky);
        if (t == SkyTheme.VANILLA || mc.level.dimension() != Level.OVERWORLD) return null;
        return t;
    }

    /** RGB fog color while a custom sky is shown, -1 to leave the game's fog alone (under water, in lava, ...). */
    public static int fogOverride() {
        SkyTheme t = active();
        if (t == null) return -1;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && (mc.player.isEyeInFluid(FluidTags.WATER) || mc.player.isEyeInFluid(FluidTags.LAVA))) return -1;
        return t.horizon;
    }

    public static boolean hideClouds() {
        return LyricsConfig.get().hideClouds && active() != null;
    }

    /** {text, highlight, glow} for "colors from the sky". */
    public static int[] lyricColors() {
        SkyTheme t = active();
        if (t != null) return new int[]{t.text, t.highlight, t.glow | 0xFF000000};
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return new int[]{0xFFFFFFFF, 0xFFFFE0B0, 0xFFB8452A};
        // Normal sky: day blue, warm at sunrise and sunset, cool at night.
        float time = (Compat.dayTime(mc.level) % 24000L) / 24000f;
        int[] day = {0xFFFFFFFF, 0xFFBFE6FF, 0xFF1E5AA8};
        int[] dusk = {0xFFFFF1E4, 0xFFFFC08A, 0xFFC2410C};
        int[] night = {0xFFE6ECFF, 0xFFB4C6FF, 0xFF27307A};
        // 0 = 6:00, 0.25 = noon, 0.5 = 18:00, 0.75 = midnight
        float sunset = bump(time, 0.5f, 0.06f);
        float sunrise = bump(time, 0.985f, 0.05f) + bump(time, -0.015f, 0.05f);
        float nightK = time > 0.54f && time < 0.96f ? 1f : 0f;
        nightK = Math.max(nightK, Math.min(1f, Math.max(0f, (time - 0.5f) / 0.04f)) * (time < 0.96f ? 1f : 0f));
        int[] out = new int[3];
        for (int i = 0; i < 3; i++) {
            int c = LyricLayout.mix(day[i], night[i], nightK);
            out[i] = LyricLayout.mix(c, dusk[i], Math.min(1f, sunset + sunrise));
        }
        return out;
    }

    private static float bump(float x, float center, float width) {
        float d = Math.abs(x - center) / width;
        return d >= 1f ? 0f : 1f - d * d;
    }
}
