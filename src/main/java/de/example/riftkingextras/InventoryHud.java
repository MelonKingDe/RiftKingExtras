package de.example.riftkingextras;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;
import org.joml.Matrix3x2fStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The fourth HUD: health, mana and rift time bars, equipment (left), armor (right), the inventory above the hotbar,
 * speed and rift damage at the bottom. Items get a 1 pixel frame in their rarity color.
 */
public final class InventoryHud {
    private static final int SLOT = 18, PITCH = 19, PAD = 6, COL_GAP = 8, BAR_H = 8; // thin bars (was 12)
    private static final int GRID_W = 9 * PITCH - 1;
    private static final int GRID_X = PAD + SLOT + COL_GAP;
    private static final int ARMOR_X = GRID_X + GRID_W + COL_GAP;
    private static final int GRID_Y = PAD + BAR_H + 4;
    private static final int HOTBAR_Y = GRID_Y + 3 * PITCH - 1 + 4;
    private static final int BOTTOM_Y = HOTBAR_Y + SLOT + 4;
    static final int W = ARMOR_X + SLOT + PAD;
    static final int H = BOTTOM_Y + BAR_H + PAD;

    private static final int HEALTH = 0xFFE0524B, MANA = 0xFF55CFFF, RIFT = 0xFF7BD88F, LEVEL = 0xFFE8C468;
    private static final int SELECTED = 0xFF55FFFF;
    private static final int[] EQUIPMENT_MENU_SLOTS = {10, 19, 28, 37};

    /** Items and rarity colors, rebuilt a few times per second. */
    private record Snap(ItemStack[] equip, ItemStack[] armor, ItemStack[] inv, int[] equipC, int[] armorC, int[] invC,
                        List<List<AbilityCooldowns.AbilityInfo>> abilities) {}

    private static volatile Snap snap = null;
    private static final ItemStack[] equipCache = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
    private static String equipSource = "none yet - open /equipment once";
    private static volatile String hookInfo = "not registered yet";
    private static int ticks = 0;

    private InventoryHud() {}

    static int scaledWidth() { return (int) Math.ceil(W * RkeConfig.invScale); }
    static int scaledHeight() { return (int) Math.ceil(H * RkeConfig.invScale); }

    static String hookInfo() { return hookInfo; }
    static void setHookInfo(String info) { hookInfo = info; }
    static String equipmentInfo() { return equipSource; }

    /** True while this HUD is shown (it then replaces the vanilla hotbar). */
    static boolean isShowing() {
        Minecraft mc = Minecraft.getInstance();
        return RkeConfig.invEnabled && mc.player != null && (!RkeConfig.invOnlySkyBlock || SkyBlockState.onSkyBlock);
    }

    static boolean hidesHotbar() { return RkeConfig.hideHotbar && isShowing(); }
    static boolean hidesStatusBars() { return RkeConfig.hideVanillaBars && isShowing(); }

    // ---------- data ----------

    static void tick(Minecraft mc) {
        if (++ticks % 5 != 0) return;
        Player p = mc.player;
        if (p == null) return;
        try {
            ItemStack[] armor = {
                    p.getItemBySlot(EquipmentSlot.HEAD), p.getItemBySlot(EquipmentSlot.CHEST),
                    p.getItemBySlot(EquipmentSlot.LEGS), p.getItemBySlot(EquipmentSlot.FEET)};
            ItemStack[] inv = new ItemStack[36];
            for (int i = 0; i < 36; i++) inv[i] = p.getInventory().getItem(i);
            ItemStack[] equip = readEquipment(mc, p);
            // abilities (and their cooldowns) of the hotbar items, for the cooldown timers
            List<List<AbilityCooldowns.AbilityInfo>> abilities = new ArrayList<>();
            for (int i = 0; i < 9; i++) abilities.add(RkeConfig.abilityTimers ? AbilityCooldowns.parse(inv[i]) : List.of());
            AbilityCooldowns.setHotbar(abilities);
            snap = new Snap(equip, armor, inv, colors(equip), colors(armor), colors(inv), abilities);
        } catch (RuntimeException e) {
            snap = null; // never break the game because of an unexpected inventory
        }
    }

    private static int[] colors(ItemStack[] stacks) {
        int[] out = new int[stacks.length];
        for (int i = 0; i < stacks.length; i++) out[i] = RkeConfig.rarityBorders ? ItemRarity.colorOf(stacks[i]) : 0;
        return out;
    }

    /**
     * SkyBlock keeps the equipment out of the normal inventory. It is read when the "Your Equipment and Stats"
     * menu (/equipment) is open and remembered for the rest of the session.
     */
    private static boolean restored = false;

