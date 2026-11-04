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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

public class RiftKingExtrasClient implements ClientModInitializer {
    public static final String MOD_ID = "riftkingextras";

    @Override
    public void onInitializeClient() {
        RkeConfig.load();
        ScoreboardConfig.load();
        StatsTracker.init();
        MusicConfig.load();

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
            hookVanillaSidebar();
        } else {
            ScoreboardHud.setHookInfo("off (hideVanilla is false in the config)");
        }

        // Fourth HUD: bars, equipment, armor, inventory and hotbar
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(MOD_ID, "inventory"),
                (graphics, tracker) -> InventoryHud.render(graphics));
        InventoryHud.setHookInfo("hotbar: " + hookVanilla(n -> n.equals("HOTBAR"), InventoryHud::hidesHotbar)
                + " | hearts, xp, action bar: " + hookVanilla(
                n -> n.equals("HEALTH_BAR") || n.equals("ARMOR_BAR") || n.equals("FOOD_BAR") || n.equals("MOUNT_HEALTH")
                        || n.equals("INFO_BAR") || n.equals("EXPERIENCE_LEVEL") || n.equals("OVERLAY_MESSAGE"),
                InventoryHud::hidesStatusBars));

        // Fifth HUD: music (now playing + synced lyrics)
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(MOD_ID, "music"),
                (graphics, tracker) -> MusicHud.render(graphics));

        // Game messages: action bar (overlay) feeds the stats bars, normal chat feeds the Time Warp countdown
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay) StatsTracker.onActionBar(ScoreboardModel.plain(message));
            else TimewarpTracker.onChat(message.getString());
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
            SkyBlockState.tick(client);
            StatsTracker.tick(client);
            ActionBarReader.tick(client);
            MusicKeybinds.tick();
            InventoryHud.tick(client);
            MushroomBreaker.tick(client);
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            CollectionTracker.persist();
            MusicManager.stop(); // closes the Windows helper
        });

        MusicKeybinds.register(category);
        MusicManager.applyEnabled();
        CollectionApi.startScheduler();
        registerCommands();
    }

    /**
     * Replaces every vanilla HUD element whose constant name matches, so it is skipped while hide.getAsBoolean() is
     * true. Constants are found by name, so this works whatever the ids are in this Fabric API version.
     */
    private static String hookVanilla(java.util.function.Predicate<String> match, java.util.function.BooleanSupplier hide) {
        StringBuilder info = new StringBuilder();
        for (Field f : VanillaHudElements.class.getFields()) {
            String name = f.getName().toUpperCase(Locale.ROOT);
            if (f.getType() != Identifier.class || !match.test(name)) continue;
            try {
                Identifier id = (Identifier) f.get(null);
                HudElementRegistry.replaceElement(id, original -> (graphics, tracker) -> {
                    if (!hide.getAsBoolean()) ScoreboardHud.callOriginal(original, graphics, tracker);
                });
                info.append(name).append(" ok; ");
            } catch (Exception e) {
                info.append(name).append(" FAILED; ");
            }
        }
        return info.length() == 0 ? "no matching element" : info.toString().trim();
    }

    /** Finds the id of the vanilla sidebar HUD element and replaces it with one that hides itself when needed. */
    private void hookVanillaSidebar() {
        // The constant is looked up by name, so it works whatever it is called in this Fabric API version
        List<Identifier> ids = new ArrayList<>();
        for (Field f : VanillaHudElements.class.getFields()) {
            if (f.getType() == Identifier.class && f.getName().toUpperCase(Locale.ROOT).contains("SCOREBOARD")) {
                try {
                    Identifier id = (Identifier) f.get(null);
                    if (f.getName().toUpperCase(Locale.ROOT).contains("SIDEBAR")) ids.add(0, id);
                    else ids.add(id);
                } catch (IllegalAccessException ignored) {
                    // skip this field
                }
            }
        }
        ids.add(Identifier.withDefaultNamespace("scoreboard"));
        ids.add(Identifier.withDefaultNamespace("scoreboard_sidebar"));

        for (Identifier id : ids) {
            try {
                HudElementRegistry.replaceElement(id, original -> (graphics, tracker) -> {
                    if (!ScoreboardHud.hidesVanilla()) ScoreboardHud.callOriginal(original, graphics, tracker);
                });
                ScoreboardHud.setHookInfo("hooked " + id);
                return;
            } catch (RuntimeException e) {
                // wrong id: try the next one
            }
        }
        ScoreboardHud.setHookInfo("FAILED: no vanilla sidebar element found, the vanilla scoreboard stays visible");
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
                                .then(ClientCommands.literal("status").executes(ctx -> {
                                    ctx.getSource().sendFeedback(Component.literal("Vanilla sidebar: " + ScoreboardHud.hookInfo()
                                            + " | hidden now: " + ScoreboardHud.hidesVanilla()));
                                    return 1;
                                }))
                                .then(ClientCommands.literal("dump").executes(ctx -> {
                                    var source = ctx.getSource();
                                    for (String line : ScoreboardHud.dump(Minecraft.getInstance())) {
                                        source.sendFeedback(Component.literal(line));
                                    }
                                    return 1;
                                }))
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
                                .then(ClientCommands.literal("last").executes(ctx -> {
                                    String line = TimewarpTracker.lastWarpLine();
                                    ctx.getSource().sendFeedback(Component.literal(line.isEmpty()
                                            ? "No chat line with \"warped time\" seen yet."
                                            : "Last seen: " + line));
                                    return 1;
                                }))
                                .then(ClientCommands.literal("reset").executes(ctx -> {
                                    TimewarpTracker.reset();
                                    ctx.getSource().sendFeedback(Component.literal("Timewarp cooldown cleared."));
                                    return 1;
                                })))
                        .then(ClientCommands.literal("session")
                                .then(ClientCommands.literal("reset").executes(ctx -> {
                                    CollectionTracker.resetSession();
                                    ctx.getSource().sendFeedback(Component.literal("Session gain and timers cleared."));
                                    return 1;
                                })))
                        .then(ClientCommands.literal("music")
                                .executes(ctx -> openScreen(MusicScreen::new))
                                .then(ClientCommands.literal("hud")
                                        .executes(ctx -> openScreen(p -> new HudEditScreen(p, HudKind.MUSIC))))
                                .then(ClientCommands.literal("status").executes(ctx -> {
                                    var source = ctx.getSource();
                                    source.sendFeedback(Component.literal("Source: " + MusicManager.status()));
                                    MusicState.Snapshot s = MusicState.get();
                                    source.sendFeedback(Component.literal(s.active()
                                            ? "Playing: " + s.title() + " - " + s.artist() + (s.playing() ? "" : " (paused)")
                                            : "Nothing playing"));
                                    source.sendFeedback(Component.literal("Lyrics: " + LyricsService.status()
                                            + " (" + LyricsService.lines().size() + " lines), cover: "
                                            + (MusicArt.ready() ? "loaded" : "none")));
                                    return 1;
                                })))
                        .then(ClientCommands.literal("inventory")
                                .executes(ctx -> openScreen(InventoryHudScreen::new))
                                .then(ClientCommands.literal("hud")
                                        .executes(ctx -> openScreen(p -> new HudEditScreen(p, HudKind.INVENTORY)))))
                        .then(ClientCommands.literal("stats").executes(ctx -> {
                            var source = ctx.getSource();
                            for (String line : StatsTracker.dump(Minecraft.getInstance())) {
                                source.sendFeedback(Component.literal(line));
                            }
                            return 1;
                        }))
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
