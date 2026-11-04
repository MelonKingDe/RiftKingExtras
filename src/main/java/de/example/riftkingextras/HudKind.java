package de.example.riftkingextras;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** The HUDs that can be moved and resized in the HUD editor. */
enum HudKind {
    TRACKER("Move tracker HUD"),
    TIMEWARP("Move Timewarp HUD"),
    SCOREBOARD("Move scoreboard HUD"),
    INVENTORY("Move inventory HUD"),
    MUSIC("Move music HUD");

    final String title;

    HudKind(String title) {
        this.title = title;
    }

    int x() {
        return switch (this) {
            case TRACKER -> RkeConfig.hudX;
            case TIMEWARP -> RkeConfig.twX;
            case SCOREBOARD -> ScoreboardConfig.x;
            case INVENTORY -> RkeConfig.invX;
            case MUSIC -> MusicConfig.x;
        };
    }

    int y() {
        return switch (this) {
            case TRACKER -> RkeConfig.hudY;
            case TIMEWARP -> RkeConfig.twY;
            case SCOREBOARD -> ScoreboardConfig.y;
            case INVENTORY -> RkeConfig.invY;
            case MUSIC -> MusicConfig.y;
        };
    }

    float scale() {
        return switch (this) {
            case TRACKER -> RkeConfig.hudScale;
            case TIMEWARP -> RkeConfig.twScale;
            case SCOREBOARD -> ScoreboardConfig.scale;
            case INVENTORY -> RkeConfig.invScale;
            case MUSIC -> MusicConfig.scale;
        };
    }

    void setX(int v) {
        switch (this) {
            case TRACKER -> RkeConfig.hudX = v;
            case TIMEWARP -> RkeConfig.twX = v;
            case SCOREBOARD -> ScoreboardConfig.x = v;
            case INVENTORY -> RkeConfig.invX = v;
            case MUSIC -> MusicConfig.x = v;
        }
    }

    void setY(int v) {
        switch (this) {
            case TRACKER -> RkeConfig.hudY = v;
            case TIMEWARP -> RkeConfig.twY = v;
            case SCOREBOARD -> ScoreboardConfig.y = v;
            case INVENTORY -> RkeConfig.invY = v;
            case MUSIC -> MusicConfig.y = v;
        }
    }

    void setScale(float v) {
        switch (this) {
            case TRACKER -> RkeConfig.hudScale = v;
            case TIMEWARP -> RkeConfig.twScale = v;
            case SCOREBOARD -> ScoreboardConfig.scale = v;
            case INVENTORY -> RkeConfig.invScale = v;
            case MUSIC -> MusicConfig.scale = v;
        }
    }

    int scaledWidth() {
        return switch (this) {
            case TRACKER -> TrackerHud.scaledWidth();
            case TIMEWARP -> TimewarpHud.scaledWidth();
            case SCOREBOARD -> ScoreboardHud.scaledWidth();
            case INVENTORY -> InventoryHud.scaledWidth();
            case MUSIC -> MusicHud.scaledWidth();
        };
    }

    int scaledHeight() {
        return switch (this) {
            case TRACKER -> TrackerHud.scaledHeight();
            case TIMEWARP -> TimewarpHud.scaledHeight();
            case SCOREBOARD -> ScoreboardHud.scaledHeight();
            case INVENTORY -> InventoryHud.scaledHeight();
            case MUSIC -> MusicHud.scaledHeight();
        };
    }

    /** A negative position means "default": scoreboard right side, inventory bottom center, others top left. */
    int clampX(int screenW, int x) {
        if (x < 0) {
            int pos = this == INVENTORY ? (screenW - scaledWidth()) / 2 : screenW - scaledWidth() - 6;
            return Math.max(0, pos);
        }
        return Math.max(0, Math.min(x, screenW - scaledWidth()));
    }

    int clampY(int screenH, int y) {
        if (y < 0) {
            int pos = this == INVENTORY ? screenH - scaledHeight() - 2 : (screenH - scaledHeight()) / 2;
            return Math.max(0, pos);
        }
        return Math.max(0, Math.min(y, screenH - scaledHeight()));
    }

    void draw(GuiGraphicsExtractor g, int x, int y) {
        switch (this) {
            case TRACKER -> TrackerHud.draw(g, x, y);
            case TIMEWARP -> TimewarpHud.draw(g, x, y);
            case SCOREBOARD -> ScoreboardHud.draw(g, x, y);
            case INVENTORY -> InventoryHud.draw(g, x, y);
            case MUSIC -> MusicHud.draw(g, x, y);
        }
    }

    void reset() {
        switch (this) {
            case TRACKER -> {
                RkeConfig.hudX = 8;
                RkeConfig.hudY = 8;
                RkeConfig.hudScale = 1.0f;
            }
            case TIMEWARP -> {
                RkeConfig.twX = 8;
                RkeConfig.twY = 130;
                RkeConfig.twScale = 1.0f;
            }
            case SCOREBOARD -> {
                ScoreboardConfig.x = -1;
                ScoreboardConfig.y = -1;
                ScoreboardConfig.scale = 1.0f;
            }
            case INVENTORY -> {
                RkeConfig.invX = -1;
                RkeConfig.invY = -1;
                RkeConfig.invScale = 1.0f;
            }
            case MUSIC -> {
                MusicConfig.x = -1;
                MusicConfig.y = 8;
                MusicConfig.scale = 1.0f;
            }
        }
    }
}
