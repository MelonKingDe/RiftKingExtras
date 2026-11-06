package de.example.riftkingextras;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Settings of the inventory HUD and the inventory screen. Open with /rke inventory or from the settings screen. */
public class InventoryHudScreen extends Screen {
    private final Screen parent;

    public InventoryHudScreen(Screen parent) {
        super(Component.literal("Inventory"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int w = Math.min(170, (this.width - 30) / 2);
        int left = this.width / 2 - w - 5;
        int right = this.width / 2 + 5;
        int y = 40;
        int step = 24;

        // left: the HUD at the bottom of the screen
        toggle(left, y, w, "Inventory HUD", () -> RkeConfig.invEnabled, v -> RkeConfig.invEnabled = v);
        toggle(left, y + step, w, "Only on SkyBlock", () -> RkeConfig.invOnlySkyBlock, v -> RkeConfig.invOnlySkyBlock = v);
        toggle(left, y + step * 2, w, "Hide vanilla hotbar", () -> RkeConfig.hideHotbar, v -> RkeConfig.hideHotbar = v);
        toggle(left, y + step * 3, w, "Hide hearts/xp/action bar", () -> RkeConfig.hideVanillaBars, v -> RkeConfig.hideVanillaBars = v);
        this.addRenderableWidget(Button.builder(Component.literal("Move inventory HUD"),
                btn -> this.minecraft.gui.setScreen(new HudEditScreen(this, HudKind.INVENTORY)))
                .bounds(left, y + step * 4 + 6, w, 20).build());

        // right: items and the inventory screen (key E)
        toggle(right, y, w, "Rarity frames (HUD)", () -> RkeConfig.rarityBorders, v -> RkeConfig.rarityBorders = v);
        toggle(right, y + step, w, "Ability cooldown timers", () -> RkeConfig.abilityTimers, v -> RkeConfig.abilityTimers = v);
        toggle(right, y + step * 2, w, "Inventory screen (E)", () -> RkeConfig.customInventory, v -> RkeConfig.customInventory = v);

        this.addRenderableWidget(Button.builder(Component.literal("Done"), btn -> onClose())
                .bounds(this.width / 2 - 75, this.height - 26, 150, 20).build());
    }

    private void toggle(int x, int y, int w, String label, BooleanSupplier get, Consumer<Boolean> set) {
        this.addRenderableWidget(Button.builder(
                Component.literal(label + ": " + (get.getAsBoolean() ? "ON" : "OFF")), btn -> {
                    set.accept(!get.getAsBoolean());
                    RkeConfig.save();
                    this.minecraft.gui.setScreen(new InventoryHudScreen(this.parent)); // refresh the labels
                }).bounds(x, y, w, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int cx = this.width / 2;
        center(graphics, "Inventory", cx, 10, 0xFFFFFFFF);
        center(graphics, "Equipment is read when you open /stats once.", cx, 24, 0xFFAAAAAA);
    }

    private void center(GuiGraphicsExtractor graphics, String text, int cx, int y, int color) {
        graphics.text(this.font, text, cx - this.font.width(text) / 2, y, color, true);
    }

    @Override
    public void removed() {
        RkeConfig.save();
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }
}
