package de.example.riftkingextras;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Matrix3x2fStack;

import java.util.List;
import java.util.Locale;

/**
 * The music HUD in the RiftKingExtras style: a card with cover, title, artist and progress, and below it the synced
 * lyrics that scroll smoothly in time with the song.
 */
public final class MusicHud {
    static final int W = 240;
    private static final int CARD_H = 50, GAP = 3, PITCH = 12, COVER = 40;

    // smooth scrolling of the lyrics
    private static float scroll = 0f;
    private static long lastFrameNanos = 0;
    private static String scrollKey = "";

    private static final List<LyricsService.Line> DEMO_LINES = List.of(
            new LyricsService.Line(0, "The lyrics show up here"),
            new LyricsService.Line(1000, "in time with your song"),
            new LyricsService.Line(2000, "no Spotify Premium needed"),
            new LyricsService.Line(3000, "they come from LRCLIB"),
            new LyricsService.Line(4000, "and scroll smoothly along"),
            new LyricsService.Line(5000, "line after line"),
            new LyricsService.Line(6000, "until the song ends"));

    private MusicHud() {}

    // ---------- size ----------

    private static int lyricsPanelHeight() {
        return MusicConfig.showLyrics ? MusicConfig.lyricLines * PITCH + 6 : 0;
    }

    static int width() { return W; }

    static int height() {
        return TrackerHud.HEADER_H + CARD_H + (MusicConfig.showLyrics ? GAP + lyricsPanelHeight() : 0) + 4;
    }

    static int scaledWidth() { return (int) Math.ceil(width() * MusicConfig.scale); }
    static int scaledHeight() { return (int) Math.ceil(height() * MusicConfig.scale); }

    // ---------- entry points ----------

    static void render(GuiGraphicsExtractor graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (!MusicConfig.enabled || mc.player == null || !MusicState.get().active()) return;
        if (mc.gui.screen() instanceof HudEditScreen e && e.kind() == HudKind.MUSIC) return; // preview instead
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        draw(graphics, HudKind.MUSIC.clampX(sw, MusicConfig.x), HudKind.MUSIC.clampY(sh, MusicConfig.y));
    }

    /** Draws the HUD with its top-left corner at (x, y). Without a song a sample is drawn (for the HUD editor). */
    static void draw(GuiGraphicsExtractor g, int x, int y) {
        MusicState.Snapshot live = MusicState.get();
        boolean demo = !live.active();
        MusicState.Snapshot s = demo
                ? new MusicState.Snapshot(true, "Song title", "Artist name", "Album", false, 83_000, 210_000, 0.5f, 0)
                : live;

        float scale = MusicConfig.scale;
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        drawContent(g, s, demo);
        pose.popMatrix();
    }

    // ---------- helpers ----------

