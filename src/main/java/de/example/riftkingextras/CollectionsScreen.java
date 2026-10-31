package de.example.riftkingextras;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Menu to add or remove tracked collections (up to 7). Open with /rke collections. */
public class CollectionsScreen extends Screen {
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9' -]{2,32}");
    private static final int LIST_Y = 78;
    private static final int ROW = 22;

    private final Screen parent;
    private final String message;
    private EditBox nameBox;
    private int listX;
    private int listW;

    public CollectionsScreen(Screen parent) {
        this(parent, "");
    }

    private CollectionsScreen(Screen parent, String message) {
        super(Component.literal("Collections"));
        this.parent = parent;
        this.message = message;
    }

    @Override
    protected void init() {
        int w = Math.min(280, this.width - 20);
        int x = this.width / 2 - w / 2;
        this.listX = x;
        this.listW = w;
        int y = 46;

        nameBox = new EditBox(this.font, x, y, w - 64, 20, Component.literal("Collection"));
        nameBox.setMaxLength(32);
        nameBox.setHint(Component.literal("Collection name"));
        this.addRenderableWidget(nameBox);
        this.addRenderableWidget(Button.builder(Component.literal("Add"), btn -> addCollection())
                .bounds(x + w - 60, y, 60, 20).build());

        int ry = LIST_Y;
        for (TrackedCollection c : new ArrayList<>(RkeConfig.collections)) {
            this.addRenderableWidget(Button.builder(Component.literal("Remove"), btn -> {
                RkeConfig.collections.remove(c);
                RkeConfig.save();
                CollectionApi.syncPlayersNow();
                reopen("");
            }).bounds(x + w - 60, ry, 60, 20).build());
            ry += ROW;
        }

        this.addRenderableWidget(Button.builder(Component.literal("Done"), btn -> onClose())
                .bounds(this.width / 2 - 75, this.height - 26, 150, 20).build());
    }

    private void addCollection() {
        String name = nameBox.getValue().trim();
        String slug = TrackedCollection.slugify(name);
        if (!VALID_NAME.matcher(name).matches() || slug.isEmpty()) {
            reopen("Invalid name (letters, digits, spaces).");
            return;
        }
        for (TrackedCollection c : RkeConfig.collections) {
            if (c.slug.equals(slug)) {
                reopen(c.name + " is already tracked.");
                return;
            }
        }
        if (RkeConfig.collections.size() >= RkeConfig.MAX_COLLECTIONS) {
            reopen("Maximum of " + RkeConfig.MAX_COLLECTIONS + " collections.");
            return;
        }
        TrackedCollection c = new TrackedCollection(name);
        c.init();
        RkeConfig.collections.add(c);
        RkeConfig.save();
        CollectionApi.syncNow();
        reopen("");
    }

    private void reopen(String msg) {
        this.minecraft.gui.setScreen(new CollectionsScreen(this.parent, msg));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        int cx = this.width / 2;
        center(graphics, "Collections", cx, 8, 0xFFFFFFFF);
        center(graphics, "Name as on the Elite leaderboard, e.g. Wilted Berberis", cx, 22, 0xFFAAAAAA);
        if (!message.isEmpty()) center(graphics, message, cx, 34, 0xFFE0524B);

        NumberFormat nf = NumberFormat.getIntegerInstance(Locale.US);
        List<TrackedCollection> list = new ArrayList<>(RkeConfig.collections);
        int ry = LIST_Y;
        for (TrackedCollection c : list) {
            long total = c.total();
            String label = c.itemNames.size() > 1 ? c.name + " (" + c.itemNames.size() + " items)" : c.name;
            graphics.text(this.font, label, listX + 4, ry + 6, 0xFFFFFFFF, true);
            String amount = total >= 0 ? nf.format(total) : "loading...";
            graphics.text(this.font, amount, listX + listW - 68 - this.font.width(amount), ry + 6, 0xFF999999, true);
            ry += ROW;
        }
        if (list.isEmpty()) center(graphics, "No collections yet.", cx, LIST_Y + 6, 0xFF777777);
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
