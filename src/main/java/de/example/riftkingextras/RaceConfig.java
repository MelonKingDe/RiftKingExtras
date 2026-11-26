package de.example.riftkingextras;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** Settings of the collection race HUD, stored in config/riftkingextras-race.json. */
public final class RaceConfig {
    public static volatile boolean enabled = false;
    public static volatile String collection = "Timite"; // typed name, matched with the Elite leaderboards
    public static volatile String rival = "top";         // "top" = the number 1, or a player name
    public static volatile String deadline = "";         // yyyy-MM-dd; empty = 60 days from the first start

    public static int x = 8, y = 190;
    public static volatile float scale = 1.0f;

    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("riftkingextras-race.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    static final DateTimeFormatter SHOWN = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private static class Data {
        boolean enabled = false;
        String collection = "Timite", rival = "top", deadline = "";
        int x = 8, y = 190;
        float scale = 1.0f;
    }

    private RaceConfig() {}

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                Data d = GSON.fromJson(Files.readString(FILE), Data.class);
                if (d != null) {
                    enabled = d.enabled;
                    if (d.collection != null && !d.collection.isBlank()) collection = d.collection;
                    if (d.rival != null) rival = d.rival;
                    if (d.deadline != null) deadline = d.deadline;
                    x = d.x; y = d.y;
                    if (d.scale >= 0.25f && d.scale <= 2.0f) scale = d.scale;
                }
            }
        } catch (Exception ignored) {}
        if (deadline.isBlank()) {
            deadline = LocalDate.now().plusDays(60).toString();
            save();
        }
    }

    public static synchronized void save() {
        try {
            Data d = new Data();
            d.enabled = enabled; d.collection = collection; d.rival = rival; d.deadline = deadline;
            d.x = x; d.y = y; d.scale = scale;
            Files.writeString(FILE, GSON.toJson(d));
        } catch (Exception ignored) {}
    }

    /** The deadline as a date (60 days from today if it cannot be read). */
    static LocalDate deadlineDate() {
        try {
            return LocalDate.parse(deadline);
        } catch (DateTimeParseException e) {
            return LocalDate.now().plusDays(60);
        }
    }

    /** Reads a date typed as dd.MM.yyyy or yyyy-MM-dd; null if it is neither. */
    static LocalDate parseTyped(String text) {
        String t = text.trim();
        try {
            return LocalDate.parse(t, SHOWN);
        } catch (DateTimeParseException ignored) {
            // try the other format
        }
        try {
            return LocalDate.parse(t);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
