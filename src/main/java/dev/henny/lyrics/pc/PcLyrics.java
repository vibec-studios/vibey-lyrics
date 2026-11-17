package dev.henny.lyrics.pc;

import dev.henny.lyrics.LyricsConfig;
import dev.henny.lyrics.VibeyLyrics;
import dev.henny.lyrics.library.Song;
import dev.henny.lyrics.lyrics.LrcLib;
import dev.henny.lyrics.lyrics.LrcParser;
import dev.henny.lyrics.lyrics.Lyrics;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * Lyrics for the track that plays on the PC: an .lrc of the same name in the song folder wins, then the download
 * cache ({@code vibey-lyrics/pc/}), then LRCLIB. Downloads are saved to the cache so a song is fetched once.
 */
public final class PcLyrics {
    public enum Status { IDLE, LOADING, FOUND, NOT_FOUND, OFFLINE, ERROR }

    private static String key = "";
    private static Song song;
    private static Lyrics lyrics;
    private static volatile Status status = Status.IDLE;
    private static volatile String error = "";
    private static final Set<String> missing = new HashSet<>();
    private static int generation;

    private PcLyrics() {}

    public static Song song() {
        return song;
    }

    public static Lyrics lyrics() {
        return lyrics;
    }

    public static Status status() {
        return status;
    }

    public static String error() {
        return error;
    }

    public static void reset() {
        key = "";
        song = null;
        lyrics = null;
        status = Status.IDLE;
        generation++;
    }

    /** Once per client tick: notices a new track and loads its lyrics in the background. */
    public static void tick() {
        NowPlaying.Track t = NowPlaying.track();
        if (t == null) {
            if (song != null) reset();
            return;
        }
        if (t.key().equals(key)) return;
        key = t.key();
        int gen = ++generation;
        String artist = t.artist().strip();
        String title = t.title().strip();
        // Browser videos often carry "Artist - Song (Official Video)" as title and the channel as artist.
        int dash = title.indexOf(" - ");
        String altArtist = dash > 0 ? title.substring(0, dash).strip() : null;
        String altTitle = dash > 0 ? LrcLib.cleanTitle(title.substring(dash + 3)) : null;
        String base = safe(artist.isEmpty() ? title : artist + " - " + title);
        song = new Song("pc", title, artist, null, null);
        lyrics = null;
        error = "";

        Path local = VibeyLyrics.songsDir().resolve(base + ".lrc");
        Path cached = cacheDir().resolve(base + ".lrc");
        for (Path p : new Path[]{local, cached}) {
            if (Files.isRegularFile(p)) {
                try {
                    lyrics = LrcParser.parse(Files.readString(p, StandardCharsets.UTF_8));
                    song = new Song("pc", title, artist, null, p);
                    status = Status.FOUND;
                    return;
                } catch (Exception e) {
                    VibeyLyrics.LOG.warn("Could not read {}: {}", p, e.toString());
                }
            }
        }
        if (missing.contains(key)) {
            status = Status.NOT_FOUND;
            return;
        }
        if (!LyricsConfig.get().onlineLookup) {
            status = Status.OFFLINE;
            return;
        }
        status = Status.LOADING;
        String myKey = key;
        long duration = t.durationMs();
        String album = t.album();
        Thread th = new Thread(() -> {
            LrcLib.Result r = null;
            String err = "";
            try {
                r = LrcLib.find(artist, title, album, duration);
                if (r == null && altArtist != null) r = LrcLib.find(altArtist, altTitle, "", duration);
            } catch (Exception e) {
                err = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            LrcLib.Result result = r;
            String failure = err;
            net.minecraft.client.Minecraft.getInstance().execute(() -> {
                if (gen != generation) return;
                if (result == null) {
                    if (failure.isEmpty()) {
                        missing.add(myKey);
                        status = Status.NOT_FOUND;
                    } else {
                        error = failure;
                        status = Status.ERROR;
                    }
                    return;
                }
                try {
                    String lrc = result.toLrc();
                    Files.createDirectories(cacheDir());
                    Files.writeString(cached, lrc, StandardCharsets.UTF_8);
                    lyrics = LrcParser.parse(lrc);
                    song = new Song("pc", title, artist, null, cached);
                    status = Status.FOUND;
                    VibeyLyrics.LOG.info("Lyrics for '{}' by {} from LRCLIB", title, artist);
                } catch (Exception e) {
                    error = e.toString();
                    status = Status.ERROR;
                }
            });
        }, "Vibey Lyrics lookup");
        th.setDaemon(true);
        th.start();
    }

    public static Path cacheDir() {
        return VibeyLyrics.songsDir().resolve("pc");
    }

    private static String safe(String s) {
        String out = s.replaceAll("[\\\\/:*?\"<>|]", "_").strip();
        return out.length() > 120 ? out.substring(0, 120) : out;
    }
}
