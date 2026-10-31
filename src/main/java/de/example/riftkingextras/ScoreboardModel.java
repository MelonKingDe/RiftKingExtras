package de.example.riftkingextras;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.text.NumberFormat;
import java.text.Normalizer;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Sorts the vanilla scoreboard lines into elements and builds the lines that the HUD shows. */
final class ScoreboardModel {
    static final int SIDE_PAD = 8;
    static final int SEP_H = 5;
    static final int LINE_H = 9;
    static final int TOP = 3;
    static final int BOTTOM = 4;
    static final int MIN_W = 110;

    /** One line of the HUD: a text or a separator. */
    record Line(Component text, boolean separator) {
        static Line sep() { return new Line(null, true); }
        static Line of(Component text) { return new Line(text, false); }
    }

    /** Everything needed to draw the HUD, with its size (unscaled). */
    record Snapshot(Component title, List<Line> lines, int width, int height) {}

    private static final Pattern LOBBY = Pattern.compile("^\\d{1,2}/\\d{1,2}/\\d{2,4}\\b.*");
    private static final Pattern SB_DATE =
            Pattern.compile("^(Early |Late )?(Spring|Summer|Autumn|Winter)\\s+\\d{1,2}(st|nd|rd|th)\\b.*");
    private static final Pattern SB_TIME = Pattern.compile("^\\d{1,2}:\\d{2}\\s?(am|pm)\\b.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern AREA = Pattern.compile("^[\u23E3\u0444].*");
    private static final Pattern CURRENCY =
            Pattern.compile("^[A-Z][A-Za-z' ]{1,24}:\\s*\\d[\\d,.]*[kKmMbB]?(\\s*\\(.*\\))?$");
    private static final Pattern NUMBER = Pattern.compile("(\\d[\\d,]*(?:\\.\\d+)?)([kKmMbB]?)");
    private static final String[] EVENT_STARTS = {
            "Event", "Dark Auction", "Jacob's Contest", "Election", "Fishing Festival", "Spooky Festival",
            "Season of Jerry", "New Year", "Starting in", "Mining Fiesta", "Carnival", "Kuudra", "Dojo"};

    private static final String SMALL_CAPS =
            "\u1D00\u0299\u1D04\u1D05\u1D07\uA730\u0262\u029C\u026A\u1D0A\u1D0B\u029F\u1D0D\u0274\u1D0F\u1D18"
                    + "\u01EB\u0280\uA731\u1D1B\u1D1C\u1D20\u1D21x\u028F\u1D22";
    private static final String LATIN = "abcdefghijklmnopqrstuvwxyz";

    private ScoreboardModel() {}

    // ---------- classification ----------

    /**
     * The text of a scoreboard line as plain, normal text: color codes that are part of the text are removed,
     * fancy letters (small caps, full width, math fonts) become normal letters, odd spaces become spaces.
     * Hypixel puts such characters into its lines, so matching on the raw text misses them.
     */
    static String plain(Component line) {
        String s = line.getString();
        s = s.replaceAll("\u00A7.", "");                  // legacy color codes inside the text
        s = Normalizer.normalize(s, Normalizer.Form.NFKC);
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int small = SMALL_CAPS.indexOf(c);
            sb.append(small >= 0 ? LATIN.charAt(small) : c);
        }
        s = sb.toString().replaceAll("[\\p{Cf}\\u00A0\\u2007\\u202F]", " "); // invisible and non-breaking chars
        return s.strip();
    }

