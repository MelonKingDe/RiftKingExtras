package de.example.riftkingextras;

import de.example.riftkingextras.mixin.SlotAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * The inventory screen (key E) in the RiftKingExtras style, taken over from RiftKingInv: armor on the left, the four
 * equipment items on the right, your character in the middle, inventory and hotbar below, rarity frames around every
 * slot, and two columns of buttons for the SkyBlock menus (left click and right click open different menus).
 */
public class RiftInventoryScreen extends AbstractContainerScreen<InventoryMenu> {

    // ---- layout (GUI pixels) ----
    private static final int W = 180, H = 176;
    private static final int ARMOR_X = 36, ARMOR_Y = 19;             // item position, the slot box is 1 pixel larger
    private static final int EQUIP_X = W - 36 - 16, EQUIP_Y = 19;
    private static final int GRID_X = 10, GRID_Y = 95, HOTBAR_Y = 153;
    private static final int BANNER_W = 24, BANNER_Y = 19, BTN = 16, BTN_STEP = 17;
    private static final int BANNER_H = 4 * BTN_STEP + 6;
    private static final int LEFT_BANNER_X = 6, RIGHT_BANNER_X = W - 6 - BANNER_W;

    private static final int ACCENT = 0xFFB060FF;
    private static final int PANEL = 0xF00E0E14;
    private static final int SLOT_BG = 0xB0181820;

    private record Btn(int x, int y, ItemStack icon, String left, String right) {}

    private final int[] origX, origY;
    private final Btn[] buttons;

    public RiftInventoryScreen(LocalPlayer player) {
        super(player.inventoryMenu, player.getInventory(), Component.literal("RiftKingExtras"), W, H);

        // move the real slots to our layout (the original positions are restored when the screen closes)
        List<Slot> slots = this.menu.slots;
        origX = new int[slots.size()];
        origY = new int[slots.size()];
        for (int i = 0; i < slots.size(); i++) {
            Slot s = slots.get(i);
            origX[i] = s.x;
            origY[i] = s.y;
            int nx = -10000, ny = -10000; // crafting grid and offhand are hidden
            if (i >= 5 && i <= 8) {               // armor
                nx = ARMOR_X;
                ny = ARMOR_Y + (i - 5) * 18;
            } else if (i >= 9 && i <= 35) {       // inventory
                int n = i - 9;
                nx = GRID_X + (n % 9) * 18;
                ny = GRID_Y + (n / 9) * 18;
            } else if (i >= 36 && i <= 44) {      // hotbar
                nx = GRID_X + (i - 36) * 18;
                ny = HOTBAR_Y;
            }
            SlotAccessor acc = (SlotAccessor) s;
            acc.riftkingextras$setX(nx);
            acc.riftkingextras$setY(ny);
        }

        buttons = new Btn[]{
                // left column
                btn(LEFT_BANNER_X, 0, Items.PAINTING, "collection", "recipes"),
                btn(LEFT_BANNER_X, 1, Items.WRITABLE_BOOK, "quests", "chapters"),
                btn(LEFT_BANNER_X, 2, Items.EXPERIENCE_BOTTLE, "skills", "levels"),
                btn(LEFT_BANNER_X, 3, Items.BOOK, "guides", null),
                // right column
                btn(RIGHT_BANNER_X, 0, Items.CRAFTING_TABLE, "craft", null),
                btn(RIGHT_BANNER_X, 1, Items.CHEST, "storage", "sacks"),
                btn(RIGHT_BANNER_X, 2, Items.BONE, "pets", "loadouts"),
                btn(RIGHT_BANNER_X, 3, Items.ENDER_PEARL, "warps", "is"),
        };
    }

    private static Btn btn(int bannerX, int index, Item icon, String left, String right) {
        return new Btn(bannerX + (BANNER_W - BTN) / 2, BANNER_Y + 3 + index * BTN_STEP, new ItemStack(icon), left, right);
    }

