package de.example.riftkingextras;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The coop's Social XP. The total and the rank come from Elite (every 5 minutes, leaderboard "social"). Between two
 * updates the mod adds the XP you see in the action bar ("+3.5 Social (...)"). Whatever Elite has more than the mod
 * counted (e.g. XP of your coop members) is added to the gain of this session.
 * It reuses the logic of a tracked collection (total, gain, correction after an Elite update).
 */
final class SocialXp {
    /** Not in the collection list, so it does not show up in the tracker HUD. */
    static final TrackedCollection DATA = new TrackedCollection("Social");

    private static final Pattern GAIN = Pattern.compile("\\+\\s*(\\d[\\d,]*(?:\\.\\d+)?)\\s*Social", Pattern.CASE_INSENSITIVE);

    private static double fraction = 0;
    private static String lastText = "";

    private SocialXp() {}

    /** Called after the config was loaded: show the last known value right away. */
    static void init() {
        DATA.savedTotal = RkeConfig.socialTotal;
        DATA.rank = RkeConfig.socialRank;
        DATA.init();
    }

    /** Called with every new action bar text. The XP is added as soon as it shows up. */
    static synchronized void onActionBar(String plain) {
        Matcher m = GAIN.matcher(plain);
        if (!m.find() || plain.equals(lastText)) return; // the same text arrives from two sources: count it once
        lastText = plain;
        try {
            fraction += Double.parseDouble(m.group(1).replace(",", ""));
        } catch (NumberFormatException e) {
            return;
        }
        long whole = (long) fraction; // the XP can have decimals, only whole points are added
        fraction -= whole;
        if (whole > 0) DATA.onPickup(whole);
    }

    /** Asks Elite for the Social XP and rank of the player (background thread). */
    static void sync(UUID uuid) {
        try {
            EliteLookup.Result r = EliteLookup.lookup(uuid, "social", s -> {});
            if (r.found()) DATA.applyApi(r.amount() >= 0 ? Math.round(r.amount()) : -1, r.rank());
        } catch (Exception ignored) {
            // the next update tries again
        }
    }
}
