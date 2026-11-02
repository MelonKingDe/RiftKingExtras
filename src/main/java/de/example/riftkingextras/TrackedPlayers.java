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

/**
 * Other players shown in the HUD table. Every player has his own collection: name -> UUID (Mojang), then amount and
 * rank of that collection from Elite.
 */
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

    /** The collection of a player: his own if it is still tracked, otherwise the first tracked collection. */
    static String slugOf(RkeConfig.PlayerEntry entry) {
        for (TrackedCollection c : RkeConfig.collections) {
            if (c.slug.equals(entry.slug)) return c.slug;
        }
        return RkeConfig.collections.isEmpty() ? "" : RkeConfig.collections.get(0).slug;
    }

    /** Infos in the order the players were added. */
    static List<Info> infos() {
        List<Info> out = new ArrayList<>();
        for (RkeConfig.PlayerEntry entry : RkeConfig.players) {
            String slug = slugOf(entry);
            Info info = INFO.computeIfAbsent(entry.name.toLowerCase(Locale.ROOT), k -> new Info(entry.name));
            if (!info.slug.equals(slug)) { // the collection of this player changed: old values do not apply
                info.amount = -1;
                info.rank = -1;
                info.status = "loading...";
                info.slug = slug;
            }
            out.add(info);
        }
        return out;
    }

    static boolean contains(String name) {
        for (RkeConfig.PlayerEntry e : RkeConfig.players) if (e.name.equalsIgnoreCase(name)) return true;
        return false;
    }

    static void add(String name, String slug) {
        RkeConfig.players.add(new RkeConfig.PlayerEntry(name, slug));
        RkeConfig.save();
        CollectionApi.syncPlayersNow();
    }

    static void remove(String name) {
        RkeConfig.players.removeIf(e -> e.name.equalsIgnoreCase(name));
        INFO.remove(name.toLowerCase(Locale.ROOT));
        RkeConfig.save();
    }

    /** Puts one player on another collection. */
    static void setCollection(String name, String slug) {
        for (RkeConfig.PlayerEntry e : RkeConfig.players) {
            if (e.name.equalsIgnoreCase(name)) e.slug = slug;
        }
        RkeConfig.save();
        CollectionApi.syncPlayersNow();
    }

    /** Refreshes every tracked player on his collection (background thread). Requests are spaced out. */
    static void syncAll() {
        for (RkeConfig.PlayerEntry entry : new ArrayList<>(RkeConfig.players)) {
            String slug = slugOf(entry);
            if (slug.isEmpty()) return; // no collection tracked at all
            Info info = INFO.computeIfAbsent(entry.name.toLowerCase(Locale.ROOT), k -> new Info(entry.name));
            try {
                if (info.uuid == null) {
                    UUID id = resolveUuid(entry.name);
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
