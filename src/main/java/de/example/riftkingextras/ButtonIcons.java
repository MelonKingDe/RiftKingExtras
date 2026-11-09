package de.example.riftkingextras;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Map;

/** Small pixel icons (10x10) for the menu buttons of the inventory screen, drawn with filled rectangles. */
final class ButtonIcons {
    static final int SIZE = 10;

    private static final Map<String, String[]> ICONS = Map.of(
            // collection: a picture on the wall
            "COL", new String[]{
                    "##########",
                    "#........#",
                    "#....##..#",
                    "#....##..#",
                    "#........#",
                    "#.#......#",
                    "#.##..#..#",
                    "#.###.##.#",
                    "#........#",
                    "##########"},
            // quests: an exclamation mark
            "QST", new String[]{
                    "....##....",
                    "....##....",
                    "....##....",
                    "....##....",
                    "....##....",
                    "....##....",
                    "..........",
                    "....##....",
                    "....##....",
                    ".........."},
            // skills: a star
            "SKL", new String[]{
                    "....##....",
                    "....##....",
                    "...####...",
                    "##########",
                    ".########.",
                    "..######..",
                    "..######..",
                    ".###..###.",
                    ".##....##.",
                    ".........."},
            // guides: a question mark
            "GDE", new String[]{
                    "...####...",
                    "..##..##..",
                    "..##..##..",
                    "......##..",
                    ".....##...",
                    "....##....",
                    "....##....",
                    "..........",
                    "....##....",
                    "....##...."},
            // craft: the 3x3 crafting grid
            "CFT", new String[]{
                    "..........",
                    ".##.##.##.",
                    ".##.##.##.",
                    "..........",
                    ".##.##.##.",
                    ".##.##.##.",
                    "..........",
                    ".##.##.##.",
                    ".##.##.##.",
                    ".........."},
            // storage: a chest
            "STO", new String[]{
                    "..........",
                    "..........",
                    ".########.",
                    ".#......#.",
                    ".########.",
                    ".#..##..#.",
                    ".#..##..#.",
                    ".#......#.",
                    ".########.",
                    ".........."},
            // pets: a paw
            "PET", new String[]{
                    "..#....#..",
                    ".###..###.",
                    ".###..###.",
                    "..#....#..",
                    "...####...",
                    "..######..",
                    ".########.",
                    ".###..###.",
                    "..##..##..",
                    ".........."},
            // warps: a portal ring
            "WRP", new String[]{
                    "...####...",
                    "..######..",
                    ".###..###.",
                    ".##.##.##.",
                    ".##.##.##.",
                    ".##....##.",
                    ".###..###.",
                    "..######..",
                    "...####...",
                    ".........."});

    private ButtonIcons() {}

    /** Draws the icon with its top-left corner at (x, y). Runs of pixels in a row become one rectangle. */
    static void draw(GuiGraphicsExtractor g, String key, int x, int y, int color) {
        String[] rows = ICONS.get(key);
        if (rows == null) return;
        for (int r = 0; r < rows.length; r++) {
            String row = rows[r];
            int c = 0;
            while (c < row.length()) {
                if (row.charAt(c) != '#') {
                    c++;
                    continue;
                }
                int start = c;
                while (c < row.length() && row.charAt(c) == '#') c++;
                TrackerHud.rect(g, x + start, y + r, x + c, y + r + 1, color);
            }
        }
    }
}
