package dev.henny.lyrics.sky;

import java.util.Locale;

/**
 * The custom skies. Each one is a panorama of the upper half of the sky (assets/vibeylyrics/textures/sky, painted by
 * tools/Skies.java) plus the colors that go with it: the horizon color becomes the fog color so the land fades into
 * the sky, and the lyric colors are used when "colors from the sky" is on.
 */
public enum SkyTheme {
    VANILLA(0, 0, 0xFFFFFFFF, 0xFFFFFFFF, 0, false),
    SUNSET(0xF5B180, 0xD1784D, 0xFFFFF3E4, 0xFFFFE0B0, 0xB8452A, false),
    GOLDEN(0xFEDB94, 0x9DA5DC, 0xFFFFF6E0, 0xFFFFD36E, 0x7A5AC8, false),
    CANDY(0xFBCDE7, 0xD1B8F4, 0xFFFFF0FA, 0xFFFF8FD0, 0x8B5CF6, false),
    DREAMY(0xF7B9CA, 0x4755AD, 0xFFFFEAF2, 0xFFFFB3CF, 0x3E4FA8, false),
    GALAXY(0x30275B, 0x23203E, 0xFFEDEBFF, 0xFFB9C7FF, 0x5A4FD0, true),
    NEBULA(0x9D45CD, 0x421B72, 0xFFFBEAFF, 0xFFFF9BE8, 0x7B3BFF, true),
    AURORA(0x12565F, 0x081525, 0xFFE8FFF6, 0xFF6BFFC0, 0x0E9F7A, true),
    SATURN(0x1D1C40, 0x0B0B1E, 0xFFFFF3E2, 0xFFF2CF8F, 0x4A3A8A, true),
    MOONLIGHT(0x334D8B, 0x122053, 0xFFEAF0FF, 0xFFAFC6FF, 0x2A3F9A, true),
    SYNTHWAVE(0xFD61A8, 0x2B0E54, 0xFFFFF0FA, 0xFFFFE36E, 0xFF3D8B, true),
    CRIMSON(0x721221, 0x20040D, 0xFFFFE4E4, 0xFFFF6A5A, 0x9A1C1C, true);

    /** RGB of the horizon, used as fog color. */
    public final int horizon;
    /** RGB high up in the sky (for the menu swatch). */
    public final int high;
    public final int text;
    public final int highlight;
    public final int glow;
    /** Night sky: slowly turns and shows shooting stars. */
    public final boolean night;

    SkyTheme(int horizon, int high, int text, int highlight, int glow, boolean night) {
        this.horizon = horizon;
        this.high = high;
        this.text = text;
        this.highlight = highlight;
        this.glow = glow;
        this.night = night;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static SkyTheme of(String id) {
        if (id != null) {
            for (SkyTheme s : values()) if (s.id().equals(id)) return s;
        }
        return VANILLA;
    }
}
