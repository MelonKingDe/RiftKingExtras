package de.example.riftkingextras;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cooldown timers for item abilities (e.g. Weird Tuba). The abilities and their cooldowns are read from the item lore
 * ("Ability: Howl  RIGHT CLICK" ... "Cooldown: 20s"). When you use an ability, SkyBlock shows its name in the action
 * bar ("-120 Mana (Howl)"): that starts the timer for every hotbar item that has this ability.
 * The timer uses the cooldown from the lore, so cooldown reductions (e.g. from your class) are not included.
 */
final class AbilityCooldowns {
    record AbilityInfo(String name, long cooldownMs) {}

    /** The longest running cooldown of an item: time left and the whole duration. */
    record State(long remainingMs, long totalMs) {}

    private static final Pattern ABILITY = Pattern.compile(
            "^(?:Item )?Ability:\\s*(.+?)(?:\\s+(?:RIGHT|LEFT) CLICK|\\s+SNEAK|\\s+PASSIVE)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern COOLDOWN = Pattern.compile(
            "^Cooldown:\\s*(?:(\\d+)h)?\\s*(?:(\\d+)m)?\\s*(?:(\\d+(?:\\.\\d+)?)s)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern USED = Pattern.compile("(\\d[\\d,]*)\\s*Mana\\s*\\(([^)]+)\\)", Pattern.CASE_INSENSITIVE);

    /** ability name (lower case) -> {end time, whole cooldown} */
    private static final Map<String, long[]> RUNNING = new ConcurrentHashMap<>();
    private static volatile List<List<AbilityInfo>> hotbar = List.of();

    private AbilityCooldowns() {}

    /** Reads the abilities of an item from its lore. */
    static List<AbilityInfo> parse(ItemStack stack) {
        List<AbilityInfo> out = new ArrayList<>();
        if (stack == null || stack.isEmpty()) return out;
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return out;

        String current = null;
        for (Component line : lore.lines()) {
            String plain = ScoreboardModel.plain(line);
            Matcher a = ABILITY.matcher(plain);
            if (a.matches()) {
                current = a.group(1).trim();
                continue;
            }
            if (current != null && plain.regionMatches(true, 0, "Cooldown:", 0, 9)) {
                Matcher c = COOLDOWN.matcher(plain);
                if (c.find()) {
                    double seconds = (c.group(1) == null ? 0 : Integer.parseInt(c.group(1)) * 3600.0)
                            + (c.group(2) == null ? 0 : Integer.parseInt(c.group(2)) * 60.0)
                            + (c.group(3) == null ? 0 : Double.parseDouble(c.group(3)));
                    if (seconds > 0) out.add(new AbilityInfo(current, Math.round(seconds * 1000)));
                }
                current = null;
            }
        }
        return out;
    }

    /** The abilities of the nine hotbar items (set by the inventory HUD). */
    static void setHotbar(List<List<AbilityInfo>> abilities) {
        hotbar = abilities;
    }

    /** Called with every new action bar text: an ability use ("-120 Mana (Howl)") starts the timer. */
    static void onActionBar(String plain) {
        if (!RkeConfig.abilityTimers) return;
        Matcher m = USED.matcher(plain);
        while (m.find()) {
            String used = m.group(2).trim().toLowerCase(Locale.ROOT);
            for (List<AbilityInfo> item : hotbar) {
                for (AbilityInfo ability : item) {
                    String name = ability.name().toLowerCase(Locale.ROOT);
                    if (name.equals(used) || name.contains(used) || used.contains(name)) {
                        RUNNING.put(name, new long[]{System.currentTimeMillis() + ability.cooldownMs(), ability.cooldownMs()});
                    }
                }
            }
        }
    }

    /** The running cooldown of an item (the longest one if it has several abilities), or null if none runs. */
    static State stateOf(List<AbilityInfo> abilities) {
        if (abilities == null || abilities.isEmpty() || RUNNING.isEmpty()) return null;
        long now = System.currentTimeMillis();
        State best = null;
        for (AbilityInfo ability : abilities) {
            long[] e = RUNNING.get(ability.name().toLowerCase(Locale.ROOT));
            if (e == null) continue;
            long remaining = e[0] - now;
            if (remaining > 0 && (best == null || remaining > best.remainingMs())) best = new State(remaining, e[1]);
        }
        return best;
    }
}
