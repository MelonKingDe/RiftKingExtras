package de.example.riftkingextras;

import de.example.riftkingextras.mixin.SlotAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The storage overlay: all ender chest pages and backpacks in one scrollable screen, always in view. The page you open
 * is highlighted and its slots are the real ones (you use it like the normal storage); the other pages show what they
 * contained when you last opened them. Under it: the page shortcuts, a search over everything, and your inventory.
 * Scrolling: mouse wheel, the scrollbar, or the scrollbar track.
 */
public class StorageScreen extends AbstractContainerScreen<AbstractContainerMenu> {
    private static final int ACCENT = 0xFFB060FF;
    private static final int PANEL = 0xF00E0E14;
    private static final int SLOT_BG = 0xB0181820;
    private static final int CELL = 18, PAD = 4, HEADER = 14, TILE_GAP = 8, BAR_W = 8, MARGIN = 10;
    private static final int TILE_W = 9 * CELL + 2 * PAD;
    private static final int INV_W = 9 * CELL + 16, INV_H = 118;
    private static final int SHORT_W = 170, SHORT_H = 76;

    private static float scroll = 0;   // kept when you go to another page
    private static String lastQuery = "";

    private final ChestMenu chest;                 // the open storage menu, or null (opened with the command)
    private final StorageTitles.Page selected;     // the open page, or null (overview menu / command)
    private final int[] origX, origY;
    private final List<Slot> pageSlots = new ArrayList<>();
    private final List<Slot> playerSlots = new ArrayList<>();
    private final String[] keys = new String[StorageTitles.ENDER_CHEST_PAGES + StorageTitles.BACKPACK_PAGES];

    private EditBox searchBox;
    private boolean dragging = false;
    private boolean revealed = false;
    private String previousQuery = "";
    private ItemStack hoverStack = null;

    public StorageScreen(AbstractContainerMenu menu, Inventory inventory, Component title) {
        // a huge size: nothing on the screen counts as "outside", so a click on an empty spot never drops the carried item
        super(menu, inventory, title, Short.MAX_VALUE, Short.MAX_VALUE);
        this.chest = menu instanceof ChestMenu c ? c : null;
        this.selected = StorageTitles.parse(ScoreboardModel.plain(title));
        for (int i = 0; i < StorageTitles.ENDER_CHEST_PAGES; i++) keys[i] = "EC" + (i + 1);
        for (int i = 0; i < StorageTitles.BACKPACK_PAGES; i++) keys[StorageTitles.ENDER_CHEST_PAGES + i] = "BP" + (i + 1);

        List<Slot> slots = menu.slots;
        origX = new int[slots.size()];
        origY = new int[slots.size()];
        int chestSlots = chest == null ? 0 : chest.getRowCount() * 9;
        for (int i = 0; i < slots.size(); i++) {
            Slot s = slots.get(i);
            origX[i] = s.x;
            origY[i] = s.y;
            if (s.container instanceof Inventory && s.getContainerSlot() < 36) playerSlots.add(s);
            else if (selected != null && i < chestSlots) pageSlots.add(s);
        }
    }

    // ------------------------------------------------------------------ layout

    private record Layout(int ux0, int uy0, int ux1, int uy1, int vx0, int vy0, int vx1, int vy1,
                          int cols, int gridX, int invX, int invY, boolean showShortcut, int shortX, int shortY) {}

    private Layout layout() {
        int ux0 = MARGIN, ux1 = this.width - MARGIN, uy0 = MARGIN;
        int uy1 = Math.max(uy0 + 100, this.height - MARGIN - INV_H - 8);
        int vx0 = ux0 + 8, vx1 = ux1 - 8 - BAR_W - 4, vy0 = uy0 + 8, vy1 = uy1 - 8;
        int cols = Math.max(1, Math.min(3, (vx1 - vx0 + TILE_GAP) / (TILE_W + TILE_GAP)));
        int gridW = cols * TILE_W + (cols - 1) * TILE_GAP;
        int gridX = vx0 + (vx1 - vx0 - gridW) / 2;
        boolean showShortcut = this.width >= SHORT_W + 12 + INV_W + 2 * MARGIN;
        int groupW = showShortcut ? SHORT_W + 12 + INV_W : INV_W;
        int groupX = (this.width - groupW) / 2;
        int by = uy1 + 8;
        return new Layout(ux0, uy0, ux1, uy1, vx0, vy0, vx1, vy1, cols, gridX,
                showShortcut ? groupX + SHORT_W + 12 : groupX, by, showShortcut, groupX, by);
    }

