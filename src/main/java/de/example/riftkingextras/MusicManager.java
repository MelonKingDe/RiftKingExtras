package de.example.riftkingextras;

import java.util.Locale;

/** Starts the platform source and feeds its updates to the state, the cover art and the lyrics. */
final class MusicManager {
    private static MusicSource source = null;
    private static String lastKey = "";
    private static boolean unsupported = false;

    private MusicManager() {}

    /** Starts or stops the source to match the "enabled" setting. */
    static synchronized void applyEnabled() {
        if (MusicConfig.enabled) start();
        else stop();
    }

    static synchronized void start() {
        if (source != null) return;
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            source = new WindowsMusicSource();
        } else if (os.contains("linux")) {
            source = new LinuxMusicSource();
        } else {
            unsupported = true;
            return;
        }
        source.start();
    }

    static synchronized void stop() {
        if (source != null) source.stop();
        source = null;
        MusicState.update(MusicState.Snapshot.INACTIVE);
    }

    static void send(String command) {
        MusicSource s = source;
        if (s != null && MusicConfig.enabled) s.send(command);
    }

    static String status() {
        if (unsupported) return "not supported on this operating system (Windows and Linux only)";
        MusicSource s = source;
        return s == null ? "off" : s.status();
    }

    /** Called by the source threads with every update; [artwork] is only set when a new cover arrived. */
    static void push(MusicState.Snapshot snapshot, byte[] artwork) {
        String key = snapshot.active() ? snapshot.key() : "";
        if (!key.equals(lastKey)) {
            lastKey = key;
            MusicArt.clear(); // a new song: forget the old cover until the new one arrives
        }
        MusicState.update(snapshot);
        if (artwork != null && artwork.length > 0) MusicArt.submit(artwork);
        LyricsService.onSnapshot(snapshot);
    }
}
