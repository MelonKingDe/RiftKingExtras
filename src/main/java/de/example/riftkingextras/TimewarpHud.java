package de.example.riftkingextras;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2fStack;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Second HUD in the same style as the tracker: the Minion Time Warp cooldown. */
public final class TimewarpHud {
    private static final int W = 200;
    private static final int CARD_H = 42;
    private static final int TEAL = 0xFF2DD4BF;
    private static final DateTimeFormatter READY_AT =
            DateTimeFormatter.ofPattern("EEE HH:mm", Locale.ENGLISH).withZone(ZoneId.systemDefault());

    private TimewarpHud() {}

    static int width() { return W; }
    static int height() { return TrackerHud.HEADER_H + CARD_H + 6; }
    static int scaledWidth() { return (int) Math.ceil(width() * RkeConfig.twScale); }
    static int scaledHeight() { return (int) Math.ceil(height() * RkeConfig.twScale); }

    static void render(GuiGraphicsExtractor graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !RkeConfig.showTimewarp) return;
        if (mc.gui.screen() instanceof HudEditScreen e && e.kind() == HudKind.TIMEWARP) return; // preview instead
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        draw(graphics, HudKind.TIMEWARP.clampX(sw, RkeConfig.twX), HudKind.TIMEWARP.clampY(sh, RkeConfig.twY));
    }

    /** Draws the HUD with its top-left corner at (x, y), scaled by the Timewarp HUD scale. */
    static void draw(GuiGraphicsExtractor g, int x, int y) {
        float s = RkeConfig.twScale;
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(s, s);
        drawContent(g);
        pose.popMatrix();
    }

    private static String fmtHms(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }

    private static void drawContent(GuiGraphicsExtractor g) {
        Font font = Minecraft.getInstance().font;
        Matrix3x2fStack pose = g.pose();
        int w = width(), h = height();

        TrackerHud.rrect(g, 0, 0, w, h, 0xCC0E0E14);   // panel, same as the tracker
        TrackerHud.border(g, 0, 0, w, h, 0x33FFFFFF);

        // header
        g.text(font, "RIFTKING", 8, 4, 0xFFFFFFFF, false);
        g.text(font, "EXTRAS", 8 + font.width("RIFTKING "), 4, 0xFFB060FF, false);
        TrackerHud.right(g, font, "TIMEWARP", w - 8, 4, TrackerHud.MUTED);
        TrackerHud.rect(g, 6, TrackerHud.HEADER_H - 2, w - 6, TrackerHud.HEADER_H - 1, 0x22FFFFFF);

        // card
        int cx = TrackerHud.PAD, cx2 = w - TrackerHud.PAD, cy = TrackerHud.HEADER_H;
        int tx = cx + 8, rx = cx2 - 5;
        boolean running = TimewarpTracker.running();
        int accent = running ? TEAL : TrackerHud.GREEN;

        TrackerHud.rrect(g, cx, cy, cx2, cy + CARD_H, TrackerHud.withAlpha(accent, 0x26));
        TrackerHud.rect(g, cx, cy + 1, cx + 3, cy + CARD_H - 1, accent);

        g.text(font, "MINION TIME WARP", tx, cy + 3, TrackerHud.LIGHT, false);

        // status pill (READY pulses)
        String status = running ? "COOLDOWN" : "READY";
        int statusColor = running ? TrackerHud.GOLD : TrackerHud.GREEN;
        if (!running) {
            boolean bright = (System.currentTimeMillis() / 600) % 2 == 0;
            statusColor = bright ? TrackerHud.GREEN : TrackerHud.withAlpha(TrackerHud.GREEN, 0xA0);
        }
        int sw = font.width(status);
        TrackerHud.rrect(g, rx - sw - 4, cy + 2, rx + 1, cy + 12, 0x33FFFFFF);
        g.text(font, status, rx - sw - 1, cy + 3, statusColor, false);

        // remaining time (bigger) or READY
        String big = running ? fmtHms(TimewarpTracker.remainingMs()) : "READY";
        pose.pushMatrix();
        pose.translate(tx, cy + 13);
        pose.scale(1.25f, 1.25f);
        g.text(font, big, 0, 0, running ? 0xFFFFFFFF : TrackerHud.GREEN, true);
        pose.popMatrix();

        // when it is ready again
        String info = running
                ? "Ready " + READY_AT.format(Instant.ofEpochMilli(RkeConfig.timewarpEndMs))
                : "No cooldown running";
        g.text(font, info, tx, cy + 27, TrackerHud.MUTED, false);

        // progress of the 48 h
        int barX1 = cx + 4, barX2 = cx2 - 4, barY = cy + CARD_H - 5;
        TrackerHud.rect(g, barX1, barY, barX2, barY + 3, 0x22FFFFFF);
        int fill = (int) ((barX2 - barX1) * TimewarpTracker.progress());
        if (fill > 0) TrackerHud.rect(g, barX1, barY, barX1 + fill, barY + 3, TrackerHud.withAlpha(accent, 0xE0));
    }
}
