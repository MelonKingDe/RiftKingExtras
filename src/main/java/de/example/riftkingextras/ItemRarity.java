package de.example.riftkingextras;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;
import java.util.Locale;

/** Finds the SkyBlock rarity of an item from the last lines of its lore ("LEGENDARY SWORD") and gives its color. */
final class ItemRarity {
    private record Rarity(String word, int rgb) {}

    // "VERY SPECIAL" has to come before "SPECIAL"
    private static final Rarity[] RARITIES = {
            new Rarity("UNCOMMON", 0x55FF55),
            new Rarity("COMMON", 0xFFFFFF),
            new Rarity("RARE", 0x5555FF),
            new Rarity("EPIC", 0xAA00AA),
            new Rarity("LEGENDARY", 0xFFAA00),
            new Rarity("MYTHIC", 0xFF55FF),
            new Rarity("DIVINE", 0x55FFFF),
            new Rarity("VERY SPECIAL", 0xFF5555),
            new Rarity("SPECIAL", 0xFF5555),
            new Rarity("ULTIMATE", 0xAA0000),
            new Rarity("SUPREME", 0xAA0000),
            new Rarity("ADMIN", 0xAA0000)};

    private ItemRarity() {}

    /** Frame of an empty slot, and of an item that has no rarity line (as in RiftKingInv). */
    static final int EMPTY = 0xFF3A3A44, FALLBACK = 0xFF9A9A9A;

    /** The frame color for any slot: rarity color, gray for an item without rarity, dark gray for an empty slot. */
    static int frameColor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return EMPTY;
        int rarity = colorOf(stack);
        return rarity != 0 ? rarity : FALLBACK;
    }

    /** The rarity color as ARGB, or 0 if the item has no rarity line. */
    static int colorOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return 0;
        List<Component> lines = lore.lines();
        // the rarity is in one of the last lines
        for (int i = lines.size() - 1; i >= Math.max(0, lines.size() - 4); i--) {
            String text = ScoreboardModel.plain(lines.get(i)).toUpperCase(Locale.ROOT);
            if (text.startsWith("A ")) text = text.substring(2); // "A SHINY ..." style lines
            if (text.startsWith("SHINY ")) text = text.substring(6);
            for (Rarity r : RARITIES) {
                if (text.startsWith(r.word())) return 0xFF000000 | r.rgb();
            }
        }
        return 0;
    }
}
