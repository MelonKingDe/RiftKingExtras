package de.example.riftkingextras;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Custom title, custom footer and accent color of the scoreboard HUD. Color codes with &amp; work (&6 gold, &l bold). */
public class ScoreboardTextScreen extends Screen {
    private final Screen parent;
    private EditBox titleBox;
    private EditBox footerBox;
    private EditBox accentBox;

    public ScoreboardTextScreen(Screen parent) {
        super(Component.literal("Texts & colors"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int w = Math.min(260, this.width - 20);
        int x = this.width / 2 - w / 2;
        int y = 44;

        titleBox = new EditBox(this.font, x, y, w, 20, Component.literal("Title"));
        titleBox.setMaxLength(64);
        titleBox.setHint(Component.literal("Custom title, e.g. &6&lMy Board"));
        titleBox.setValue(ScoreboardConfig.customTitle);
        this.addRenderableWidget(titleBox);

        this.addRenderableWidget(Button.builder(
                Component.literal("Use Hypixel title: " + (ScoreboardConfig.useHypixelTitle ? "ON" : "OFF")), btn -> {
                    saveBoxes();
                    ScoreboardConfig.useHypixelTitle = !ScoreboardConfig.useHypixelTitle;
                    ScoreboardConfig.save();
                    this.minecraft.gui.setScreen(new ScoreboardTextScreen(this.parent));
                }).bounds(x, y + 24, w, 20).build());

        footerBox = new EditBox(this.font, x, y + 66, w, 20, Component.literal("Footer"));
        footerBox.setMaxLength(64);
        footerBox.setHint(Component.literal("Custom footer, e.g. &7play.example.net"));
        footerBox.setValue(ScoreboardConfig.customFooter);
        this.addRenderableWidget(footerBox);

        this.addRenderableWidget(Button.builder(
                Component.literal("Use custom footer: " + (ScoreboardConfig.useCustomFooter ? "ON" : "OFF")), btn -> {
                    saveBoxes();
                    ScoreboardConfig.useCustomFooter = !ScoreboardConfig.useCustomFooter;
                    ScoreboardConfig.save();
                    this.minecraft.gui.setScreen(new ScoreboardTextScreen(this.parent));
                }).bounds(x, y + 90, w, 20).build());

        accentBox = new EditBox(this.font, x, y + 132, w, 20, Component.literal("Accent"));
        accentBox.setMaxLength(7);
        accentBox.setHint(Component.literal("Accent color, e.g. #B060FF"));
        accentBox.setValue(ScoreboardConfig.accent);
        this.addRenderableWidget(accentBox);

        this.addRenderableWidget(Button.builder(Component.literal("Done"), btn -> onClose())
                .bounds(this.width / 2 - 75, this.height - 26, 150, 20).build());
    }

    private void saveBoxes() {
        if (titleBox == null) return;
        ScoreboardConfig.customTitle = titleBox.getValue();
        ScoreboardConfig.footerSet(footerBox.getValue());
        String accent = accentBox.getValue().trim();
        if (accent.matches("#?[0-9A-Fa-f]{6}")) ScoreboardConfig.accent = accent.startsWith("#") ? accent : "#" + accent;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int cx = this.width / 2;
        center(graphics, "Texts & colors", cx, 8, 0xFFFFFFFF);
        center(graphics, "Color codes: &6 gold, &b aqua, &c red, &l bold, &r reset", cx, 21, 0xFFAAAAAA);
        int x = this.width / 2 - Math.min(260, this.width - 20) / 2;
        graphics.text(this.font, "Title", x, 34, 0xFF9AA0A6, false);
        graphics.text(this.font, "Footer", x, 34 + 66 - 10, 0xFF9AA0A6, false);
        graphics.text(this.font, "Accent color (line under the title)", x, 34 + 132 - 10, 0xFF9AA0A6, false);
    }

    private void center(GuiGraphicsExtractor graphics, String text, int cx, int y, int color) {
        graphics.text(this.font, text, cx - this.font.width(text) / 2, y, color, true);
    }

    @Override
    public void removed() {
        saveBoxes();
        ScoreboardConfig.save();
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }
}
