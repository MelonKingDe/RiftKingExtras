package de.example.riftkingextras;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Settings of the inventory HUD. Open with /rke inventory or from the settings screen. */
public class InventoryHudScreen extends Screen {
    private final Screen parent;

    public InventoryHudScreen(Screen parent) {
        super(Component.literal("Inventory HUD"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int w = 220;
        int x = this.width / 2 - w / 2;
        int y = 34;
        int step = 24;

        toggle(x, y, w, "Inventory HUD", () -> RkeConfig.invEnabled, v -> RkeConfig.invEnabled = v);
        toggle(x, y + step, w, "Only on SkyBlock", () -> RkeConfig.invOnlySkyBlock, v -> RkeConfig.invOnlySkyBlock = v);
        toggle(x, y + step * 2, w, "Hide vanilla hotbar", () -> RkeConfig.hideHotbar, v -> RkeConfig.hideHotbar = v);
        toggle(x, y + step * 3, w, "Hide vanilla health/xp bars", () -> RkeConfig.hideStatusBars, v -> RkeConfig.hideStatusBars = v);
        toggle(x, y + step * 4, w, "Rarity frames", () -> RkeConfig.rarityBorders, v -> RkeConfig.rarityBorders = v);

        this.addRenderableWidget(Button.builder(Component.literal("Move inventory HUD"),
                btn -> this.minecraft.gui.setScreen(new HudEditScreen(this, HudKind.INVENTORY)))
                .bounds(x, y + step * 5 + 6, w, 20).build());

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
        String title = "Inventory HUD";
        graphics.text(this.font, title, this.width / 2 - this.font.width(title) / 2, 10, 0xFFFFFFFF, true);
        String hint = "Equipment is read when you open /equipment once.";
        graphics.text(this.font, hint, this.width / 2 - this.font.width(hint) / 2, 22, 0xFFAAAAAA, false);
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
