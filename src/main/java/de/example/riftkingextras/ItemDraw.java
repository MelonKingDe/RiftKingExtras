package de.example.riftkingextras;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/** Draws an item (and its count/durability bar) into the GUI. */
final class ItemDraw {
    private ItemDraw() {}

    static String status() {
        return "direct calls item(...) and itemDecorations(...)";
    }

    /** Draws the 16x16 item at (x, y). */
    static boolean item(GuiGraphicsExtractor g, ItemStack stack, int x, int y) {
        g.item(stack, x, y);
        return true;
    }

    /** Draws the stack count and the durability bar on top of the item. */
    static void decorations(GuiGraphicsExtractor g, Font font, ItemStack stack, int x, int y) {
        g.itemDecorations(font, stack, x, y);
    }
}
