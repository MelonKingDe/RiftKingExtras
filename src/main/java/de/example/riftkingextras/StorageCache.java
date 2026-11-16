package de.example.riftkingextras;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.example.riftkingextras.mixin.AbstractContainerScreenAccessor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Remembers the content of every storage page you open (ender chest pages and backpacks), per SkyBlock profile. The
 * pages are saved as small files in config/riftkingextras/storage/, so they are still there after a restart.
 */
final class StorageCache {
    private static final Map<String, StoragePage> PAGES = new ConcurrentHashMap<>(); // "profile|EC3" -> page
    private static final Set<String> TRIED_LOAD = new HashSet<>();
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "riftkingextras-storage-io");
        t.setDaemon(true);
        return t;
    });

    // the page menu that is open right now
    private static AbstractContainerMenu lastMenu = null;
    private static int openTicks = 0;
    private static int readTicks = 0;
    private static String liveKey = null;  // full key of the page being read
    private static boolean liveDirty = false;

    private StorageCache() {}

    private static String fullKey(String pageKey) {
        return StatsTracker.profileName + "|" + pageKey;
    }

    // ---------- reading the open page ----------

    /** Called every client tick: while a storage page is open, its content is copied. When it closes it is saved. */
    static void tick(Minecraft mc) {
        if (!RkeConfig.storageOverlay || mc.level == null) return;

        if (mc.gui.screen() instanceof AbstractContainerScreen<?> screen) {
            StorageTitles.Page page = StorageTitles.parse(ScoreboardModel.plain(screen.getTitle()));
            AbstractContainerMenu menu = ((AbstractContainerScreenAccessor) screen).riftkingextras$getMenu();
            if (page != null && menu instanceof ChestMenu chest) {
                String key = fullKey(page.key());
                if (liveKey != null && !liveKey.equals(key)) finishLive(mc); // went straight to another page
                if (menu != lastMenu) {
                    lastMenu = menu;
                    openTicks = 0;
                    readTicks = 0;
                }
                openTicks++;
                if (++readTicks % 10 == 1) read(chest, page, key);
                return;
            }
        }
        lastMenu = null;
        openTicks = 0;
        if (liveKey != null) finishLive(mc);
    }

    private static void read(ChestMenu chest, StorageTitles.Page page, String key) {
        int rows = chest.getRowCount();
        int count = Math.min(rows * 9, chest.slots.size());
        ItemStack[] stacks = new ItemStack[rows * 9];
        boolean any = false;
        for (int i = 0; i < stacks.length; i++) {
            stacks[i] = i < count ? chest.slots.get(i).getItem().copy() : ItemStack.EMPTY;
            if (!stacks[i].isEmpty()) any = true;
        }
        if (!any && openTicks < 20) return; // the items may not have arrived yet
        PAGES.put(key, new StoragePage(page.key(), rows, stacks, System.currentTimeMillis()));
        liveKey = key;
        liveDirty = true;
    }

    /** The page was closed: write it to its file (the item data is turned into text here, the writing is in the background). */
    private static void finishLive(Minecraft mc) {
        String key = liveKey;
        liveKey = null;
        if (key == null || !liveDirty) return;
        liveDirty = false;
        StoragePage page = PAGES.get(key);
        if (page == null || mc.level == null) return;

        String profile = key.substring(0, key.indexOf('|'));
        JsonArray items = new JsonArray();
        for (ItemStack stack : page.stacks) items.add(EquipmentStore.encode(stack, mc.level.registryAccess()));
        JsonObject root = new JsonObject();
        root.addProperty("rows", page.rows);
        root.addProperty("time", page.time);
        root.add("items", items);
        String text = root.toString();
        Path file = dir(profile).resolve(page.key + ".json");
        IO.execute(() -> {
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, text);
            } catch (IOException ignored) {
                // the page just is not saved
            }
        });
    }

    // ---------- reading pages for the overview ----------

    private static Path dir(String profile) {
        String safe = profile.replaceAll("[^A-Za-z0-9_-]", "_");
        return FabricLoader.getInstance().getConfigDir().resolve("riftkingextras").resolve("storage").resolve(safe);
    }

    /** The page as it is in memory (a page you opened in this game session), or null. */
    static StoragePage peek(String pageKey) {
        return PAGES.get(fullKey(pageKey));
    }

    /** True if a file for this page was not read yet. */
    static boolean needsLoad(String pageKey) {
        String key = fullKey(pageKey);
        return !PAGES.containsKey(key) && !TRIED_LOAD.contains(key);
    }

    /** Reads the saved file of a page (on the game thread, one page is small). Returns null if there is none. */
    static StoragePage load(Minecraft mc, String pageKey) {
        String key = fullKey(pageKey);
        if (PAGES.containsKey(key)) return PAGES.get(key);
        if (!TRIED_LOAD.add(key) || mc.level == null) return null;
        Path file = dir(StatsTracker.profileName).resolve(pageKey + ".json");
        if (!Files.exists(file)) return null;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            int rows = root.get("rows").getAsInt();
            JsonArray items = root.getAsJsonArray("items");
            ItemStack[] stacks = new ItemStack[rows * 9];
            for (int i = 0; i < stacks.length; i++) {
                stacks[i] = i < items.size()
                        ? EquipmentStore.decode(items.get(i).getAsString(), mc.level.registryAccess()) : ItemStack.EMPTY;
            }
            StoragePage page = new StoragePage(pageKey, rows, stacks, root.get("time").getAsLong());
            PAGES.put(key, page);
            return page;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
