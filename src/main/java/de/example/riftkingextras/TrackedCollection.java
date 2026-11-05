package de.example.riftkingextras;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One tracked collection. The first block of fields is saved in the config, the transient ones are runtime state.
 * Gson needs the public no-arg constructor.
 */
public final class TrackedCollection {
    // --- saved ---
    public String name = "";     // display name, e.g. "Agaricus Cap"
    public String slug = "";     // Elite leaderboard name, e.g. "agaricus-cap"
    public List<String> itemNames = new ArrayList<>(); // items that count +1 each (defaults to the name)
    public long savedTotal = -1;
    public int rank = 0;
    public long calibratedMs = 0;
    public String color = "";   // optional override like "#FF8800" for the bar in the HUD

    // --- runtime ---
    private transient volatile long apiValue = -1; // last value from Elite
    private transient volatile long pending = 0;   // counted locally, Elite does not know it yet
    private transient volatile long sessionGain = 0; // everything gained this session (shown as +N)
    private transient volatile long localGain = 0;   // only what was seen entering the inventory (used for items/hour)
    private transient boolean syncedThisSession = false;
    transient int lastInvCount = -1;

    // pickups that Elite has not confirmed yet: {time, amount}, oldest first
    private transient final ArrayDeque<long[]> pendingEntries = new ArrayDeque<>();
    private transient long droppedPool = 0;   // dropped or thrown by you: picking it up again is no gain
    private transient long lastDropMs = 0;
    private static final long CONFIRM_MS = 600_000L;     // Elite should know a pickup after 10 minutes
    private static final long DROP_MEMORY_MS = 300_000L; // a dropped item despawns after 5 minutes
    private transient final RateTimer timer = new RateTimer(); // own rate timer with auto pause

    /** Collections that are fed by several items: every listed item counts as 1. */
    private static final Map<String, List<String>> BUILTIN_ITEMS = Map.of(
            "timite", List.of("Youngite", "Timite", "Obsolite"));

    /** Fixed colors for the Rift collections; every other collection gets a stable color made from its name. */
    private static final Map<String, Integer> KNOWN_COLORS = Map.of(
            "caducous-stem", 0xFFE53935,      // red
            "living-metal-heart", 0xFF2F6BFF, // blue
            "hemovibe", 0xFF8E1B1B,           // dark red
            "timite", 0xFF5AC8FA,             // light blue
            "half-eaten-carrot", 0xFFFF9800,  // orange
            "wilted-berberis", 0xFF8D5A2B,    // brown
            "agaricus-cap", 0xFFFF6B6B);      // light red

    public TrackedCollection() {}

    public TrackedCollection(String name) {
        this.name = name.trim();
        this.slug = slugify(name);
        this.itemNames = defaultItems(this.name);
    }

    private static List<String> defaultItems(String name) {
        List<String> builtin = BUILTIN_ITEMS.get(slugify(name));
        return builtin != null ? new ArrayList<>(builtin) : new ArrayList<>(List.of(name.trim()));
    }

    /** "Wilted Berberis" -> "wilted-berberis" (name of the Elite leaderboard). */
    public static String slugify(String name) {
        String s = name.trim().toLowerCase(Locale.ROOT).replace("'", "");
        s = s.replaceAll("[^a-z0-9]+", "-");
        s = s.replaceAll("^-+|-+$", "");
        return s;
    }

    /** Color of the bar in the HUD: config override, then the Rift color, then a stable pseudo-random one. */
    int colorArgb() {
        if (color != null && color.matches("#?[0-9A-Fa-f]{6}")) {
            return 0xFF000000 | Integer.parseInt(color.replace("#", ""), 16);
        }
        Integer known = KNOWN_COLORS.get(slug);
        if (known != null) return known;
        int h = slug.hashCode() * 0x9E3779B1; // scramble so similar names get different colors
        float hue = ((h >>> 8) & 0xFFFF) / 65535f;
        return 0xFF000000 | hsvToRgb(hue, 0.62f, 0.95f);
    }