    private int rowsOf(String key) {
        if (selected != null && chest != null && key.equals(selected.key())) return chest.getRowCount();
        StoragePage page = StorageCache.peek(key);
        if (page != null) return page.rows;
        return key.startsWith("EC") ? 5 : 3;
    }

    private int tileHeight(String key) {
        return HEADER + rowsOf(key) * CELL + PAD * 2;
    }

    /** Height of every row of tiles. */
    private int[] rowHeights(int cols) {
        int rowCount = (keys.length + cols - 1) / cols;
        int[] heights = new int[rowCount];
        for (int i = 0; i < keys.length; i++) heights[i / cols] = Math.max(heights[i / cols], tileHeight(keys[i]));
        return heights;
    }

    private int contentHeight(int[] rowHeights) {
        int sum = 0;
        for (int h : rowHeights) sum += h + TILE_GAP;
        return sum;
    }

    /** y of the top of a row of tiles, relative to the content (before scrolling). */
    private static int rowTop(int[] rowHeights, int row) {
        int y = 0;
        for (int r = 0; r < row; r++) y += rowHeights[r] + TILE_GAP;
        return y;
    }

    private float maxScroll(Layout l, int[] rowHeights) {
        return Math.max(0, contentHeight(rowHeights) - (l.vy1() - l.vy0()));
    }

    // ------------------------------------------------------------------ setup

    @Override
    protected void init() {
        super.init();
        this.leftPos = 0; // the whole screen belongs to this overlay, slot positions are absolute
        this.topPos = 0;

        Layout l = layout();
        searchBox = new EditBox(this.font, l.invX() + 6, l.invY() + 6, INV_W - 12, 16, Component.literal("Search"));
        searchBox.setMaxLength(40);
        searchBox.setHint(Component.literal("Search all pages..."));
        searchBox.setValue(lastQuery);
        this.addRenderableWidget(searchBox);
    }

    @Override
    public void removed() {
        List<Slot> slots = this.menu.slots;
        for (int i = 0; i < slots.size() && i < origX.length; i++) {
            SlotAccessor acc = (SlotAccessor) slots.get(i);
            acc.riftkingextras$setX(origX[i]);
            acc.riftkingextras$setY(origY[i]);
        }
        lastQuery = searchBox == null ? lastQuery : searchBox.getValue();
        super.removed();
    }