    private static ItemStack[] readEquipment(Minecraft mc, Player p) {
        // once per game start: bring back the equipment saved in the config (needs a loaded world for the item data)
        if (!restored && mc.level != null) {
            restored = true;
            if (allEmpty(equipCache)) {
                boolean any = false;
                for (int k = 0; k < 4; k++) {
                    ItemStack saved = EquipmentStore.decode(RkeConfig.equipmentJson[k], mc.level.registryAccess());
                    equipCache[k] = saved;
                    if (!saved.isEmpty()) any = true;
                }
                if (any) equipSource = "restored from the last game session (open /equipment to refresh)";
            }
        }
        if (mc.gui.screen() instanceof AbstractContainerScreen<?> screen
                && screen.getTitle().getString().contains("Equipment")) {
            List<Slot> slots = screen.getMenu().slots;
            for (int k = 0; k < 4; k++) {
                int index = EQUIPMENT_MENU_SLOTS[k];
                if (index >= slots.size()) continue;
                ItemStack s = slots.get(index).getItem();
                boolean empty = s.isEmpty() || s.getHoverName().getString().toLowerCase(Locale.ROOT).contains("empty");
                equipCache[k] = empty ? ItemStack.EMPTY : s.copy();
            }
            equipSource = "read from the equipment menu";

            // keep it for the next game start
            boolean changed = false;
            for (int k = 0; k < 4; k++) {
                String json = EquipmentStore.encode(equipCache[k], mc.level.registryAccess());
                if (!json.equals(RkeConfig.equipmentJson[k])) {
                    RkeConfig.equipmentJson[k] = json;
                    changed = true;
                }
            }
            if (changed) RkeConfig.save();
        } else if (allEmpty(equipCache)) {
            // some setups show the equipment in the four crafting slots of the inventory
            List<Slot> slots = p.inventoryMenu.slots;
            if (slots.size() > 4) {
                boolean any = false;
                for (int k = 0; k < 4; k++) {
                    ItemStack s = slots.get(k + 1).getItem();
                    if (!s.isEmpty()) {
                        equipCache[k] = s.copy();
                        any = true;
                    }
                }
                if (any) equipSource = "read from the crafting slots";
            }
        }
        return equipCache.clone();
    }

    private static boolean allEmpty(ItemStack[] stacks) {
        for (ItemStack s : stacks) if (!s.isEmpty()) return false;
        return true;
    }

    /** The selected hotbar slot. Looked up by reflection (method or field name differs between versions). */
    private static int selectedSlot(Player p) {
        Object inv = p.getInventory();
        try {
            Method m = inv.getClass().getMethod("getSelectedSlot");
            return (int) m.invoke(inv);
        } catch (ReflectiveOperationException | RuntimeException e) {
            try {
                return inv.getClass().getField("selected").getInt(inv);
            } catch (ReflectiveOperationException | RuntimeException e2) {
                return -1;
            }
        }
    }

    // ---------- drawing ----------

    static void render(GuiGraphicsExtractor graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (!isShowing()) return;
        if (mc.gui.screen() instanceof HudEditScreen e && e.kind() == HudKind.INVENTORY) return; // preview instead
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        draw(graphics, HudKind.INVENTORY.clampX(sw, RkeConfig.invX), HudKind.INVENTORY.clampY(sh, RkeConfig.invY));
    }

    /** Draws the HUD with its top-left corner at (x, y), scaled by the inventory HUD scale. */
    static void draw(GuiGraphicsExtractor g, int x, int y) {
        float s = RkeConfig.invScale;
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(s, s);
        drawContent(g);
        pose.popMatrix();
    }

