package de.example.riftkingextras;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Menu to add or remove tracked players. Every player has his own collection (click the collection button of a
 * player to switch it), so player 1 can be compared on Timite while player 2 is compared on Agaricus Cap.
 */
public class TrackedPlayersScreen extends Screen {
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final int LIST_Y = 100;
    private static final int ROW = 22;

    /** Collection that a newly added player gets (kept while the screen is reopened). */
    private static String newSlug = "";

    private final Screen parent;
    private final String message;
    private EditBox nameBox;
    private int listX;
    private int listW;

    public TrackedPlayersScreen(Screen parent) {
        this(parent, "");
    }

    private TrackedPlayersScreen(Screen parent, String message) {
        super(Component.literal("Tracked players"));
        this.parent = parent;
        this.message = message;
    }

    private static String collectionName(String slug) {
        TrackedCollection c = CollectionTracker.bySlug(slug);
        return c == null ? "-" : c.name;
    }

    /** The slug after the given one in the list of tracked collections (wraps around). */
    private static String nextSlug(String current) {
        List<TrackedCollection> list = new ArrayList<>(RkeConfig.collections);
        if (list.isEmpty()) return "";
        int index = -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).slug.equals(current)) index = i;
        }
        return list.get((index + 1) % list.size()).slug;
    }

    @Override
    protected void init() {
        int w = Math.min(300, this.width - 20);
        int x = this.width / 2 - w / 2;
        this.listX = x;
        this.listW = w;
        int y = 46;

        if (CollectionTracker.bySlug(newSlug) == null && !RkeConfig.collections.isEmpty()) {
            newSlug = RkeConfig.collections.get(0).slug;
        }

        nameBox = new EditBox(this.font, x, y, w - 64, 20, Component.literal("Name"));
        nameBox.setMaxLength(16);
        nameBox.setHint(Component.literal("Player name"));
        this.addRenderableWidget(nameBox);
        this.addRenderableWidget(Button.builder(Component.literal("Add"), btn -> addPlayer())
                .bounds(x + w - 60, y, 60, 20).build());

        Button newCollection = Button.builder(Component.literal("New player's collection: " + collectionName(newSlug)),
                btn -> {
                    newSlug = nextSlug(newSlug);
                    reopen("");
                }).bounds(x, y + 24, w, 20).build();
        newCollection.active = RkeConfig.collections.size() > 1;
        this.addRenderableWidget(newCollection);

        int ry = LIST_Y;
        for (RkeConfig.PlayerEntry entry : new ArrayList<>(RkeConfig.players)) {
            String name = entry.name;
            String slug = TrackedPlayers.slugOf(entry);
            Button collection = Button.builder(Component.literal(collectionName(slug)), btn -> {
                TrackedPlayers.setCollection(name, nextSlug(slug));
                reopen("");
            }).bounds(x + w - 60 - 4 - 130, ry, 130, 20).build();
            collection.active = RkeConfig.collections.size() > 1;
            this.addRenderableWidget(collection);

            this.addRenderableWidget(Button.builder(Component.literal("Remove"), btn -> {
                TrackedPlayers.remove(name);
                reopen("");
            }).bounds(x + w - 60, ry, 60, 20).build());
            ry += ROW;
        }

        this.addRenderableWidget(Button.builder(Component.literal("Done"), btn -> onClose())
                .bounds(this.width / 2 - 75, this.height - 26, 150, 20).build());
    }

    private void addPlayer() {
        String name = nameBox.getValue().trim();
        if (RkeConfig.collections.isEmpty()) {
            reopen("Track a collection first.");
        } else if (!VALID_NAME.matcher(name).matches()) {
            reopen("Invalid name (letters, digits or _).");
        } else if (TrackedPlayers.contains(name)) {
            reopen(name + " is already tracked.");
        } else if (RkeConfig.players.size() >= RkeConfig.MAX_PLAYERS) {
            reopen("Maximum of " + RkeConfig.MAX_PLAYERS + " players.");
        } else {
            TrackedPlayers.add(name, newSlug);
            reopen("");
        }
    }

    private void reopen(String msg) {
        this.minecraft.gui.setScreen(new TrackedPlayersScreen(this.parent, msg));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        int cx = this.width / 2;
        center(graphics, "Tracked players", cx, 8, 0xFFFFFFFF);
        center(graphics, "Each player has his own collection. Click it to switch.", cx, 22, 0xFFAAAAAA);
        if (!message.isEmpty()) center(graphics, message, cx, 34, 0xFFE0524B);

        List<TrackedPlayers.Info> infos = TrackedPlayers.infos();
        int ry = LIST_Y;
        int buttonX = listX + listW - 60 - 4 - 130;
        for (TrackedPlayers.Info info : infos) {
            String status = info.amount >= 0 ? "ok" : info.status;
            graphics.text(this.font, info.name, listX + 4, ry + 6, 0xFFFFFFFF, true);
            int statusX = listX + 4 + this.font.width(info.name) + 6;
            if (statusX + this.font.width(status) < buttonX - 4) {
                graphics.text(this.font, status, statusX, ry + 6, 0xFF777777, true);
            }
            ry += ROW;
        }
        if (infos.isEmpty()) center(graphics, "No players yet.", cx, LIST_Y + 6, 0xFF777777);
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
