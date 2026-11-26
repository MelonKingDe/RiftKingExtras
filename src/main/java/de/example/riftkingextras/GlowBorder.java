package de.example.riftkingextras;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The glowing border: a soft pulsing glow around a panel and light streaks that run along its border. It can be switched
 * on for every feature separately (main switch, color and speed are shared). Ported from the Collection Tracker mod.
 */
final class GlowBorder {
    /** Feature keys and the names shown in the glow settings. */
    static final String[] FEATURES = {"tracker", "timewarp", "scoreboard", "inventory", "music", "social", "race",
            "invscreen", "menus", "storage"};
    static final String[] LABELS = {"Tracker HUD", "Timewarp HUD", "Scoreboard", "Inventory HUD", "Music HUD",
            "Social XP HUD", "Collection race HUD", "Inventory screen", "Menus", "Storage overlay"};

    private static final Map<Long, int[][]> PERIMETER = new HashMap<>();

    private GlowBorder() {}

    static boolean active(String feature) {
        return RkeConfig.glowEnabled && !RkeConfig.glowDisabled.contains(feature);
    }

    /** The glow color as RGB (a wrong value gives the purple of the mod). */
    static int rgb() {
        String c = RkeConfig.glowColor == null ? "" : RkeConfig.glowColor.trim();
        if (c.matches("#?[0-9A-Fa-f]{6}")) return Integer.parseInt(c.replace("#", ""), 16);
        return 0xB060FF;
    }

    /** Draws the glow for a panel at (x, y) of the size w x h, if it is switched on for this feature. */
    static void decorate(GuiGraphicsExtractor g, int x, int y, int w, int h, String feature) {
        if (!active(feature) || w < 12 || h < 12) return;
        int rgb = rgb();
        double seconds = Math.max(2, RkeConfig.glowSeconds);
        double time = System.nanoTime() / 1e9;
        double pulse = 0.5 + 0.5 * Math.sin(time * 1.4);

        if (RkeConfig.glowOuter) outer(g, x, y, w, h, rgb, pulse);
        streaks(g, x, y, w, h, rgb, (time / seconds) % 1.0);
    }

    /** A few soft rings around the panel that pulse slowly. */
    private static void outer(GuiGraphicsExtractor g, int x, int y, int w, int h, int rgb, double pulse) {
        for (int k = 1; k <= 4; k++) {
            double falloff = 1.0 - (k - 1) / 4.0;
            int alpha = (int) (14 + 30 * pulse * falloff * falloff);
            int color = (alpha << 24) | rgb;
            TrackerHud.rect(g, x + 2, y - k, x + w - 2, y - k + 1, color);                 // top
            TrackerHud.rect(g, x + 2, y + h + k - 1, x + w - 2, y + h + k, color);         // bottom
            TrackerHud.rect(g, x - k, y + 2, x - k + 1, y + h - 2, color);                 // left
            TrackerHud.rect(g, x + w + k - 1, y + 2, x + w + k, y + h - 2, color);         // right
        }
    }

    /** The border pixels in order, clockwise from the top left. The corners are cut like the rounded panels. */
    private static int[][] perimeter(int w, int h) {
        long key = ((long) w << 32) | h;
        int[][] cached = PERIMETER.get(key);
        if (cached != null) return cached;

        List<int[]> points = new ArrayList<>();
        for (int px = 2; px < w - 2; px++) points.add(new int[]{px, 0});                  // top edge, left to right
        points.add(new int[]{w - 2, 1});
        for (int py = 2; py < h - 2; py++) points.add(new int[]{w - 1, py});              // right edge, top to bottom
        points.add(new int[]{w - 2, h - 2});
        for (int px = w - 3; px >= 2; px--) points.add(new int[]{px, h - 1});             // bottom edge, right to left
        points.add(new int[]{1, h - 2});
        for (int py = h - 3; py >= 2; py--) points.add(new int[]{0, py});                 // left edge, bottom to top
        points.add(new int[]{1, 1});

        if (PERIMETER.size() > 40) PERIMETER.clear();
        int[][] result = points.toArray(new int[0][]);
        PERIMETER.put(key, result);
        return result;
    }

    /** Two light streaks (on opposite sides) that run around the border, brightest at the head and fading behind. */
    private static void streaks(GuiGraphicsExtractor g, int x, int y, int w, int h, int rgb, double progress) {
        int[][] ring = perimeter(w, h);
        int n = ring.length;
        int tail = Math.max(10, n * 3 / 10);
        for (int streak = 0; streak < 2; streak++) {
            double head = ((progress + streak * 0.5) % 1.0) * n;
            for (int i = 0; i < tail; i++) {
                int index = (int) Math.floor(head - i);
                index = ((index % n) + n) % n;
                double fade = 1.0 - (double) i / tail;
                int alpha = (int) (255 * fade * fade);
                if (alpha < 12) continue;
                int[] p = ring[index];
                TrackerHud.rect(g, x + p[0], y + p[1], x + p[0] + 1, y + p[1] + 1, (alpha << 24) | rgb);
            }
        }
    }
}
