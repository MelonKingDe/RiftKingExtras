package de.example.riftkingextras;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Knows whether the player is on SkyBlock and whether they are in the Rift. */
final class SkyBlockState {
    static volatile boolean onSkyBlock = false;
    static volatile boolean inRift = false;
    private static int ticks = 0;

    private SkyBlockState() {}

    /**
     * SkyBlock = the sidebar title says SKYBLOCK. Rift = an area line of the sidebar starts with the Rift symbol
     * (the Cyrillic "ef" that SkyBlock uses), or the action bar currently shows the Rift time ("73m16s Left").
     */
    static void tick(Minecraft mc) {
        if (++ticks % 10 != 0) return;
        try {
            ScoreboardReader.Board board = mc.level == null ? null : ScoreboardReader.read(mc);
            onSkyBlock = board != null && board.title().getString().toUpperCase(Locale.ROOT).contains("SKYBLOCK");
            boolean rift = false;
            if (onSkyBlock) {
                for (Component line : board.lines()) {
                    String plain = ScoreboardModel.plain(line);
                    if (plain.startsWith("\u0444") || plain.contains("The Rift")) {
                        rift = true;
                        break;
                    }
                }
                if (!rift) rift = StatsTracker.riftFresh();
            }
            inRift = rift;
        } catch (RuntimeException e) {
            onSkyBlock = false;
            inRift = false;
        }
    }
}
