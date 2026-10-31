package de.example.riftkingextras;

import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Other players shown in the HUD table: name -> UUID (Mojang), then amount and rank from Elite. */
public final class TrackedPlayers {
    static final class Info {
        final String name;
        volatile UUID uuid = null;
        volatile double amount = -1;
        volatile long rank = -1;
        volatile String status = "loading...";
        volatile String slug = ""; // collection the values belong to

        Info(String name) {
            this.name = name;
        }
    }

    private static final Map<String, Info> INFO = new ConcurrentHashMap<>();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private TrackedPlayers() {}

    static int count() {
        return RkeConfig.players.size();
    }

    /** Infos in the order the players were added. */
    static List<Info> infos() {
        TrackedCollection compare = CollectionTracker.compareCollection();
        String cur = compare == null ? "" : compare.slug;
        List<Info> out = new ArrayList<>();
        for (String n : RkeConfig.players) {
            Info info = INFO.computeIfAbsent(n.toLowerCase(Locale.ROOT), k -> new Info(n));
            if (!info.slug.equals(cur)) { // compared collection changed: old values do not apply
                info.amount = -1;
                info.rank = -1;
                info.status = "loading...";
                info.slug = cur;
            }
            out.add(info);
        }
        return out;
    }

    static boolean contains(String name) {
        for (String n : RkeConfig.players) if (n.equalsIgnoreCase(name)) return true;
        return false;
    }

    static void add(String name) {
        RkeConfig.players.add(name);
        RkeConfig.save();
        CollectionApi.syncPlayersNow();
    }

    static void remove(String name) {
        RkeConfig.players.removeIf(n -> n.equalsIgnoreCase(name));
        INFO.remove(name.toLowerCase(Locale.ROOT));
        RkeConfig.save();
    }

    /** Refreshes every tracked player (background thread). Requests are spaced out to be gentle on the APIs. */
    static void syncAll() {
        TrackedCollection compare = CollectionTracker.compareCollection();
        if (compare == null) return;
        String slug = compare.slug;
        for (String name : new ArrayList<>(RkeConfig.players)) {
            Info info = INFO.computeIfAbsent(name.toLowerCase(Locale.ROOT), k -> new Info(name));
            try {
                if (info.uuid == null) {
                    UUID id = resolveUuid(name);
                    if (id == null) {
                        info.status = "not found";
                        continue;
                    }
                    info.uuid = id;
                }
                EliteLookup.Result r = EliteLookup.lookup(info.uuid, slug, s -> {});
                if (!r.found()) {
                    info.amount = -1;
                    info.rank = -1;
                    info.status = "no rank";
                } else {
                    info.amount = r.amount();
                    info.rank = r.rank();
                    info.status = "ok";
                }
                info.slug = slug;
            } catch (Exception e) {
                if (info.amount < 0) info.status = "n/a"; // keep old values if we already had some
            }
            try {
                Thread.sleep(400);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /** Name -> UUID via the public Mojang lookup (no key needed). Returns null if the name does not exist. */
    private static UUID resolveUuid(String name) throws Exception {
        String[] bases = {
                "https://api.minecraftservices.com/minecraft/profile/lookup/name/",
                "https://api.mojang.com/users/profiles/minecraft/"
        };
        Exception last = null;
        for (String base : bases) {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(base + name))
                        .timeout(Duration.ofSeconds(10))
                        .header("Accept", "application/json")
                        .header("User-Agent", "RiftKingExtras/1.0 (Fabric client mod)")
                        .GET()
                        .build();
                HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() == 200) {
                    String id = JsonParser.parseString(res.body()).getAsJsonObject().get("id").getAsString();
                    return UUID.fromString(id.replaceFirst(
                            "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
                            "$1-$2-$3-$4-$5"));
                }
                if (res.statusCode() == 404 || res.statusCode() == 204) return null; // name does not exist
                last = new Exception("HTTP " + res.statusCode());
            } catch (Exception e) {
                last = e;
            }
        }
        throw last != null ? last : new Exception("lookup failed");
    }
}
