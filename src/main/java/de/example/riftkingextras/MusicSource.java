package de.example.riftkingextras;

/** Where the "now playing" data comes from (Windows media session or Linux MPRIS). */
interface MusicSource {
    void start();

    void stop();

    /** One of: playpause, next, previous, restart, volume_up, volume_down. */
    void send(String command);

    /** A short description of what is going on, for the settings screen. */
    String status();
}
