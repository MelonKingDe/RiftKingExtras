package de.example.riftkingextras;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Settings of the glowing border: main switch, color, speed, and which features get it. Open with /rke glow. */
public class GlowScreen extends Screen {
    private static final String[] COLOR_NAMES = {"Purple", "Blue", "Cyan", "Green", "Gold", "Red", "Pink", "White"};
    private static final String[] COLOR_VALUES = {"#B060FF", "#4D7CFF", "#2DD4BF", "#7BD88F", "#E8C468", "#E0524B", "#FF7BC8", "#FFFFFF"};
    private static final int[] SPEEDS = {10, 6, 3};
    private static final String[] SPEED_NAMES = {"Slow", "Normal", "Fast"};

    private final Screen parent;
    private EditBox colorBox;

    public GlowScreen(Screen parent) {
        super(Component.literal("Glow border"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = this.width / 2 - 155, right = this.width / 2 + 5, w = 150;
        int y = 32;

        this.addRenderableWidget(Button.builder(Component.literal("Glow border: " + (RkeConfig.glowEnabled ? "ON" : "OFF")),
                btn -> flip(() -> RkeConfig.glowEnabled = !RkeConfig.glowEnabled)).bounds(left, y, w, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Soft glow outside: " + (RkeConfig.glowOuter ? "ON" : "OFF")),
                btn -> flip(() -> RkeConfig.glowOuter = !RkeConfig.glowOuter)).bounds(right, y, w, 20).build());

        int speedIndex = 1;
        for (int i = 0; i < SPEEDS.length; i++) if (SPEEDS[i] == RkeConfig.glowSeconds) speedIndex = i;
        final int current = speedIndex;
        this.addRenderableWidget(Button.builder(Component.literal("Speed: " + SPEED_NAMES[current]),
                btn -> flip(() -> RkeConfig.glowSeconds = SPEEDS[(current + 1) % SPEEDS.length]))
                .bounds(left, y + 24, w, 20).build());

        // color: a row of presets and a field for any color
        colorBox = new EditBox(this.font, right, y + 24, 80, 20, Component.literal("Color"));
        colorBox.setMaxLength(7);
        colorBox.setHint(Component.literal("#RRGGBB"));
        colorBox.setValue(RkeConfig.glowColor);
        this.addRenderableWidget(colorBox);
        this.addRenderableWidget(Button.builder(Component.literal("Apply"), btn -> {
            String c = colorBox.getValue().trim();
            if (c.matches("#?[0-9A-Fa-f]{6}")) {
                RkeConfig.glowColor = c.startsWith("#") ? c : "#" + c;
                RkeConfig.save();
            }
            this.minecraft.gui.setScreen(new GlowScreen(this.parent));
        }).bounds(right + 84, y + 24, 66, 20).build());

        int bw = Math.min(40, (this.width - 20) / 8 - 3);
        int bx = this.width / 2 - (8 * (bw + 3)) / 2;
        for (int i = 0; i < 8; i++) {
            final int index = i;
            this.addRenderableWidget(Button.builder(Component.literal(COLOR_NAMES[i].substring(0, Math.min(3, COLOR_NAMES[i].length()))),
                    btn -> {
                        RkeConfig.glowColor = COLOR_VALUES[index];
                        RkeConfig.save();
                        this.minecraft.gui.setScreen(new GlowScreen(this.parent));
                    }).bounds(bx + i * (bw + 3), y + 52, bw, 16).build());
        }

        // which features get the glow
        for (int i = 0; i < GlowBorder.FEATURES.length; i++) {
            final String feature = GlowBorder.FEATURES[i];
            boolean on = !RkeConfig.glowDisabled.contains(feature);
            int col = i % 2, row = i / 2;
            this.addRenderableWidget(Button.builder(Component.literal(GlowBorder.LABELS[i] + ": " + (on ? "ON" : "OFF")), btn -> flip(() -> {
                if (!RkeConfig.glowDisabled.remove(feature)) RkeConfig.glowDisabled.add(feature);
            })).bounds(col == 0 ? left : right, y + 96 + row * 22, w, 20).build());
        }

        this.addRenderableWidget(Button.builder(Component.literal("Done"), btn -> onClose())
                .bounds(this.width / 2 - 75, this.height - 26, 150, 20).build());
    }

    private void flip(Runnable change) {
        change.run();
        RkeConfig.save();
        this.minecraft.gui.setScreen(new GlowScreen(this.parent));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int cx = this.width / 2;
        String title = "Glow border";
        graphics.text(this.font, title, cx - this.font.width(title) / 2, 10, 0xFFFFFFFF, true);
        String hint = "Color presets: Pur, Blu, Cya, Gre, Gol, Red, Pin, Whi. Features with the glow:";
        graphics.text(this.font, hint, cx - this.font.width(hint) / 2, 32 + 74, 0xFF9AA0A6, false);

        // a small preview of the chosen color
        int rgb = GlowBorder.rgb();
        TrackerHud.rrect(graphics, cx + 160, 36, cx + 176, 52, 0xFF000000 | rgb);
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