    private static String mmss(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    /** Cuts the text to the width and adds "..." */
    private static String ellipsize(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        int dots = font.width("...");
        StringBuilder sb = new StringBuilder();
        int w = 0;
        for (int i = 0; i < text.length(); i++) {
            int cw = font.width(String.valueOf(text.charAt(i)));
            if (w + cw + dots > maxWidth) break;
            sb.append(text.charAt(i));
            w += cw;
        }
        return sb + "...";
    }

    /** A long title scrolls character by character (with a pause at the start); a short one stays. */
    private static String marquee(Font font, String text, int maxWidth, long now) {
        if (font.width(text) <= maxWidth) return text;
        String loop = text + "     " + text;
        int cycle = text.length() + 5;
        long step = (now / 140) % (cycle + 12);
        int offset = (int) Math.max(0, step - 12); // hold at the start for about 1.7 seconds
        StringBuilder sb = new StringBuilder();
        int w = 0;
        for (int i = offset; i < loop.length(); i++) {
            int cw = font.width(String.valueOf(loop.charAt(i)));
            if (w + cw > maxWidth) break;
            sb.append(loop.charAt(i));
            w += cw;
        }
        return sb.toString();
    }

    // ---------- content ----------

    private static void drawContent(GuiGraphicsExtractor g, MusicState.Snapshot s, boolean demo) {
        Font font = Minecraft.getInstance().font;
        int accent = MusicConfig.accentArgb();
        long now = System.currentTimeMillis();
        int w = W, h = height();

        TrackerHud.rrect(g, 0, 0, w, h, 0xCC0E0E14);   // same panel as the other HUDs
        TrackerHud.border(g, 0, 0, w, h, 0x33FFFFFF);

        // header: brand, and on the right the state (paused, volume, or MUSIC)
        g.text(font, "RIFTKING", 8, 4, 0xFFFFFFFF, false);
        g.text(font, "EXTRAS", 8 + font.width("RIFTKING "), 4, 0xFFB060FF, false);
        String right;
        int rightColor = TrackerHud.MUTED;
        if (!s.playing() && !demo) {
            right = "PAUSED";
            rightColor = TrackerHud.GOLD;
        } else if (MusicState.volumeRecentlyChanged() && s.volume() >= 0) {
            right = "VOL " + Math.round(s.volume() * 100) + "%";
            rightColor = accent;
        } else {
            right = "MUSIC";
        }
        TrackerHud.right(g, font, right, w - 8, 4, rightColor);
        TrackerHud.rect(g, 6, TrackerHud.HEADER_H - 2, w - 6, TrackerHud.HEADER_H - 1, 0x22FFFFFF);

        // card: cover, title, artist, progress
        int cx = TrackerHud.PAD, cx2 = w - TrackerHud.PAD, cy = TrackerHud.HEADER_H;
        TrackerHud.rrect(g, cx, cy, cx2, cy + CARD_H, TrackerHud.withAlpha(accent, 0x26));
        TrackerHud.rect(g, cx, cy + 1, cx + 3, cy + CARD_H - 1, accent);

        int textX = cx + 10;
        if (MusicConfig.showArt) {
            int coverX = cx + 7, coverY = cy + 5;
            if (MusicArt.ready() && !demo) {
                g.blit(RenderPipelines.GUI_TEXTURED, MusicArt.ID, coverX, coverY, 0f, 0f, COVER, COVER,
                        MusicArt.SIZE, MusicArt.SIZE, MusicArt.SIZE, MusicArt.SIZE);
            } else {
                TrackerHud.rrect(g, coverX, coverY, coverX + COVER, coverY + COVER, TrackerHud.withAlpha(accent, 0x44));
                g.text(font, "\u266A", coverX + (COVER - font.width("\u266A")) / 2, coverY + (COVER - 8) / 2, 0xFFFFFFFF, true);
            }
            TrackerHud.border(g, coverX - 1, coverY - 1, coverX + COVER + 1, coverY + COVER + 1, 0x33FFFFFF);
            textX = coverX + COVER + 8;
        }
        int textRight = cx2 - 6;
        int maxW = textRight - textX;

        g.text(font, marquee(font, s.title(), maxW, now), textX, cy + 6, 0xFFFFFFFF, true);
        g.text(font, ellipsize(font, s.artist(), maxW), textX, cy + 17, TrackerHud.LIGHT, false);

        long position = demo ? s.positionMs() : s.position(now);
        double progress = s.durationMs() > 0 ? Math.min(1.0, position / (double) s.durationMs()) : 0;
        int barY = cy + 31;
        TrackerHud.rrect(g, textX, barY, textRight, barY + 4, 0x33FFFFFF);
        int fill = (int) Math.round((textRight - textX) * progress);
        if (fill >= 4) TrackerHud.rrect(g, textX, barY, textX + fill, barY + 4, accent);
        else if (fill > 0) TrackerHud.rect(g, textX, barY, textX + fill, barY + 4, accent);

        g.text(font, mmss(position), textX, cy + 38, TrackerHud.MUTED, false);
        TrackerHud.right(g, font, mmss(s.durationMs()), textRight, cy + 38, TrackerHud.MUTED);

        // lyrics
        if (MusicConfig.showLyrics) {
            int ly = cy + CARD_H + GAP;
            int panelH = lyricsPanelHeight();
            TrackerHud.rrect(g, cx, ly, cx2, ly + panelH, 0x14FFFFFF);
            drawLyrics(g, font, s, demo, position, cx + 6, cx2 - 6, ly, panelH);
        }
    }

    private static void drawLyrics(GuiGraphicsExtractor g, Font font, MusicState.Snapshot s, boolean demo,
                                   long position, int left, int right, int top, int panelH) {
        List<LyricsService.Line> lines;
        int current;
        if (demo) {
            lines = DEMO_LINES;
            current = 2;
        } else {
            LyricsService.Status status = LyricsService.status();
            if (status != LyricsService.Status.READY) {
                String message = switch (status) {
                    case LOADING -> "Loading lyrics...";
                    case NONE -> "No synced lyrics found";
                    default -> "";
                };
                if (!message.isEmpty()) {
                    g.text(font, message, (left + right - font.width(message)) / 2, top + panelH / 2 - 4,
                            TrackerHud.MUTED, false);
                }
                return;
            }
            lines = LyricsService.lines();
            current = LyricsService.indexAt(lines, position + MusicConfig.lyricOffsetMs);
        }
        if (lines.isEmpty()) return;

        // smooth scroll towards the current line
        long nowNanos = System.nanoTime();
        float dt = lastFrameNanos == 0 ? 0 : Math.min(0.25f, (nowNanos - lastFrameNanos) / 1_000_000_000f);
        lastFrameNanos = nowNanos;
        String key = demo ? "demo" : s.key();
        float target = Math.max(0, current);
        if (!key.equals(scrollKey) || dt == 0) {
            scrollKey = key;
            scroll = target;
        } else {
            scroll += (target - scroll) * (float) (1 - Math.exp(-dt * 10));
        }

        int rows = MusicConfig.lyricLines;
        float reach = rows / 2f + 0.1f;
        float centerY = top + panelH / 2f;
        int maxTextW = right - left;
        Matrix3x2fStack pose = g.pose();

        int first = Math.max(0, (int) Math.floor(scroll - reach));
        int last = Math.min(lines.size() - 1, (int) Math.ceil(scroll + reach));
        for (int i = first; i <= last; i++) {
            float dist = i - scroll;
            float alpha = 1f - Math.abs(dist) / reach;
            if (alpha < 0.12f) continue;

            String text = lines.get(i).text();
            if (MusicConfig.profanityFilter) text = ProfanityFilter.apply(text);
            if (text.isBlank()) text = "\u266A"; // instrumental part

            // a long line is made smaller first, then cut
            int tw = font.width(text);
            float textScale = tw <= maxTextW ? 1f : Math.max(0.65f, maxTextW / (float) tw);
            if (tw * textScale > maxTextW) {
                text = ellipsize(font, text, (int) (maxTextW / textScale));
                tw = font.width(text);
            }

            boolean isCurrent = i == current;
            int rgb = isCurrent ? 0xFFFFFF : 0xB8C0C8;
            int color = ((int) (alpha * 255) << 24) | rgb;

            pose.pushMatrix();
            pose.translate((left + right) / 2f - tw * textScale / 2f, centerY + dist * PITCH - 4f * textScale);
            pose.scale(textScale, textScale);
            g.text(font, text, 0, 0, color, isCurrent);
            pose.popMatrix();
        }
    }
}
