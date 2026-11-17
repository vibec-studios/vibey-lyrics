package dev.henny.lyrics.client;

import dev.henny.lyrics.Compat;
import dev.henny.lyrics.LyricsConfig;
import dev.henny.lyrics.client.ui.CardScreen;
import dev.henny.lyrics.client.ui.Gfx;
import dev.henny.lyrics.sky.SkyTheme;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** "Advanced": font, colors, size, spacing, animation, placement details, sync offset and audio. */
public final class AdvancedScreen extends CardScreen {
    private static final int[] TEXT_COLORS = {0xFFFFFF, 0xFFE8D6, 0xE0F7FF, 0xFFF3B0, 0xD9F99D, 0x111111};
    private static final int[] HIGHLIGHT_COLORS = {0xFF4FD8, 0xFFD43B, 0xFF7A3D, 0x3BE8FF, 0x4ADE80, 0xFFFFFF};
    private static final int[] GLOW_COLORS = {0x8A2BE2, 0xE0115F, 0x1D4ED8, 0x16A34A, 0xFF8A00, 0x000000};
    private static final String[] FONTS = LyricsConfig.FONTS;

    public AdvancedScreen(Screen parent) {
        super(Component.translatable("vibeylyrics.advanced"), parent);
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
        return 2000;
    }

    @Override
    protected void drawHeader(Gfx g, int x, int y, int w) {
        title(g, I18n.get("vibeylyrics.advanced"), x + PAD, y + 13);
        closeButton(g, x, y, w);
    }

