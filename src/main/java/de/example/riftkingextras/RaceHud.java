package de.example.riftkingextras;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2fStack;

import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * The collection race HUD in the RiftKingExtras style: your amount against your rival's, the gap or lead, how much you
 * need per day to catch up before the deadline, the days left and your progress.
 */
public final class RaceHud {
    private static final int W = 210, CARD_H = 112;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
    private static final int PURPLE = 0xFFB060FF;

    private RaceHud() {}

    static int width() { return W; }
    static int height() { return TrackerHud.HEADER_H + CARD_H + 5; }
    static int scaledWidth() { return (int) Math.ceil(width() * RaceConfig.scale); }
    static int scaledHeight() { return (int) Math.ceil(height() * RaceConfig.scale); }

    static void render(GuiGraphicsExtractor graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (!RaceConfig.enabled || mc.player == null) return;
        if (mc.gui.screen() instanceof HudEditScreen e && e.kind() == HudKind.RACE) return; // preview instead
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        draw(graphics, HudKind.RACE.clampX(sw, RaceConfig.x), HudKind.RACE.clampY(sh, RaceConfig.y));
    }

    static void draw(GuiGraphicsExtractor g, int x, int y) {
        float s = RaceConfig.scale;
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

    private static String cut(String text, int max) {
        return text.length() > max ? text.substring(0, max - 1) + "." : text;
    }

    private static void drawContent(GuiGraphicsExtractor g) {
        Font font = Minecraft.getInstance().font;
        int w = W, h = height();

        TrackerHud.rrect(g, 0, 0, w, h, 0xCC0E0E14);   // same panel as the other HUDs
        TrackerHud.border(g, 0, 0, w, h, 0x33FFFFFF);
        GlowBorder.decorate(g, 0, 0, w, h, "race");

        g.text(font, "RIFTKING", 8, 4, 0xFFFFFFFF, false);
        g.text(font, "EXTRAS", 8 + font.width("RIFTKING "), 4, PURPLE, false);
        TrackerHud.right(g, font, "COLLECTION RACE", w - 8, 4, TrackerHud.MUTED);
        TrackerHud.rect(g, 6, TrackerHud.HEADER_H - 2, w - 6, TrackerHud.HEADER_H - 1, 0x22FFFFFF);

        // the values: from Elite, your own amount live if the collection is tracked
        CollectionRace.View view = CollectionRace.view();
        boolean demo = view == null;
        String title = view != null ? view.title() : RaceConfig.collection;
        long mine = view != null ? view.mine() : -1;
        long rivalAmount = view != null ? view.rivalAmount() : -1;
        String rivalName = view != null ? view.rivalName() : RaceConfig.rival;
        int myRank = view != null ? view.myRank() : 0;

        if (view != null && !view.boardId().isEmpty()) {
            TrackedCollection tracked = CollectionTracker.bySlug(view.boardId());
            if (tracked != null && tracked.total() >= 0) mine = tracked.total();
        }
        if (demo && Minecraft.getInstance().gui.screen() != null) { // a sample in the HUD editor
            mine = 477_851;
            rivalAmount = 515_788;
            rivalName = "Rival";
            myRank = 2;
        }

        int cx = TrackerHud.PAD, cx2 = w - TrackerHud.PAD, cy = TrackerHud.HEADER_H;
        int tx = cx + 8, rx = cx2 - 5;
        boolean known = mine >= 0 && rivalAmount >= 0;
        long gap = known ? rivalAmount - mine : 0;
        boolean behind = gap > 0;
        int stateColor = !known ? TrackerHud.MUTED : behind ? TrackerHud.RED : TrackerHud.GREEN;

        TrackerHud.rrect(g, cx, cy, cx2, cy + CARD_H, TrackerHud.withAlpha(PURPLE, 0x26));
        TrackerHud.rect(g, cx, cy + 1, cx + 3, cy + CARD_H - 1, stateColor);

        // title and your rank
        g.text(font, cut(title.toUpperCase(Locale.ROOT), 22), tx, cy + 3, TrackerHud.LIGHT, false);
        String rank = myRank > 0 ? "#" + myRank : "#-";
        int rw = font.width(rank);
        TrackerHud.rrect(g, rx - rw - 4, cy + 2, rx + 1, cy + 12, 0x33FFFFFF);
        g.text(font, rank, rx - rw - 1, cy + 3, TrackerHud.GOLD, false);

        // you and the rival
        g.text(font, "You", tx, cy + 16, 0xFFFFFFFF, true);
        TrackerHud.right(g, font, mine >= 0 ? fmt(mine) : "...", rx, cy + 16, 0xFFFFFFFF);
        g.text(font, cut(rivalName, 16), tx, cy + 28, 0xFFFF8A8A, true);
        TrackerHud.right(g, font, rivalAmount >= 0 ? fmt(rivalAmount) : "...", rx, cy + 28, 0xFFFF8A8A);

        // gap (or lead) and what you need per day
        LocalDate deadline = RaceConfig.deadlineDate();
        long daysLeft = Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(), deadline));
        long needPerDay = known && behind ? (long) Math.ceil(gap / (double) Math.max(1, daysLeft)) : 0;