    /** The element a (non-empty) line belongs to, or null if it matches nothing known. */
    static SbElement classify(String plain) {
        String t = plain.trim();
        String lower = t.toLowerCase(Locale.ROOT);
        if (lower.contains("hypixel.net") || lower.startsWith("www.")) return SbElement.FOOTER;
        if (LOBBY.matcher(t).matches()) return SbElement.LOBBY;
        if (SB_DATE.matcher(t).matches()) return SbElement.DATE;
        if (SB_TIME.matcher(t).matches()) return SbElement.TIME;
        if (AREA.matcher(t).matches()) return SbElement.AREA;
        if (t.startsWith("Purse:") || t.startsWith("Piggy:")) return SbElement.PURSE;
        if (t.startsWith("Bits:")) return SbElement.BITS;
        if (t.startsWith("Slayer Quest") || t.startsWith("Slayer")) return SbElement.SLAYER;
        if (t.startsWith("Objective") || t.startsWith("Quest")) return SbElement.OBJECTIVE;
        for (String start : EVENT_STARTS) {
            if (t.startsWith(start)) return SbElement.EVENT;
        }
        if (CURRENCY.matcher(t).matches()) return SbElement.CURRENCY;
        return null;
    }

    // ---------- number format ----------

    private record Seg(Style style, String text) {}

    /** Rewrites the number of a "Name: 1,234" line (mode 1 = short, 2 = long); everything else stays as it is. */
    static Component reformat(Component line, int mode) {
        if (mode == 0) return line;

        List<Seg> segs = new ArrayList<>();
        line.visit((style, text) -> {
            segs.add(new Seg(style, text));
            return Optional.<Boolean>empty();
        }, Style.EMPTY);

        MutableComponent out = Component.literal("");
        boolean seenColon = false;
        boolean done = false;
        for (Seg seg : segs) {
            String text = seg.text();
            int searchFrom = -1;
            if (seenColon) {
                searchFrom = 0;
            } else {
                int colon = text.indexOf(':');
                if (colon >= 0) {
                    seenColon = true;
                    searchFrom = colon + 1;
                }
            }
            if (!done && searchFrom >= 0) {
                Matcher m = NUMBER.matcher(text);
                if (m.find(searchFrom)) {
                    String replacement = formatNumber(m.group(1), m.group(2), mode);
                    if (replacement != null) {
                        text = text.substring(0, m.start()) + replacement + text.substring(m.end());
                        done = true;
                    }
                }
            }
            out.append(Component.literal(text).withStyle(seg.style()));
        }
        return out;
    }