    @Override
    protected int drawBody(Gfx g, int x, int y, int w) {
        LyricsConfig c = LyricsConfig.get();
        int r = x + w;

        y = section(g, I18n.get("vibeylyrics.adv.text"), x, y);
        int h = row(g, x, y, w, "vibeylyrics.adv.font");
        cycler(g, r, y + h / 2, I18n.get("vibeylyrics.font." + c.font.replace("vibeylyrics:", "")), () -> c.font = cycle(FONTS, c.font, -1), () -> c.font = cycle(FONTS, c.font, 1));
        y += h + 2;
        h = row(g, x, y, w, "vibeylyrics.adv.bold");
        toggle(g, "bold", r, y + h / 2, c.bold, () -> c.bold = !c.bold);
        y += h + 2;
        h = row(g, x, y, w, "vibeylyrics.adv.depth");
        toggle(g, "depth", r, y + h / 2, c.depth, () -> c.depth = !c.depth);
        y += h + 2;
        h = row(g, x, y, w, "vibeylyrics.adv.uppercase");
        toggle(g, "upper", r, y + h / 2, c.uppercase, () -> c.uppercase = !c.uppercase);
        y += h + 2;
        h = row(g, x, y, w, "vibeylyrics.adv.size");
        stepperLabel(g, r, y + h / 2, c.size + " %", () -> c.size = Math.max(50, c.size - 10), () -> c.size = Math.min(300, c.size + 10));
        y += h + 2;
        h = row(g, x, y, w, "vibeylyrics.adv.spacing");
        stepperLabel(g, r, y + h / 2, c.lineSpacing + " %", () -> c.lineSpacing = Math.max(0, c.lineSpacing - 10), () -> c.lineSpacing = Math.min(100, c.lineSpacing + 10));
        y += h + 10;

        y = section(g, I18n.get("vibeylyrics.adv.colors"), x, y);
        h = row(g, x, y, w, "vibeylyrics.adv.matchsky");
        toggle(g, "matchsky", r, y + h / 2, c.matchSky, () -> c.matchSky = !c.matchSky);
        y += h + 2;
        h = row(g, x, y, w, "vibeylyrics.adv.color.text");
        swatches(g, r, y + h / 2, TEXT_COLORS, c.textColor, v -> c.textColor = v);
        y += h + 2;
        h = row(g, x, y, w, "vibeylyrics.adv.color.highlight");
        swatches(g, r, y + h / 2, HIGHLIGHT_COLORS, c.highlightColor, v -> c.highlightColor = v);
        y += h + 2;
        h = row(g, x, y, w, "vibeylyrics.adv.color.glow");
        swatches(g, r, y + h / 2, GLOW_COLORS, c.glowColor, v -> c.glowColor = v);
        y += h + 2;
        h = row(g, x, y, w, "vibeylyrics.adv.glow");
        stepperLabel(g, r, y + h / 2, c.glowStrength + " %", () -> c.glowStrength = Math.max(0, c.glowStrength - 10), () -> c.glowStrength = Math.min(100, c.glowStrength + 10));
        y += h + 2;
        h = row(g, x, y, w, "vibeylyrics.adv.box");
        toggle(g, "box", r, y + h / 2, c.box, () -> c.box = !c.box);
        y += h + 10;

        y = section(g, I18n.get("vibeylyrics.adv.animation"), x, y);
        h = row(g, x, y, w, "vibeylyrics.adv.anim");
        LyricsConfig.Anim[] anims = LyricsConfig.Anim.values();
        cycler(g, r, y + h / 2, I18n.get("vibeylyrics.anim." + lower(c.anim)),
                () -> c.anim = anims[(c.anim.ordinal() + anims.length - 1) % anims.length],
                () -> c.anim = anims[(c.anim.ordinal() + 1) % anims.length]);
        y += h + 2;
        h = row(g, x, y, w, "vibeylyrics.adv.reveal");
        LyricsConfig.Reveal[] reveals = LyricsConfig.Reveal.values();
        cycler(g, r, y + h / 2, I18n.get("vibeylyrics.reveal." + lower(c.reveal)),
                () -> c.reveal = reveals[(c.reveal.ordinal() + reveals.length - 1) % reveals.length],
                () -> c.reveal = reveals[(c.reveal.ordinal() + 1) % reveals.length]);
        y += h + 2;
        h = row(g, x, y, w, "vibeylyrics.adv.sparkles");
        toggle(g, "sparkles", r, y + h / 2, c.sparkles, () -> c.sparkles = !c.sparkles);
        y += h + 2;
        h = row(g, x, y, w, "vibeylyrics.adv.next");
        toggle(g, "next", r, y + h / 2, c.showNext, () -> c.showNext = !c.showNext);
        y += h + 2;
        h = row(g, x, y, w, "vibeylyrics.adv.pill");
        toggle(g, "pill", r, y + h / 2, c.pill, () -> c.pill = !c.pill);
        y += h + 10;

        y = section(g, I18n.get("vibeylyrics.sky"), x, y);
        h = row(g, x, y, w, "vibeylyrics.adv.skytheme");
        SkyTheme[] skies = SkyTheme.values();
        SkyTheme sky = SkyTheme.of(c.sky);
        cycler(g, r, y + h / 2, I18n.get("vibeylyrics.sky." + sky.id()),
                () -> c.sky = skies[(sky.ordinal() + skies.length - 1) % skies.length].id(),
                () -> c.sky = skies[(sky.ordinal() + 1) % skies.length].id());
        y += h + 2;
        h = row(g, x, y, w, "vibeylyrics.adv.clouds");
        toggle(g, "clouds", r, y + h / 2, c.hideClouds, () -> c.hideClouds = !c.hideClouds);
        y += h + 2;
        h = row(g, x, y, w, "vibeylyrics.adv.stars");
        toggle(g, "stars", r, y + h / 2, c.shootingStars, () -> c.shootingStars = !c.shootingStars);
        y += h + 10;

        y = section(g, I18n.get("vibeylyrics.adv.placement"), x, y);
        h = row(g, x, y, w, "vibeylyrics.adv.skyfollow");
        toggle(g, "skyfollow", r, y + h / 2, c.skyFollow, () -> c.skyFollow = !c.skyFollow);
        y += h + 2;
        h = row(g, x, y, w, "vibeylyrics.adv.skyheight");
        stepperLabel(g, r, y + h / 2, c.skyHeight + "°", () -> c.skyHeight = Math.max(0, c.skyHeight - 5), () -> c.skyHeight = Math.min(60, c.skyHeight + 5));
        y += h + 2;
        h = row(g, x, y, w, "vibeylyrics.adv.front");
        stepperLabel(g, r, y + h / 2, c.frontDistance + " m", () -> c.frontDistance = Math.max(2, c.frontDistance - 1), () -> c.frontDistance = Math.min(12, c.frontDistance + 1));
        y += h + 2;
        h = row(g, x, y, w, "vibeylyrics.adv.billboard");
        toggle(g, "billboard", r, y + h / 2, c.anchorBillboard, () -> c.anchorBillboard = !c.anchorBillboard);
        y += h + 2;
        h = row(g, x, y, w, "vibeylyrics.adv.hud");
        LyricsConfig.HudPlace[] places = LyricsConfig.HudPlace.values();
        cycler(g, r, y + h / 2, I18n.get("vibeylyrics.hud." + lower(c.hudPlace)),
                () -> c.hudPlace = places[(c.hudPlace.ordinal() + places.length - 1) % places.length],
                () -> c.hudPlace = places[(c.hudPlace.ordinal() + 1) % places.length]);
        y += h + 10;

        y = section(g, I18n.get("vibeylyrics.adv.sync"), x, y);
        h = row(g, x, y, w, "vibeylyrics.adv.offset");
        String off = (c.offsetMs > 0 ? "+" : "") + c.offsetMs + " ms";
        stepperLabel(g, r, y + h / 2, off, () -> c.offsetMs -= 50, () -> c.offsetMs += 50);
        y += h + 2;
        h = row(g, x, y, w, "vibeylyrics.adv.volume");
        stepperLabel(g, r, y + h / 2, c.volume + " %", () -> c.volume = Math.max(0, c.volume - 5), () -> c.volume = Math.min(100, c.volume + 5));
        y += h + 2;
        h = row(g, x, y, w, "vibeylyrics.adv.mutemusic");
        toggle(g, "mute", r, y + h / 2, c.muteVanillaMusic, () -> c.muteVanillaMusic = !c.muteVanillaMusic);
        y += h + 2;
        h = row(g, x, y, w, "vibeylyrics.adv.online");
        toggle(g, "online", r, y + h / 2, c.onlineLookup, () -> c.onlineLookup = !c.onlineLookup);
        y += h;
        return y + 4;
    }

