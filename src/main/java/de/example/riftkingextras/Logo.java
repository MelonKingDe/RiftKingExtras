package de.example.riftkingextras;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** The RiftKingExtras logo shown at the top of the settings screen. */
final class Logo {
    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(RiftKingExtrasClient.MOD_ID, "textures/gui/logo.png");
    private static final int TEX_W = 354;
    private static final int TEX_H = 129;

    private Logo() {}

    /** Draws the logo centered on cx; returns the y coordinate below it. */
    static int draw(GuiGraphicsExtractor graphics, int cx, int top, int height) {
        int width = Math.round(height * (float) TEX_W / TEX_H);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, cx - width / 2, top, 0f, 0f, width, height,
                TEX_W, TEX_H, TEX_W, TEX_H);
        return top + height;
    }
}