    @Override
    public void removed() {
        List<Slot> slots = this.menu.slots;
        for (int i = 0; i < slots.size() && i < origX.length; i++) {
            SlotAccessor acc = (SlotAccessor) slots.get(i);
            acc.riftkingextras$setX(origX[i]);
            acc.riftkingextras$setY(origY[i]);
        }
        super.removed();
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractBackground(g, mouseX, mouseY, delta);
        int l = this.leftPos, t = this.topPos;

        // panel and header, like the HUDs
        TrackerHud.rrect(g, l, t, l + W, t + H, PANEL);
        TrackerHud.border(g, l, t, l + W, t + H, 0x55FFFFFF);
        g.text(this.font, "RIFTKING", l + 8, t + 4, 0xFFFFFFFF, false);
        g.text(this.font, "EXTRAS", l + 8 + this.font.width("RIFTKING "), t + 4, ACCENT, false);
        String label = "INVENTORY";
        g.text(this.font, label, l + W - 8 - this.font.width(label), t + 4, TrackerHud.MUTED, false);
        TrackerHud.rect(g, l + 6, t + TrackerHud.HEADER_H - 2, l + W - 6, t + TrackerHud.HEADER_H - 1, 0x22FFFFFF);

        // the two button columns
        drawBanner(g, l + LEFT_BANNER_X, t + BANNER_Y);
        drawBanner(g, l + RIGHT_BANNER_X, t + BANNER_Y);

        // your character
        TrackerHud.rrect(g, l + 56, t + 18, l + 124, t + 90, 0x66000000);
        TrackerHud.border(g, l + 56, t + 18, l + 124, t + 90, 0x22FFFFFF);
        if (this.minecraft != null && this.minecraft.player != null) {
            InventoryScreen.extractEntityInInventoryFollowsMouse(
                    g, l + 56, t + 18, l + 124, t + 90, 30, 0.0625F, mouseX, mouseY, this.minecraft.player);
        }

        // slot boxes with the rarity frame (armor, inventory, hotbar)
        for (int i = 5; i <= 44; i++) {
            Slot s = this.menu.slots.get(i);
            box(g, l + s.x - 1, t + s.y - 1, ItemRarity.frameColor(s.getItem()));
        }
        ItemStack[] equipment = InventoryHud.equipment();
        for (int k = 0; k < 4; k++) {
            box(g, l + EQUIP_X - 1, t + EQUIP_Y + k * 18 - 1, ItemRarity.frameColor(equipment[k]));
        }

        // button icons with a hover highlight
        for (Btn b : buttons) {
            g.item(b.icon(), l + b.x(), t + b.y());
            if (hit(mouseX, mouseY, l + b.x(), t + b.y(), BTN, BTN)) {
                TrackerHud.rrect(g, l + b.x(), t + b.y(), l + b.x() + BTN, t + b.y() + BTN, 0x40FFFFFF);
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        int l = this.leftPos, t = this.topPos;

        // equipment items (they are no real slots)
        ItemStack[] equipment = InventoryHud.equipment();
        for (int k = 0; k < 4; k++) {
            ItemStack st = equipment[k];
            int x = l + EQUIP_X, y = t + EQUIP_Y + k * 18;
            if (!st.isEmpty()) {
                g.item(st, x, y);
                g.itemDecorations(this.font, st, x, y);
            }
            if (hit(mouseX, mouseY, x - 1, y - 1, 18, 18)) {
                TrackerHud.rrect(g, x, y, x + 16, y + 16, 0x40FFFFFF);
                if (!st.isEmpty()) {
                    g.setTooltipForNextFrame(this.font, st, mouseX, mouseY);
                } else {
                    g.setTooltipForNextFrame(this.font, Component.literal("Click: /stats"), mouseX, mouseY);
                }
            }
        }

        // button tooltips
        for (Btn b : buttons) {
            if (hit(mouseX, mouseY, l + b.x(), t + b.y(), BTN, BTN)) {
                String text = "Left click: /" + b.left() + (b.right() != null ? "   Right click: /" + b.right() : "");
                g.setTooltipForNextFrame(this.font, Component.literal(text), mouseX, mouseY);
            }
        }

        this.extractTooltip(g, mouseX, mouseY);
    }

    /** No "Inventory" / "Crafting" labels. */
    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
    }

    private static void box(GuiGraphicsExtractor g, int x, int y, int frame) {
        TrackerHud.rrect(g, x, y, x + 18, y + 18, SLOT_BG);
        TrackerHud.border(g, x, y, x + 18, y + 18, frame);
    }

    private static void drawBanner(GuiGraphicsExtractor g, int x, int y) {
        TrackerHud.rrect(g, x, y, x + BANNER_W, y + BANNER_H, TrackerHud.withAlpha(ACCENT, 0x30));
        TrackerHud.border(g, x, y, x + BANNER_W, y + BANNER_H, TrackerHud.withAlpha(ACCENT, 0x80));
        TrackerHud.rect(g, x + 4, y + 1, x + BANNER_W - 4, y + 2, ACCENT);
    }

    private static boolean hit(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        int button = event.button(); // 0 = left, 1 = right
        int l = this.leftPos, t = this.topPos;

        for (Btn b : buttons) {
            if (hit(mx, my, l + b.x(), t + b.y(), BTN, BTN)) {
                String command = button == 0 ? b.left() : (button == 1 ? b.right() : null);
                if (command != null) send(command);
                return true;
            }
        }
        for (int k = 0; k < 4; k++) {
            if (hit(mx, my, l + EQUIP_X - 1, t + EQUIP_Y + k * 18 - 1, 18, 18)) {
                send("stats");
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private static void send(String command) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null) p.connection.sendCommand(command);
    }
}
