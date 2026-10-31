package de.example.riftkingextras;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Looks up amount and leaderboard rank of the own player (same calls as the NameTagMod). */
public final class EliteLookup {
    public record Result(boolean found, long rank, double amount) {}

    private static final long PROFILE_TTL_MS = 600_000L;
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private record ProfileEntry(String id, long fetched) {}

    private static final Map<UUID, ProfileEntry> PROFILES = new ConcurrentHashMap<>();

    private EliteLookup() {}

    static Result lookup(UUID uuid, String slug, Consumer<String> log) throws Exception {
        String profile = resolveProfile(uuid, log);
        String url = base() + RkeConfig.rankPath
                .replace("{lb}", slug)
                .replace("{uuid}", undashed(uuid))
                .replace("{profile}", profile);
        HttpResponse<String> res = request(url);
        log.accept("GET " + url + " -> HTTP " + res.statusCode());
        log.accept(snippet(res.body()));

        if (res.statusCode() == 404) return new Result(false, -1, -1);
        if (res.statusCode() / 100 != 2) throw new IOException("HTTP " + res.statusCode());

        JsonElement root = JsonParser.parseString(res.body());
        Double rank = number(root, "rank", "position");
        Double amount = number(root, "amount", "value", "collection", "score");
        if (rank == null && amount == null) throw new IOException("unexpected response format");
        return new Result(true, rank == null ? -1 : rank.longValue(), amount == null ? -1 : amount);
    }

    private static String resolveProfile(UUID uuid, Consumer<String> log) throws Exception {
        long now = System.currentTimeMillis();
        ProfileEntry cached = PROFILES.get(uuid);
        if (cached != null && now - cached.fetched() < PROFILE_TTL_MS) return cached.id();

        String url = base() + RkeConfig.profilePath.replace("{uuid}", undashed(uuid));
        HttpResponse<String> res = request(url);
        log.accept("GET " + url + " -> HTTP " + res.statusCode());
        log.accept(snippet(res.body()));
        if (res.statusCode() / 100 != 2) throw new IOException("profile lookup: HTTP " + res.statusCode());

        JsonElement el = find(JsonParser.parseString(res.body()), "profileId", "profile_id", "id");
        if (el == null || el.getAsString().isBlank()) throw new IOException("profile id not found");
        String id = el.getAsString();
        PROFILES.put(uuid, new ProfileEntry(id, now));
        return id;
    }

    /** Forget the cached profile ids (e.g. after switching the SkyBlock profile). */
    static void clearProfileCache() {
        PROFILES.clear();
    }

    private static HttpResponse<String> request(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .header("User-Agent", "RiftKingExtras/1.0 (Fabric client mod)")
                .GET()
                .build();
        return HTTP.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private static String base() {
        String b = RkeConfig.apiBase.trim();
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        return b;
    }

    private static String undashed(UUID uuid) {
        return uuid.toString().replace("-", "");
    }

    private static String snippet(String body) {
        if (body == null) return "(empty)";
        String s = body.replaceAll("\\s+", " ").trim();
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }

    /** Breadth-first search for the first primitive value under one of the given keys (case-insensitive). */
    private static JsonElement find(JsonElement root, String... keys) {
        Deque<JsonElement> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            JsonElement el = queue.poll();
            if (el.isJsonObject()) {
                JsonObject o = el.getAsJsonObject();
                for (String key : keys) {
                    for (Map.Entry<String, JsonElement> en : o.entrySet()) {
                        if (en.getKey().equalsIgnoreCase(key) && en.getValue().isJsonPrimitive()) {
                            return en.getValue();
                        }
                    }
                }
                for (Map.Entry<String, JsonElement> en : o.entrySet()) {
                    if (!en.getValue().isJsonPrimitive()) queue.add(en.getValue());
                }
            } else if (el.isJsonArray()) {
                JsonArray arr = el.getAsJsonArray();
                for (JsonElement child : arr) queue.add(child);
            }
        }
        return null;
    }

    private static Double number(JsonElement root, String... keys) {
        JsonElement el = find(root, keys);
        if (el == null) return null;
        try {
            return el.getAsDouble();
        } catch (Exception e) {
            return null;
        }
    }
}
