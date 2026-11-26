package de.example.riftkingextras;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.time.LocalDate;

/** Settings of the collection race: which collection, against whom, until when. Open with /rke race. */
public class RaceScreen extends Screen {
    private final Screen parent;
    private EditBox collectionBox, rivalBox, deadlineBox;
    private String message = "";

    public RaceScreen(Screen parent) {
        this(parent, "");
    }

    private RaceScreen(Screen parent, String message) {
        super(Component.literal("Collection race"));
        this.parent = parent;
        this.message = message;
    }

    @Override
    protected void init() {
        int w = Math.min(240, this.width - 20);
        int x = this.width / 2 - w / 2;
        int y = 40;

        this.addRenderableWidget(Button.builder(Component.literal("Collection race HUD: " + (RaceConfig.enabled ? "ON" : "OFF")), btn -> {
            RaceConfig.enabled = !RaceConfig.enabled;
            RaceConfig.save();
            if (RaceConfig.enabled) CollectionApi.raceRefreshNow();
            this.minecraft.gui.setScreen(new RaceScreen(this.parent));
        }).bounds(x, y, w, 20).build());

        collectionBox = box(x, y + 44, w, "Collection, e.g. Timite", RaceConfig.collection);
        rivalBox = box(x, y + 82, w, "Rival: a player name, or top", RaceConfig.rival);
        deadlineBox = box(x, y + 120, w, "Goal date dd.MM.yyyy",
                RaceConfig.SHOWN.format(RaceConfig.deadlineDate()));

        this.addRenderableWidget(Button.builder(Component.literal("Apply and update now"), btn -> apply())
                .bounds(x, y + 148, w, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Move race HUD"),
                btn -> {
                    saveFields();
                    this.minecraft.gui.setScreen(new HudEditScreen(this, HudKind.RACE));
                }).bounds(x, y + 172, w, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Done"), btn -> onClose())
                .bounds(this.width / 2 - 75, this.height - 26, 150, 20).build());
    }

    private EditBox box(int x, int y, int w, String hint, String value) {
        EditBox box = new EditBox(this.font, x, y, w, 18, Component.literal(hint));
        box.setMaxLength(40);
        box.setHint(Component.literal(hint));
        box.setValue(value);
        this.addRenderableWidget(box);
        return box;
    }

    /** Saves what is typed; returns false if the date is wrong. */
    private boolean saveFields() {
        String collection = collectionBox.getValue().trim();
        if (!collection.isEmpty()) RaceConfig.collection = collection;
        RaceConfig.rival = rivalBox.getValue().trim().isEmpty() ? "top" : rivalBox.getValue().trim();
        LocalDate date = RaceConfig.parseTyped(deadlineBox.getValue());
        if (date == null) return false;
        RaceConfig.deadline = date.toString();
        RaceConfig.save();
        return true;
    }

    private void apply() {
        boolean ok = saveFields();
        RaceConfig.save();
        CollectionApi.raceRefreshNow();
        this.minecraft.gui.setScreen(new RaceScreen(this.parent, ok ? "Saved. Updating..." : "Wrong date: use dd.MM.yyyy"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int cx = this.width / 2;
        center(graphics, "Collection race", cx, 10, 0xFFFFFFFF);
        center(graphics, "You against a player or the number 1, with a goal date.", cx, 24, 0xFFAAAAAA);
        int x = cx - Math.min(240, this.width - 20) / 2;
        graphics.text(this.font, "Collection", x, 40 + 34, 0xFF9AA0A6, false);
        graphics.text(this.font, "Rival", x, 40 + 72, 0xFF9AA0A6, false);
        graphics.text(this.font, "Goal date", x, 40 + 110, 0xFF9AA0A6, false);
        if (!message.isEmpty()) center(graphics, message, cx, 40 + 198, message.startsWith("Wrong") ? 0xFFE0524B : 0xFF7BD88F);
    }

    private void center(GuiGraphicsExtractor graphics, String text, int cx, int y, int color) {
        graphics.text(this.font, text, cx - this.font.width(text) / 2, y, color, true);
    }

    @Override
    public void removed() {
        if (collectionBox != null) saveFields();
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }
}
