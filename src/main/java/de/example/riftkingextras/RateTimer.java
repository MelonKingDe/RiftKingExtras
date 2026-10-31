package de.example.riftkingextras;

/**
 * Uptime/downtime timer: the first gain starts it, 60 s without a gain pause it, the next gain resumes it.
 * When it pauses, the idle time is moved from uptime to downtime, so downtime is the real pause time.
 * Used once per collection and once for all collections together ("overall").
 */
final class RateTimer {
    static final long IDLE_PAUSE_MS = 60_000L;

    private volatile long uptimeMs = 0;
    private volatile long downtimeMs = 0;
    private volatile boolean started = false; // first gain seen
    private volatile boolean active = false;  // running right now
    private long idleMs = 0;                  // in-world time since the last gain
    private long lastTickMs = 0;

    /** Something was gained: starts or resumes the timer. */
    void onGain() {
        started = true;
        active = true;
        idleMs = 0;
    }

    /** Called every client tick while in a world. */
    void tick(long now) {
        long dt = lastTickMs == 0 ? 0 : now - lastTickMs;
        lastTickMs = now;
        if (dt <= 0 || dt > 10_000 || !started) return; // ignore gaps (freezes), nothing runs before the first gain
        if (active) {
            uptimeMs += dt;
            idleMs += dt;
            if (idleMs >= IDLE_PAUSE_MS) {
                // that idle time was not productive: it counts as downtime
                uptimeMs = Math.max(0, uptimeMs - idleMs);
                downtimeMs += idleMs;
                active = false;
            }
        } else {
            downtimeMs += dt;
        }
    }

    /** Forget the tick clock (e.g. when leaving the world) so the time away is not counted. */
    void resetClock() {
        lastTickMs = 0;
    }

    /** 0..1 = share of the 60 s idle time already used up while running, -1 if not running. */
    double idleFraction() {
        return active ? Math.min(1.0, idleMs / (double) IDLE_PAUSE_MS) : -1;
    }

    long uptime() { return uptimeMs; }
    long downtime() { return downtimeMs; }
    boolean isStarted() { return started; }
    boolean isActive() { return active; }
}
