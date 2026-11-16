package de.example.riftkingextras;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The titles of the SkyBlock storage menus and the commands that open their pages. */
final class StorageTitles {
    static final int ENDER_CHEST_PAGES = 9;
    static final int BACKPACK_PAGES = 18;

    /** A storage page: kind "EC" (ender chest) or "BP" (backpack) and its number (1-based). */
    record Page(String kind, int index) {
        String key() { return kind + index; }
        boolean enderChest() { return "EC".equals(kind); }
        String label() { return (enderChest() ? "Ender Chest " : "Backpack ") + index; }
        String command() { return commandFor(kind, index); }
    }

    // "Ender Chest (3/9)" and "Large Backpack (Slot #5)"; a star can be in front of the brackets
    private static final Pattern ENDER_CHEST = Pattern.compile("^Ender Chest (?:\u2726 )?\\((\\d+)/\\d+\\)$");
    private static final Pattern BACKPACK = Pattern.compile("^.+Backpack (?:\u2726 )?\\(Slot #(\\d+)\\)$");

    private StorageTitles() {}

    /** The page for a (plain) menu title, or null if the title is no storage page. */
    static Page parse(String title) {
        Matcher m = ENDER_CHEST.matcher(title.trim());
        if (m.matches()) return new Page("EC", Integer.parseInt(m.group(1)));
        m = BACKPACK.matcher(title.trim());
        if (m.matches()) return new Page("BP", Integer.parseInt(m.group(1)));
        return null;
    }

    /** The main storage menu with the icons of all pages. */
    static boolean isOverview(String title) {
        return title.trim().equals("Storage");
    }

    static String commandFor(String kind, int index) {
        return ("EC".equals(kind) ? "enderchest " : "backpack ") + index;
    }

    static String labelFor(String key) {
        boolean ec = key.startsWith("EC");
        return (ec ? "Ender Chest " : "Backpack ") + key.substring(2);
    }
}
