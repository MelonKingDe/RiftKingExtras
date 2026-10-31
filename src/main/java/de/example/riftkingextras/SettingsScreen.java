package de.example.riftkingextras;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Main settings screen with the RiftKingExtras logo. Open with /rke or the key (default: H). */
public class SettingsScreen extends Screen {
    private final Screen parent;

    public SettingsScreen(Screen parent) {
        super(Component.literal("RiftKingExtras"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int w = 150;
        int left = this.width / 2 - w - 5;
        int right = this.width / 2 + 5;
        int y = 52;

        // Left column: tracker
        this.addRenderableWidget(Button.builder(
                Component.literal("Collections (" + RkeConfig.collections.size() + "/" + RkeConfig.MAX_COLLECTIONS + ")"),
                btn -> this.minecraft.gui.setScreen(new CollectionsScreen(this))).bounds(left, y, w, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("Tracked players (" + RkeConfig.players.size() + "/" + RkeConfig.MAX_PLAYERS + ")"),
                btn -> this.minecraft.gui.setScreen(new TrackedPlayersScreen(this))).bounds(left, y + 24, w, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Move tracker HUD"),
                btn -> this.minecraft.gui.setScreen(new HudEditScreen(this, HudKind.TRACKER)))
                .bounds(left, y + 48, w, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("Timers in HUD: " + (RkeConfig.showTimers ? "ON" : "OFF")), btn -> {
                    RkeConfig.showTimers = !RkeConfig.showTimers;
                    RkeConfig.save();
                    this.minecraft.gui.setScreen(new SettingsScreen(this.parent)); // refresh the label
                }).bounds(left, y + 72, w, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Scoreboard..."),
                btn -> this.minecraft.gui.setScreen(new ScoreboardScreen(this)))
                .bounds(left, y + 96, w, 20).build());

        // Right column: Timewarp HUD and sync
        this.addRenderableWidget(Button.builder(
                Component.literal("Timewarp HUD: " + (RkeConfig.showTimewarp ? "ON" : "OFF")), btn -> {
                    RkeConfig.showTimewarp = !RkeConfig.showTimewarp;
                    RkeConfig.save();
                    this.minecraft.gui.setScreen(new SettingsScreen(this.parent));
                }).bounds(right, y, w, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Move Timewarp HUD"),
                btn -> this.minecraft.gui.setScreen(new HudEditScreen(this, HudKind.TIMEWARP)))
                .bounds(right, y + 24, w, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Reset Timewarp timer"), btn -> {
            TimewarpTracker.reset();
        }).bounds(right, y + 48, w, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Sync with Elite now"),
                btn -> CollectionApi.syncNow()).bounds(right, y + 72, w, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Done"), btn -> onClose())
                .bounds(this.width / 2 - 75, this.height - 26, 150, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        Logo.draw(graphics, this.width / 2, 1, 42);
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
