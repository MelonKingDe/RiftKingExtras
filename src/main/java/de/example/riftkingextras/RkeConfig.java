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
    public static volatile float hudScale = 1.0f; // 0.25 .. 2.0

    // Second HUD: Minion Time Warp cooldown
    public static int twX = 8, twY = 130;
    public static volatile float twScale = 1.0f;
    public static volatile boolean showTimewarp = true;
    public static volatile long timewarpEndMs = 0; // real-time (epoch ms) when the cooldown ends, 0 = none running
    public static volatile String timewarpMessage = "you have successfully warped time for your minion";

    // Fourth HUD: health/mana/rift time bars, equipment, armor, inventory and hotbar
    public static int invX = -1, invY = -1; // -1 = default (bottom center)
    public static volatile float invScale = 1.0f;
    public static volatile boolean invEnabled = true;
    public static volatile boolean invOnlySkyBlock = true;
    public static volatile boolean hideHotbar = true;
    public static volatile boolean hideVanillaBars = true; // hearts, xp bar + level, action bar
    public static volatile boolean rarityBorders = true;
    public static volatile long lastSpeed = -1, lastRiftDamage = -1, lastDefense = -1; // last values seen (tab list, /stats menu, attribute)
    public static volatile double speedScale = 1000.0;    // speed = movement speed attribute * speedScale
    public static volatile boolean speedCalibrated = false;
    public static volatile boolean showTimers = true; // uptime/downtime line per collection in the HUD
    public static final List<TrackedCollection> collections = new CopyOnWriteArrayList<>();

    /** A tracked player and the collection he is compared on (empty slug = the first tracked collection). */
    public static final class PlayerEntry {
        public String name = "";
        public String slug = "";

        public PlayerEntry() {}

        PlayerEntry(String name, String slug) {
            this.name = name;
            this.slug = slug;
        }
    }

    public static final List<PlayerEntry> players = new CopyOnWriteArrayList<>();

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
        int invX = -1, invY = -1;
        long lastSpeed = -1, lastRiftDamage = -1, lastDefense = -1;
        double speedScale = 1000.0;
        boolean speedCalibrated = false;
        float invScale = 1.0f;
        boolean invEnabled = true, invOnlySkyBlock = true, hideHotbar = true, hideVanillaBars = true, rarityBorders = true;
        List<TrackedCollection> collections = new ArrayList<>();
        List<PlayerEntry> trackedPlayers = new ArrayList<>();
        List<String> players = new ArrayList<>(); // old format, only read
        String playerSlug = "";                   // old format, only read
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
                    if (d.hudScale >= 0.25f && d.hudScale <= 2.0f) hudScale = d.hudScale;
                    twX = d.twX; twY = d.twY; showTimewarp = d.showTimewarp; timewarpEndMs = d.timewarpEndMs;
                    if (d.twScale >= 0.25f && d.twScale <= 2.0f) twScale = d.twScale;
                    if (d.timewarpMessage != null && !d.timewarpMessage.isBlank()) timewarpMessage = d.timewarpMessage;
                    invX = d.invX; invY = d.invY; invEnabled = d.invEnabled; invOnlySkyBlock = d.invOnlySkyBlock;
                    hideHotbar = d.hideHotbar; hideVanillaBars = d.hideVanillaBars; rarityBorders = d.rarityBorders;
                    if (d.invScale >= 0.25f && d.invScale <= 2.0f) invScale = d.invScale;
                    lastSpeed = d.lastSpeed; lastRiftDamage = d.lastRiftDamage; lastDefense = d.lastDefense;
                    if (d.speedScale > 0) speedScale = d.speedScale;
                    speedCalibrated = d.speedCalibrated;
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
                    if (d.trackedPlayers != null) {
                        for (PlayerEntry e : d.trackedPlayers) {
                            if (e == null || e.name == null || e.name.isBlank() || players.size() >= MAX_PLAYERS) continue;
                            if (e.slug == null) e.slug = "";
                            players.add(e);
                        }
                    }
                    if (players.isEmpty() && d.players != null) { // older config: all players on one collection
                        String oldSlug = d.playerSlug == null ? "" : d.playerSlug;
                        for (String n : d.players) {
                            if (n != null && !n.isBlank() && players.size() < MAX_PLAYERS) {
                                players.add(new PlayerEntry(n.trim(), oldSlug));
                            }
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
            d.invX = invX; d.invY = invY; d.invScale = invScale; d.invEnabled = invEnabled;
            d.invOnlySkyBlock = invOnlySkyBlock; d.hideHotbar = hideHotbar; d.hideVanillaBars = hideVanillaBars;
            d.rarityBorders = rarityBorders;
            d.lastSpeed = lastSpeed; d.lastRiftDamage = lastRiftDamage; d.lastDefense = lastDefense;
            d.speedScale = speedScale; d.speedCalibrated = speedCalibrated;
            d.collections = new ArrayList<>(collections);
            d.trackedPlayers = new ArrayList<>(players);
            d.apiBase = apiBase; d.profilePath = profilePath; d.rankPath = rankPath;
            Files.writeString(FILE, GSON.toJson(d));
        } catch (Exception ignored) {}
    }
}
