package de.example.riftkingextras;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mana and rift time come from the action bar, speed and rift damage from the tab list. In the Rift the health is
 * shown as vanilla hearts (it is not in the action bar), so it is taken from the player. Everything is read from text
 * or values, so a wrong format only shows "-" and never breaks anything.
 */
final class StatsTracker {
    private static final long FRESH_MS = 6_000;

    static volatile long health = -1, maxHealth = -1;   // from the action bar (main SkyBlock islands)
    static volatile long mana = -1, maxMana = -1;
    static volatile String riftText = "";
    static volatile int riftSeconds = -1;
    static volatile int riftMax = 0;
    static volatile long speed = -1, riftDamage = -1;
    static volatile String speedSource = "none yet";
    static volatile boolean speedLive = false;      // seen in the tab list this session (otherwise: last known value)
    static volatile boolean riftDamageLive = false;
    static volatile long lastActionBarMs = 0, lastHealthMs = 0, lastManaMs = 0, lastRiftMs = 0;
    static volatile String lastActionBar = "";
    private static int ticks = 0;

    private static final String NUM = "(\\d[\\d,]*)";
    private static final Pattern HP_AFTER = Pattern.compile(NUM + "\\s*/\\s*" + NUM + "\\s*\u2764");
    private static final Pattern HP_BEFORE = Pattern.compile("\u2764\\uFE0F?\\s*" + NUM + "\\s*/\\s*" + NUM);
    // "214/214" + one symbol + "Mana"
    private static final Pattern MANA = Pattern.compile(NUM + "\\s*/\\s*" + NUM + "\\D{0,3}Mana", Pattern.CASE_INSENSITIVE);
    private static final Pattern MANA_SYMBOL_AFTER = Pattern.compile(NUM + "\\s*/\\s*" + NUM + "\\s*\u270E");
    private static final Pattern MANA_SYMBOL_BEFORE = Pattern.compile("\u270E\\s*" + NUM + "\\s*/\\s*" + NUM);
    // "73m16s" + symbol + "Left" (Rift action bar)
    private static final Pattern RIFT_LEFT =
            Pattern.compile("(?:(\\d+)h\\s?)?(?:(\\d+)m\\s?)?(\\d+)s\\D{0,3}Left", Pattern.CASE_INSENSITIVE);
    private static final Pattern RIFT_TIME = Pattern.compile("(?:(\\d+)h\\s?)?(?:(\\d+)m\\s?)?(\\d+)s");
    // a tab line that STARTS with "Speed" ("Speed: ✦400"); "Mining Speed" and similar do not match
    private static final Pattern SPEED = Pattern.compile("^\\W*speed\\b\\D{0,8}" + NUM, Pattern.CASE_INSENSITIVE);
    private static final Pattern RIFT_DAMAGE =
            Pattern.compile("rift (?:damage|dmg):?\\s*\\D{0,3}" + NUM, Pattern.CASE_INSENSITIVE);

    private StatsTracker() {}

    private static long num(String s) {
        return Long.parseLong(s.replace(",", ""));
    }

    // ---------- action bar ----------

    /** The action bar text was seen again unchanged: it is still current. */
    static void touchActionBar() {
        lastActionBarMs = System.currentTimeMillis();
    }

    /** Called with the cleaned text of every new action bar. */
    static void onActionBar(String plain) {
        long now = System.currentTimeMillis();
        lastActionBarMs = now;
        lastActionBar = plain;

        Matcher m = HP_AFTER.matcher(plain);
        if (m.find() || (m = HP_BEFORE.matcher(plain)).find()) {
            health = num(m.group(1));
            maxHealth = num(m.group(2));
            lastHealthMs = now;
        }

        m = MANA.matcher(plain);
        if (m.find() || (m = MANA_SYMBOL_AFTER.matcher(plain)).find() || (m = MANA_SYMBOL_BEFORE.matcher(plain)).find()) {
            mana = num(m.group(1));
            maxMana = num(m.group(2));
            lastManaMs = now;
        }

        m = RIFT_LEFT.matcher(plain);
        boolean found = m.find();
        if (!found && (plain.toLowerCase(Locale.ROOT).contains("rift") || plain.contains("\u0444"))) {
            m = RIFT_TIME.matcher(plain);
            found = m.find();
        }
        if (found) {
            int h = m.group(1) == null ? 0 : Integer.parseInt(m.group(1));
            int min = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
            int sec = Integer.parseInt(m.group(3));
            int total = h * 3600 + min * 60 + sec;
            // a new visit to the Rift starts a new maximum
            riftMax = now - lastRiftMs > 30_000 ? total : Math.max(riftMax, total);
            lastRiftMs = now;
            riftSeconds = total;
            riftText = (total / 60) + "m" + (total % 60) + "s"; // minutes can go over 60, like on the action bar
        }
    }

