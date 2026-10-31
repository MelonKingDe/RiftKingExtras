package de.example.riftkingextras;

/** The kinds of scoreboard lines that can be shown, hidden and ordered. */
enum SbElement {
    LOBBY("Date & lobby code", true),
    DATE("SkyBlock date", true),
    TIME("SkyBlock time", true),
    AREA("Area", true),
    PURSE("Purse / Piggy", true),
    BITS("Bits", true),
    CURRENCY("Other currencies", true),
    OBJECTIVE("Objective", true),
    SLAYER("Slayer quest", true),
    EVENT("Events", true),
    OTHER("Other lines", true),
    IRL_TIME("Real time", false),
    TIMEWARP("Minion Time Warp", false),
    SEPARATOR("Separator", true),
    FOOTER("Footer", true);

    final String label;
    final boolean defaultEnabled;

    SbElement(String label, boolean defaultEnabled) {
        this.label = label;
        this.defaultEnabled = defaultEnabled;
    }
}
