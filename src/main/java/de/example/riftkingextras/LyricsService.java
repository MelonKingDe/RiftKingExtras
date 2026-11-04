package de.example.riftkingextras;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Synced lyrics from LRCLIB (https://lrclib.net), a free public service: no Spotify account, no Premium and no API
 * key needed. The lyrics are looked up by title, artist, album and length, and shown in time with the song.
 */
final class LyricsService {
    enum Status { IDLE, LOADING, READY, NONE }

    record Line(long timeMs, String text) {}

    private static final String BASE = "https://lrclib.net/api";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final ExecutorService EXEC = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "riftkingextras-lyrics");
        t.setDaemon(true);
        return t;
    });

    private static final Pattern TIME_TAG = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]");

    private static volatile Status status = Status.IDLE;
    private static volatile List<Line> lines = List.of();
    private static volatile String currentKey = "";

    private LyricsService() {}

    static Status status() { return status; }
    static List<Line> lines() { return lines; }
    static String currentKey() { return currentKey; }

    /** Called with every playback update; starts a lookup when the song changed. */
    static void onSnapshot(MusicState.Snapshot s) {
        if (!s.active() || s.title().isBlank() || s.artist().isBlank() || s.durationMs() <= 0) return;
        String key = s.key();
        if (key.equals(currentKey)) return;

        currentKey = key;
        lines = List.of();
        status = Status.LOADING;
        EXEC.execute(() -> {
            List<Line> result = null;
            try {
                result = fetch(s.title(), s.artist(), s.album(), s.durationMs());
            } catch (Exception ignored) {
                // no lyrics
            }
            if (!key.equals(currentKey)) return; // another song started meanwhile
            if (result == null || result.isEmpty()) {
                lines = List.of();
                status = Status.NONE;
            } else {
                lines = result;
                status = Status.READY;
            }
        });
    }

    /** Index of the line that is sung at the given position, or -1 before the first line. */
    static int indexAt(List<Line> list, long positionMs) {
        int lo = 0, hi = list.size() - 1, found = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (list.get(mid).timeMs() <= positionMs) {
                found = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return found;
    }

    // ---------- lookup ----------

    private static List<Line> fetch(String title, String artist, String album, long durationMs) throws Exception {
        int seconds = (int) Math.round(durationMs / 1000.0);
        String cleanTitle = cleanTitle(title);
        String mainArtist = firstArtist(artist);

        // 1. exact match with title, artist, album and length
        if (!album.isBlank()) {
            List<Line> exact = get(title, artist, album, seconds);
            if (exact != null) return exact;
        }
        // 2. search by title and artist, then with cleaned names (no "- Remastered", no "(feat. ...)")
        List<Line> found = search("track_name=" + enc(title) + "&artist_name=" + enc(artist), seconds);
        if (found != null) return found;
        found = search("track_name=" + enc(cleanTitle) + "&artist_name=" + enc(mainArtist), seconds);
        if (found != null) return found;
        // 3. free text search
        return search("q=" + enc(cleanTitle + " " + mainArtist), seconds);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String cleanTitle(String title) {
        String t = title.replaceAll("\\s*[(\\[].*?[)\\]]", "");
        t = t.replaceAll("\\s+-\\s+.*$", "");
        return t.isBlank() ? title : t.trim();
    }

    private static String firstArtist(String artist) {
        String first = artist.split("(?i)\\s*(,|&|;|\\bfeat\\.?|\\bft\\.?|\\bx\\b)\\s*")[0].trim();
        return first.isBlank() ? artist : first;
    }

    private static String request(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .header("User-Agent", "RiftKingExtras/1.0 (Fabric client mod)")
                .GET()
                .build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        return res.statusCode() == 200 ? res.body() : null;
    }

    private static List<Line> get(String title, String artist, String album, int seconds) throws Exception {
        String body = request(BASE + "/get?track_name=" + enc(title) + "&artist_name=" + enc(artist)
                + "&album_name=" + enc(album) + "&duration=" + seconds);
        if (body == null) return null;
        return synced(JsonParser.parseString(body).getAsJsonObject());
    }

    /** Picks the search result that has synced lyrics and whose length is closest to the song's. */
    private static List<Line> search(String query, int seconds) throws Exception {
        String body = request(BASE + "/search?" + query);
        if (body == null) return null;
        JsonArray array = JsonParser.parseString(body).getAsJsonArray();

        List<JsonObject> candidates = new ArrayList<>();
        for (JsonElement e : array) {
            JsonObject o = e.getAsJsonObject();
            JsonElement synced = o.get("syncedLyrics");
            if (synced != null && !synced.isJsonNull() && !synced.getAsString().isBlank()) candidates.add(o);
        }
        candidates.sort(Comparator.comparingDouble(o -> Math.abs(duration(o) - seconds)));
        if (candidates.isEmpty() || Math.abs(duration(candidates.get(0)) - seconds) > 10) return null;
        return synced(candidates.get(0));
    }

    private static double duration(JsonObject o) {
        JsonElement d = o.get("duration");
        return d == null || d.isJsonNull() ? 0 : d.getAsDouble();
    }

    private static List<Line> synced(JsonObject o) {
        JsonElement synced = o.get("syncedLyrics");
        if (synced == null || synced.isJsonNull()) return null;
        List<Line> parsed = parseLrc(synced.getAsString());
        return parsed.isEmpty() ? null : parsed;
    }

    /** Parses LRC text: lines like "[01:23.45] some text" (several time tags per line are allowed). */
    static List<Line> parseLrc(String lrc) {
        List<Line> out = new ArrayList<>();
        for (String raw : lrc.split("\\R")) {
            Matcher m = TIME_TAG.matcher(raw);
            List<Long> times = new ArrayList<>();
            int textStart = 0;
            while (m.find()) {
                long minutes = Long.parseLong(m.group(1));
                long secs = Long.parseLong(m.group(2));
                long millis = 0;
                if (m.group(3) != null) {
                    String frac = (m.group(3) + "00").substring(0, 3); // ".5" = 500 ms, ".45" = 450 ms
                    millis = Long.parseLong(frac);
                }
                times.add(minutes * 60_000 + secs * 1000 + millis);
                textStart = m.end();
            }
            if (times.isEmpty()) continue;
            String text = raw.substring(textStart).trim();
            for (long t : times) out.add(new Line(t, text));
        }
        out.sort(Comparator.comparingLong(Line::timeMs));
        return out;
    }
}
