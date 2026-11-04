package de.example.riftkingextras;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Settings of the music HUD. Open with /rke music or from the settings screen. */
public class MusicScreen extends Screen {
    private static final int[] LINES = {3, 5, 7};
    private static final int[] OFFSETS = {-500, -300, -100, 0, 100, 300, 500};

    private final Screen parent;

    public MusicScreen(Screen parent) {
        super(Component.literal("Music HUD"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int w = 150;
        int left = this.width / 2 - w - 5;
        int right = this.width / 2 + 5;
        int y = 44;
        int step = 24;

        toggle(left, y, w, "Music HUD", () -> MusicConfig.enabled, v -> {
            MusicConfig.enabled = v;
            MusicManager.applyEnabled(); // starts or stops the reader
        });
        toggle(left, y + step, w, "Lyrics", () -> MusicConfig.showLyrics, v -> MusicConfig.showLyrics = v);
        toggle(left, y + step * 2, w, "Cover art", () -> MusicConfig.showArt, v -> MusicConfig.showArt = v);
        toggle(left, y + step * 3, w, "Profanity filter", () -> MusicConfig.profanityFilter, v -> MusicConfig.profanityFilter = v);

        // lyric lines: 3, 5 or 7
        int lineIndex = 1;
        for (int i = 0; i < LINES.length; i++) if (LINES[i] == MusicConfig.lyricLines) lineIndex = i;
        final int currentLines = lineIndex;
        this.addRenderableWidget(Button.builder(Component.literal("Lyric lines: " + LINES[currentLines]), btn -> {
            MusicConfig.lyricLines = LINES[(currentLines + 1) % LINES.length];
            MusicConfig.save();
            this.minecraft.gui.setScreen(new MusicScreen(this.parent));
        }).bounds(right, y, w, 20).build());

        // lyrics timing
        int offsetIndex = 3;
        for (int i = 0; i < OFFSETS.length; i++) if (OFFSETS[i] == MusicConfig.lyricOffsetMs) offsetIndex = i;
        final int currentOffset = offsetIndex;
        String sign = MusicConfig.lyricOffsetMs > 0 ? "+" : "";
        this.addRenderableWidget(Button.builder(
                Component.literal("Lyrics timing: " + sign + MusicConfig.lyricOffsetMs + " ms"), btn -> {
                    MusicConfig.lyricOffsetMs = OFFSETS[(currentOffset + 1) % OFFSETS.length];
                    MusicConfig.save();
                    this.minecraft.gui.setScreen(new MusicScreen(this.parent));
                }).bounds(right, y + step, w, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Move music HUD"),
                btn -> this.minecraft.gui.setScreen(new HudEditScreen(this, HudKind.MUSIC)))
                .bounds(right, y + step * 2, w, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Done"), btn -> onClose())
                .bounds(this.width / 2 - 75, this.height - 26, 150, 20).build());
    }

    private void toggle(int x, int y, int w, String label, BooleanSupplier get, Consumer<Boolean> set) {
        this.addRenderableWidget(Button.builder(
                Component.literal(label + ": " + (get.getAsBoolean() ? "ON" : "OFF")), btn -> {
                    set.accept(!get.getAsBoolean());
                    MusicConfig.save();
                    this.minecraft.gui.setScreen(new MusicScreen(this.parent)); // refresh the labels
                }).bounds(x, y, w, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int cx = this.width / 2;
        center(graphics, "Music HUD", cx, 10, 0xFFFFFFFF);
        center(graphics, "Works with the free version of Spotify. Lyrics come from LRCLIB.", cx, 24, 0xFFAAAAAA);
        center(graphics, "Source: " + MusicManager.status(), cx, this.height - 44, 0xFF9AA0A6);
    }

    private void center(GuiGraphicsExtractor graphics, String text, int cx, int y, int color) {
        graphics.text(this.font, text, cx - this.font.width(text) / 2, y, color, true);
    }

    @Override
    public void removed() {
        MusicConfig.save();
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }
}
