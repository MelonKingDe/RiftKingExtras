package de.example.riftkingextras;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Every chest-style menu (SkyBlock Menu, Skills, Sacks, Storage, Auction House ...) in the same style as the inventory
 * screen: a dark rounded panel, the title in the header, a frame and a light background in the rarity color around
 * every slot. The gray glass panes that fill the empty spaces of SkyBlock menus are hidden. The slots stay where they
 * are, so everything works exactly as before.
 */
public class RiftContainerScreen extends AbstractContainerScreen<ChestMenu> {
    private static final int W = 176;
    private static final int PANEL = 0xF00E0E14;
    private static final int SLOT_BG = 0xB0181820;
    private static final int FILLER_BG = 0xFF15151C;

    private final int rows;
    private final StorageTitles.Page storagePage; // set if this menu is a storage page (ender chest or backpack)
    private final int panelHeight;
    private final int[] rarity;      // rarity color per slot, refreshed a few times per second
    private long lastRarityRefresh = 0;

    public RiftContainerScreen(ChestMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, 114 + menu.getRowCount() * 18);
        this.rows = menu.getRowCount();
        this.storagePage = RkeConfig.storageOverlay ? StorageTitles.parse(ScoreboardModel.plain(title)) : null;
        this.panelHeight = 114 + rows * 18;
        this.rarity = new int[menu.slots.size()];
    }

    /** The gray glass panes (blank name) that SkyBlock menus use as a background. */
    private static boolean isFiller(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (!BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().endsWith("glass_pane")) return false;
        return ScoreboardModel.plain(stack.getHoverName()).isEmpty();
    }

    private void refreshRarity() {
        long now = System.currentTimeMillis();
        if (now - lastRarityRefresh < 150) return;
        lastRarityRefresh = now;
        List<Slot> slots = this.menu.slots;
        for (int i = 0; i < slots.size() && i < rarity.length; i++) rarity[i] = ItemRarity.colorOf(slots.get(i).getItem());
    }

    // ------------------------------------------------------------------ page navigator (storage pages)

    private static final int NAV_W = 108, NAV_H = 150, BTN = 14, BTN_STEP = 16;
    private static final int NAV_BG = 0xFF2A2533, NAV_BG_HOVER = 0xFF3A3150, NAV_BORDER = 0xFF5B4B7A;

    /** A button of the navigator: where it is (relative to the panel), what it opens. */
    private record NavButton(int x, int y, String label, String key, String command) {}

    private java.util.List<NavButton> navButtons() {
        java.util.List<NavButton> out = new java.util.ArrayList<>();
        int x0 = -NAV_W - 6 + 6, y0 = 16; // relative to the left edge and top of the main panel
        for (int i = 0; i < StorageTitles.ENDER_CHEST_PAGES; i++) {
            out.add(new NavButton(x0 + (i % 3) * BTN_STEP, y0 + (i / 3) * BTN_STEP, String.valueOf(i + 1),
                    "EC" + (i + 1), StorageTitles.commandFor("EC", i + 1)));
        }
        int y1 = y0 + 3 * BTN_STEP + 18;
        for (int i = 0; i < StorageTitles.BACKPACK_PAGES; i++) {
            out.add(new NavButton(x0 + (i % 6) * BTN_STEP, y1 + (i / 6) * BTN_STEP, String.valueOf(i + 1),
                    "BP" + (i + 1), StorageTitles.commandFor("BP", i + 1)));
        }
        out.add(new NavButton(x0, y1 + 3 * BTN_STEP + 4, "OVERVIEW", "", "storage"));
        return out;
    }

    private boolean navigatorVisible() {
        return storagePage != null && this.leftPos >= NAV_W + 8;
    }

    private void drawNavigator(GuiGraphicsExtractor g, int l, int t, int mouseX, int mouseY) {
        int x0 = l - NAV_W - 6;
        TrackerHud.rrect(g, x0, t, x0 + NAV_W, t + NAV_H, PANEL);
        TrackerHud.border(g, x0, t, x0 + NAV_W, t + NAV_H, 0x55FFFFFF);
        g.text(this.font, "ENDER CHEST", x0 + 6, t + 5, TrackerHud.MUTED, false);
        g.text(this.font, "BACKPACKS", x0 + 6, t + 16 + 3 * BTN_STEP + 6, TrackerHud.MUTED, false);

        for (NavButton b : navButtons()) {
            int bx = l + b.x(), by = t + b.y();
            boolean overview = b.key().isEmpty();
            int w = overview ? NAV_W - 12 : BTN;
            boolean hover = mouseX >= bx && mouseX < bx + w && mouseY >= by && mouseY < by + BTN;
            boolean current = b.key().equals(storagePage.key());
            boolean known = overview || StorageCache.peek(b.key()) != null;

            TrackerHud.rrect(g, bx, by, bx + w, by + BTN, current ? 0xFF6A4C93 : hover ? NAV_BG_HOVER : NAV_BG);
            TrackerHud.border(g, bx, by, bx + w, by + BTN, current || hover ? 0xFFB060FF : NAV_BORDER);
            int color = current || hover ? 0xFFFFFFFF : known ? 0xFFD9C8FF : 0xFF6B7078; // pages never opened are dim
            g.text(this.font, b.label(), bx + (w - this.font.width(b.label())) / 2, by + 3, color, false);
        }
    }

    private boolean navigatorClick(double mx, double my) {
        int l = this.leftPos, t = this.topPos;
        for (NavButton b : navButtons()) {
            int bx = l + b.x(), by = t + b.y();
            int w = b.key().isEmpty() ? NAV_W - 12 : BTN;
            if (mx >= bx && mx < bx + w && my >= by && my < by + BTN) {
                if (!b.key().equals(storagePage.key())) {
                    LocalPlayer player = Minecraft.getInstance().player;
                    if (player != null) player.connection.sendCommand(b.command());
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (navigatorVisible() && navigatorClick(event.x(), event.y())) return true;
        return super.mouseClicked(event, doubleClick);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractBackground(g, mouseX, mouseY, delta);
        refreshRarity();
        int l = this.leftPos, t = this.topPos;

        // panel with the title in the header
        TrackerHud.rrect(g, l, t, l + W, t + panelHeight, PANEL);
        TrackerHud.border(g, l, t, l + W, t + panelHeight, 0x55FFFFFF);
        GlowBorder.decorate(g, l, t, W, panelHeight, "menus");
        g.text(this.font, this.title, l + 8, t + 4, 0xFFFFFFFF, false);
        TrackerHud.rect(g, l + 6, t + TrackerHud.HEADER_H - 2, l + W - 6, t + TrackerHud.HEADER_H - 1,
                TrackerHud.withAlpha(0xFFB060FF, 0x99));

        if (navigatorVisible()) drawNavigator(g, l, t, mouseX, mouseY);

        // a thin line between the menu and your inventory
        int lineY = t + 18 + rows * 18 + 5;
        TrackerHud.rect(g, l + 8, lineY, l + W - 8, lineY + 1, 0x22FFFFFF);

        // slot boxes with the rarity frame and background
        List<Slot> slots = this.menu.slots;
        for (int i = 0; i < slots.size(); i++) {
            Slot s = slots.get(i);
            ItemStack stack = s.getItem();
            int color = i < rarity.length ? rarity[i] : 0;
            int frame = stack.isEmpty() ? ItemRarity.EMPTY : color != 0 ? color : ItemRarity.FALLBACK;
            int x = l + s.x - 1, y = t + s.y - 1;
            TrackerHud.rrect(g, x, y, x + 18, y + 18, SLOT_BG);
            if (color != 0) TrackerHud.rrect(g, x, y, x + 18, y + 18, TrackerHud.withAlpha(color, 0x30));
            if (!isFiller(stack)) TrackerHud.border(g, x, y, x + 18, y + 18, frame);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        int l = this.leftPos, t = this.topPos;

        // cover the glass panes with the slot background, so the menu looks clean
        List<Slot> slots = this.menu.slots;
        for (int i = 0; i < rows * 9 && i < slots.size(); i++) {
            Slot s = slots.get(i);
            if (isFiller(s.getItem())) TrackerHud.rrect(g, l + s.x, t + s.y, l + s.x + 16, t + s.y + 16, FILLER_BG);
        }
        this.extractTooltip(g, mouseX, mouseY);
    }

    /** No "Inventory" / title labels of the vanilla layout (the title is in our header). */
    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
    }
}
