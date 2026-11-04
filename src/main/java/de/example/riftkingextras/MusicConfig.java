package de.example.riftkingextras;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/** Settings of the music HUD, stored in config/riftkingextras-music.json. */
public final class MusicConfig {
    public static volatile boolean enabled = true;
    public static volatile boolean showLyrics = true;
    public static volatile boolean showArt = true;
    public static volatile boolean profanityFilter = true;
    public static volatile int lyricLines = 5;       // 3, 5 or 7 lines
    public static volatile int lyricOffsetMs = 0;    // positive = the lyrics show up earlier
    public static volatile String accent = "#1DB954";

    // position and size (x = -1: right edge)
    public static int x = -1, y = 8;
    public static volatile float scale = 1.0f;

    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("riftkingextras-music.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static class Data {
        boolean enabled = true, showLyrics = true, showArt = true, profanityFilter = true;
        int lyricLines = 5, lyricOffsetMs = 0;
        String accent = "#1DB954";
        int x = -1, y = 8;
        float scale = 1.0f;
    }

    private MusicConfig() {}

    public static void load() {
        try {
            if (!Files.exists(FILE)) return;
            Data d = GSON.fromJson(Files.readString(FILE), Data.class);
            if (d == null) return;
            enabled = d.enabled; showLyrics = d.showLyrics; showArt = d.showArt; profanityFilter = d.profanityFilter;
            lyricLines = (d.lyricLines == 3 || d.lyricLines == 7) ? d.lyricLines : 5;
            lyricOffsetMs = Math.max(-5000, Math.min(5000, d.lyricOffsetMs));
            if (d.accent != null) accent = d.accent;
            x = d.x; y = d.y;
            if (d.scale >= 0.25f && d.scale <= 2.0f) scale = d.scale;
        } catch (Exception ignored) {}
    }

    public static synchronized void save() {
        try {
            Data d = new Data();
            d.enabled = enabled; d.showLyrics = showLyrics; d.showArt = showArt; d.profanityFilter = profanityFilter;
            d.lyricLines = lyricLines; d.lyricOffsetMs = lyricOffsetMs; d.accent = accent;
            d.x = x; d.y = y; d.scale = scale;
            Files.writeString(FILE, GSON.toJson(d));
        } catch (Exception ignored) {}
    }

    /** The accent color as ARGB (falls back to the green of the original for an invalid value). */
    static int accentArgb() {
        String a = accent == null ? "" : accent.trim();
        if (a.matches("#?[0-9A-Fa-f]{6}")) return 0xFF000000 | Integer.parseInt(a.replace("#", ""), 16);
        return 0xFF1DB954;
    }
}
