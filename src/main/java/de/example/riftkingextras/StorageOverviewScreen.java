package de.example.riftkingextras;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

/**
 * The storage overlay: all pages of your ender chest and backpacks at once, with a search over everything you saw.
 * The pages are the ones you opened before (see StorageCache), a click on a page opens it. It replaces the "Storage"
 * menu of SkyBlock, and can also be opened anywhere with /rke storage to look at the saved pages.
 */
public class StorageOverviewScreen extends Screen {
    private static final int ACCENT = 0xFFB060FF;
    private static final int PANEL = 0xF00E0E14;
    private static final int COLS = 3, TILE_ROWS = 3;
    private static final String[] TAB_NAMES = {"Ender Chest", "Backpacks 1-9", "Backpacks 10-18"};

    private static String lastQuery = "";
    private static int lastTab = 0;

    private final ChestMenu menu; // the open "Storage" menu, or null when opened with the command
    private int tab;
    private EditBox searchBox;

    public StorageOverviewScreen(ChestMenu menu) {
        super(Component.literal("Storage"));
        this.menu = menu;
        this.tab = lastTab;
    }

    // ---------- widgets ----------

    @Override
    protected void init() {
        int left = 18, top = 28;
        searchBox = new EditBox(this.font, left, top, Math.min(200, this.width / 2 - 30), 18, Component.literal("Search"));
        searchBox.setMaxLength(40);
        searchBox.setHint(Component.literal("Search all pages..."));
        searchBox.setValue(lastQuery);
        this.addRenderableWidget(searchBox);
        this.setInitialFocus(searchBox);

        int bw = 96;
        int x = this.width - 18 - 3 * bw - 2 * 4;
        for (int i = 0; i < 3; i++) {
            final int index = i;
            String label = (i == tab ? "[ " : "") + TAB_NAMES[i] + (i == tab ? " ]" : "");
            this.addRenderableWidget(Button.builder(Component.literal(label), btn -> {
                lastQuery = searchBox.getValue();
                lastTab = index;
                this.minecraft.gui.setScreen(new StorageOverviewScreen(menu));
            }).bounds(x + i * (bw + 4), top - 1, bw, 20).build());
        }
    }

    // ---------- pages ----------

    private String pageKey(int tile) {
        return switch (tab) {
            case 0 -> "EC" + (tile + 1);
            case 1 -> "BP" + (tile + 1);
            default -> "BP" + (tile + 10);
        };
    }

    // ---------- drawing ----------

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractBackground(g, mouseX, mouseY, delta);
        Minecraft mc = Minecraft.getInstance();
        lastQuery = searchBox == null ? lastQuery : searchBox.getValue();
        String query = lastQuery.trim().toLowerCase(java.util.Locale.ROOT);
        boolean filtering = !query.isEmpty();

        int x0 = 10, y0 = 10, x1 = this.width - 10, y1 = this.height - 10;
        TrackerHud.rrect(g, x0, y0, x1, y1, PANEL);
        TrackerHud.border(g, x0, y0, x1, y1, 0x55FFFFFF);
        g.text(this.font, "RIFTKING", x0 + 8, y0 + 4, 0xFFFFFFFF, false);
        g.text(this.font, "EXTRAS", x0 + 8 + this.font.width("RIFTKING "), y0 + 4, ACCENT, false);
        String right = "STORAGE  -  profile " + StatsTracker.profileName;
        g.text(this.font, right, x1 - 8 - this.font.width(right), y0 + 4, TrackerHud.MUTED, false);
        TrackerHud.rect(g, x0 + 6, y0 + 13, x1 - 6, y0 + 14, TrackerHud.withAlpha(ACCENT, 0x99));

        // load saved pages, one file per frame, so opening the screen does not stutter
        int loadBudget = 1;
        StoragePage[] pages = new StoragePage[COLS * TILE_ROWS];
        int maxRows = 3;
        for (int i = 0; i < pages.length; i++) {
            String key = pageKey(i);
            pages[i] = StorageCache.peek(key);
            if (pages[i] == null && loadBudget > 0 && StorageCache.needsLoad(key)) {
                pages[i] = StorageCache.load(mc, key);
                loadBudget--;
            }
            if (pages[i] != null) maxRows = Math.max(maxRows, pages[i].rows);
        }

        // size of the mini inventories so that 3 x 3 pages fit
        int gridTop = y0 + 52;
        int availW = x1 - x0 - 24, availH = y1 - gridTop - 10;
        int gapX = 8, gapY = 8, header = 12;
        float scaleW = (availW - 2 * gapX) / (COLS * 162f);
        float scaleH = (availH / (float) TILE_ROWS - gapY - header - 4) / (maxRows * 18f);
        float s = Math.max(0.5f, Math.min(1.0f, Math.min(scaleW, scaleH)));
        s = (float) Math.floor(s * 20) / 20f; // steps of 5 percent keep the pixels clean

        int tileW = Math.round(162 * s), tileH = header + Math.round(maxRows * 18 * s) + 4;
        int startX = (this.width - (COLS * tileW + (COLS - 1) * gapX)) / 2;
        Matrix3x2fStack pose = g.pose();

