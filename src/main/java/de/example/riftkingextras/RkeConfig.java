package de.example.riftkingextras;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Settings and last known values, stored in config/riftkingextras.json. */
public final class RkeConfig {
    public static final int MAX_COLLECTIONS = 7;
    public static final int MAX_PLAYERS = 6;

    public static int hudX = 8, hudY = 8;
    public static volatile float hudScale = 1.0f; // 0.5 .. 2.0

    // Second HUD: Minion Time Warp cooldown
    public static int twX = 8, twY = 130;
    public static volatile float twScale = 1.0f;
    public static volatile boolean showTimewarp = true;
    public static volatile long timewarpEndMs = 0; // real-time (epoch ms) when the cooldown ends, 0 = none running
    public static volatile String timewarpMessage = "you have successfully warped time for your minion";
    public static volatile boolean showTimers = true; // uptime/downtime line per collection in the HUD
    public static final List<TrackedCollection> collections = new CopyOnWriteArrayList<>();
    public static final List<String> players = new CopyOnWriteArrayList<>();
    public static volatile String playerSlug = ""; // collection the player table compares on ("" = first)

    // Same defaults as config/nametaglines.json of the NameTagMod
    public static String apiBase = "https://api.eliteskyblock.com";
    public static String profilePath = "/profile/{uuid}/selected";
    public static String rankPath = "/leaderboard/rank/{lb}/{uuid}/{profile}";

    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("riftkingextras.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static class Data {
        int hudX = 8, hudY = 8;
        boolean showTimers = true;
        float hudScale = 1.0f;
        int twX = 8, twY = 130;
        float twScale = 1.0f;
        boolean showTimewarp = true;
        long timewarpEndMs = 0;
        String timewarpMessage = "you have successfully warped time for your minion";
        List<TrackedCollection> collections = new ArrayList<>();
        List<String> players = new ArrayList<>();
        String playerSlug = "";
        String apiBase = "https://api.eliteskyblock.com";
        String profilePath = "/profile/{uuid}/selected";
        String rankPath = "/leaderboard/rank/{lb}/{uuid}/{profile}";
    }

    private RkeConfig() {}

    public static void load() {
        boolean firstStart = !Files.exists(FILE);
        try {
            if (!firstStart) {
                Data d = GSON.fromJson(Files.readString(FILE), Data.class);
                if (d != null) {
                    hudX = d.hudX; hudY = d.hudY; showTimers = d.showTimers;
                    if (d.hudScale >= 0.5f && d.hudScale <= 2.0f) hudScale = d.hudScale;
                    twX = d.twX; twY = d.twY; showTimewarp = d.showTimewarp; timewarpEndMs = d.timewarpEndMs;
                    if (d.twScale >= 0.5f && d.twScale <= 2.0f) twScale = d.twScale;
                    if (d.timewarpMessage != null && !d.timewarpMessage.isBlank()) timewarpMessage = d.timewarpMessage;
                    if (d.playerSlug != null) playerSlug = d.playerSlug;
                    if (d.apiBase != null && !d.apiBase.isBlank()) apiBase = d.apiBase;
                    if (d.profilePath != null && !d.profilePath.isBlank()) profilePath = d.profilePath;
                    if (d.rankPath != null && !d.rankPath.isBlank()) rankPath = d.rankPath;
                    collections.clear();
                    if (d.collections != null) {
                        for (TrackedCollection c : d.collections) {
                            if (c == null || c.name == null || c.name.isBlank()) continue;
                            if (collections.size() < MAX_COLLECTIONS) collections.add(c);
                        }
                    }
                    players.clear();
                    if (d.players != null) {
                        for (String n : d.players) {
                            if (n != null && !n.isBlank() && players.size() < MAX_PLAYERS) players.add(n.trim());
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        if (firstStart) collections.add(new TrackedCollection("Agaricus Cap")); // starting point
        for (TrackedCollection c : collections) c.init();
    }

    public static synchronized void save() {
        try {
            for (TrackedCollection c : collections) c.snapshot();
            Data d = new Data();
            d.hudX = hudX; d.hudY = hudY; d.showTimers = showTimers; d.hudScale = hudScale;
            d.twX = twX; d.twY = twY; d.twScale = twScale; d.showTimewarp = showTimewarp;
            d.timewarpEndMs = timewarpEndMs; d.timewarpMessage = timewarpMessage;
            d.collections = new ArrayList<>(collections);
            d.players = new ArrayList<>(players);
            d.playerSlug = playerSlug;
            d.apiBase = apiBase; d.profilePath = profilePath; d.rankPath = rankPath;
            Files.writeString(FILE, GSON.toJson(d));
        } catch (Exception ignored) {}
    }
}
