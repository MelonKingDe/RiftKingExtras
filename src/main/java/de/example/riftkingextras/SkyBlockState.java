package de.example.riftkingextras;

import net.minecraft.client.Minecraft;

import java.util.Locale;

/** Knows whether the player is on SkyBlock (the sidebar title says SKYBLOCK). */
final class SkyBlockState {
    static volatile boolean onSkyBlock = false;
    private static int ticks = 0;

    private SkyBlockState() {}

    static void tick(Minecraft mc) {
        if (++ticks % 10 != 0) return;
        try {
            ScoreboardReader.Board board = mc.level == null ? null : ScoreboardReader.read(mc);
            onSkyBlock = board != null && board.title().getString().toUpperCase(Locale.ROOT).contains("SKYBLOCK");
        } catch (RuntimeException e) {
            onSkyBlock = false;
        }
    }
}