    @Override
    protected void drawFooter(Gfx g, int x, int bottom, int w) {
        int y = bottom + 8;
        int bx = x + PAD;
        int bw = w - PAD * 2;
        int half = (bw - 4) / 2;
        button(g, bx, y, half, 20, I18n.get("vibeylyrics.reset"), Style.DANGER, () -> {
            LyricsConfig c = LyricsConfig.get();
            LyricsConfig d = new LyricsConfig();
            c.font = d.font;
            c.bold = d.bold;
            c.uppercase = d.uppercase;
            c.size = d.size;
            c.lineSpacing = d.lineSpacing;
            c.applyPreset(c.preset);
            c.anim = d.anim;
            c.reveal = d.reveal;
            c.showNext = d.showNext;
            c.pill = d.pill;
            c.matchSky = d.matchSky;
            c.hideClouds = d.hideClouds;
            c.shootingStars = d.shootingStars;
            c.sparkles = d.sparkles;
            c.skyFollow = d.skyFollow;
            c.skyHeight = d.skyHeight;
            c.frontDistance = d.frontDistance;
            c.anchorBillboard = d.anchorBillboard;
            c.hudPlace = d.hudPlace;
            c.offsetMs = d.offsetMs;
            c.volume = d.volume;
            c.muteVanillaMusic = d.muteVanillaMusic;
            c.onlineLookup = d.onlineLookup;
            c.save();
        });
        button(g, bx + half + 4, y, bw - half - 4, 20, I18n.get("vibeylyrics.done"), Style.PRIMARY, () -> Compat.openScreen(parent));
    }

    private static String cycle(String[] values, String cur, int dir) {
        int i = 0;
        for (int k = 0; k < values.length; k++) if (values[k].equals(cur)) i = k;
        return values[(i + dir + values.length) % values.length];
    }

    private static String lower(Enum<?> e) {
        return e.name().toLowerCase(Locale.ROOT);
    }
}
