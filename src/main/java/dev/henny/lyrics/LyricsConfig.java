package dev.henny.lyrics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Everything the player can change, saved as config/vibey-lyrics.json. */
public final class LyricsConfig {
    public enum Position { SKY, FRONT, WORLD, HUD }

    /** How the words of a line show up. */
    public enum Reveal { LINE, WORD }

    /** How a line comes in (and, for some, how its words move while sung). */
    public enum Anim { POP, FADE, SLIDE, ZOOM, WAVE, BOUNCE, TYPE, GLITCH, RAINBOW, NONE }

    public enum HudPlace { TOP, CENTER, BOTTOM }

    /** Fonts shipped with the mod (assets/vibeylyrics/font) plus the two vanilla ones. */
    public static final String[] FONTS = {
            "default", "uniform", "vibeylyrics:bebas", "vibeylyrics:bungee", "vibeylyrics:marker",
            "vibeylyrics:pacifico", "vibeylyrics:pressstart", "vibeylyrics:monoton"
    };

    /** Ready-made looks; picking one in the main menu sets font, animation and colors. */
    public enum Preset {
        NEON("vibeylyrics:bungee", Anim.POP, 0xFFFFFFFF, 0xFFFF4FD8, 0xFF8A2BE2, 70, false, true),
        CLASSIC("default", Anim.POP, 0xFFFFFFFF, 0xFFFFD43B, 0xFF000000, 55, false, true),
        SUNSET("vibeylyrics:pacifico", Anim.ZOOM, 0xFFFFE8D6, 0xFFFF7A3D, 0xFFE0115F, 60, false, false),
        OCEAN("vibeylyrics:bebas", Anim.WAVE, 0xFFE0F7FF, 0xFF3BE8FF, 0xFF1D4ED8, 65, false, false),
        ARCADE("vibeylyrics:pressstart", Anim.GLITCH, 0xFFF7FFF0, 0xFF4ADE80, 0xFF0F7A35, 70, false, false),
        MARKER("vibeylyrics:marker", Anim.BOUNCE, 0xFFFFFFFF, 0xFFFFD43B, 0xFF000000, 45, false, false),
        RETRO("vibeylyrics:monoton", Anim.RAINBOW, 0xFFFFFFFF, 0xFFFF4FD8, 0xFF8A2BE2, 55, false, false),
        MINIMAL("default", Anim.FADE, 0xFFFFFFFF, 0xFFFFFFFF, 0xFF000000, 0, true, true);

        public final String font;
        public final Anim anim;
        public final int text;
        public final int highlight;
        public final int glow;
        public final int glowStrength;
        public final boolean box;
        public final boolean bold;

        Preset(String font, Anim anim, int text, int highlight, int glow, int glowStrength, boolean box, boolean bold) {
            this.font = font;
            this.anim = anim;
            this.text = text;
            this.highlight = highlight;
            this.glow = glow;
            this.glowStrength = glowStrength;
            this.box = box;
            this.bold = bold;
        }
    }

    // main menu
    public String song = "";
    public Position position = Position.SKY;
    public Preset preset = Preset.NEON;
    public int volume = 80;

    // advanced: look
    public String font = Preset.NEON.font;
    public boolean bold = true;
    /** Little sparkles fly off each word the moment it is sung. */
    public boolean sparkles = true;
    public boolean uppercase = false;
    public int textColor = Preset.NEON.text;
    public int highlightColor = Preset.NEON.highlight;
    public int glowColor = Preset.NEON.glow;
    public int glowStrength = Preset.NEON.glowStrength;
    public boolean box = false;
    /** 50 … 300 percent. */
    public int size = 100;
    /** Extra space between rows, in percent of the line height. */
    public int lineSpacing = 30;
    public Reveal reveal = Reveal.LINE;
    public Anim anim = Preset.NEON.anim;
    public boolean showNext = true;

    // advanced: placement
    public HudPlace hudPlace = HudPlace.CENTER;
    /** Sky: true = follows where you look, false = stays in one compass direction. */
    public boolean skyFollow = false;
    public float skyYaw = 0f;
    /** Degrees above the horizon. */
    public int skyHeight = 22;
    public int frontDistance = 4;
    public double anchorX;
    public double anchorY = 80;
    public double anchorZ;
    public float anchorYaw;
    public boolean anchorSet = false;
    /** World anchor: true = always faces you, false = fixed like a sign. */
    public boolean anchorBillboard = true;

    // advanced: sync and audio
    /** Milliseconds added to the song position before picking the line (positive = lyrics earlier). */
    public int offsetMs = 0;
    public boolean muteVanillaMusic = true;
    public boolean onlineLookup = true;
    /** Follow the music that plays on this computer (Spotify, browser, ...) and fetch its lyrics. */
    public boolean pcMode = false;

    // ------------------------------------------------------------------------------------------- persistence

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static LyricsConfig instance;

    public static LyricsConfig get() {
        if (instance == null) instance = load();
        return instance;
    }

    public void applyPreset(Preset p) {
        preset = p;
        font = p.font;
        anim = p.anim;
        bold = p.bold;
        textColor = p.text;
        highlightColor = p.highlight;
        glowColor = p.glow;
        glowStrength = p.glowStrength;
        box = p.box;
    }

    private static LyricsConfig load() {
        Path file = VibeyLyrics.configFile();
        if (Files.isRegularFile(file)) {
            try {
                LyricsConfig c = GSON.fromJson(Files.readString(file), LyricsConfig.class);
                if (c != null) {
                    c.sanitize();
                    return c;
                }
            } catch (Exception e) {
                VibeyLyrics.LOG.warn("Could not read {}: {}", file, e.toString());
            }
        }
        return new LyricsConfig();
    }

    private void sanitize() {
        if (position == null) position = Position.SKY;
        if (preset == null) preset = Preset.NEON;
        if (reveal == null) reveal = Reveal.LINE;
        if (anim == null) anim = Anim.POP;
        if (hudPlace == null) hudPlace = HudPlace.CENTER;
        if (font == null || !java.util.Arrays.asList(FONTS).contains(font)) font = "default";
        if (song == null) song = "";
        size = Math.max(50, Math.min(300, size));
        volume = Math.max(0, Math.min(100, volume));
        glowStrength = Math.max(0, Math.min(100, glowStrength));
        skyHeight = Math.max(0, Math.min(60, skyHeight));
        frontDistance = Math.max(2, Math.min(12, frontDistance));
        lineSpacing = Math.max(0, Math.min(100, lineSpacing));
    }

    public void save() {
        try {
            Path file = VibeyLyrics.configFile();
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(this));
        } catch (IOException e) {
            VibeyLyrics.LOG.warn("Could not save config: {}", e.toString());
        }
    }
}