    private static String formatNumber(String digits, String suffix, int mode) {
        try {
            double v = Double.parseDouble(digits.replace(",", ""));
            switch (suffix.toLowerCase(Locale.ROOT)) {
                case "k" -> v *= 1_000;
                case "m" -> v *= 1_000_000;
                case "b" -> v *= 1_000_000_000;
                default -> { }
            }
            if (mode == 2) return NumberFormat.getIntegerInstance(Locale.US).format(Math.round(v));
            if (v >= 1e9) return trim(v / 1e9) + "B";
            if (v >= 1e6) return trim(v / 1e6) + "M";
            if (v >= 1e3) return trim(v / 1e3) + "k";
            return String.valueOf(Math.round(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String trim(double v) {
        return String.format(Locale.US, "%.2f", v).replaceAll("\\.?0+$", "");
    }

    // ---------- extra lines ----------

    private static Component irlLine() {
        String pattern = ScoreboardConfig.time24h ? "HH:mm" : "h:mm a";
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH));
        return Component.literal("Real time: ").withColor(0x9AA0A6)
                .append(Component.literal(time).withColor(0xFFFFFF));
    }

    private static Component timewarpLine() {
        MutableComponent line = Component.literal("Time Warp: ").withColor(0x9AA0A6);
        if (TimewarpTracker.running()) {
            long s = TimewarpTracker.remainingMs() / 1000;
            String hms = String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
            return line.append(Component.literal(hms).withColor(0xE8C468));
        }
        return line.append(Component.literal("READY").withColor(0x7BD88F));
    }

    // ---------- build ----------

    /** Builds the HUD content from the title and lines of the vanilla scoreboard. */
    static Snapshot build(Component hypixelTitle, List<Component> board) {
        Font font = Minecraft.getInstance().font;

        // 1. sort the board lines into elements (unknown lines inherit the kind of their block)
        Map<SbElement, List<Component>> groups = new EnumMap<>(SbElement.class);
        SbElement blockKind = null;
        for (Component c : board) {
            String plain = plain(c);
            if (plain.isEmpty()) {
                blockKind = null;
                continue;
            }
            SbElement kind = classify(plain);
            if (kind == null) {
                kind = blockKind != null ? blockKind : SbElement.OTHER;
            } else if (kind == SbElement.OBJECTIVE || kind == SbElement.SLAYER || kind == SbElement.EVENT) {
                blockKind = kind;
            }
            groups.computeIfAbsent(kind, k -> new ArrayList<>()).add(c);
        }

        // 2. lay them out in the configured order
        List<Line> lines = new ArrayList<>();
        for (ScoreboardConfig.Entry entry : ScoreboardConfig.entries) {
            SbElement element = entry.element();
            if (element == null || !entry.enabled) continue;
            switch (element) {
                case SEPARATOR -> lines.add(Line.sep());
                case IRL_TIME -> lines.add(Line.of(irlLine()));
                case TIMEWARP -> lines.add(Line.of(timewarpLine()));
                case FOOTER -> {
                    if (ScoreboardConfig.useCustomFooter && !ScoreboardConfig.customFooter.isBlank()) {
                        lines.add(Line.of(AmpText.parse(ScoreboardConfig.customFooter)));
                    } else {
                        for (Component c : groups.getOrDefault(element, List.of())) lines.add(Line.of(c));
                    }
                }
                case PURSE, BITS, CURRENCY -> {
                    for (Component c : groups.getOrDefault(element, List.of())) {
                        lines.add(Line.of(reformat(c, ScoreboardConfig.numberFormat)));
                    }
                }
                default -> {
                    for (Component c : groups.getOrDefault(element, List.of())) lines.add(Line.of(c));
                }
            }
        }

        // 3. clean up separators
        if (ScoreboardConfig.condenseSeparators) {
            List<Line> condensed = new ArrayList<>();
            for (Line l : lines) {
                boolean prevSep = !condensed.isEmpty() && condensed.get(condensed.size() - 1).separator();
                if (!(l.separator() && prevSep)) condensed.add(l);
            }
            lines = condensed;
        }
        if (ScoreboardConfig.hideEdgeSeparators) {
            while (!lines.isEmpty() && lines.get(0).separator()) lines.remove(0);
            while (!lines.isEmpty() && lines.get(lines.size() - 1).separator()) lines.remove(lines.size() - 1);
        }

        // 4. title and size
        Component title = hypixelTitle;
        if (!ScoreboardConfig.useHypixelTitle && !ScoreboardConfig.customTitle.isBlank()) {
            title = AmpText.parse(ScoreboardConfig.customTitle);
        }
        int width = Math.max(MIN_W, font.width(title) + 2 * SIDE_PAD);
        int height = TrackerHud.HEADER_H + TOP;
        for (Line l : lines) {
            if (l.separator()) {
                height += SEP_H;
            } else {
                width = Math.max(width, font.width(l.text()) + 2 * SIDE_PAD);
                height += LINE_H + ScoreboardConfig.lineSpacing;
            }
        }
        height += BOTTOM;
        return new Snapshot(title, lines, width, height);
    }

    /** Sample board that is shown while positioning the HUD outside of SkyBlock. */
    static Snapshot demo() {
        List<Component> board = List.of(
                AmpText.parse("&70/31/26 &8m123A"),
                Component.literal(""),
                AmpText.parse("&7Late Autumn 14th"),
                AmpText.parse("&77:20pm &b\u263D"),
                AmpText.parse("&7\u23E3 &bVillage"),
                Component.literal(""),
                AmpText.parse("Purse: &61,234,567"),
                AmpText.parse("Bits: &b12,345"),
                Component.literal(""),
                AmpText.parse("&ewww.hypixel.net"));
        return build(AmpText.parse("&e&lSKYBLOCK"), board);
    }
}
