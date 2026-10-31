package de.example.riftkingextras;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/** Turns text with &-color codes (&6 gold, &l bold, &r reset ...) into a styled Component. */
final class AmpText {
    private static final String COLOR_CODES = "0123456789abcdef";
    private static final int[] COLORS = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};

    private AmpText() {}

    static MutableComponent parse(String text) {
        MutableComponent out = Component.literal("");
        Style style = Style.EMPTY;
        StringBuilder buf = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c == '&' || c == '\u00A7') && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                int color = COLOR_CODES.indexOf(code);
                if (color >= 0 || "lonmkr".indexOf(code) >= 0) {
                    flush(out, buf, style);
                    if (color >= 0) {
                        style = Style.EMPTY.withColor(COLORS[color]); // a color code resets the formatting
                    } else {
                        switch (code) {
                            case 'l' -> style = style.withBold(true);
                            case 'o' -> style = style.withItalic(true);
                            case 'n' -> style = style.withUnderlined(true);
                            case 'm' -> style = style.withStrikethrough(true);
                            case 'k' -> style = style.withObfuscated(true);
                            default -> style = Style.EMPTY;
                        }
                    }
                    i++;
                    continue;
                }
            }
            buf.append(c);
        }
        flush(out, buf, style);
        return out;
    }

    private static void flush(MutableComponent out, StringBuilder buf, Style style) {
        if (buf.length() == 0) return;
        out.append(Component.literal(buf.toString()).withStyle(style));
        buf.setLength(0);
    }
}
