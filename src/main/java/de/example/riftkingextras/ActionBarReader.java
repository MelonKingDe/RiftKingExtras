package de.example.riftkingextras;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;

/**
 * Reads the current action bar text straight from the game's HUD object, whichever packet the server used to send it.
 * The field is found by its type (Component) and name (contains "overlay"), so it keeps working when it is renamed.
 */
final class ActionBarReader {
    private static Field field = null;
    private static boolean resolved = false;
    private static String status = "not resolved yet";
    private static String last = "";
    private static int ticks = 0;

    private ActionBarReader() {}

    static String status() {
        return status;
    }

    private static void resolve(Object gui) {
        resolved = true;
        for (Field f : gui.getClass().getDeclaredFields()) {
            if (f.getType() == Component.class && f.getName().toLowerCase().contains("overlay")) {
                try {
                    f.setAccessible(true);
                    field = f;
                    status = "reading field " + f.getName();
                    return;
                } catch (RuntimeException e) {
                    status = "field " + f.getName() + " is not accessible";
                }
            }
        }
        if (field == null && status.equals("not resolved yet")) status = "NOT FOUND (only the chat event is used)";
    }

    /** Called every client tick (checks every second tick). */
    static void tick(Minecraft mc) {
        if (++ticks % 2 != 0 || mc.player == null) return;
        if (!resolved) resolve(mc.gui);
        if (field == null) return;
        try {
            Object value = field.get(mc.gui);
            if (value instanceof Component c) {
                String plain = ScoreboardModel.plain(c);
                if (plain.isEmpty()) return;
                if (plain.equals(last)) {
                    StatsTracker.touchActionBar();
                } else {
                    last = plain;
                    StatsTracker.onActionBar(plain);
                }
            }
        } catch (IllegalAccessException | RuntimeException ignored) {
            // try again next time
        }
    }
}
