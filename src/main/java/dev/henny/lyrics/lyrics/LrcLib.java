package dev.henny.lyrics.lyrics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.henny.lyrics.BuildInfo;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Client for LRCLIB (lrclib.net), a free, community-run database of time-synced lyrics. No account or key; we only
 * send the search text and identify ourselves with the mod name, as LRCLIB asks clients to.
 */
public final class LrcLib {
    private static final String BASE = "https://lrclib.net/api";
    private static final String USER_AGENT =
            "Vibey Lyrics " + BuildInfo.VERSION + " (https://github.com/vibec-studios/vibey-lyrics)";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private LrcLib() {}

    public record Result(long id, String track, String artist, String album, long durationMs,
                         boolean instrumental, String synced, String plain) {
        public boolean hasSynced() {
            return synced != null && !synced.isBlank();
        }

        /** The result as an .lrc file with title/artist/album/length tags. */
        public String toLrc() {
            StringBuilder sb = new StringBuilder();
            sb.append("[ti:").append(track).append("]\n");
            sb.append("[ar:").append(artist).append("]\n");
            if (!album.isBlank()) sb.append("[al:").append(album).append("]\n");
            if (durationMs > 0) sb.append("[length:").append(LrcParser.clock(durationMs)).append("]\n");
            sb.append("[re:LRCLIB (lrclib.net) via Vibey Lyrics]\n\n");
            sb.append(synced.strip()).append('\n');
            return sb.toString();
        }
    }

    public static CompletableFuture<List<Result>> search(String query) {
        String url = BASE + "/search?q=" + URLEncoder.encode(query.strip(), StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .GET().build();
        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).thenApply(res -> {
            if (res.statusCode() != 200) throw new IllegalStateException("HTTP " + res.statusCode());
            return parse(res.body());
        });
    }

    /**
     * Best synced lyrics for a track that plays somewhere: exact match first, then a search by artist and title
     * (picking the result closest in length), then a looser search with "(Remastered)", "feat." etc. removed.
     * Runs blocking, call it off the render thread. Returns null when nothing synced exists.
     */
    public static Result find(String artist, String title, String album, long durationMs) throws Exception {
        if (durationMs > 0) {
            String url = BASE + "/get?artist_name=" + enc(artist) + "&track_name=" + enc(title)
                    + "&album_name=" + enc(album) + "&duration=" + Math.round(durationMs / 1000.0);
            HttpResponse<String> res = HTTP.send(get(url), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() == 200) {
                List<Result> one = parse("[" + res.body() + "]");
                if (!one.isEmpty() && one.get(0).hasSynced()) return one.get(0);
            }
        }
        Result r = best(searchPath("/search?track_name=" + enc(title) + "&artist_name=" + enc(artist)), durationMs);
        if (r != null) return r;
        String clean = cleanTitle(title);
        return best(searchPath("/search?q=" + enc((artist + " " + clean).strip())), durationMs);
    }

    private static List<Result> searchPath(String path) throws Exception {
        HttpResponse<String> res = HTTP.send(get(BASE + path), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() != 200) throw new IllegalStateException("HTTP " + res.statusCode());
        return parse(res.body());
    }

    private static Result best(List<Result> results, long durationMs) {
        Result best = null;
        long bestDiff = Long.MAX_VALUE;
        for (Result r : results) {
            if (!r.hasSynced()) continue;
            long diff = durationMs > 0 && r.durationMs() > 0 ? Math.abs(r.durationMs() - durationMs) : 0;
            if (diff < bestDiff) {
                best = r;
                bestDiff = diff;
            }
        }
        // A synced file for a much longer or shorter version would drift: better nothing.
        return bestDiff <= 6000 ? best : null;
    }

    /** "Song (Remastered 2011) - Live [Official Video] feat. X" → "Song". */
    public static String cleanTitle(String title) {
        String t = title.replaceAll("\\s*[(\\[][^)\\]]*[)\\]]", "");
        t = t.replaceAll("(?i)\\s+(feat\\.?|ft\\.?|featuring)\\s+.*$", "");
        int dash = t.indexOf(" - ");
        if (dash > 0) t = t.substring(0, dash);
        return t.strip().isEmpty() ? title.strip() : t.strip();
    }

    private static HttpRequest get(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .GET().build();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s.strip(), StandardCharsets.UTF_8);
    }

    static List<Result> parse(String body) {
        List<Result> out = new ArrayList<>();
        JsonElement root = JsonParser.parseString(body);
        if (!root.isJsonArray()) return out;
        JsonArray arr = root.getAsJsonArray();
        for (JsonElement e : arr) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            out.add(new Result(
                    num(o, "id"),
                    str(o, "trackName"),
                    str(o, "artistName"),
                    str(o, "albumName"),
                    Math.round(dbl(o, "duration") * 1000),
                    o.has("instrumental") && !o.get("instrumental").isJsonNull() && o.get("instrumental").getAsBoolean(),
                    strOrNull(o, "syncedLyrics"),
                    strOrNull(o, "plainLyrics")));
        }
        // Synced lyrics first, the rest keeps LRCLIB's order.
        out.sort((a, b) -> Boolean.compare(b.hasSynced(), a.hasSynced()));
        return out;
    }

    private static String str(JsonObject o, String k) {
        String s = strOrNull(o, k);
        return s == null ? "" : s;
    }

    private static String strOrNull(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    private static long num(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? 0 : e.getAsLong();
    }

    private static double dbl(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? 0 : e.getAsDouble();
    }
}
