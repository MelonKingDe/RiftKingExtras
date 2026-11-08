package de.example.riftkingextras;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2fStack;

import java.lang.management.ManagementFactory;
import java.text.NumberFormat;
import java.util.Locale;

/** The Social XP HUD: coop Social XP (from Elite), the XP gained since the game started, and the time played. */
public final class SocialHud {
    private static final int W = 200, CARD_H = 40;
    private static final int PINK = 0xFFFF7BC8;

    /** When the game started (the start of the Java process), so the time includes the loading. */
    private static final long GAME_START;

    static {
        long start = System.currentTimeMillis();
        try {
            start = ManagementFactory.getRuntimeMXBean().getStartTime();
        } catch (Throwable ignored) {
            // no management module: the time then counts from the first use of this HUD
        }
        GAME_START = start;
    }

    private SocialHud() {}

    static int width() { return W; }
    static int height() { return TrackerHud.HEADER_H + CARD_H + 5; }
    static int scaledWidth() { return (int) Math.ceil(width() * RkeConfig.socScale); }
    static int scaledHeight() { return (int) Math.ceil(height() * RkeConfig.socScale); }

    static void render(GuiGraphicsExtractor graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (!RkeConfig.showSocial || mc.player == null) return;
        if (mc.gui.screen() instanceof HudEditScreen e && e.kind() == HudKind.SOCIAL) return; // preview instead
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        draw(graphics, HudKind.SOCIAL.clampX(sw, RkeConfig.socX), HudKind.SOCIAL.clampY(sh, RkeConfig.socY));
    }

    static void draw(GuiGraphicsExtractor g, int x, int y) {
        float s = RkeConfig.socScale;
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(s, s);
        drawContent(g);
        pose.popMatrix();
    }

    private static String fmt(long v) {
        return NumberFormat.getIntegerInstance(Locale.US).format(v);
    }

    private static String hms(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }

    private static void drawContent(GuiGraphicsExtractor g) {
        Font font = Minecraft.getInstance().font;
        Matrix3x2fStack pose = g.pose();
        TrackedCollection social = SocialXp.DATA;
        int w = W, h = height();

        TrackerHud.rrect(g, 0, 0, w, h, 0xCC0E0E14);   // same panel as the other HUDs
        TrackerHud.border(g, 0, 0, w, h, 0x33FFFFFF);

        // header
        g.text(font, "RIFTKING", 8, 4, 0xFFFFFFFF, false);
        g.text(font, "EXTRAS", 8 + font.width("RIFTKING "), 4, 0xFFB060FF, false);
        long cal = social.calibratedMs;
        String sync = cal == 0 ? "syncing..." : "synced " + ((System.currentTimeMillis() - cal) / 60000) + "m ago";
        TrackerHud.right(g, font, sync, w - 8, 4, TrackerHud.MUTED);
        TrackerHud.rect(g, 6, TrackerHud.HEADER_H - 2, w - 6, TrackerHud.HEADER_H - 1, 0x22FFFFFF);

        // card
        int cx = TrackerHud.PAD, cx2 = w - TrackerHud.PAD, cy = TrackerHud.HEADER_H;
        int tx = cx + 8, rx = cx2 - 5;
        TrackerHud.rrect(g, cx, cy, cx2, cy + CARD_H, TrackerHud.withAlpha(PINK, 0x26));
        TrackerHud.rect(g, cx, cy + 1, cx + 3, cy + CARD_H - 1, PINK);

        g.text(font, "SOCIAL XP (COOP)", tx, cy + 3, TrackerHud.LIGHT, false);

        String rank = social.rank > 0 ? "#" + fmt(social.rank) : "#-";
        int rw = font.width(rank);
        TrackerHud.rrect(g, rx - rw - 4, cy + 2, rx + 1, cy + 12, 0x33FFFFFF);
        g.text(font, rank, rx - rw - 1, cy + 3, TrackerHud.GOLD, false);

        // total (bigger) and the gain since the game started
        long total = social.total();
        pose.pushMatrix();
        pose.translate(tx, cy + 13);
        pose.scale(1.25f, 1.25f);
        g.text(font, total >= 0 ? fmt(total) : "loading...", 0, 0, 0xFFFFFFFF, true);
        pose.popMatrix();
        TrackerHud.right(g, font, "+" + fmt(social.sessionGain()), rx, cy + 16, TrackerHud.GREEN);

        // time played since the game started, and the XP per hour of that time
        long played = System.currentTimeMillis() - GAME_START;
        double hours = Math.max(0.01, played / 3_600_000.0);
        g.text(font, "Played " + hms(played), tx, cy + 28, TrackerHud.MUTED, false);
        TrackerHud.right(g, font, fmt((long) (social.sessionGain() / hours)) + "/h", rx, cy + 28, TrackerHud.MUTED);
    }
}
