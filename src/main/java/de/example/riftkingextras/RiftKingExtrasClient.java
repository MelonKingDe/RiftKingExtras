package de.example.riftkingextras;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.function.Function;

public class RiftKingExtrasClient implements ClientModInitializer {
    public static final String MOD_ID = "riftkingextras";

    @Override
    public void onInitializeClient() {
        RkeConfig.load();
        ScoreboardConfig.load();

        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(MOD_ID, "tracker"),
                (graphics, tracker) -> TrackerHud.render(graphics));

        // Second, separate HUD: Minion Time Warp cooldown
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(MOD_ID, "timewarp"),
                (graphics, tracker) -> TimewarpHud.render(graphics));

        // Scoreboard HUD, and hiding the vanilla sidebar while it is shown
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(MOD_ID, "scoreboard"),
                (graphics, tracker) -> ScoreboardHud.render(graphics));
        if (ScoreboardConfig.hideVanilla) { // set "hideVanilla": false in the scoreboard config to skip this
            try {
                HudElementRegistry.replaceElement(Identifier.withDefaultNamespace("scoreboard_sidebar"),
                        original -> (graphics, tracker) -> {
                            if (!ScoreboardHud.hidesVanilla()) ScoreboardHud.callOriginal(original, graphics, tracker);
                        });
            } catch (RuntimeException e) {
                // if the sidebar element is not found, the vanilla scoreboard simply stays visible
            }
        }

        // The Time Warp message arrives as a game (system) message: start the 48 h countdown
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) TimewarpTracker.onChat(message.getString());
        });

        // Key to open the settings (default: H, changeable under Options > Controls)
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        KeyMapping openSettings = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.riftkingextras.open_settings", InputConstants.Type.KEYSYM, InputConstants.KEY_H, category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openSettings.consumeClick()) {
                client.gui.setScreen(new SettingsScreen(client.gui.screen()));
            }
            CollectionTracker.tick(client);
            ScoreboardHud.tick(client);
            MushroomBreaker.tick(client);
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> CollectionTracker.persist());

        CollectionApi.startScheduler();
        registerCommands();
    }

    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommands.literal("rke")
                        .executes(ctx -> openScreen(SettingsScreen::new))
                        .then(ClientCommands.literal("hud")
                                .executes(ctx -> openScreen(p -> new HudEditScreen(p, HudKind.TRACKER))))
                        .then(ClientCommands.literal("collections")
                                .executes(ctx -> openScreen(CollectionsScreen::new)))
                        .then(ClientCommands.literal("players")
                                .executes(ctx -> openScreen(TrackedPlayersScreen::new)))
                        .then(ClientCommands.literal("scoreboard")
                                .executes(ctx -> openScreen(ScoreboardScreen::new))
                                .then(ClientCommands.literal("hud")
                                        .executes(ctx -> openScreen(p -> new HudEditScreen(p, HudKind.SCOREBOARD)))))
                        .then(ClientCommands.literal("timewarp")
                                .then(ClientCommands.literal("hud")
                                        .executes(ctx -> openScreen(p -> new HudEditScreen(p, HudKind.TIMEWARP))))
                                .then(ClientCommands.literal("start").executes(ctx -> {
                                    TimewarpTracker.start();
                                    ctx.getSource().sendFeedback(Component.literal("Timewarp cooldown started (48 h)."));
                                    return 1;
                                }))
                                .then(ClientCommands.literal("reset").executes(ctx -> {
                                    TimewarpTracker.reset();
                                    ctx.getSource().sendFeedback(Component.literal("Timewarp cooldown cleared."));
                                    return 1;
                                })))
                        .then(ClientCommands.literal("sync").executes(ctx -> {
                            CollectionApi.syncNow();
                            ctx.getSource().sendFeedback(Component.literal("Sync with Elite started."));
                            return 1;
                        }))
                        .then(ClientCommands.literal("test").executes(ctx -> {
                            Minecraft mc = Minecraft.getInstance();
                            var source = ctx.getSource();
                            CollectionApi.test(line -> mc.execute(() -> source.sendFeedback(Component.literal(line))));
                            return 1;
                        }))));
    }

    /** Opens a screen delayed, otherwise closing the chat overwrites it. */
    private static int openScreen(Function<Screen, Screen> factory) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.gui.setScreen(factory.apply(null)));
        return 1;
    }
}
