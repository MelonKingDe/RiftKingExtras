package de.example.riftkingextras;

import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Syncs amount and rank of all tracked collections (own player and tracked players) with Elite every 5 minutes. */
public final class CollectionApi {
    private static final long INTERVAL_MS = 300_000L;
    private static final long RETRY_MS = 120_000L;

    private static ScheduledExecutorService exec;
    private static volatile long lastAttempt = 0;

    private CollectionApi() {}

    static void startScheduler() {
        exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "riftkingextras-sync");
            t.setDaemon(true);
            return t;
        });
        // Checks every 15 s whether a sync is due; only runs while a world is loaded
        exec.scheduleAtFixedRate(CollectionApi::maybeSync, 5, 15, TimeUnit.SECONDS);
    }

    static void syncNow() {
        lastAttempt = 0;
        EliteLookup.clearProfileCache();
        if (exec != null) exec.execute(CollectionApi::maybeSync);
    }

    /** Refresh only the tracked players (e.g. right after adding one). */
    static void syncPlayersNow() {
        if (exec != null) exec.execute(TrackedPlayers::syncAll);
    }

    private static void maybeSync() {
        long now = System.currentTimeMillis();
        if (now - lastAttempt < INTERVAL_MS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        UUID uuid = mc.player.getUUID();
        lastAttempt = now;

        if (!syncOwn(uuid)) {
            lastAttempt = now - INTERVAL_MS + RETRY_MS; // retry sooner after an error
        }
        TrackedPlayers.syncAll();
    }

    private static boolean syncOwn(UUID uuid) {
        boolean ok = true;
        for (TrackedCollection c : new ArrayList<>(RkeConfig.collections)) {
            try {
                EliteLookup.Result r = EliteLookup.lookup(uuid, c.slug, s -> {});
                if (r.found()) {
                    c.applyApi(r.amount() >= 0 ? Math.round(r.amount()) : -1, r.rank());
                }
            } catch (Exception e) {
                ok = false;
            }
            pause(300);
        }
        CollectionTracker.persist();
        return ok;
    }

    private static void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Debug helper for /rke test: runs one lookup per collection and reports every step. */
    static void test(Consumer<String> out) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            out.accept("Join a world first.");
            return;
        }
        if (RkeConfig.collections.isEmpty()) {
            out.accept("No collections tracked yet.");
            return;
        }
        UUID uuid = mc.player.getUUID();
        exec.execute(() -> {
            for (TrackedCollection c : new ArrayList<>(RkeConfig.collections)) {
                try {
                    out.accept("Player " + uuid + ", leaderboard '" + c.slug + "'");
                    EliteLookup.Result r = EliteLookup.lookup(uuid, c.slug, out);
                    if (!r.found()) {
                        out.accept("Result: player has no rank on this leaderboard (HTTP 404).");
                    } else {
                        out.accept("Result: amount=" + r.amount() + ", rank=" + r.rank());
                    }
                } catch (Exception e) {
                    out.accept("Failed: " + e);
                }
            }
        });
    }
}
