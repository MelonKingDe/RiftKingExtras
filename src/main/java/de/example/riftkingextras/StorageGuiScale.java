package de.example.riftkingextras;

import net.minecraft.client.Minecraft;

/**
 * A separate GUI size for the storage overlay only. While the storage screen is open, the GUI scale of the game is set
 * to the chosen value (a smaller scale shows more pages at once); when you leave the storage it goes back. The option
 * is read and set by reflection, so a different name in a Minecraft version cannot break the build.
 */
final class StorageGuiScale {
    private static boolean applied = false;
    private static int original = 0;
    private static int outsideTicks = 0;

    private StorageGuiScale() {}

    /** Called every client tick. */
    static void tick(Minecraft mc) {
        boolean inStorage = mc.gui.screen() instanceof StorageScreen;
        if (inStorage) {
            outsideTicks = 0;
            if (!applied && RkeConfig.storageGuiScale > 0) apply(mc);
        } else if (applied && ++outsideTicks > 6) { // a short pause between two pages must not flip the scale back and forth
            restore(mc);
        }
    }

    private static void apply(Minecraft mc) {
        Integer current = read(mc);
        if (current == null || current == RkeConfig.storageGuiScale) return;
        original = current;
        if (write(mc, RkeConfig.storageGuiScale)) applied = true;
    }

    /** Puts the GUI scale of the game back (also called when the game closes). */
    static void restore(Minecraft mc) {
        if (!applied) return;
        applied = false;
        outsideTicks = 0;
        write(mc, original);
    }

    private static Object option(Minecraft mc) throws ReflectiveOperationException {
        return mc.options.getClass().getMethod("guiScale").invoke(mc.options);
    }

    private static Integer read(Minecraft mc) {
        try {
            Object option = option(mc);
            return (Integer) option.getClass().getMethod("get").invoke(option);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static boolean write(Minecraft mc, int value) {
        try {
            Object option = option(mc);
            option.getClass().getMethod("set", Object.class).invoke(option, value);
            try {
                mc.getClass().getMethod("resizeDisplay").invoke(mc); // applies the new size to the open screen
            } catch (ReflectiveOperationException ignored) {
                // setting the option already resizes the display in most versions
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }
}
