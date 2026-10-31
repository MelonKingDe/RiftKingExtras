package de.example.riftkingextras;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Menu to add or remove the players shown in the HUD table and to pick the collection to compare on. */
public class TrackedPlayersScreen extends Screen {
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final int LIST_Y = 100;
    private static final int ROW = 22;

    private final Screen parent;
    private final String message;
    private EditBox nameBox;
    private int listX;

    public TrackedPlayersScreen(Screen parent) {
        this(parent, "");
    }

    private TrackedPlayersScreen(Screen parent, String message) {
        super(Component.literal("Tracked players"));
        this.parent = parent;
        this.message = message;
    }

    @Override
    protected void init() {
        int w = Math.min(260, this.width - 20);
        int x = this.width / 2 - w / 2;
        this.listX = x;
        int y = 46;

        nameBox = new EditBox(this.font, x, y, w - 64, 20, Component.literal("Name"));
        nameBox.setMaxLength(16);
        nameBox.setHint(Component.literal("Player name"));
        this.addRenderableWidget(nameBox);
        this.addRenderableWidget(Button.builder(Component.literal("Add"), btn -> addPlayer())
                .bounds(x + w - 60, y, 60, 20).build());

        TrackedCollection compare = CollectionTracker.compareCollection();
        String label = "Compare on: " + (compare == null ? "-" : compare.name);
        Button compareButton = Button.builder(Component.literal(label), btn -> cycleCollection())
                .bounds(x, y + 24, w, 20).build();
        compareButton.active = RkeConfig.collections.size() > 1;
        this.addRenderableWidget(compareButton);

        int ry = LIST_Y;
        for (String name : new ArrayList<>(RkeConfig.players)) {
            this.addRenderableWidget(Button.builder(Component.literal("Remove"), btn -> {
                TrackedPlayers.remove(name);
                reopen("");
            }).bounds(x + w - 60, ry, 60, 20).build());
            ry += ROW;
        }

        this.addRenderableWidget(Button.builder(Component.literal("Done"), btn -> onClose())
                .bounds(this.width / 2 - 75, this.height - 26, 150, 20).build());
    }

    /** Switch the player table to the next tracked collection. */
    private void cycleCollection() {
        List<TrackedCollection> list = new ArrayList<>(RkeConfig.collections);
        if (list.size() < 2) return;
        TrackedCollection current = CollectionTracker.compareCollection();
        int next = (list.indexOf(current) + 1) % list.size();
        RkeConfig.playerSlug = list.get(next).slug;
        RkeConfig.save();
        CollectionApi.syncPlayersNow();
        reopen("");
    }

    private void addPlayer() {
        String name = nameBox.getValue().trim();
        if (!VALID_NAME.matcher(name).matches()) {
            reopen("Invalid name (letters, digits or _).");
        } else if (TrackedPlayers.contains(name)) {
            reopen(name + " is already tracked.");
        } else if (RkeConfig.players.size() >= RkeConfig.MAX_PLAYERS) {
            reopen("Maximum of " + RkeConfig.MAX_PLAYERS + " players.");
        } else {
            TrackedPlayers.add(name);
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
        center(graphics, "Their amount, rank and your difference show in the HUD.", cx, 22, 0xFFAAAAAA);
        if (!message.isEmpty()) center(graphics, message, cx, 34, 0xFFE0524B);

        List<TrackedPlayers.Info> infos = TrackedPlayers.infos();
        int ry = LIST_Y;
        for (TrackedPlayers.Info info : infos) {
            String status = info.amount >= 0 ? "ok" : info.status;
            graphics.text(this.font, info.name, listX + 4, ry + 6, 0xFFFFFFFF, true);
            graphics.text(this.font, status, listX + 4 + 100, ry + 6, 0xFF777777, true);
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
