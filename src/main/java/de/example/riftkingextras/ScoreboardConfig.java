package de.example.riftkingextras;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Settings of the scoreboard HUD, stored in config/riftkingextras-scoreboard.json. */
public final class ScoreboardConfig {
    public static final class Entry {
        public String kind = "";
        public boolean enabled = true;

        public Entry() {}

        Entry(SbElement element, boolean enabled) {
            this.kind = element.name();
            this.enabled = enabled;
        }

        /** The element of this entry, or null if the saved name is unknown. */
        SbElement element() {
            try {
                return SbElement.valueOf(kind);
            } catch (Exception e) {
                return null;
            }
        }
    }

    // behaviour
    public static volatile boolean enabled = true;
    public static volatile boolean hideVanilla = true;      // hide the vanilla sidebar while this one is shown
    public static volatile boolean outsideSkyBlock = false; // also restyle scoreboards outside of SkyBlock
    public static volatile boolean hideWhenTab = false;
    public static volatile boolean hideWhenChat = false;
    public static volatile boolean hideWhenF3 = false;

    // appearance
    public static volatile boolean textShadow = true;
    public static volatile int numberFormat = 0;  // 0 = as on Hypixel, 1 = short (1.2M), 2 = long (1,234,567)
    public static volatile int textAlign = 0;     // 0 left, 1 center, 2 right
    public static volatile int titleAlign = 1;
    public static volatile int lineSpacing = 1;
    public static volatile boolean hideEdgeSeparators = true;
    public static volatile boolean condenseSeparators = true;
    public static volatile boolean time24h = true;
    public static volatile String accent = "#B060FF";

    // texts
    public static volatile boolean useHypixelTitle = true;
    public static volatile String customTitle = "";
    public static volatile boolean useCustomFooter = false;
    public static volatile String customFooter = "";

    // position and size (x/y = -1: default, right side / vertically centered)
    public static int x = -1, y = -1;
    public static volatile float scale = 1.0f;

    public static final List<Entry> entries = new CopyOnWriteArrayList<>();

    private static final Path FILE =
            FabricLoader.getInstance().getConfigDir().resolve("riftkingextras-scoreboard.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static class Data {
        boolean enabled = true, hideVanilla = true, outsideSkyBlock = false;
        boolean hideWhenTab = false, hideWhenChat = false, hideWhenF3 = false;
        boolean textShadow = true, hideEdgeSeparators = true, condenseSeparators = true, time24h = true;
        boolean useHypixelTitle = true, useCustomFooter = false;
        int numberFormat = 0, textAlign = 0, titleAlign = 1, lineSpacing = 1;
        int x = -1, y = -1;
        float scale = 1.0f;
        String accent = "#B060FF", customTitle = "", customFooter = "";
        List<Entry> entries = new ArrayList<>();
    }

    private ScoreboardConfig() {}

    static List<Entry> defaultEntries() {
        SbElement[] order = {
                SbElement.LOBBY, SbElement.DATE, SbElement.TIME, SbElement.AREA, SbElement.SEPARATOR,
                SbElement.PURSE, SbElement.BITS, SbElement.CURRENCY, SbElement.SEPARATOR,
                SbElement.OBJECTIVE, SbElement.SLAYER, SbElement.EVENT, SbElement.OTHER, SbElement.SEPARATOR,
                SbElement.IRL_TIME, SbElement.TIMEWARP, SbElement.PING, SbElement.TPS, SbElement.FOOTER};
        List<Entry> list = new ArrayList<>();
        for (SbElement e : order) list.add(new Entry(e, e.defaultEnabled));
        return list;
    }

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                Data d = GSON.fromJson(Files.readString(FILE), Data.class);
                if (d != null) {
                    enabled = d.enabled; hideVanilla = d.hideVanilla; outsideSkyBlock = d.outsideSkyBlock;
                    hideWhenTab = d.hideWhenTab; hideWhenChat = d.hideWhenChat; hideWhenF3 = d.hideWhenF3;
                    textShadow = d.textShadow; hideEdgeSeparators = d.hideEdgeSeparators;
                    condenseSeparators = d.condenseSeparators; time24h = d.time24h;
                    useHypixelTitle = d.useHypixelTitle; useCustomFooter = d.useCustomFooter;
                    numberFormat = Math.max(0, Math.min(2, d.numberFormat));
                    textAlign = Math.max(0, Math.min(2, d.textAlign));
                    titleAlign = Math.max(0, Math.min(2, d.titleAlign));
                    lineSpacing = Math.max(0, Math.min(6, d.lineSpacing));
                    x = d.x; y = d.y;
                    if (d.scale >= 0.25f && d.scale <= 2.0f) scale = d.scale;
                    if (d.accent != null) accent = d.accent;
                    if (d.customTitle != null) customTitle = d.customTitle;
                    if (d.customFooter != null) customFooter = d.customFooter;
                    entries.clear();
                    if (d.entries != null) {
                        for (Entry e : d.entries) {
                            if (e != null && e.element() != null) entries.add(e);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        if (entries.isEmpty()) entries.addAll(defaultEntries());
        ensureComplete();
    }

    /** Makes sure every element exists at least once (new versions may add elements). */
    private static void ensureComplete() {
        for (SbElement element : SbElement.values()) {
            if (element == SbElement.SEPARATOR) continue;
            boolean found = false;
            for (Entry e : entries) {
                if (e.element() == element) {
                    found = true;
                    break;
                }
            }
            if (!found) entries.add(new Entry(element, element.defaultEnabled));
        }
    }

    public static synchronized void save() {
        try {
            Data d = new Data();
            d.enabled = enabled; d.hideVanilla = hideVanilla; d.outsideSkyBlock = outsideSkyBlock;
            d.hideWhenTab = hideWhenTab; d.hideWhenChat = hideWhenChat; d.hideWhenF3 = hideWhenF3;
            d.textShadow = textShadow; d.hideEdgeSeparators = hideEdgeSeparators;
            d.condenseSeparators = condenseSeparators; d.time24h = time24h;
            d.useHypixelTitle = useHypixelTitle; d.useCustomFooter = useCustomFooter;
            d.numberFormat = numberFormat; d.textAlign = textAlign; d.titleAlign = titleAlign;
            d.lineSpacing = lineSpacing; d.x = x; d.y = y; d.scale = scale;
            d.accent = accent; d.customTitle = customTitle; d.customFooter = customFooter;
            d.entries = new ArrayList<>(entries);
            Files.writeString(FILE, GSON.toJson(d));
        } catch (Exception ignored) {}
    }

    static void footerSet(String text) {
        customFooter = text;
    }

    static void resetEntries() {
        entries.clear();
        entries.addAll(defaultEntries());
        save();
    }

    /** The accent color as ARGB (falls back to purple for an invalid value). */
    static int accentArgb() {
        String a = accent == null ? "" : accent.trim();
        if (a.matches("#?[0-9A-Fa-f]{6}")) return 0xFF000000 | Integer.parseInt(a.replace("#", ""), 16);
        return 0xFFB060FF;
    }
}
