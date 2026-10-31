package de.example.riftkingextras;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2fStack;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The tracker HUD: a dark rounded panel with a header, one card per collection (colored bar on the left),
 * an overall timer and an optional table of tracked players. Drawn at HUD scale RkeConfig.hudScale.
 */
public final class TrackerHud {
    private static final int PANEL_W = 200, TABLE_W = 240;
    static final int PAD = 5;
    static final int HEADER_H = 15;
    private static final int CARD_H = 27, CARD_H_TIMERS = 38, GAP = 3;
    private static final int OVERALL_H = 13;
    private static final int EMPTY_BODY = 26;
    private static final int TABLE_HEAD = 28, ROW_H = 11;

    static final int GREEN = 0xFF7BD88F, RED = 0xFFE0524B, GOLD = 0xFFE8C468;
    static final int MUTED = 0xFF6B7078, LIGHT = 0xFFAEB4BC;

    private TrackerHud() {}

    // ---------- size (unscaled units, then scaled) ----------

    private static boolean hasTable() {
        return !RkeConfig.collections.isEmpty() && !RkeConfig.players.isEmpty();
    }

    private static int cardH() {
        return RkeConfig.showTimers ? CARD_H_TIMERS : CARD_H;
    }

    private static int baseHeight() {
        int n = RkeConfig.collections.size();
        int body = n == 0 ? EMPTY_BODY
                : n * (cardH() + GAP) + (RkeConfig.showTimers ? OVERALL_H + GAP : 0);
        return HEADER_H + body + 2;
    }

    static int width() {
        return hasTable() ? TABLE_W : PANEL_W;
    }

    static int height() {
        int h = baseHeight();
        if (hasTable()) h += TABLE_HEAD + RkeConfig.players.size() * ROW_H + 4;
        return h;
    }

    static int scaledWidth() { return (int) Math.ceil(width() * RkeConfig.hudScale); }
    static int scaledHeight() { return (int) Math.ceil(height() * RkeConfig.hudScale); }

    static int clampX(int screenW, int x) { return Math.max(0, Math.min(x, screenW - scaledWidth())); }
    static int clampY(int screenH, int y) { return Math.max(0, Math.min(y, screenH - scaledHeight())); }

    // ---------- drawing helpers ----------

    /** Single place for filled rectangles (if the method name differs in a Minecraft version, fix it here). */
    static void rect(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
        g.fill(x1, y1, x2, y2, color);
    }

    /** Rectangle with rounded corners (radius 2), built from non-overlapping parts so translucent colors stay even. */
    static void rrect(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
        rect(g, x1 + 2, y1, x2 - 2, y1 + 1, color);
        rect(g, x1 + 1, y1 + 1, x2 - 1, y1 + 2, color);
        rect(g, x1, y1 + 2, x2, y2 - 2, color);
        rect(g, x1 + 1, y2 - 2, x2 - 1, y2 - 1, color);
        rect(g, x1 + 2, y2 - 1, x2 - 2, y2, color);
    }

    /** 1px outline following the rounded shape. */
    static void border(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
        rect(g, x1 + 2, y1, x2 - 2, y1 + 1, color);
        rect(g, x1 + 2, y2 - 1, x2 - 2, y2, color);
        rect(g, x1, y1 + 2, x1 + 1, y2 - 2, color);
        rect(g, x2 - 1, y1 + 2, x2, y2 - 2, color);
        rect(g, x1 + 1, y1 + 1, x1 + 2, y1 + 2, color);
        rect(g, x2 - 2, y1 + 1, x2 - 1, y1 + 2, color);
        rect(g, x1 + 1, y2 - 2, x1 + 2, y2 - 1, color);
        rect(g, x2 - 2, y2 - 2, x2 - 1, y2 - 1, color);
    }

