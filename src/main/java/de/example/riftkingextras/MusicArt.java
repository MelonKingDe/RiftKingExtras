package de.example.riftkingextras;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The album cover as a GUI texture. The image is cut to a square and scaled to 96x96, and always registered under the
 * same id, so the previous cover is replaced and nothing piles up in memory.
 */
final class MusicArt {
    static final Identifier ID = Identifier.fromNamespaceAndPath(RiftKingExtrasClient.MOD_ID, "music/cover");
    static final int SIZE = 96;

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "riftkingextras-music-art");
        t.setDaemon(true);
        return t;
    });

    private static volatile boolean ready = false;
    private static volatile boolean pending = false;
    private static volatile int received = 0;
    private static volatile String lastError = "none";
    private static volatile int generation = 0;

    private MusicArt() {}

    static boolean ready() {
        return ready;
    }

    static boolean pending() {
        return pending;
    }

    /** For /rke music status. */
    static String info() {
        return (ready ? "loaded" : pending ? "loading" : "none") + " (images received: " + received
                + ", last error: " + lastError + ")";
    }

    /** A new song started: stop drawing the old cover and drop covers that are still being prepared. */
    static void clear() {
        generation++;
        ready = false;
    }

    /** Decodes the image (any format Java can read, usually JPEG or PNG) and uploads it as the cover. */
    static void submit(byte[] bytes) {
        final int gen = generation;
        received++;
        pending = true;
        EXEC.execute(() -> {
            try {
                NativeImage image = NativeImage.read(squarePng(bytes));
                Minecraft mc = Minecraft.getInstance();
                mc.execute(() -> {
                    if (gen != generation) { // the song changed meanwhile
                        image.close();
                        pending = false;
                        return;
                    }
                    mc.getTextureManager().register(ID, new DynamicTexture(() -> "riftkingextras music cover", image));
                    ready = true;
                    pending = false;
                    lastError = "none";
                });
            } catch (Exception e) {
                ready = false;
                pending = false;
                lastError = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
            }
        });
    }

    private static byte[] squarePng(byte[] data) throws Exception {
        BufferedImage source = ImageIO.read(new ByteArrayInputStream(data));
        if (source == null) throw new IllegalArgumentException("unknown image format");
        int side = Math.min(source.getWidth(), source.getHeight());
        BufferedImage square = source.getSubimage((source.getWidth() - side) / 2, (source.getHeight() - side) / 2, side, side);

        BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(square, 0, 0, SIZE, SIZE, null);
        g.dispose();

        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(out, "png", png);
        return png.toByteArray();
    }
}