    private static void place(Slot s, int x, int y) {
        SlotAccessor acc = (SlotAccessor) s;
        acc.riftkingextras$setX(x);
        acc.riftkingextras$setY(y);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractBackground(g, mouseX, mouseY, delta);
        Layout l = layout();
        int[] rowHeights = rowHeights(l.cols());
        String query = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        lastQuery = searchBox == null ? lastQuery : searchBox.getValue();

        // scroll to the open page once, and to the first match when the search text changes
        if (!revealed) {
            revealed = true;
            int index = selectedIndex();
            if (index >= 0) {
                int top = rowTop(rowHeights, index / l.cols());
                int bottom = top + rowHeights[index / l.cols()];
                int viewH = l.vy1() - l.vy0();
                if (top < scroll || bottom > scroll + viewH) scroll = top;
            }
        }
        if (!query.equals(previousQuery)) {
            previousQuery = query;
            if (!query.isEmpty()) {
                for (int i = 0; i < keys.length; i++) {
                    StoragePage p = StorageCache.peek(keys[i]);
                    if (p != null && p.matches(query) > 0) {
                        scroll = rowTop(rowHeights, i / l.cols());
                        break;
                    }
                }
            }
        }
        scroll = Math.max(0, Math.min(scroll, maxScroll(l, rowHeights)));

        // upper panel
        TrackerHud.rrect(g, l.ux0(), l.uy0(), l.ux1(), l.uy1(), PANEL);
        TrackerHud.border(g, l.ux0(), l.uy0(), l.ux1(), l.uy1(), 0x55FFFFFF);

        // hide every real slot, then place the ones that are visible
        for (Slot s : this.menu.slots) place(s, -10000, -10000);
        hoverStack = null;
        int viewTop = l.vy0(), viewBottom = l.vy1();
        int loadBudget = 1; // one saved page is read per frame, so opening the screen does not stutter

        g.enableScissor(l.vx0() - 2, viewTop, l.vx1() + 2, viewBottom);
        for (int i = 0; i < keys.length; i++) {
            String key = keys[i];
            int row = i / l.cols(), col = i % l.cols();
            int tx = l.gridX() + col * (TILE_W + TILE_GAP);
            int ty = viewTop - Math.round(scroll) + rowTop(rowHeights, row);
            int th = tileHeight(key);
            if (ty + th < viewTop || ty > viewBottom) continue; // not visible

            boolean isSelected = selected != null && key.equals(selected.key());
            StoragePage page = StorageCache.peek(key);
            if (page == null && !isSelected && loadBudget > 0 && StorageCache.needsLoad(key)) {
                page = StorageCache.load(Minecraft.getInstance(), key);
                loadBudget--;
            }
            boolean hover = mouseX >= tx && mouseX < tx + TILE_W && mouseY >= Math.max(ty, viewTop)
                    && mouseY < Math.min(ty + th, viewBottom);

            TrackerHud.rrect(g, tx, ty, tx + TILE_W, ty + th, 0xA0181820);
            if (isSelected) { // the open page is highlighted
                TrackerHud.border(g, tx - 1, ty - 1, tx + TILE_W + 1, ty + th + 1, ACCENT);
                TrackerHud.border(g, tx, ty, tx + TILE_W, ty + th, ACCENT);
            } else {
                TrackerHud.border(g, tx, ty, tx + TILE_W, ty + th, hover ? TrackerHud.withAlpha(ACCENT, 0xAA) : 0x33FFFFFF);
            }
            g.text(this.font, StorageTitles.labelFor(key), tx + PAD + 2, ty + 3, isSelected ? ACCENT : 0xFFFFFFFF, false);

            // status on the right of the header
            String status = "";
            int statusColor = TrackerHud.MUTED;
            if (!query.isEmpty() && page != null) {
                int found = page.matches(query);
                status = found == 0 ? "-" : found + " found";
                statusColor = found == 0 ? TrackerHud.MUTED : ACCENT;
            } else if (page != null && !isSelected) {
                long minutes = Math.max(0, (System.currentTimeMillis() - page.time) / 60000);
                status = minutes < 60 ? minutes + "m ago" : minutes < 2880 ? (minutes / 60) + "h ago" : (minutes / 1440) + "d ago";
            }
            if (!status.isEmpty()) g.text(this.font, status, tx + TILE_W - PAD - 2 - this.font.width(status), ty + 3, statusColor, false);

            int rows = rowsOf(key);
            if (page == null && !isSelected) {
                String text = "Not opened yet - click to open";
                g.text(this.font, text, tx + (TILE_W - this.font.width(text)) / 2, ty + HEADER + rows * CELL / 2 - 4, TrackerHud.MUTED, false);
                continue;
            }

            for (int idx = 0; idx < rows * 9; idx++) {
                int sx = tx + PAD + (idx % 9) * CELL, sy = ty + HEADER + (idx / 9) * CELL;
                boolean fullyVisible = sy >= viewTop && sy + CELL <= viewBottom;
                TrackerHud.rrect(g, sx, sy, sx + CELL, sy + CELL, SLOT_BG);

                ItemStack stack = isSelected && idx < pageSlots.size() ? pageSlots.get(idx).getItem()
                        : page != null && idx < page.stacks.length ? page.stacks[idx] : ItemStack.EMPTY;
                boolean match = !query.isEmpty() && page != null && idx < page.search.length && page.search[idx].contains(query);

                if (isSelected && idx < pageSlots.size() && fullyVisible) {
                    place(pageSlots.get(idx), sx + 1, sy + 1); // the real slot sits here
                } else if (!stack.isEmpty()) {
                    g.item(stack, sx + 1, sy + 1);
                    g.itemDecorations(this.font, stack, sx + 1, sy + 1);
                    if (!query.isEmpty() && !match) TrackerHud.rect(g, sx, sy, sx + CELL, sy + CELL, 0xB00E0E14); // dim
                    if (mouseX >= sx && mouseX < sx + CELL && mouseY >= Math.max(sy, viewTop) && mouseY < Math.min(sy + CELL, viewBottom)) {
                        hoverStack = stack;
                    }
                }
                if (match) TrackerHud.border(g, sx, sy, sx + CELL, sy + CELL, ACCENT);
            }
        }
        g.disableScissor();

        // scrollbar
        int trackX = l.vx1() + 4, trackH = l.vy1() - l.vy0();
        TrackerHud.rrect(g, trackX, l.vy0(), trackX + BAR_W, l.vy1(), 0x33FFFFFF);
        float max = maxScroll(l, rowHeights);
        if (max > 0) {
            int thumbH = Math.max(16, Math.round(trackH * (trackH / (float) contentHeight(rowHeights))));
            int thumbY = l.vy0() + Math.round((trackH - thumbH) * (scroll / max));
            TrackerHud.rrect(g, trackX, thumbY, trackX + BAR_W, thumbY + thumbH, dragging ? ACCENT : 0xAAC9B4F0);
        }

        drawBottom(g, l, mouseX, mouseY);
    }

