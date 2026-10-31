package de.example.riftkingextras;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Locale;

/** The scoreboard HUD in the RiftKingExtras style. Replaces the vanilla sidebar while it is shown. */
public final class ScoreboardHud {
    private static volatile ScoreboardModel.Snapshot snapshot = null; // live content, null = nothing to show
    private static ScoreboardModel.Snapshot demo = null;
    private static int ticks = 0;
    private static Method hudElementMethod = null;

    private ScoreboardHud() {}

    // ---------- content ----------

    /** Called every client tick: re-reads the vanilla scoreboard a few times per second. */
    static void tick(Minecraft mc) {
        if (++ticks % 4 != 0) return;
        if (!ScoreboardConfig.enabled || mc.level == null) {
            snapshot = null;
            return;
        }
        try {
            ScoreboardReader.Board board = ScoreboardReader.read(mc);
            if (board == null) {
                snapshot = null;
                return;
            }
            boolean onSkyBlock = board.title().getString().toUpperCase(Locale.ROOT).contains("SKYBLOCK");
            if (!onSkyBlock && !ScoreboardConfig.outsideSkyBlock) {
                snapshot = null;
                return;
            }
            snapshot = ScoreboardModel.build(board.title(), board.lines());
        } catch (RuntimeException e) {
            snapshot = null; // never break the game because of a scoreboard we do not understand
        }
    }

    /** What is used to measure and preview the HUD: the live content, or a sample if there is none. */
    private static ScoreboardModel.Snapshot layoutSnapshot() {
        ScoreboardModel.Snapshot live = snapshot;
        if (live != null) return live;
        ScoreboardModel.Snapshot sample = ScoreboardModel.demo(); // rebuilt so config changes show up at once
        demo = sample;
        return sample;
    }

    static int scaledWidth() { return (int) Math.ceil(layoutSnapshot().width() * ScoreboardConfig.scale); }
    static int scaledHeight() { return (int) Math.ceil(layoutSnapshot().height() * ScoreboardConfig.scale); }

    // ---------- vanilla sidebar ----------

    /** True while this HUD takes the place of the vanilla sidebar. */
    static boolean hidesVanilla() {
        return ScoreboardConfig.enabled && ScoreboardConfig.hideVanilla && snapshot != null;
    }

    /**
     * Calls the original vanilla HUD element. The HudElement method is looked up by reflection, so this works
     * whatever the method is called in the current Fabric API.
     */
    static void callOriginal(HudElement original, Object graphics, Object deltaTracker) {
        try {
            if (hudElementMethod == null) {
                for (Method m : HudElement.class.getMethods()) {
                    if (Modifier.isAbstract(m.getModifiers())) {
                        hudElementMethod = m;
                        break;
                    }
                }
            }
            if (hudElementMethod != null) hudElementMethod.invoke(original, graphics, deltaTracker);
        } catch (Throwable ignored) {
            // if the vanilla element cannot be drawn, it simply stays hidden
        }
    }

    // ---------- drawing ----------

    static void render(GuiGraphicsExtractor graphics) {
        Minecraft mc = Minecraft.getInstance();
        ScoreboardModel.Snapshot live = snapshot;
        if (live == null || mc.player == null || !ScoreboardConfig.enabled) return;
        if (mc.gui.screen() instanceof HudEditScreen e && e.kind() == HudKind.SCOREBOARD) return; // preview instead
        if (ScoreboardConfig.hideWhenTab && mc.options.keyPlayerList.isDown()) return;
        if (ScoreboardConfig.hideWhenChat && mc.gui.screen() instanceof ChatScreen) return;
        if (ScoreboardConfig.hideWhenF3 && mc.debugEntries.isOverlayVisible()) return;

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        draw(graphics, HudKind.SCOREBOARD.clampX(sw, ScoreboardConfig.x), HudKind.SCOREBOARD.clampY(sh, ScoreboardConfig.y));
    }

    /** Draws the HUD with its top-left corner at (x, y), scaled by the scoreboard scale. */
    static void draw(GuiGraphicsExtractor g, int x, int y) {
        ScoreboardModel.Snapshot s = layoutSnapshot();
        float scale = ScoreboardConfig.scale;
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        drawContent(g, s);
        pose.popMatrix();
    }

    /** Single place that draws a (colored) Component, in case the text method differs in a Minecraft version. */
    private static void text(GuiGraphicsExtractor g, Font font, Component text, int x, int y, boolean shadow) {
        g.text(font, text, x, y, 0xFFFFFFFF, shadow);
    }

    private static int alignedX(int mode, int boxWidth, int textWidth) {
        return switch (mode) {
            case 1 -> (boxWidth - textWidth) / 2;
            case 2 -> boxWidth - ScoreboardModel.SIDE_PAD - textWidth;
            default -> ScoreboardModel.SIDE_PAD;
        };
    }

    private static void drawContent(GuiGraphicsExtractor g, ScoreboardModel.Snapshot s) {
        Font font = Minecraft.getInstance().font;
        int w = s.width(), h = s.height();
        int accent = ScoreboardConfig.accentArgb();
        boolean shadow = ScoreboardConfig.textShadow;

        TrackerHud.rrect(g, 0, 0, w, h, 0xCC0E0E14);   // same panel as the other HUDs
        TrackerHud.border(g, 0, 0, w, h, 0x33FFFFFF);

        // header: the title, with an accent colored line below it
        text(g, font, s.title(), alignedX(ScoreboardConfig.titleAlign, w, font.width(s.title())), 4, shadow);
        TrackerHud.rect(g, 6, TrackerHud.HEADER_H - 2, w - 6, TrackerHud.HEADER_H - 1, TrackerHud.withAlpha(accent, 0x99));

        // lines
        int y = TrackerHud.HEADER_H + ScoreboardModel.TOP;
        for (ScoreboardModel.Line line : s.lines()) {
            if (line.separator()) {
                TrackerHud.rect(g, 8, y + 2, w - 8, y + 3, 0x22FFFFFF);
                y += ScoreboardModel.SEP_H;
            } else {
                int tw = font.width(line.text());
                text(g, font, line.text(), alignedX(ScoreboardConfig.textAlign, w, tw), y, shadow);
                y += ScoreboardModel.LINE_H + ScoreboardConfig.lineSpacing;
            }
        }
    }
}
