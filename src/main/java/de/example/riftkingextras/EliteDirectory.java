package de.example.riftkingextras;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The parts of the Elite API that the collection race needs: the list of all leaderboards (to find a collection by its
 * name), the top of a leaderboard, and the rank of a player. Ported from the Collection Tracker mod (MIT).
 */
final class EliteDirectory {
    record Board(String id, String title, String collection, String itemId, String shortName, String interval, String bestiary) {}

    record Entry(int rank, String ign, double amount) {}

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static volatile List<Board> cache = List.of();
    private static volatile long cacheTime = 0;

    private EliteDirectory() {}

    private static JsonElement get(String path) throws Exception {
        String base = RkeConfig.apiBase.trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        HttpRequest req = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .header("User-Agent", "RiftKingExtras/1.0 (Fabric client mod)")
                .GET().build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() != 200) throw new RuntimeException("HTTP " + res.statusCode() + " at " + path);
        return JsonParser.parseString(res.body());
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /** All leaderboards (kept for one hour). */
    static synchronized List<Board> boards() throws Exception {
        if (!cache.isEmpty() && System.currentTimeMillis() - cacheTime < 3_600_000L) return cache;
        JsonObject all = get("/leaderboards").getAsJsonObject().getAsJsonObject("leaderboards");
        List<Board> out = new ArrayList<>();
        for (var e : all.entrySet()) {
            JsonObject o = e.getValue().getAsJsonObject();
            out.add(new Board(e.getKey(), str(o, "title"), str(o, "collection"), str(o, "itemId"), str(o, "short"),
                    str(o, "intervalType"), str(o, "bestiary")));
        }
        cache = out;
        cacheTime = System.currentTimeMillis();
        return out;
    }

    private static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** The best matching collection leaderboard for a typed name like "timite" or "nether wart". */
    static Board findCollection(String query) throws Exception {
        String q = norm(query);
        if (q.isEmpty()) throw new RuntimeException("No collection set");
        Board best = null;
        int bestScore = 0;
        for (Board b : boards()) {
            if (b.bestiary() != null) continue;
            int score = 0;
            for (String field : new String[]{b.collection(), b.itemId(), b.shortName(), b.title(), b.id()}) {
                String n = norm(field);
                if (n.isEmpty()) continue;
                if (n.equals(q)) score = Math.max(score, 100);
                else if (n.equals(q + "collection") || n.equals("rift" + q)) score = Math.max(score, 90);
                else if (n.endsWith(q) || n.startsWith(q)) score = Math.max(score, 40);
                else if (n.contains(q)) score = Math.max(score, 20);
            }
            if (score == 0) continue;
            if (b.collection() != null) score += 10;
            if (b.interval() == null || b.interval().equalsIgnoreCase("Current")) score += 5;
            if (score > bestScore) {
                bestScore = score;
                best = b;
            }
        }
        if (best == null) throw new RuntimeException("No collection leaderboard for '" + query + "'");
        return best;
    }

    /** A player in the top N of a leaderboard, or null. */
    static Entry findInTop(String board, String ign, int limit) throws Exception {
        JsonArray entries = get("/leaderboard/" + enc(board) + "?limit=" + limit).getAsJsonObject().getAsJsonArray("entries");
        for (int i = 0; i < entries.size(); i++) {
            JsonObject o = entries.get(i).getAsJsonObject();
            String name = str(o, "ign");
            if (name != null && name.equalsIgnoreCase(ign)) return new Entry(i + 1, name, o.get("amount").getAsDouble());
        }
        return null;
    }

    /** The current number 1 of a leaderboard. */
    static Entry top1(String board) throws Exception {
        JsonArray entries = get("/leaderboard/" + enc(board) + "?limit=1").getAsJsonObject().getAsJsonArray("entries");
        if (entries.isEmpty()) throw new RuntimeException("Leaderboard is empty");
        JsonObject o = entries.get(0).getAsJsonObject();
        String name = str(o, "ign");
        return new Entry(1, name == null ? "?" : name, o.get("amount").getAsDouble());
    }

    /** Rank and amount of a player (selected profile). */
    static Entry rankOf(String board, UUID player, String displayName) throws Exception {
        String id = player.toString().replace("-", "");
        String profile = get("/profile/" + id + "/selected").getAsJsonObject().get("profileId").getAsString();
        JsonObject pos = get("/leaderboard/rank/" + enc(board) + "/" + id + "/" + profile).getAsJsonObject();
        return new Entry(pos.get("rank").getAsInt(), displayName, pos.get("amount").getAsDouble());
    }

    /** Minecraft name to UUID through Elite's account endpoint. */
    static UUID uuidOf(String ign) throws Exception {
        String id = get("/account/" + enc(ign)).getAsJsonObject().get("id").getAsString();
        if (id.length() == 32) id = id.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5");
        return UUID.fromString(id);
    }
}
