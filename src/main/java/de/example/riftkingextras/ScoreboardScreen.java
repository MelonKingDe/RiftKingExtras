package de.example.riftkingextras;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Settings of the scoreboard HUD. Open with /rke scoreboard or from the settings screen. */
public class ScoreboardScreen extends Screen {
    private static final String[] NUMBER_FORMATS = {"As on Hypixel", "Short (1.2M)", "Long (1,234,567)"};
    private static final String[] ALIGNS = {"Left", "Center", "Right"};

    private final Screen parent;

    public ScoreboardScreen(Screen parent) {
        super(Component.literal("Scoreboard"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int w = 150;
        int left = this.width / 2 - w - 5;
        int right = this.width / 2 + 5;
        int y = 30;
        int step = 22;

        // Left column: behaviour
        toggle(left, y, w, "Scoreboard HUD", () -> ScoreboardConfig.enabled, v -> ScoreboardConfig.enabled = v);
        toggle(left, y + step, w, "Hide vanilla one", () -> ScoreboardConfig.hideVanilla, v -> ScoreboardConfig.hideVanilla = v);
        toggle(left, y + step * 2, w, "Outside SkyBlock", () -> ScoreboardConfig.outsideSkyBlock, v -> ScoreboardConfig.outsideSkyBlock = v);
        toggle(left, y + step * 3, w, "Hide when Tab", () -> ScoreboardConfig.hideWhenTab, v -> ScoreboardConfig.hideWhenTab = v);
        toggle(left, y + step * 4, w, "Hide when chat", () -> ScoreboardConfig.hideWhenChat, v -> ScoreboardConfig.hideWhenChat = v);
        toggle(left, y + step * 5, w, "Hide when F3", () -> ScoreboardConfig.hideWhenF3, v -> ScoreboardConfig.hideWhenF3 = v);
        toggle(left, y + step * 6, w, "Text shadow", () -> ScoreboardConfig.textShadow, v -> ScoreboardConfig.textShadow = v);
        toggle(left, y + step * 7, w, "24-hour real time", () -> ScoreboardConfig.time24h, v -> ScoreboardConfig.time24h = v);

        // Right column: lines and looks
        button(right, y, w, "Lines & order...", () -> this.minecraft.gui.setScreen(new ScoreboardLinesScreen(this, 0)));
        button(right, y + step, w, "Texts & colors...", () -> this.minecraft.gui.setScreen(new ScoreboardTextScreen(this)));
        cycle(right, y + step * 2, w, "Numbers", NUMBER_FORMATS, ScoreboardConfig.numberFormat,
                v -> ScoreboardConfig.numberFormat = v);
        cycle(right, y + step * 3, w, "Text align", ALIGNS, ScoreboardConfig.textAlign,
                v -> ScoreboardConfig.textAlign = v);
        cycle(right, y + step * 4, w, "Title align", ALIGNS, ScoreboardConfig.titleAlign,
                v -> ScoreboardConfig.titleAlign = v);
        cycle(right, y + step * 5, w, "Line spacing", new String[]{"0", "1", "2", "3", "4", "5", "6"},
                ScoreboardConfig.lineSpacing, v -> ScoreboardConfig.lineSpacing = v);
        toggle(right, y + step * 6, w, "Hide edge separators", () -> ScoreboardConfig.hideEdgeSeparators,
                v -> ScoreboardConfig.hideEdgeSeparators = v);
        toggle(right, y + step * 7, w, "Condense separators", () -> ScoreboardConfig.condenseSeparators,
                v -> ScoreboardConfig.condenseSeparators = v);

        button(this.width / 2 - 155, this.height - 26, 150, "Move scoreboard HUD",
                () -> this.minecraft.gui.setScreen(new HudEditScreen(this, HudKind.SCOREBOARD)));
        button(this.width / 2 + 5, this.height - 26, 150, "Done", this::onClose);
    }

    private void button(int x, int y, int w, String label, Runnable action) {
        this.addRenderableWidget(Button.builder(Component.literal(label), btn -> action.run())
                .bounds(x, y, w, 20).build());
    }

    private void toggle(int x, int y, int w, String label, BooleanSupplier get, Consumer<Boolean> set) {
        this.addRenderableWidget(Button.builder(
                Component.literal(label + ": " + (get.getAsBoolean() ? "ON" : "OFF")), btn -> {
                    set.accept(!get.getAsBoolean());
                    ScoreboardConfig.save();
                    this.minecraft.gui.setScreen(new ScoreboardScreen(this.parent)); // refresh the labels
                }).bounds(x, y, w, 20).build());
    }

    private void cycle(int x, int y, int w, String label, String[] options, int current, Consumer<Integer> set) {
        int safe = Math.max(0, Math.min(options.length - 1, current));
        this.addRenderableWidget(Button.builder(Component.literal(label + ": " + options[safe]), btn -> {
            set.accept((safe + 1) % options.length);
            ScoreboardConfig.save();
            this.minecraft.gui.setScreen(new ScoreboardScreen(this.parent));
        }).bounds(x, y, w, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        String title = "Scoreboard";
        graphics.text(this.font, title, this.width / 2 - this.font.width(title) / 2, 10, 0xFFFFFFFF, true);
    }

    @Override
    public void removed() {
        ScoreboardConfig.save();
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }
}