        ItemStack hoverStack = null;
        for (int i = 0; i < pages.length; i++) {
            int tx = startX + (i % COLS) * (tileW + gapX);
            int ty = gridTop + (i / COLS) * (tileH + gapY);
            boolean hover = mouseX >= tx && mouseX < tx + tileW && mouseY >= ty && mouseY < ty + tileH;
            StoragePage page = pages[i];

            TrackerHud.rrect(g, tx - 2, ty, tx + tileW + 2, ty + tileH, 0xA0181820);
            TrackerHud.border(g, tx - 2, ty, tx + tileW + 2, ty + tileH, hover ? ACCENT : 0x33FFFFFF);
            g.text(this.font, StorageTitles.labelFor(pageKey(i)), tx + 2, ty + 2, 0xFFFFFFFF, false);

            if (page == null) {
                String text = "Not opened yet - click to open";
                g.text(this.font, text, tx + (tileW - this.font.width(text)) / 2, ty + tileH / 2, TrackerHud.MUTED, false);
                continue;
            }

            String status;
            int statusColor = TrackerHud.MUTED;
            if (filtering) {
                int found = page.matches(query);
                status = found == 0 ? "-" : found + " found";
                statusColor = found == 0 ? TrackerHud.MUTED : ACCENT;
            } else {
                long minutes = Math.max(0, (System.currentTimeMillis() - page.time) / 60000);
                status = minutes < 60 ? minutes + "m ago" : minutes < 2880 ? (minutes / 60) + "h ago" : (minutes / 1440) + "d ago";
            }
            g.text(this.font, status, tx + tileW - this.font.width(status), ty + 2, statusColor, false);

            int cell = Math.max(1, Math.round(18 * s));
            for (int idx = 0; idx < page.stacks.length; idx++) {
                int sx = tx + (idx % 9) * cell, sy = ty + header + (idx / 9) * cell;
                TrackerHud.rect(g, sx + 1, sy + 1, sx + cell - 1, sy + cell - 1, 0x22FFFFFF);
                ItemStack stack = page.stacks[idx];
                if (stack.isEmpty()) continue;

                pose.pushMatrix();
                pose.translate(sx + 1.0f * s, sy + 1.0f * s);
                pose.scale(s, s);
                g.item(stack, 0, 0);
                g.itemDecorations(this.font, stack, 0, 0);
                pose.popMatrix();

                if (filtering) {
                    if (page.search[idx].contains(query)) {
                        TrackerHud.border(g, sx, sy, sx + cell, sy + cell, ACCENT);
                    } else {
                        TrackerHud.rect(g, sx + 1, sy + 1, sx + cell - 1, sy + cell - 1, 0xB00E0E14); // dim what does not match
                    }
                }
                if (mouseX >= sx && mouseX < sx + cell && mouseY >= sy && mouseY < sy + cell) hoverStack = stack;
            }
        }
        this.hoverStack = hoverStack;
    }

    private ItemStack hoverStack = null;

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        if (hoverStack != null && !hoverStack.isEmpty()) {
            g.setTooltipForNextFrame(this.font, hoverStack, mouseX, mouseY);
        }
    }

    // ---------- input ----------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true; // search box and tab buttons
        if (event.button() != 0) return false;

        // which tile was clicked? (same geometry as when drawing)
        int x0 = 10, y1 = this.height - 10, x1 = this.width - 10, y0 = 10;
        int gridTop = y0 + 52, gapX = 8, gapY = 8, header = 12;
        int maxRows = 3;
        for (int i = 0; i < COLS * TILE_ROWS; i++) {
            StoragePage p = StorageCache.peek(pageKey(i));
            if (p != null) maxRows = Math.max(maxRows, p.rows);
        }
        int availW = x1 - x0 - 24, availH = y1 - gridTop - 10;
        float scaleW = (availW - 2 * gapX) / (COLS * 162f);
        float scaleH = (availH / (float) TILE_ROWS - gapY - header - 4) / (maxRows * 18f);
        float s = Math.max(0.5f, Math.min(1.0f, Math.min(scaleW, scaleH)));
        s = (float) Math.floor(s * 20) / 20f;
        int tileW = Math.round(162 * s), tileH = header + Math.round(maxRows * 18 * s) + 4;
        int startX = (this.width - (COLS * tileW + (COLS - 1) * gapX)) / 2;

        for (int i = 0; i < COLS * TILE_ROWS; i++) {
            int tx = startX + (i % COLS) * (tileW + gapX);
            int ty = gridTop + (i / COLS) * (tileH + gapY);
            if (event.x() >= tx - 2 && event.x() < tx + tileW + 2 && event.y() >= ty && event.y() < ty + tileH) {
                String key = pageKey(i);
                LocalPlayer player = Minecraft.getInstance().player;
                if (player != null) {
                    player.connection.sendCommand(StorageTitles.commandFor(key.substring(0, 2), Integer.parseInt(key.substring(2))));
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public void onClose() {
        LocalPlayer player = this.minecraft == null ? null : this.minecraft.player;
        if (menu != null && player != null && player.containerMenu == menu) {
            player.closeContainer(); // close the SkyBlock menu behind this screen, like the normal screen does
        } else {
            super.onClose();
        }
    }
}