    private int selectedIndex() {
        if (selected == null) return -1;
        for (int i = 0; i < keys.length; i++) if (keys[i].equals(selected.key())) return i;
        return -1;
    }

    /** The shortcut buttons, the search panel and your inventory. */
    private void drawBottom(GuiGraphicsExtractor g, Layout l, int mouseX, int mouseY) {
        // your inventory (the real slots) with the search box above it
        TrackerHud.rrect(g, l.invX(), l.invY(), l.invX() + INV_W, l.invY() + INV_H, PANEL);
        TrackerHud.border(g, l.invX(), l.invY(), l.invX() + INV_W, l.invY() + INV_H, 0x55FFFFFF);
        for (Slot s : playerSlots) {
            int cs = s.getContainerSlot();
            int x, y;
            if (cs >= 9) {
                int n = cs - 9;
                x = l.invX() + 8 + (n % 9) * CELL + 1;
                y = l.invY() + 30 + (n / 9) * CELL + 1;
            } else {
                x = l.invX() + 8 + cs * CELL + 1;
                y = l.invY() + 30 + 3 * CELL + 4 + 1;
            }
            place(s, x, y);
            ItemStack stack = s.getItem();
            int rarity = ItemRarity.colorOf(stack);
            int frame = stack.isEmpty() ? ItemRarity.EMPTY : rarity != 0 ? rarity : ItemRarity.FALLBACK;
            TrackerHud.rrect(g, x - 1, y - 1, x + 17, y + 17, SLOT_BG);
            if (rarity != 0) TrackerHud.rrect(g, x - 1, y - 1, x + 17, y + 17, TrackerHud.withAlpha(rarity, 0x30));
            TrackerHud.border(g, x - 1, y - 1, x + 17, y + 17, frame);
        }

        // shortcuts to every page
        if (!l.showShortcut()) return;
        int sx = l.shortX(), sy = l.shortY();
        TrackerHud.rrect(g, sx, sy, sx + SHORT_W, sy + SHORT_H, PANEL);
        TrackerHud.border(g, sx, sy, sx + SHORT_W, sy + SHORT_H, 0x55FFFFFF);
        g.text(this.font, "SHORTCUT", sx + 8, sy + 4, TrackerHud.MUTED, false);
        for (int i = 0; i < keys.length; i++) {
            int[] pos = shortcutPos(l, i);
            String key = keys[i];
            boolean current = selected != null && key.equals(selected.key());
            boolean hover = mouseX >= pos[0] && mouseX < pos[0] + 16 && mouseY >= pos[1] && mouseY < pos[1] + 16;
            boolean known = StorageCache.peek(key) != null;
            boolean enderChest = key.startsWith("EC");
            int base = enderChest ? 0xFF2A2533 : 0xFF33291F;
            TrackerHud.rrect(g, pos[0], pos[1], pos[0] + 16, pos[1] + 16, current ? 0xFF6A4C93 : hover ? 0xFF3A3150 : base);
            TrackerHud.border(g, pos[0], pos[1], pos[0] + 16, pos[1] + 16,
                    current || hover ? ACCENT : enderChest ? 0xFF5B4B7A : 0xFF7A6240);
            String label = key.substring(2);
            int color = current || hover ? 0xFFFFFFFF : known ? (enderChest ? 0xFFD9C8FF : 0xFFF0D9A0) : 0xFF6B7078;
            g.text(this.font, label, pos[0] + (16 - this.font.width(label)) / 2, pos[1] + 4, color, false);
        }
    }

