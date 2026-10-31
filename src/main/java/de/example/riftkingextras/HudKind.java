package de.example.riftkingextras;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** The two HUDs that can be moved and resized in the HUD editor. */
enum HudKind {
    TRACKER("Move tracker HUD"),
    TIMEWARP("Move Timewarp HUD"),
    SCOREBOARD("Move scoreboard HUD");

    final String title;

    HudKind(String title) {
        this.title = title;
    }

    int x() {
        return switch (this) {
            case TRACKER -> RkeConfig.hudX;
            case TIMEWARP -> RkeConfig.twX;
            case SCOREBOARD -> ScoreboardConfig.x;
        };
    }

    int y() {
        return switch (this) {
            case TRACKER -> RkeConfig.hudY;
            case TIMEWARP -> RkeConfig.twY;
            case SCOREBOARD -> ScoreboardConfig.y;
        };
    }

    float scale() {
        return switch (this) {
            case TRACKER -> RkeConfig.hudScale;
            case TIMEWARP -> RkeConfig.twScale;
            case SCOREBOARD -> ScoreboardConfig.scale;
        };
    }

    void setX(int v) {
        switch (this) {
            case TRACKER -> RkeConfig.hudX = v;
            case TIMEWARP -> RkeConfig.twX = v;
            case SCOREBOARD -> ScoreboardConfig.x = v;
        }
    }

    void setY(int v) {
        switch (this) {
            case TRACKER -> RkeConfig.hudY = v;
            case TIMEWARP -> RkeConfig.twY = v;
            case SCOREBOARD -> ScoreboardConfig.y = v;
        }
    }

    void setScale(float v) {
        switch (this) {
            case TRACKER -> RkeConfig.hudScale = v;
            case TIMEWARP -> RkeConfig.twScale = v;
            case SCOREBOARD -> ScoreboardConfig.scale = v;
        }
    }

    int scaledWidth() {
        return switch (this) {
            case TRACKER -> TrackerHud.scaledWidth();
            case TIMEWARP -> TimewarpHud.scaledWidth();
            case SCOREBOARD -> ScoreboardHud.scaledWidth();
        };
    }

    int scaledHeight() {
        return switch (this) {
            case TRACKER -> TrackerHud.scaledHeight();
            case TIMEWARP -> TimewarpHud.scaledHeight();
            case SCOREBOARD -> ScoreboardHud.scaledHeight();
        };
    }

    /** A negative position means "default": right side for x, vertically centered for y. */
    int clampX(int screenW, int x) {
        if (x < 0) return Math.max(0, screenW - scaledWidth() - 6);
        return Math.max(0, Math.min(x, screenW - scaledWidth()));
    }

    int clampY(int screenH, int y) {
        if (y < 0) return Math.max(0, (screenH - scaledHeight()) / 2);
        return Math.max(0, Math.min(y, screenH - scaledHeight()));
    }

    void draw(GuiGraphicsExtractor g, int x, int y) {
        switch (this) {
            case TRACKER -> TrackerHud.draw(g, x, y);
            case TIMEWARP -> TimewarpHud.draw(g, x, y);
            case SCOREBOARD -> ScoreboardHud.draw(g, x, y);
        }
    }

    void reset() {
        if (this == TRACKER) {
            RkeConfig.hudX = 8;
            RkeConfig.hudY = 8;
            RkeConfig.hudScale = 1.0f;
        } else if (this == TIMEWARP) {
            RkeConfig.twX = 8;
            RkeConfig.twY = 130;
            RkeConfig.twScale = 1.0f;
        } else {
            ScoreboardConfig.x = -1;
            ScoreboardConfig.y = -1;
            ScoreboardConfig.scale = 1.0f;
        }
    }
}
