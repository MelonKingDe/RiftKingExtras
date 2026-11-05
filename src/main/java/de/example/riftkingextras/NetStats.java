package de.example.riftkingextras;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;

/**
 * Ping (from the tab list entry of the player) and an estimate of the server TPS.
 * The server tells the client its world time about once a second and the client sets its clock to that value. If the
 * server runs slower than 20 ticks per second, the clock gets set back; the TPS is the progress of the server's time
 * divided by the real time that passed, measured between two such corrections.
 */
final class NetStats {
    private static volatile double tps = -1;

    private static Object lastLevel = null;
    private static long lastGameTime = -1;
    private static long anchorNanos = 0;
    private static long anchorGameTime = 0;
    private static long lastCorrectionNanos = 0;

    private NetStats() {}

    /** Ping in milliseconds, or -1 if unknown. */
    static int ping(Minecraft mc) {
        try {
            ClientPacketListener connection = mc.getConnection();
            if (connection == null || mc.player == null) return -1;
            PlayerInfo info = connection.getPlayerInfo(mc.player.getUUID());
            return info == null ? -1 : info.getLatency();
        } catch (RuntimeException e) {
            return -1;
        }
    }

    /** Estimated server TPS (0..20), or -1 if there is no estimate yet. */
    static double tps() {
        return tps;
    }

    /** Called every client tick. */
    static void tick(Minecraft mc) {
        if (mc.level == null) {
            lastLevel = null;
            lastGameTime = -1;
            anchorNanos = 0;
            tps = -1;
            return;
        }
        long now = System.nanoTime();
        long gameTime = mc.level.getGameTime();

        if (mc.level != lastLevel) { // a new world: start measuring again
            lastLevel = mc.level;
            lastGameTime = gameTime;
            anchorNanos = 0;
            lastCorrectionNanos = now;
            return;
        }

        boolean corrected = gameTime != lastGameTime + 1; // the client clock normally moves one tick per tick
        lastGameTime = gameTime;

        if (corrected) {
            lastCorrectionNanos = now;
            if (anchorNanos == 0) {
                anchorNanos = now;
                anchorGameTime = gameTime;
            } else if (now - anchorNanos >= 4_000_000_000L) { // measure over at least 4 seconds
                double seconds = (now - anchorNanos) / 1e9;
                double measured = (gameTime - anchorGameTime) / seconds;
                tps = Math.max(0, Math.min(20.0, measured));
                anchorNanos = now;
                anchorGameTime = gameTime;
            }
        } else if (now - lastCorrectionNanos > 15_000_000_000L) {
            tps = 20.0; // no correction for a long time: the client kept in step with the server
        }
    }
}
