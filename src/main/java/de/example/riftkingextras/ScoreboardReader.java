package de.example.riftkingextras;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the vanilla sidebar scoreboard (title and lines, top to bottom).
 * This is the only place that touches the scoreboard classes of Minecraft.
 */
final class ScoreboardReader {
    record Board(Component title, List<Component> lines) {}

    private ScoreboardReader() {}

    /** Returns the sidebar content or null if the server shows no sidebar. */
    static Board read(Minecraft mc) {
        if (mc.level == null) return null;
        Scoreboard scoreboard = mc.level.getScoreboard();
        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective == null) return null;

        List<PlayerScoreEntry> entries = new ArrayList<>(scoreboard.listPlayerScores(objective));
        entries.removeIf(PlayerScoreEntry::isHidden);
        // highest score first = top line (same order as the vanilla sidebar)
        entries.sort((a, b) -> a.value() != b.value()
                ? Integer.compare(b.value(), a.value())
                : a.owner().compareToIgnoreCase(b.owner()));

        List<Component> lines = new ArrayList<>();
        for (PlayerScoreEntry entry : entries) {
            if (lines.size() >= 15) break;
            PlayerTeam team = scoreboard.getPlayersTeam(entry.owner());
            lines.add(PlayerTeam.formatNameForTeam(team, entry.ownerName()));
        }
        return new Board(objective.getDisplayName(), lines);
    }
}
