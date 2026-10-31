package de.example.riftkingextras;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.IntSupplier;

/** Lets you place and resize one of the HUDs with sliders (live preview). Open with /rke hud or /rke timewarp hud. */
public class HudEditScreen extends Screen {
    private final Screen parent;
    private final HudKind kind;
    private PosSlider xSlider;
    private PosSlider ySlider;

    public HudEditScreen(Screen parent, HudKind kind) {
        super(Component.literal(kind.title));
        this.parent = parent;
        this.kind = kind;
    }

    HudKind kind() {
        return kind;
    }

    @Override
    protected void init() {
        // a default position (negative) becomes a real one, so the sliders can start from it
        kind.setX(kind.clampX(this.width, kind.x()));
        kind.setY(kind.clampY(this.height, kind.y()));

        int w = Math.min(220, this.width - 20);
        int x = this.width / 2 - w / 2;
        int y = this.height / 2 - 40;

        xSlider = new PosSlider(kind, x, y, w, 20, true, () -> Math.max(0, this.width - kind.scaledWidth()));
        ySlider = new PosSlider(kind, x, y + 26, w, 20, false, () -> Math.max(0, this.height - kind.scaledHeight()));
        this.addRenderableWidget(xSlider);
        this.addRenderableWidget(ySlider);

        this.addRenderableWidget(new ScaleSlider(kind, x, y + 52, w, 20, () -> {
            // a new size changes how far the HUD can move: keep it on screen
            kind.setX(Math.max(0, Math.min(kind.x(), this.width - kind.scaledWidth())));
            kind.setY(Math.max(0, Math.min(kind.y(), this.height - kind.scaledHeight())));
            xSlider.refresh();
            ySlider.refresh();
        }));

        this.addRenderableWidget(Button.builder(Component.literal("Reset"), btn -> {
            kind.reset();
            // reopen so the sliders pick up the new values
            this.minecraft.gui.setScreen(new HudEditScreen(this.parent, kind));
        }).bounds(x, y + 78, w, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Done"), btn -> onClose())
                .bounds(this.width / 2 - 75, this.height - 26, 150, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // Preview first, so the sliders stay on top of it
        kind.draw(graphics,
                kind.clampX(this.width, kind.x()),
                kind.clampY(this.height, kind.y()));
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        int cx = this.width / 2;
        center(graphics, kind.title, cx, 10, 0xFFFFFFFF);
        center(graphics, "Set position and size with the sliders.", cx, 24, 0xFFAAAAAA);
    }

    private void center(GuiGraphicsExtractor graphics, String text, int cx, int y, int color) {
        graphics.text(this.font, text, cx - this.font.width(text) / 2, y, color, true);
    }

    @Override
    public void removed() {
        RkeConfig.save();
        ScoreboardConfig.save();
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }

    /** Slider for the HUD position in GUI pixels; the range follows the current HUD size. */
    private static class PosSlider extends AbstractSliderButton {
        private final HudKind kind;
        private final boolean horizontal;
        private final IntSupplier range;

        PosSlider(HudKind kind, int x, int y, int width, int height, boolean horizontal, IntSupplier range) {
            super(x, y, width, height, Component.empty(),
                    fraction(horizontal ? kind.x() : kind.y(), range.getAsInt()));
            this.kind = kind;
            this.horizontal = horizontal;
            this.range = range;
            updateMessage();
        }

        private static double fraction(int pos, int range) {
            return range <= 0 ? 0 : Math.max(0.0, Math.min(1.0, pos / (double) range));
        }

        private int pos() {
            int r = range == null ? 0 : range.getAsInt();
            return (int) Math.round(this.value * r);
        }

        /** Re-read the position from the config (after the HUD size changed). */
        void refresh() {
            this.value = fraction(horizontal ? kind.x() : kind.y(), range.getAsInt());
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal((horizontal ? "Horizontal: " : "Vertical: ") + pos()));
        }

        @Override
        protected void applyValue() {
            if (horizontal) kind.setX(pos());
            else kind.setY(pos());
        }
    }

    /** Slider for the HUD size: 25% to 200% in 5% steps. */
    private static class ScaleSlider extends AbstractSliderButton {
        private static final float MIN = 0.25f, MAX = 2.0f;
        private final HudKind kind;
        private final Runnable onChange;

        ScaleSlider(HudKind kind, int x, int y, int width, int height, Runnable onChange) {
            super(x, y, width, height, Component.empty(), (kind.scale() - MIN) / (MAX - MIN));
            this.kind = kind;
            this.onChange = onChange;
            updateMessage();
        }

        private float scale() {
            return Math.round((MIN + (float) this.value * (MAX - MIN)) * 20f) / 20f;
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal("Size: " + Math.round(scale() * 100) + "%"));
        }

        @Override
        protected void applyValue() {
            kind.setScale(scale());
            if (onChange != null) onChange.run();
        }
    }
}
