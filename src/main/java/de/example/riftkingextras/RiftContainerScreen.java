package de.example.riftkingextras;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
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
    private final int panelHeight;
    private final int[] rarity;      // rarity color per slot, refreshed a few times per second
    private long lastRarityRefresh = 0;

    public RiftContainerScreen(ChestMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, 114 + menu.getRowCount() * 18);
        this.rows = menu.getRowCount();
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

    // ------------------------------------------------------------------ drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractBackground(g, mouseX, mouseY, delta);
        refreshRarity();
        int l = this.leftPos, t = this.topPos;

        // panel with the title in the header
        TrackerHud.rrect(g, l, t, l + W, t + panelHeight, PANEL);
        TrackerHud.border(g, l, t, l + W, t + panelHeight, 0x55FFFFFF);
        g.text(this.font, this.title, l + 8, t + 4, 0xFFFFFFFF, false);
        TrackerHud.rect(g, l + 6, t + TrackerHud.HEADER_H - 2, l + W - 6, t + TrackerHud.HEADER_H - 1,
                TrackerHud.withAlpha(0xFFB060FF, 0x99));

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
