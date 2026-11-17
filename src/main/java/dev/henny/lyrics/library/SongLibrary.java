package dev.henny.lyrics.library;

import dev.henny.lyrics.VibeyLyrics;
import dev.henny.lyrics.audio.Decoder;
import dev.henny.lyrics.lyrics.LrcParser;
import dev.henny.lyrics.lyrics.Lyrics;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** The song folder {@code .minecraft/vibey-lyrics/}: audio files and .lrc files paired by file name. */
public final class SongLibrary {
    public static final String DEMO = "Vibe Craft Studios - Pixel Skies";
    private static final Pattern TAG = Pattern.compile("^\\[(ti|ar):(.*)]\\s*$", Pattern.CASE_INSENSITIVE);
    private static List<Song> songs = List.of();

    private SongLibrary() {}

    public static List<Song> songs() {
        return songs;
    }

    public static Song find(String id) {
        for (Song s : songs) if (s.id().equals(id)) return s;
        return null;
    }

    /** Creates the folder with a short read-me and the demo song the first time. */
    public static void ensureFolder() {
        Path dir = VibeyLyrics.songsDir();
        try {
            boolean fresh = !Files.isDirectory(dir);
            Files.createDirectories(dir);
            Path readme = dir.resolve("README.txt");
            if (!Files.exists(readme)) {
                try (InputStream in = SongLibrary.class.getResourceAsStream("/assets/vibeylyrics/folder-readme.txt")) {
                    if (in != null) Files.copy(in, readme);
                }
            }
            if (fresh) copyDemo();
        } catch (IOException e) {
            VibeyLyrics.LOG.warn("Could not prepare {}: {}", dir, e.toString());
        }
    }

    public static void copyDemo() throws IOException {
        Path dir = VibeyLyrics.songsDir();
        for (String ext : new String[]{".ogg", ".lrc"}) {
            Path target = dir.resolve(DEMO + ext);
            if (Files.exists(target)) continue;
            try (InputStream in = SongLibrary.class.getResourceAsStream("/assets/vibeylyrics/demo/pixel_skies" + ext)) {
                if (in != null) Files.copy(in, target);
            }
        }
    }

    public static List<Song> rescan() {
        ensureFolder();
        Path dir = VibeyLyrics.songsDir();
        Map<String, Path[]> groups = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(Files::isRegularFile).sorted().forEach(p -> {
                String name = p.getFileName().toString();
                int dot = name.lastIndexOf('.');
                if (dot <= 0) return;
                String base = name.substring(0, dot);
                String key = base.toLowerCase(Locale.ROOT);
                boolean lrc = name.toLowerCase(Locale.ROOT).endsWith(".lrc");
                if (!lrc && !Decoder.supported(name)) return;
                Path[] g = groups.computeIfAbsent(key, k -> new Path[2]);
                if (lrc) g[1] = p;
                else if (g[0] == null) g[0] = p;
            });
        } catch (IOException e) {
            VibeyLyrics.LOG.warn("Could not list {}: {}", dir, e.toString());
        }
        List<Song> out = new ArrayList<>();
        for (Map.Entry<String, Path[]> e : groups.entrySet()) {
            Path audio = e.getValue()[0];
            Path lrc = e.getValue()[1];
            String base = (audio != null ? audio : lrc).getFileName().toString();
            base = base.substring(0, base.lastIndexOf('.'));
            String title = base;
            String artist = "";
            int dash = base.indexOf(" - ");
            if (dash > 0) {
                artist = base.substring(0, dash).strip();
                title = base.substring(dash + 3).strip();
            }
            if (lrc != null) {
                String[] tags = tags(lrc);
                if (!tags[0].isBlank()) title = tags[0];
                if (!tags[1].isBlank()) artist = tags[1];
            }
            out.add(new Song(e.getKey(), title, artist, audio, lrc));
        }
        // Demo last, the player's own songs first.
        out.sort((a, b) -> {
            boolean da = a.id().equals(DEMO.toLowerCase(Locale.ROOT));
            boolean db = b.id().equals(DEMO.toLowerCase(Locale.ROOT));
            if (da != db) return da ? 1 : -1;
            return a.label().compareToIgnoreCase(b.label());
        });
        songs = List.copyOf(out);
        return songs;
    }

    private static String[] tags(Path lrc) {
        String[] out = {"", ""};
        try {
            for (String line : read(lrc).split("\\r?\\n")) {
                Matcher m = TAG.matcher(line.strip());
                if (!m.matches()) continue;
                if (m.group(1).equalsIgnoreCase("ti")) out[0] = m.group(2).strip();
                else out[1] = m.group(2).strip();
            }
        } catch (IOException ignored) {
        }
        return out;
    }

    public static Lyrics loadLyrics(Song song) throws IOException {
        if (song.lrc() == null) return null;
        return LrcParser.parse(read(song.lrc()));
    }

    /** UTF-8, or Windows-1252 for old files that are not valid UTF-8. */
    static String read(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, java.nio.charset.Charset.forName("windows-1252"));
        }
    }

    /** Saves downloaded lyrics; returns the file. */
    public static Path saveLrc(String baseName, String content) throws IOException {
        String safe = baseName.replaceAll("[\\\\/:*?\"<>|]", "_").strip();
        if (safe.isEmpty()) safe = "lyrics";
        Path file = VibeyLyrics.songsDir().resolve(safe + ".lrc");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }
}
