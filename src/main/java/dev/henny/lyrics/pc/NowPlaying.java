package dev.henny.lyrics.pc;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.henny.lyrics.VibeyLyrics;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Watches what plays on this computer outside Minecraft: Windows media controls (Spotify, browsers, media players)
 * through a small PowerShell helper, Spotify / Apple Music through osascript on macOS and any MPRIS player through
 * playerctl on Linux. The position is extrapolated between reports and only re-anchored when it drifts, so the
 * lyrics move smoothly.
 */
public final class NowPlaying {
    public enum State { OFF, STARTING, RUNNING, UNSUPPORTED }

    /** One report from the system player. */
    public record Track(String app, String title, String artist, String album, boolean playing, long positionMs, long durationMs) {
        public String key() {
            return (artist + "\u0000" + title).toLowerCase(Locale.ROOT);
        }

        /** "Spotify", "Chrome", ... from ids like "SpotifyAB.SpotifyMusic_...!Spotify" or "chrome.exe". */
        public String appName() {
            String a = app == null ? "" : app;
            int bang = a.lastIndexOf('!');
            if (bang >= 0) a = a.substring(bang + 1);
            if (a.toLowerCase(Locale.ROOT).endsWith(".exe")) a = a.substring(0, a.length() - 4);
            int dot = a.lastIndexOf('.');
            if (dot >= 0 && dot < a.length() - 1) a = a.substring(dot + 1);
            if (a.isEmpty()) return "";
            return Character.toUpperCase(a.charAt(0)) + a.substring(1);
        }
    }

    private static volatile State state = State.OFF;
    private static volatile Track track;
    private static volatile String detail = "";
    private static Thread thread;
    private static volatile boolean running;
    private static volatile Process process;
    private static Path commandFile;

    // Smoothed clock, only touched under the class lock.
    private static long anchorPos;
    private static long anchorNanos;
    private static boolean anchorPlaying;
    private static String anchorKey = "";

    private NowPlaying() {}

    public static State state() {
        return state;
    }

    /** Extra info for the menu, e.g. "install playerctl". */
    public static String detail() {
        return detail;
    }

    /** The current track, or null when nothing plays. */
    public static Track track() {
        return track;
    }

    public static synchronized void start() {
        if (running) return;
        running = true;
        state = State.STARTING;
        detail = "";
        thread = new Thread(NowPlaying::loop, "Vibey Lyrics now playing");
        thread.setDaemon(true);
        thread.start();
    }

    public static synchronized void stop() {
        running = false;
        state = State.OFF;
        track = null;
        Process p = process;
        if (p != null) p.destroy();
        if (thread != null) thread.interrupt();
        thread = null;
    }

    public static synchronized long positionMs() {
        Track t = track;
        if (t == null) return 0;
        long pos = anchorPos;
        if (anchorPlaying) pos += (System.nanoTime() - anchorNanos) / 1_000_000L;
        if (t.durationMs() > 0) pos = Math.min(pos, t.durationMs());
        return Math.max(0, pos);
    }

    public static boolean isPlaying() {
        Track t = track;
        return t != null && t.playing();
    }

    public static void togglePlayPause() {
        String os = os();
        try {
            if (os.equals("windows")) {
                if (commandFile != null) Files.writeString(commandFile, "toggle");
            } else if (os.equals("mac")) {
                Track t = track;
                String app = t != null && t.app().equals("Music") ? "Music" : "Spotify";
                new ProcessBuilder("osascript", "-e", "tell application \"" + app + "\" to playpause").start();
            } else {
                new ProcessBuilder("playerctl", "play-pause").start();
            }
        } catch (IOException e) {
            VibeyLyrics.LOG.debug("Play/pause failed: {}", e.toString());
        }
    }

    /** Takes a fresh report and moves the smoothed clock only when it is clearly off. */
    private static synchronized void report(Track t) {
        track = t;
        if (t == null) return;
        long now = System.nanoTime();
        long predicted = anchorPos + (anchorPlaying ? (now - anchorNanos) / 1_000_000L : 0);
        long drift = t.positionMs() - predicted;
        if (!t.key().equals(anchorKey) || t.playing() != anchorPlaying || Math.abs(drift) > 400) {
            anchorPos = t.positionMs();
        } else {
            // Small differences are jitter in the reports: lean towards them slowly.
            anchorPos = predicted + drift / 8;
        }
        anchorNanos = now;
        anchorPlaying = t.playing();
        anchorKey = t.key();
    }

    // ============================================================================================ backends

