package de.example.riftkingextras;

import java.util.Locale;

/**
 * Minion Time Warp cooldown: 48 hours of real time, started by the chat message
 * "You have successfully warped time for your Minion". The end time is saved, so the countdown keeps running
 * while the game is closed.
 */
public final class TimewarpTracker {
    static final long COOLDOWN_MS = 48L * 60 * 60 * 1000;

    /** Hypixel puts this in front of the message: "TIME WARP! You have successfully warped time for your Minions!" */
    private static final String PREFIX = "time warp!";

    private static long lastStartMs = 0;
    private static volatile String lastWarpLine = "";

    private TimewarpTracker() {}

    /**
     * Called for every game chat message. The message counts if it starts with the phrase, after the optional
     * "TIME WARP!" prefix. Player chat is prefixed with a name, so it never matches. The Chocolate Factory and
     * Aging Items messages do not contain "minion" and are ignored.
     */
    static void onChat(String plainMessage) {
        if (plainMessage == null) return;
        String text = plainMessage.trim().toLowerCase(Locale.ROOT);
        if (text.contains("warped time")) lastWarpLine = plainMessage.trim(); // for /rke timewarp last

        if (text.startsWith(PREFIX)) text = text.substring(PREFIX.length()).trim();
        if (text.startsWith(RkeConfig.timewarpMessage.trim().toLowerCase(Locale.ROOT))) start();
    }

    /** The last chat line that contained "warped time" (shown by /rke timewarp last), to check what the mod sees. */
    static String lastWarpLine() {
        return lastWarpLine;
    }

    /** Starts a fresh 48 h cooldown from now. */
    static void start() {
        long now = System.currentTimeMillis();
        if (now - lastStartMs < 5_000) return; // ignore a duplicated message
        lastStartMs = now;
        RkeConfig.timewarpEndMs = now + COOLDOWN_MS;
        RkeConfig.save();
    }

    static void reset() {
        RkeConfig.timewarpEndMs = 0;
        RkeConfig.save();
    }

    /** Milliseconds left, 0 when ready (or never used). */
    static long remainingMs() {
        long end = RkeConfig.timewarpEndMs;
        return end <= 0 ? 0 : Math.max(0, end - System.currentTimeMillis());
    }

    /** True while a cooldown is running. */
    static boolean running() {
        return remainingMs() > 0;
    }

    /** 0..1, how much of the 48 h is already over (1 = ready). */
    static double progress() {
        if (RkeConfig.timewarpEndMs <= 0) return 1.0;
        return Math.max(0.0, Math.min(1.0, 1.0 - remainingMs() / (double) COOLDOWN_MS));
    }
}
