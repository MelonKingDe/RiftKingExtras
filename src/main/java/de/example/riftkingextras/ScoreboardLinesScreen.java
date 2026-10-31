package de.example.riftkingextras;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Order and visibility of the scoreboard lines (one page of entries at a time). */
public class ScoreboardLinesScreen extends Screen {
    private static final int PAGE_SIZE = 6;
    private static final int ROW = 22;

    private final Screen parent;
    private final int page;

    public ScoreboardLinesScreen(Screen parent, int page) {
        super(Component.literal("Lines & order"));
        this.parent = parent;
        this.page = page;
    }

    private int pages() {
        return Math.max(1, (ScoreboardConfig.entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private void reopen(int newPage) {
        this.minecraft.gui.setScreen(new ScoreboardLinesScreen(this.parent, Math.max(0, Math.min(newPage, pages() - 1))));
    }

    @Override
    protected void init() {
        List<ScoreboardConfig.Entry> list = ScoreboardConfig.entries;
        int x0 = this.width / 2 - 140;
        int y = 34;

        int start = Math.min(page, pages() - 1) * PAGE_SIZE;
        int end = Math.min(list.size(), start + PAGE_SIZE);
        for (int i = start; i < end; i++) {
            final int index = i;
            ScoreboardConfig.Entry entry = list.get(i);
            SbElement element = entry.element();
            String name = element == null ? entry.kind : element.label;

            this.addRenderableWidget(Button.builder(Component.literal("^"), btn -> move(index, -1))
                    .bounds(x0, y, 20, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("v"), btn -> move(index, +1))
                    .bounds(x0 + 22, y, 20, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal((entry.enabled ? "[ON]  " : "[OFF]  ") + name), btn -> {
                entry.enabled = !entry.enabled;
                ScoreboardConfig.save();
                reopen(page);
            }).bounds(x0 + 44, y, 170, 20).build());
            if (element == SbElement.SEPARATOR) {
                this.addRenderableWidget(Button.builder(Component.literal("Remove"), btn -> {
                    ScoreboardConfig.entries.remove(entry);
                    ScoreboardConfig.save();
                    reopen(page);
                }).bounds(x0 + 216, y, 64, 20).build());
            }
            y += ROW;
        }

        int navY = 34 + PAGE_SIZE * ROW + 2;
        this.addRenderableWidget(Button.builder(Component.literal("< Prev"), btn -> reopen(page - 1))
                .bounds(this.width / 2 - 100, navY, 60, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Next >"), btn -> reopen(page + 1))
                .bounds(this.width / 2 + 40, navY, 60, 20).build());

        int actionY = navY + 24;
        this.addRenderableWidget(Button.builder(Component.literal("Add separator"), btn -> {
            ScoreboardConfig.entries.add(new ScoreboardConfig.Entry(SbElement.SEPARATOR, true));
            ScoreboardConfig.save();
            reopen(pages() - 1);
        }).bounds(this.width / 2 - 140, actionY, 135, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Reset order"), btn -> {
            ScoreboardConfig.resetEntries();
            reopen(0);
        }).bounds(this.width / 2 + 5, actionY, 135, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Done"), btn -> onClose())
                .bounds(this.width / 2 - 75, this.height - 26, 150, 20).build());
    }

    private void move(int index, int delta) {
        List<ScoreboardConfig.Entry> list = ScoreboardConfig.entries;
        int target = index + delta;
        if (target < 0 || target >= list.size()) return;
        ScoreboardConfig.Entry a = list.get(index);
        ScoreboardConfig.Entry b = list.get(target);
        list.set(index, b);
        list.set(target, a);
        ScoreboardConfig.save();
        reopen(page);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int cx = this.width / 2;
        center(graphics, "Lines & order", cx, 8, 0xFFFFFFFF);
        center(graphics, "Top to bottom as shown. Separators are the thin lines.", cx, 21, 0xFFAAAAAA);
        int navY = 34 + PAGE_SIZE * ROW + 2;
        center(graphics, "Page " + (Math.min(page, pages() - 1) + 1) + " / " + pages(), cx, navY + 6, 0xFFAAAAAA);
    }

    private void center(GuiGraphicsExtractor graphics, String text, int cx, int y, int color) {
        graphics.text(this.font, text, cx - this.font.width(text) / 2, y, color, true);
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