    private static void loop() {
        int failures = 0;
        while (running) {
            try {
                switch (os()) {
                    case "windows" -> runWindows();
                    case "mac" -> pollMac();
                    default -> pollLinux();
                }
                failures = 0;
            } catch (InterruptedException e) {
                return;
            } catch (Exception e) {
                failures++;
                VibeyLyrics.LOG.debug("Now playing backend: {}", e.toString());
                if (failures > 5) {
                    state = State.UNSUPPORTED;
                    if (detail.isEmpty()) detail = e.getMessage() == null ? e.toString() : e.getMessage();
                }
            }
            if (!running) return;
            try {
                Thread.sleep(state == State.UNSUPPORTED ? 15_000 : 1500);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    /** Runs the PowerShell helper and reads its JSON lines until it ends. */
    private static void runWindows() throws IOException, InterruptedException {
        Path dir = FabricLoader.getInstance().getConfigDir().resolve("vibeylyrics");
        Files.createDirectories(dir);
        Path script = dir.resolve("nowplaying.ps1");
        try (InputStream in = NowPlaying.class.getResourceAsStream("/assets/vibeylyrics/nowplaying/windows.ps1")) {
            if (in == null) throw new IOException("helper script missing");
            Files.copy(in, script, StandardCopyOption.REPLACE_EXISTING);
        }
        commandFile = dir.resolve("nowplaying.cmd");
        Files.deleteIfExists(commandFile);
        ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-File", script.toString(), String.valueOf(ProcessHandle.current().pid()), commandFile.toString());
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process p = pb.start();
        process = p;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while (running && (line = r.readLine()) != null) {
                line = line.strip();
                if (!line.startsWith("{")) continue;
                JsonObject o = JsonParser.parseString(line).getAsJsonObject();
                if (o.has("error")) {
                    state = State.UNSUPPORTED;
                    detail = o.get("error").getAsString();
                    return;
                }
                state = State.RUNNING;
                if (!o.has("active") || !o.get("active").getAsBoolean() || str(o, "title").isBlank()) {
                    report(null);
                    continue;
                }
                report(new Track(str(o, "app"), str(o, "title"), str(o, "artist"), str(o, "album"),
                        o.get("playing").getAsBoolean(), o.get("pos").getAsLong(), o.get("dur").getAsLong()));
            }
        } finally {
            p.destroy();
            process = null;
        }
        if (running) throw new IOException("helper ended");
    }

    private static final String MAC_SPOTIFY = String.join("\n",
            "tell application \"Spotify\"",
            "if player state is stopped then return \"\"",
            "return (player state as text) & tab & (artist of current track) & tab & (name of current track) & tab & (album of current track) & tab & ((player position * 1000) as integer) & tab & (duration of current track)",
            "end tell");
    private static final String MAC_MUSIC = String.join("\n",
            "tell application \"Music\"",
            "if player state is stopped then return \"\"",
            "return (player state as text) & tab & (artist of current track) & tab & (name of current track) & tab & (album of current track) & tab & ((player position * 1000) as integer) & tab & (((duration of current track) * 1000) as integer)",
            "end tell");

    /** Spotify, then Apple Music; each only if it already runs (asking would start it). */
    private static void pollMac() throws IOException, InterruptedException {
        while (running) {
            Track t = null;
            if (isRunning("Spotify")) t = parseTabs("Spotify", exec("osascript", "-e", MAC_SPOTIFY), false);
            if ((t == null || !t.playing()) && isRunning("Music")) {
                Track m = parseTabs("Music", exec("osascript", "-e", MAC_MUSIC), false);
                if (m != null && (t == null || m.playing())) t = m;
            }
            state = State.RUNNING;
            report(t);
            Thread.sleep(700);
        }
    }

    /** Any MPRIS player (Spotify, browsers, VLC, ...) through playerctl. */
    private static void pollLinux() throws IOException, InterruptedException {
        while (running) {
            String out;
            try {
                out = exec("playerctl", "metadata", "--format",
                        "{{lc(status)}}\t{{artist}}\t{{title}}\t{{album}}\t{{position}}\t{{mpris:length}}\t{{playerName}}");
            } catch (IOException e) {
                state = State.UNSUPPORTED;
                detail = "playerctl";
                throw e;
            }
            state = State.RUNNING;
            Track t = null;
            String[] f = out.split("\t", -1);
            if (f.length >= 7 && !f[2].isBlank()) {
                t = new Track(f[6], f[2], f[1], f[3], f[0].startsWith("playing"),
                        parseLong(f[4]) / 1000, parseLong(f[5]) / 1000);
            }
            report(t);
            Thread.sleep(700);
        }
    }

    private static Track parseTabs(String app, String out, boolean micro) {
        String[] f = out.strip().split("\t", -1);
        if (f.length < 6 || f[2].isBlank()) return null;
        long pos = parseLong(f[4]);
        long dur = parseLong(f[5]);
        return new Track(app, f[2], f[1], f[3], f[0].toLowerCase(Locale.ROOT).startsWith("playing"),
                micro ? pos / 1000 : pos, micro ? dur / 1000 : dur);
    }

    private static boolean isRunning(String processName) {
        try {
            return !exec("pgrep", "-x", processName).isBlank();
        } catch (Exception e) {
            return false;
        }
    }

    private static String exec(String... cmd) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        process = p;
        try {
            byte[] out = p.getInputStream().readAllBytes();
            if (!p.waitFor(5, TimeUnit.SECONDS)) p.destroyForcibly();
            return new String(out, StandardCharsets.UTF_8);
        } finally {
            process = null;
        }
    }

    private static long parseLong(String s) {
        try {
            return Math.round(Double.parseDouble(s.strip().replace(',', '.')));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    static String os() {
        String n = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (n.contains("win")) return "windows";
        if (n.contains("mac") || n.contains("darwin")) return "mac";
        return "linux";
    }
}