    static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0xFFFFFF);
    }

    static String fmt(long value) {
        return NumberFormat.getIntegerInstance(Locale.US).format(value);
    }

    static String fmtTime(long ms) {
        long s = Math.max(0, ms / 1000);
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec)
                     : String.format(Locale.ROOT, "%02d:%02d", m, sec);
    }

    static void right(GuiGraphicsExtractor g, Font font, String text, int rightX, int y, int color) {
        g.text(font, text, rightX - font.width(text), y, color, false);
    }

    static int statusColor(boolean started, boolean active) {
        return !started ? MUTED : active ? GREEN : GOLD;
    }

    // ---------- entry points ----------

    static void render(GuiGraphicsExtractor graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.gui.screen() instanceof HudEditScreen e && e.kind() == HudKind.TRACKER) return; // preview is shown instead
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        draw(graphics, clampX(sw, RkeConfig.hudX), clampY(sh, RkeConfig.hudY));
    }

    /** Draws the whole HUD with its top-left corner at (x, y), scaled by the HUD scale. */
    static void draw(GuiGraphicsExtractor g, int x, int y) {
        float s = RkeConfig.hudScale;
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(s, s);
        drawContent(g);
        pose.popMatrix();
    }

    // ---------- content (local coordinates, origin = top-left of the panel) ----------

    private static void drawContent(GuiGraphicsExtractor g) {
        Font font = Minecraft.getInstance().font;
        Matrix3x2fStack pose = g.pose();
        int w = width(), h = height();

        rrect(g, 0, 0, w, h, 0xCC0E0E14);        // panel
        border(g, 0, 0, w, h, 0x33FFFFFF);

        // header
        g.text(font, "RIFTKING", 8, 4, 0xFFFFFFFF, false);
        g.text(font, "EXTRAS", 8 + font.width("RIFTKING "), 4, 0xFFB060FF, false);
        long cal = CollectionTracker.lastSyncMs();
        String sync = cal == 0 ? "syncing..." : "synced " + ((System.currentTimeMillis() - cal) / 60000) + "m ago";
        right(g, font, sync, w - 8, 4, MUTED);
        rect(g, 6, HEADER_H - 2, w - 6, HEADER_H - 1, 0x22FFFFFF);

        List<TrackedCollection> list = new ArrayList<>(RkeConfig.collections);
        if (list.isEmpty()) {
            g.text(font, "No collections tracked.", 8, HEADER_H + 3, 0xFF9AA0A6, false);
            g.text(font, "Open settings: /rke", 8, HEADER_H + 14, MUTED, false);
            return;
        }

        int cy = HEADER_H;
        int ch = cardH();
        for (TrackedCollection c : list) {
            drawCard(g, font, pose, c, w, cy, ch);
            cy += ch + GAP;
        }
        if (RkeConfig.showTimers) drawOverall(g, font, w, cy);

        drawTable(g, font, w, baseHeight());
    }

    private static void drawCard(GuiGraphicsExtractor g, Font font, Matrix3x2fStack pose,
                                 TrackedCollection c, int w, int cy, int ch) {
        int cx = PAD, cx2 = w - PAD;
        int color = c.colorArgb();
        int tx = cx + 8, rx = cx2 - 5;

        rrect(g, cx, cy, cx2, cy + ch, withAlpha(color, 0x26)); // card, tinted with the collection color
        rect(g, cx, cy + 1, cx + 3, cy + ch - 1, color);        // colored bar on the left

        String name = c.name.length() > 18 ? c.name.substring(0, 18) : c.name;
        g.text(font, name.toUpperCase(Locale.ROOT), tx, cy + 3, LIGHT, false);

        // rank pill
        String rank = c.rank > 0 ? "#" + fmt(c.rank) : "#-";
        int rw = font.width(rank);
        rrect(g, rx - rw - 4, cy + 2, rx + 1, cy + 12, 0x33FFFFFF);
        g.text(font, rank, rx - rw - 1, cy + 3, GOLD, false);

        // amount (bigger) and gain / rate
        long total = c.total();
        pose.pushMatrix();
        pose.translate(tx, cy + 13);
        pose.scale(1.25f, 1.25f);
        g.text(font, total >= 0 ? fmt(total) : "loading...", 0, 0, 0xFFFFFFFF, true);
        pose.popMatrix();
        right(g, font, "+" + fmt(c.sessionGain()) + " · " + fmt(c.perHour()) + "/h", rx, cy + 16, GREEN);

        // timers
        if (RkeConfig.showTimers) {
            int ly = cy + 27;
            int sc = statusColor(c.isStarted(), c.isActive());
            rect(g, tx, ly + 2, tx + 4, ly + 6, sc);
            g.text(font, "Up " + fmtTime(c.uptime()), tx + 8, ly, GREEN, false);
            String down = "Down " + fmtTime(c.downtime());
            g.text(font, down, tx + 78, ly, RED, false);
            String status = !c.isStarted() ? "WAITING" : c.isActive() ? "ACTIVE" : "PAUSED";
            if (tx + 78 + font.width(down) + 6 < rx - font.width(status)) {
                right(g, font, status, rx, ly, sc);
            }
        }

        // thin bar at the bottom: time left until the auto pause
        double idle = c.idleFraction();
        if (idle >= 0) {
            int bw = (int) ((cx2 - cx - 8) * (1.0 - idle));
            if (bw > 0) rect(g, cx + 4, cy + ch - 2, cx + 4 + bw, cy + ch - 1, withAlpha(color, 0xB0));
        }
    }

    private static void drawOverall(GuiGraphicsExtractor g, Font font, int w, int cy) {
        int cx = PAD, cx2 = w - PAD;
        RateTimer all = CollectionTracker.OVERALL; // all collections together
        rrect(g, cx, cy, cx2, cy + OVERALL_H, 0x1AFFFFFF);
        rect(g, cx + 5, cy + 4, cx + 9, cy + 8, statusColor(all.isStarted(), all.isActive()));
        g.text(font, "ALL", cx + 13, cy + 3, LIGHT, false);
        g.text(font, "Up " + fmtTime(all.uptime()), cx + 40, cy + 3, GREEN, false);
        g.text(font, "Down " + fmtTime(all.downtime()), cx + 108, cy + 3, RED, false);
    }

    private static void drawTable(GuiGraphicsExtractor g, Font font, int w, int top) {
        if (!hasTable()) return;
        TrackedCollection compare = CollectionTracker.compareCollection();
        if (compare == null) return;
        long ownTotal = compare.total();

        int cx = PAD, cx2 = w - PAD;
        int cAmount = 150, cRank = 192, cDiff = w - 8;

        rect(g, 6, top, w - 6, top + 1, 0x22FFFFFF);
        rect(g, 8, top + 6, 12, top + 10, compare.colorArgb());
        String title = compare.name.length() > 24 ? compare.name.substring(0, 24) : compare.name;
        g.text(font, "Comparing: " + title, 16, top + 5, LIGHT, false);

        int hy = top + 17;
        g.text(font, "PLAYER", 8, hy, MUTED, false);
        right(g, font, "AMOUNT", cAmount, hy, MUTED);
        right(g, font, "RANK", cRank, hy, MUTED);
        right(g, font, "YOU +/-", cDiff, hy, MUTED);

        int ry = top + TABLE_HEAD;
        int i = 0;
        for (TrackedPlayers.Info info : TrackedPlayers.infos()) {
            if (i++ % 2 == 0) rect(g, cx, ry - 1, cx2, ry + 9, 0x12FFFFFF); // zebra stripe

            String name = info.name.length() > 12 ? info.name.substring(0, 12) : info.name;
            g.text(font, name, 8, ry, 0xFFFFFFFF, false);

            if (info.amount >= 0) {
                long theirs = Math.round(info.amount);
                right(g, font, fmt(theirs), cAmount, ry, 0xFFCCCCCC);
                right(g, font, info.rank > 0 ? "#" + fmt(info.rank) : "-", cRank, ry, GOLD);

                if (ownTotal >= 0) {
                    long d = ownTotal - theirs;
                    String diff = (d >= 0 ? "+" : "-") + fmt(Math.abs(d)); // + = you are ahead
                    right(g, font, diff, cDiff, ry, d >= 0 ? GREEN : RED);
                } else {
                    right(g, font, "-", cDiff, ry, MUTED);
                }
            } else {
                right(g, font, info.status, cAmount, ry, MUTED);
            }
            ry += ROW_H;
        }
    }
}