    private static void drawContent(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        Snap s = snap;

        TrackerHud.rrect(g, 0, 0, W, H, 0xCC0E0E14);     // same panel as the other HUDs
        TrackerHud.border(g, 0, 0, W, H, 0x33FFFFFF);

        // health and mana bars
        int barW = (W - 2 * PAD - 6) / 2;
        StatsTracker.BarInfo health = StatsTracker.healthBar(mc);
        StatsTracker.BarInfo mana = StatsTracker.manaBar();
        bar(g, font, PAD, PAD, barW, health.frac(), health.text(), HEALTH);
        bar(g, font, PAD + barW + 6, PAD, barW, mana.frac(), mana.text(), MANA);

        // equipment (left) and armor (right): four slots, the last one in the hotbar row
        for (int k = 0; k < 4; k++) {
            int rowY = k < 3 ? GRID_Y + k * PITCH : HOTBAR_Y;
            slot(g, font, s == null ? ItemStack.EMPTY : s.equip()[k], s == null ? 0 : s.equipC()[k], PAD, rowY, false);
            slot(g, font, s == null ? ItemStack.EMPTY : s.armor()[k], s == null ? 0 : s.armorC()[k], ARMOR_X, rowY, false);
        }

        // inventory (3 rows) above the hotbar
        int selected = mc.player == null ? -1 : selectedSlot(mc.player);
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                int i = 9 + r * 9 + c;
                slot(g, font, s == null ? ItemStack.EMPTY : s.inv()[i], s == null ? 0 : s.invC()[i],
                        GRID_X + c * PITCH, GRID_Y + r * PITCH, false);
            }
        }
        for (int c = 0; c < 9; c++) {
            slot(g, font, s == null ? ItemStack.EMPTY : s.inv()[c], s == null ? 0 : s.invC()[c],
                    GRID_X + c * PITCH, HOTBAR_Y, c == selected,
                    s == null ? null : AbilityCooldowns.stateOf(s.abilities().get(c)));
        }

        // bottom row: speed (left), time/level bar (middle), rift damage or defense (right)
        boolean rift = SkyBlockState.inRift;
        String speed = "\u2726 " + (StatsTracker.speed < 0 ? "-" : fmt(StatsTracker.speed));
        g.text(font, speed, PAD, BOTTOM_Y, StatsTracker.speedLive ? 0xFFE8C468 : 0xFF8A8F98, true);

        String stat;
        int statColor;
        if (rift) { // Rift: rift damage
            stat = (StatsTracker.riftDamage < 0 ? "-" : fmt(StatsTracker.riftDamage)) + " \u2694";
            statColor = StatsTracker.riftDamageLive ? 0xFFFF8080 : 0xFF8A8F98;
        } else {    // everywhere else: defense
            stat = "\u2748 " + (StatsTracker.defense < 0 ? "-" : fmt(StatsTracker.defense));
            statColor = StatsTracker.defenseLive ? 0xFF7BD88F : 0xFF8A8F98;
        }
        g.text(font, stat, W - PAD - font.width(stat), BOTTOM_Y, statColor, true);

        int midX = PAD + 46;
        int midW = W - 2 * PAD - 92;
        StatsTracker.BarInfo bottom = rift ? StatsTracker.riftBar(mc) : StatsTracker.levelBar(mc);
        bar(g, font, midX, BOTTOM_Y, midW, bottom.frac(), bottom.text(), rift ? RIFT : LEVEL);
    }

    private static String fmt(long v) {
        return String.format(Locale.US, "%,d", v);
    }

    /** A bar with a rounded track, a colored fill and the value centered in it. */
    private static void bar(GuiGraphicsExtractor g, Font font, int x, int y, int w, double frac, String text, int color) {
        TrackerHud.rrect(g, x, y, x + w, y + BAR_H, 0x33FFFFFF);
        int fill = (int) Math.round(w * frac);
        if (fill >= 4) TrackerHud.rrect(g, x, y, x + fill, y + BAR_H, TrackerHud.withAlpha(color, 0xCC));
        else if (fill > 0) TrackerHud.rect(g, x, y + 1, x + fill, y + BAR_H - 1, TrackerHud.withAlpha(color, 0xCC));
        // small text, so it fits into the thin bar
        float textScale = 0.75f;
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(x + (w - font.width(text) * textScale) / 2f, y + (BAR_H - 8 * textScale) / 2f);
        pose.scale(textScale, textScale);
        g.text(font, text, 0, 0, 0xFFFFFFFF, true);
        pose.popMatrix();
    }

    /** One item slot: faint background, 1 pixel frame in the rarity color, the item, and a marker if selected. */
    private static void slot(GuiGraphicsExtractor g, Font font, ItemStack stack, int rarity, int x, int y, boolean selected) {
        slot(g, font, stack, rarity, x, y, selected, null);
    }

    /** Like above; [cooldown] (hotbar only) darkens the item and shows the seconds until its ability is ready. */
    private static void slot(GuiGraphicsExtractor g, Font font, ItemStack stack, int rarity, int x, int y,
                             boolean selected, AbilityCooldowns.State cooldown) {
        int bg = rarity != 0 ? TrackerHud.withAlpha(rarity, 0x26) : 0x1AFFFFFF;
        TrackerHud.rrect(g, x, y, x + SLOT, y + SLOT, bg);
        if (rarity != 0) TrackerHud.border(g, x, y, x + SLOT, y + SLOT, rarity);

        if (!stack.isEmpty()) {
            if (ItemDraw.item(g, stack, x + 1, y + 1)) {
                ItemDraw.decorations(g, font, stack, x + 1, y + 1);
            } else {
                // no item drawing available: show the first letter of the name instead
                String name = stack.getHoverName().getString().strip();
                if (!name.isEmpty()) g.text(font, name.substring(0, 1), x + 6, y + 5, rarity != 0 ? rarity : 0xFFFFFFFF, true);
            }
        }
        if (cooldown != null && !stack.isEmpty()) {
            // like the vanilla cooldown: a dark layer that shrinks as the time runs out, plus the seconds
            double left = cooldown.totalMs() > 0 ? Math.min(1.0, cooldown.remainingMs() / (double) cooldown.totalMs()) : 1.0;
            int top = y + 1 + (int) Math.floor(16 * (1.0 - left));
            TrackerHud.rect(g, x + 1, top, x + 17, y + 17, 0xA0000000);
            long secs = (cooldown.remainingMs() + 999) / 1000;
            String text = secs >= 60 ? (secs / 60) + ":" + String.format(Locale.ROOT, "%02d", secs % 60) : String.valueOf(secs);
            float textScale = 0.8f;
            Matrix3x2fStack pose = g.pose();
            pose.pushMatrix();
            pose.translate(x + (SLOT - font.width(text) * textScale) / 2f, y + (SLOT - 8 * textScale) / 2f);
            pose.scale(textScale, textScale);
            g.text(font, text, 0, 0, 0xFFFFFFFF, true);
            pose.popMatrix();
        }
        if (selected) TrackerHud.border(g, x - 1, y - 1, x + SLOT + 1, y + SLOT + 1, SELECTED);
    }
}
