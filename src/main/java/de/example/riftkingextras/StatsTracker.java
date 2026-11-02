package de.example.riftkingextras;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Health, mana and rift time come from the action bar, speed and rift damage from the tab list.
 * Everything is read from text, so a wrong format only shows "-" and never breaks anything.
 */
final class StatsTracker {
    static volatile long health = -1, maxHealth = -1;
    static volatile long mana = -1, maxMana = -1;
    static volatile String riftText = "";
    static volatile int riftSeconds = -1;
    static volatile int riftMax = 0;
    static volatile long speed = -1, riftDamage = -1;
    static volatile long lastActionBarMs = 0;
    static volatile String lastActionBar = "";
    private static long lastRiftMs = 0;
    private static int ticks = 0;

    private static final String NUM = "(\\d[\\d,]*)";
    private static final Pattern HP_AFTER = Pattern.compile(NUM + "\\s*/\\s*" + NUM + "\\s*\u2764");
    private static final Pattern HP_BEFORE = Pattern.compile("\u2764\\uFE0F?\\s*" + NUM + "\\s*/\\s*" + NUM);
    private static final Pattern MANA_AFTER = Pattern.compile(NUM + "\\s*/\\s*" + NUM + "\\s*\u270E");
    private static final Pattern MANA_BEFORE = Pattern.compile("\u270E\\s*" + NUM + "\\s*/\\s*" + NUM);
    private static final Pattern MANA_WORD = Pattern.compile(NUM + "\\s*/\\s*" + NUM + "\\s*Mana", Pattern.CASE_INSENSITIVE);
    private static final Pattern RIFT_TIME = Pattern.compile("(?:(\\d+)h\\s?)?(?:(\\d+)m\\s?)?(\\d+)s");
    private static final Pattern SPEED = Pattern.compile("speed:?\\s*\\D{0,3}" + NUM, Pattern.CASE_INSENSITIVE);
    private static final Pattern RIFT_DAMAGE =
            Pattern.compile("rift (?:damage|dmg):?\\s*\\D{0,3}" + NUM, Pattern.CASE_INSENSITIVE);

    private StatsTracker() {}

    private static long num(String s) {
        return Long.parseLong(s.replace(",", ""));
    }

    // ---------- action bar ----------

    /** Called for every action bar message (already cleaned text). */
    static void onActionBar(String plain) {
        lastActionBarMs = System.currentTimeMillis();
        lastActionBar = plain;

        Matcher m = HP_AFTER.matcher(plain);
        if (m.find()) {
            health = num(m.group(1));
            maxHealth = num(m.group(2));
        } else if ((m = HP_BEFORE.matcher(plain)).find()) {
            health = num(m.group(1));
            maxHealth = num(m.group(2));
        }

        m = MANA_AFTER.matcher(plain);
        if (m.find() || (m = MANA_BEFORE.matcher(plain)).find() || (m = MANA_WORD.matcher(plain)).find()) {
            mana = num(m.group(1));
            maxMana = num(m.group(2));
        }

        String lower = plain.toLowerCase(Locale.ROOT);
        if (lower.contains("rift") || plain.contains("\u0444")) {
            m = RIFT_TIME.matcher(plain);
            if (m.find()) {
                int h = m.group(1) == null ? 0 : Integer.parseInt(m.group(1));
                int min = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
                int sec = Integer.parseInt(m.group(3));
                int total = h * 3600 + min * 60 + sec;
                long now = System.currentTimeMillis();
                // a new visit to the Rift starts a new maximum
                riftMax = now - lastRiftMs > 30_000 ? total : Math.max(riftMax, total);
                lastRiftMs = now;
                riftSeconds = total;
                riftText = m.group().replace(" ", "");
            }
        }
    }

    // ---------- tab list ----------

    /** Called every client tick: reads speed and rift damage from the tab list about once per second. */
    static void tick(Minecraft mc) {
        if (++ticks % 20 != 0) return;
        for (String line : tabLines(mc)) {
            Matcher m = SPEED.matcher(line);
            if (m.find()) speed = num(m.group(1));
            m = RIFT_DAMAGE.matcher(line);
            if (m.find()) riftDamage = num(m.group(1));
        }
    }

    private static List<String> tabLines(Minecraft mc) {
        List<String> out = new ArrayList<>();
        try {
            ClientPacketListener connection = mc.getConnection();
            if (connection == null) return out;
            for (PlayerInfo info : connection.getOnlinePlayers()) {
                Component name = info.getTabListDisplayName();
                if (name != null) out.add(ScoreboardModel.plain(name));
            }
        } catch (RuntimeException ignored) {
            // no tab list available
        }
        return out;
    }

    // ---------- fractions for the bars ----------

    static double healthFrac() { return frac(health, maxHealth); }
    static double manaFrac() { return frac(mana, maxMana); }
    static double riftFrac() { return riftSeconds < 0 || riftMax <= 0 ? 0 : Math.min(1.0, riftSeconds / (double) riftMax); }

    private static double frac(long value, long max) {
        return value < 0 || max <= 0 ? 0 : Math.max(0, Math.min(1.0, value / (double) max));
    }

    // ---------- debugging ----------

    /** Lines for /rke stats: what the mod parsed and what it saw. */
    static List<String> dump(Minecraft mc) {
        List<String> out = new ArrayList<>();
        out.add("Health " + health + "/" + maxHealth + ", Mana " + mana + "/" + maxMana);
        out.add("Rift time " + (riftText.isEmpty() ? "-" : riftText) + " (max " + riftMax + "s)");
        out.add("Speed " + speed + ", Rift damage " + riftDamage);
        out.add("Last action bar: " + (lastActionBar.isEmpty() ? "none seen yet" : lastActionBar));
        for (String line : tabLines(mc)) {
            String l = line.toLowerCase(Locale.ROOT);
            if (l.contains("speed") || l.contains("damage") || l.contains("rift")) out.add("Tab: " + line);
        }
        out.add("Items: " + ItemDraw.status());
        out.add("Equipment: " + InventoryHud.equipmentInfo());
        out.add("Hooks: " + InventoryHud.hookInfo());
        return out;
    }
}