    // ---------- tab list ----------

    /** Called every client tick: reads speed and rift damage from the tab list about once per second. */
    static void tick(Minecraft mc) {
        if (++menuTicks % 10 == 0) readStatsMenu(mc);
        if (++ticks % 20 != 0) return;
        boolean tabSpeed = false;
        for (String line : tabLines(mc)) {
            Matcher m = SPEED.matcher(line);
            if (m.find()) {
                speed = num(m.group(1));
                speedLive = true;
                speedSource = "tab list";
                RkeConfig.lastSpeed = speed;
                tabSpeed = true;
            }
            m = RIFT_DAMAGE.matcher(line);
            if (m.find()) {
                riftDamage = num(m.group(1));
                riftDamageLive = true;
                RkeConfig.lastRiftDamage = riftDamage;
            }
        }
        if (!tabSpeed) { // no speed line in the tab list (e.g. in the Rift): use the movement speed attribute
            double base = attributeBase(mc.player);
            if (base > 0) {
                speed = Math.round(base * RkeConfig.speedScale);
                speedLive = true;
                speedSource = RkeConfig.speedCalibrated ? "attribute, calibrated by the stats menu"
                        : "attribute x1000 (open /stats once to calibrate)";
                RkeConfig.lastSpeed = speed;
            }
        }
    }

    private static Object speedHolder = null;
    private static boolean speedHolderResolved = false;

    /**
     * The base value of the player's movement speed attribute (SkyBlock sets it to about speed / 1000).
     * Used when the tab list has no speed line, e.g. in the Rift. Everything is looked up by reflection, so a
     * renamed class member cannot break the build. The base value is used, so sprinting does not change it.
     * Returns -1 if it cannot be read.
     */
    static double attributeBase(Player p) {
        if (p == null) return -1;
        try {
            if (!speedHolderResolved) {
                speedHolderResolved = true;
                Class<?> attributes = Class.forName("net.minecraft.world.entity.ai.attributes.Attributes");
                for (String name : new String[]{"MOVEMENT_SPEED", "GENERIC_MOVEMENT_SPEED"}) {
                    try {
                        speedHolder = attributes.getField(name).get(null);
                        break;
                    } catch (ReflectiveOperationException ignored) {
                        // try the next name
                    }
                }
            }
            if (speedHolder == null) return -1;
            Method getAttribute = null;
            for (Method m : p.getClass().getMethods()) {
                if (m.getName().equals("getAttribute") && m.getParameterCount() == 1) {
                    getAttribute = m;
                    break;
                }
            }
            if (getAttribute == null) return -1;
            Object instance = getAttribute.invoke(p, speedHolder);
            if (instance == null) return -1;
            return (double) instance.getClass().getMethod("getBaseValue").invoke(instance);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return -1;
        }
    }

    // ---------- the /stats menu ----------

    private static int menuTicks = 0;
    private static String statsMenuInfo = "not opened yet";

