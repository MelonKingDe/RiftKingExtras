package de.example.riftkingextras;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.Locale;

/** The content of one storage page (ender chest or backpack) as last seen, with a search text for every slot. */
final class StoragePage {
    final String key;      // "EC3" or "BP12"
    final int rows;
    final long time;       // when it was last seen
    final ItemStack[] stacks;
    final String[] search; // name and lore of every item in lower case

    StoragePage(String key, int rows, ItemStack[] stacks, long time) {
        this.key = key;
        this.rows = rows;
        this.time = time;
        this.stacks = stacks;
        this.search = new String[stacks.length];
        for (int i = 0; i < stacks.length; i++) search[i] = searchText(stacks[i]);
    }

    private static String searchText(ItemStack stack) {
        if (stack.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(ScoreboardModel.plain(stack.getHoverName()).toLowerCase(Locale.ROOT));
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore != null) {
            for (Component line : lore.lines()) sb.append(' ').append(ScoreboardModel.plain(line).toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }

    /** How many slots match the search text (lower case). */
    int matches(String query) {
        int n = 0;
        for (String s : search) if (!s.isEmpty() && s.contains(query)) n++;
        return n;
    }
}
