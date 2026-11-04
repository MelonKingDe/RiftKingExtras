package de.example.riftkingextras;

/** The latest playback state, written by the platform source (background thread) and read by the HUD. */
final class MusicState {
    /** What is playing right now. [positionMs] was correct at [capturedAt] (System.currentTimeMillis()). */
    record Snapshot(boolean active, String title, String artist, String album, boolean playing,
                    long positionMs, long durationMs, float volume, long capturedAt) {
        static final Snapshot INACTIVE = new Snapshot(false, "", "", "", false, 0, 0, -1f, 0);

        /** The position at the given time: moves on by itself while playing, so it is smooth between updates. */
        long position(long now) {
            long p = positionMs + (playing ? Math.max(0, now - capturedAt) : 0);
            return durationMs > 0 ? Math.min(p, durationMs) : p;
        }

        /** Identifies the song (a new key = a new song). */
        String key() {
            return title + "|" + artist + "|" + durationMs;
        }
    }

    private static volatile Snapshot snapshot = Snapshot.INACTIVE;
    private static volatile long volumeShownUntil = 0;

    private MusicState() {}

    static Snapshot get() {
        return snapshot;
    }

    static void update(Snapshot next) {
        Snapshot prev = snapshot;
        if (next.volume() >= 0 && prev.volume() >= 0 && Math.abs(next.volume() - prev.volume()) > 0.004f) {
            volumeShownUntil = System.currentTimeMillis() + 2500; // show the volume for a moment after a change
        }
        snapshot = next;
    }

    /** True for a short time after the volume changed. */
    static boolean volumeRecentlyChanged() {
        return System.currentTimeMillis() < volumeShownUntil;
    }
}