    private static int hsvToRgb(float h, float s, float v) {
        float hh = (h - (float) Math.floor(h)) * 6f;
        int i = (int) Math.floor(hh);
        float f = hh - i;
        float p = v * (1 - s), q = v * (1 - s * f), t = v * (1 - s * (1 - f));
        float r, g, b;
        switch (i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
    }

    /** Called after loading the config. */
    void init() {
        if (name == null) name = "";
        if (slug == null || slug.isBlank()) slug = slugify(name);
        List<String> builtin = BUILTIN_ITEMS.get(slug);
        boolean onlyDefault = itemNames != null && itemNames.size() == 1 && itemNames.get(0).equalsIgnoreCase(name);
        if (itemNames == null || itemNames.isEmpty() || (builtin != null && onlyDefault)) {
            itemNames = defaultItems(name); // also upgrades an older config entry for Timite
        }
        apiValue = savedTotal;
        pending = 0;
        pendingEntries.clear();
    }

    /** Items left the inventory without a menu (dropped, thrown): picking them up again is not a gain. */
    synchronized void onLoss(long diff) {
        droppedPool += diff;
        lastDropMs = System.currentTimeMillis();
    }

    /** Items entered the inventory. Returns how much of it counts as a gain (not what you dropped before). */
    synchronized long onPickup(long diff) {
        long now = System.currentTimeMillis();
        if (now - lastDropMs > DROP_MEMORY_MS) droppedPool = 0;
        long back = Math.min(droppedPool, diff);
        droppedPool -= back;
        long gained = diff - back;
        if (gained > 0) {
            sessionGain += gained;
            localGain += gained;
            pending += gained;
            pendingEntries.addLast(new long[]{now, gained});
            timer.onGain(); // a gain starts or resumes the rate timer
        }
        return gained;
    }

    /** Called every client tick while in a world. */
    void tickTimers(long now) {
        timer.tick(now);
    }

    void resetClock() {
        timer.resetClock();
    }

    /**
     * New values from Elite.
     * - Elite is higher than what we show: gained without seeing it (minion menu, sacks, e.g. 1 Hemoglass = 160
     *   Hemovibe); the difference belongs in the +N of this session.
     * - Elite is lower: pickups of the last 10 minutes may simply not be in Elite yet and stay; whatever is missing
     *   beyond that was counted wrongly, so it is removed from the total and from the gain.
     */
    synchronized void applyApi(long value, long newRank) {
        long now = System.currentTimeMillis();
        if (newRank >= 0) rank = (int) newRank;
        if (value >= 0) {
            long shown = total();
            if (shown >= 0 && value < shown) {
                long missing = shown - value;
                long recent = 0;
                for (long[] e : pendingEntries) if (now - e[0] <= CONFIRM_MS) recent += e[1];

                long unconfirmed = Math.min(missing, recent);
                long overcount = missing - unconfirmed; // Elite should have it by now: counted wrongly
                if (overcount > 0) {
                    sessionGain = Math.max(0, sessionGain - overcount);
                    localGain = Math.max(0, localGain - overcount);
                }

                // keep the newest entries that add up to the unconfirmed amount
                ArrayDeque<long[]> kept = new ArrayDeque<>();
                long keep = unconfirmed;
                Iterator<long[]> it = pendingEntries.descendingIterator();
                while (it.hasNext() && keep > 0) {
                    long[] e = it.next();
                    if (now - e[0] > CONFIRM_MS) continue;
                    long take = Math.min(e[1], keep);
                    kept.addFirst(new long[]{e[0], take});
                    keep -= take;
                }
                pendingEntries.clear();
                pendingEntries.addAll(kept);
                apiValue = value;
                pending = unconfirmed;
            } else {
                if (syncedThisSession && shown >= 0 && value > shown) sessionGain += value - shown;
                apiValue = value;
                pendingEntries.clear();
                pending = 0;
            }
            syncedThisSession = true;
        }
        calibratedMs = now;
    }

    /** Copies the shown value into the field that gets saved. */
    void snapshot() {
        savedTotal = total();
    }

    long total() { return apiValue < 0 ? -1 : apiValue + pending; }
    long sessionGain() { return sessionGain; }

    /** Items per hour of rate-timer uptime (paused time is not counted). */
    long perHour() {
        double hours = timer.uptime() / 3_600_000.0;
        return hours < 0.01 ? 0 : (long) (localGain / hours);
    }

    /** Clears this session's gain and timer (the shown total stays). */
    void resetSession() {
        sessionGain = 0;
        localGain = 0;
        timer.reset();
    }

    long uptime() { return timer.uptime(); }
    long downtime() { return timer.downtime(); }
    boolean isStarted() { return timer.isStarted(); }
    boolean isActive() { return timer.isActive(); }
    double idleFraction() { return timer.idleFraction(); }
}