    /** x, y of the i-th shortcut button: ender chest row, then backpacks 1-9 and 10-18. */
    private static int[] shortcutPos(Layout l, int i) {
        int row, col;
        if (i < StorageTitles.ENDER_CHEST_PAGES) {
            row = 0;
            col = i;
        } else {
            int b = i - StorageTitles.ENDER_CHEST_PAGES;
            row = 1 + b / 9;
            col = b % 9;
        }
        return new int[]{l.shortX() + 8 + col * 18, l.shortY() + 16 + row * 18};
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        if (hoverStack != null && !hoverStack.isEmpty()) g.setTooltipForNextFrame(this.font, hoverStack, mouseX, mouseY);
        this.extractTooltip(g, mouseX, mouseY);
    }

    /** No "Inventory" / title labels of the vanilla layout. */
    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
    }

    // ------------------------------------------------------------------ input

    private void openPage(String key) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) player.connection.sendCommand(StorageTitles.commandFor(key.substring(0, 2), Integer.parseInt(key.substring(2))));
    }

    private void setScrollFromMouse(Layout l, double mouseY) {
        int[] rowHeights = rowHeights(l.cols());
        float max = maxScroll(l, rowHeights);
        int trackH = l.vy1() - l.vy0();
        int thumbH = Math.max(16, Math.round(trackH * (trackH / (float) contentHeight(rowHeights))));
        double ratio = (mouseY - l.vy0() - thumbH / 2.0) / Math.max(1, trackH - thumbH);
        scroll = (float) Math.max(0, Math.min(max, ratio * max));
    }

    /** The key of the tile at this position (inside the viewport), or null. */
    private String tileAt(Layout l, double mx, double my) {
        int[] rowHeights = rowHeights(l.cols());
        for (int i = 0; i < keys.length; i++) {
            int row = i / l.cols(), col = i % l.cols();
            int tx = l.gridX() + col * (TILE_W + TILE_GAP);
            int ty = l.vy0() - Math.round(scroll) + rowTop(rowHeights, row);
            if (mx >= tx && mx < tx + TILE_W && my >= ty && my < ty + tileHeight(keys[i])) return keys[i];
        }
        return null;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        Layout l = layout();
        if (event.button() == 0) {
            if (mx >= l.vx1() + 2 && mx <= l.vx1() + 6 + BAR_W && my >= l.vy0() && my <= l.vy1()) {
                dragging = true;
                setScrollFromMouse(l, my);
                return true;
            }
            if (l.showShortcut()) {
                for (int i = 0; i < keys.length; i++) {
                    int[] pos = shortcutPos(l, i);
                    if (mx >= pos[0] && mx < pos[0] + 16 && my >= pos[1] && my < pos[1] + 16) {
                        if (selected == null || !keys[i].equals(selected.key())) openPage(keys[i]);
                        return true;
                    }
                }
            }
            if (mx >= l.vx0() && mx < l.vx1() && my >= l.vy0() && my < l.vy1()) {
                String key = tileAt(l, mx, my);
                if (key != null && (selected == null || !key.equals(selected.key()))) { // another page: open it
                    openPage(key);
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragging) {
            setScrollFromMouse(layout(), event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        Layout l = layout();
        if (mouseX >= l.ux0() && mouseX < l.ux1() && mouseY >= l.uy0() && mouseY < l.uy1()) {
            scroll = (float) Math.max(0, Math.min(maxScroll(l, rowHeights(l.cols())), scroll - scrollY * 24));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // while you type in the search box, keys like "e" must not close the screen
        if (searchBox != null && searchBox.isFocused() && event.key() != 256) {
            searchBox.keyPressed(event);
            return true;
        }
        return super.keyPressed(event);
    }
}
