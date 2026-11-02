package de.example.riftkingextras;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Counts items of all tracked collections that enter the inventory. */
public final class CollectionTracker {
    /** Overall timer: active while any tracked collection gains, paused after 60 s without any gain. */
    static final RateTimer OVERALL = new RateTimer();

    private static int ticks = 0;

    private CollectionTracker() {}

    static void tick(Minecraft mc) {
        List<TrackedCollection> list = new ArrayList<>(RkeConfig.collections);
        if (mc.player == null) {
            for (TrackedCollection c : list) {
                c.lastInvCount = -1;
                c.resetClock();
            }
            OVERALL.resetClock();
            return;
        }

        long now = System.currentTimeMillis();
        boolean anyGain = false;
        if (!list.isEmpty()) {
            int[] counts = countInInventory(mc.player, list);
            // While a menu is open only update the reference (moving items around does not count)
            boolean menuOpen = mc.gui.screen() != null;
            for (int i = 0; i < list.size(); i++) {
                TrackedCollection c = list.get(i);
                if (menuOpen || c.lastInvCount < 0) {
                    c.lastInvCount = counts[i];
                } else {
                    if (counts[i] > c.lastInvCount) {
                        c.onPickup(counts[i] - c.lastInvCount);
                        anyGain = true;
                    }
                    c.lastInvCount = counts[i];
                }
                c.tickTimers(now);
            }
        }
        if (anyGain) OVERALL.onGain();
        OVERALL.tick(now);

        if (++ticks % 600 == 0) persist(); // save about every 30 seconds
    }

    /** Clears the session gain and all timers (also the overall timer). */
    static void resetSession() {
        for (TrackedCollection c : RkeConfig.collections) c.resetSession();
        OVERALL.reset();
    }

    static void persist() {
        RkeConfig.save();
    }

    private static int[] countInInventory(Player player, List<TrackedCollection> list) {
        int[] counts = new int[list.size()];
        var inv = player.getInventory();
        for (int slot = 0; slot < inv.getContainerSize(); slot++) {
            ItemStack s = inv.getItem(slot);
            if (s.isEmpty()) continue;
            String name = s.getHoverName().getString().strip();
            for (int i = 0; i < list.size(); i++) {
                for (String item : list.get(i).itemNames) {
                    if (name.equalsIgnoreCase(item)) {
                        counts[i] += s.getCount(); // every listed item counts 1 for the collection
                        break;
                    }
                }
            }
        }
        return counts;
    }

    /** The tracked collection with this Elite leaderboard name, or null. */
    static TrackedCollection bySlug(String slug) {
        for (TrackedCollection c : RkeConfig.collections) {
            if (c.slug.equals(slug)) return c;
        }
        return null;
    }

    static long lastSyncMs() {
        long max = 0;
        for (TrackedCollection c : RkeConfig.collections) max = Math.max(max, c.calibratedMs);
        return max;
    }
}