        int cardW = (cx2 - cx - 12 - 4) / 2;
        int cardY = cy + 42;
        int leftX = cx + 6, rightX = cx + 6 + cardW + 4;
        TrackerHud.rrect(g, leftX, cardY, leftX + cardW, cardY + 24, 0x22FFFFFF);
        TrackerHud.rrect(g, rightX, cardY, rightX + cardW, cardY + 24, 0x22FFFFFF);
        g.text(font, behind || !known ? "GAP" : "LEAD", leftX + 5, cardY + 3, TrackerHud.MUTED, false);
        g.text(font, known ? fmt(Math.abs(gap)) : "-", leftX + 5, cardY + 13, stateColor, true);
        g.text(font, "NEED / DAY", rightX + 5, cardY + 3, TrackerHud.MUTED, false);
        g.text(font, known ? (behind ? fmt(needPerDay) : "-") : "-", rightX + 5, cardY + 13, TrackerHud.GOLD, true);

        // days left
        g.text(font, "Days left", tx, cy + 74, TrackerHud.MUTED, false);
        String days = daysLeft == 0 ? "deadline passed" : String.valueOf(daysLeft);
        TrackerHud.right(g, font, days, rx, cy + 74, daysLeft == 0 ? TrackerHud.RED : 0xFFFFFFFF);

        // progress: your amount against the rival's
        double progress = known && rivalAmount > 0 ? Math.min(1.0, mine / (double) rivalAmount) : 0;
        int barX1 = tx, barX2 = rx, barY = cy + 86;
        TrackerHud.rrect(g, barX1, barY, barX2, barY + 6, 0x33FFFFFF);
        int fill = (int) Math.round((barX2 - barX1) * progress);
        if (fill >= 4) TrackerHud.rrect(g, barX1, barY, barX1 + fill, barY + 6, PURPLE);
        else if (fill > 0) TrackerHud.rect(g, barX1, barY, barX1 + fill, barY + 6, PURPLE);
        g.text(font, "Progress", tx, cy + 95, TrackerHud.MUTED, false);
        TrackerHud.right(g, font, known ? String.format(Locale.US, "%.2f%%", progress * 100) : "-", rx, cy + 95, TrackerHud.LIGHT);

        // footer: when it was updated, or what went wrong
        String footer;
        int footerColor = TrackerHud.MUTED;
        if (view != null && view.error() != null) {
            footer = cut(view.error(), 34);
            footerColor = TrackerHud.RED;
        } else if (view != null && view.updatedMs() > 0) {
            footer = "Updated " + TIME.format(Instant.ofEpochMilli(view.updatedMs())) + "  -  goal " + RaceConfig.SHOWN.format(deadline);
        } else {
            footer = "Loading...";
        }
        g.text(font, footer, tx, cy + 104 - 2, footerColor, false);
    }
}