    /**
     * While a stats menu (title contains "stat") is open, the speed (and rift damage) is read from the item names,
     * e.g. "Speed 153". That value is saved, and it also calibrates the conversion of the movement speed attribute,
     * so the speed then follows equipment changes live without opening the menu again.
     */
    private static void readStatsMenu(Minecraft mc) {
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> screen)) return;
        String title = screen.getTitle().getString();
        if (!title.toLowerCase(Locale.ROOT).contains("stat")) return;

        List<String> speedItems = new ArrayList<>();
        for (Slot slot : screen.getMenu().slots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            String name = ScoreboardModel.plain(stack.getHoverName());
            if (name.toLowerCase(Locale.ROOT).contains("speed") && speedItems.size() < 6) speedItems.add(name);

            Matcher m = SPEED.matcher(name);
            if (m.find()) applyMenuSpeed(mc, num(m.group(1)));
            m = RIFT_DAMAGE.matcher(name);
            if (m.find()) {
                riftDamage = num(m.group(1));
                riftDamageLive = true;
                RkeConfig.lastRiftDamage = riftDamage;
            }
        }
        statsMenuInfo = "\"" + title + "\", items with 'speed': " + (speedItems.isEmpty() ? "none" : speedItems);
    }

    private static void applyMenuSpeed(Minecraft mc, long value) {
        speed = value;
        speedLive = true;
        speedSource = "stats menu";
        RkeConfig.lastSpeed = value;
        double base = attributeBase(mc.player);
        if (base > 0) {
            RkeConfig.speedScale = value / base; // calibrates the attribute conversion
            RkeConfig.speedCalibrated = true;
        }
        RkeConfig.save();
    }

    /** Loads the values seen last time, so they show right away until the tab list gives new ones. */
    static void init() {
        speed = RkeConfig.lastSpeed;
        riftDamage = RkeConfig.lastRiftDamage;
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

    // ---------- what the bars show ----------

    /** A bar: how full it is (0..1) and the text in it. */
    record BarInfo(double frac, String text) {}

    private static boolean fresh(long time) {
        return System.currentTimeMillis() - time < FRESH_MS;
    }

    private static String fmt(long v) {
        return String.format(Locale.US, "%,d", v);
    }

    private static String hearts(float v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.format(Locale.US, "%.1f", v);
    }

    /** Health: the real numbers from the action bar if it has them, otherwise the vanilla hearts (Rift). */
    static BarInfo healthBar(Minecraft mc) {
        if (health >= 0 && maxHealth > 0 && fresh(lastHealthMs)) {
            return new BarInfo(Math.min(1.0, health / (double) maxHealth), fmt(health) + "/" + fmt(maxHealth));
        }
        Player p = mc.player;
        if (p == null) return new BarInfo(0, "-");
        float max = p.getMaxHealth() / 2f; // one heart = 2 health points
        float cur = Math.min(p.getHealth() / 2f, max);
        return new BarInfo(max <= 0 ? 0 : cur / max, hearts(cur) + "/" + hearts(max));
    }

    static BarInfo manaBar() {
        if (mana < 0 || maxMana <= 0 || !fresh(lastManaMs)) return new BarInfo(0, "-");
        return new BarInfo(Math.min(1.0, mana / (double) maxMana), fmt(mana) + "/" + fmt(maxMana));
    }

    static BarInfo riftBar(Minecraft mc) {
        if (riftSeconds < 0 || !fresh(lastRiftMs)) return new BarInfo(0, "-");
        float progress = xpProgress(mc.player);
        double frac = progress > 0 ? progress : (riftMax <= 0 ? 0 : Math.min(1.0, riftSeconds / (double) riftMax));
        return new BarInfo(frac, riftText);
    }

    /** The vanilla xp bar fill (the Rift shows its time there). -1 if it cannot be read. */
    private static float xpProgress(Player p) {
        if (p == null) return -1;
        try {
            return p.getClass().getField("experienceProgress").getFloat(p);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return -1;
        }
    }

    // ---------- debugging ----------

    /** Lines for /rke stats: what the mod parsed and what it saw. */
    static List<String> dump(Minecraft mc) {
        List<String> out = new ArrayList<>();
        BarInfo hp = healthBar(mc);
        out.add("Health: " + hp.text() + (health >= 0 && fresh(lastHealthMs) ? " (action bar)" : " (hearts)")
                + " | Mana: " + manaBar().text() + " | Rift: " + riftBar(mc).text() + " (max " + riftMax + "s)");
        out.add("Speed " + speed + (speedLive ? " (" + speedSource + ")" : " (last known)")
                + ", Rift damage " + riftDamage + (riftDamageLive ? " (tab list)" : " (last known)"));
        out.add("Speed scale " + String.format(Locale.US, "%.1f", RkeConfig.speedScale)
                + (RkeConfig.speedCalibrated ? " (calibrated)" : " (default)") + ", attribute base " + attributeBase(mc.player));
        out.add("Stats menu: " + statsMenuInfo);
        out.add("Action bar reader: " + ActionBarReader.status());
        out.add("Last action bar: " + (lastActionBar.isEmpty() ? "none seen yet" : lastActionBar));
        for (String line : tabLines(mc)) {
            String l = line.toLowerCase(Locale.ROOT);
            if (l.contains("speed") || l.contains("damage") || l.contains("rift") || line.contains("\u2726")) {
                out.add("Tab: " + line);
            }
        }
        out.add("Items: " + ItemDraw.status());
        out.add("Equipment: " + InventoryHud.equipmentInfo());
        out.add("Hooks: " + InventoryHud.hookInfo());
        return out;
    }
}
