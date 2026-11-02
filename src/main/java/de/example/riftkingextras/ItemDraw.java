package de.example.riftkingextras;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;

/**
 * Draws an item (and its count/durability bar) into the GUI. The two draw methods of the graphics class are
 * looked up by reflection, so a different method name in a Minecraft version cannot break the build.
 */
final class ItemDraw {
    private static Method itemMethod = null;
    private static Method decorationsMethod = null;
    private static boolean resolved = false;
    private static String status = "not used yet";

    private ItemDraw() {}

    private static void resolve(GuiGraphicsExtractor g) {
        resolved = true;
        for (Method m : g.getClass().getMethods()) {
            Class<?>[] p = m.getParameterTypes();
            String n = m.getName();
            if (itemMethod == null && p.length == 3 && p[0] == ItemStack.class && p[1] == int.class && p[2] == int.class
                    && (n.equals("item") || n.equals("renderItem") || n.equals("renderFakeItem"))) {
                itemMethod = m;
            }
            if (decorationsMethod == null && p.length == 4 && p[0] == Font.class && p[1] == ItemStack.class
                    && p[2] == int.class && p[3] == int.class
                    && (n.equals("itemDecorations") || n.equals("renderItemDecorations"))) {
                decorationsMethod = m;
            }
        }
        status = "item=" + (itemMethod == null ? "NOT FOUND" : itemMethod.getName())
                + ", decorations=" + (decorationsMethod == null ? "NOT FOUND" : decorationsMethod.getName());
    }

    static String status() {
        return status;
    }

    /** Draws the 16x16 item at (x, y). Returns false if no draw method was found. */
    static boolean item(GuiGraphicsExtractor g, ItemStack stack, int x, int y) {
        if (!resolved) resolve(g);
        if (itemMethod == null) return false;
        try {
            itemMethod.invoke(g, stack, x, y);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /** Draws the stack count and the durability bar on top of the item. */
    static void decorations(GuiGraphicsExtractor g, Font font, ItemStack stack, int x, int y) {
        if (!resolved) resolve(g);
        if (decorationsMethod == null) return;
        try {
            decorationsMethod.invoke(g, font, stack, x, y);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // no count shown
        }
    }
}
