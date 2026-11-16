package de.example.riftkingextras;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.Color;
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
    private static volatile int accent = 0xFF1DB954; // the main color of the current cover
    private static volatile boolean pending = false;
    private static volatile int received = 0;
    private static volatile String lastError = "none";
    private static volatile int generation = 0;

    private MusicArt() {}

    static boolean ready() {
        return ready;
    }

    /** The main color of the cover (ARGB). */
    static int accent() {
        return accent;
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
                Prepared prepared = prepare(bytes);
                NativeImage image = NativeImage.read(prepared.png());
                Minecraft mc = Minecraft.getInstance();
                mc.execute(() -> {
                    if (gen != generation) { // the song changed meanwhile
                        image.close();
                        pending = false;
                        return;
                    }
                    mc.getTextureManager().register(ID, new DynamicTexture(() -> "riftkingextras music cover", image));
                    accent = prepared.accent();
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

    private record Prepared(byte[] png, int accent) {}

    /** Cuts the cover to a square, scales it to 96x96 as PNG and finds its main color. */
    private static Prepared prepare(byte[] data) throws Exception {
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
        return new Prepared(png.toByteArray(), mainColor(out));
    }

    /**
     * The main color of the image: the pixels are sorted into 18 hue groups, vivid and bright pixels count more than gray
     * or dark ones, and the average of the strongest group is made vivid and bright enough to be used as an accent.
     */
    private static int mainColor(BufferedImage img) {
        double[] weight = new double[18];
        double[][] sum = new double[18][3];
        for (int y = 0; y < img.getHeight(); y += 2) {
            for (int x = 0; x < img.getWidth(); x += 2) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 255, gr = (rgb >> 8) & 255, b = rgb & 255;
                float[] hsb = Color.RGBtoHSB(r, gr, b, null);
                if (hsb[2] < 0.15f) continue; // almost black
                double w = hsb[1] * hsb[1] * hsb[2] + 0.01; // vivid colors win, gray still counts a little
                int bucket = Math.min(17, (int) (hsb[0] * 18));
                weight[bucket] += w;
                sum[bucket][0] += r * w;
                sum[bucket][1] += gr * w;
                sum[bucket][2] += b * w;
            }
        }
        int best = 0;
        for (int i = 1; i < 18; i++) if (weight[i] > weight[best]) best = i;
        if (weight[best] <= 0) return 0xFFB0B0C8; // a black cover: a neutral light color

        int r = (int) (sum[best][0] / weight[best]);
        int gr = (int) (sum[best][1] / weight[best]);
        int b = (int) (sum[best][2] / weight[best]);
        float[] hsb = Color.RGBtoHSB(r, gr, b, null);
        float saturation = hsb[1] < 0.12f ? hsb[1] : Math.max(hsb[1], 0.45f); // keep real grays gray
        float brightness = Math.max(0.60f, Math.min(0.95f, hsb[2]));
        return 0xFF000000 | (Color.HSBtoRGB(hsb[0], saturation, brightness) & 0xFFFFFF);
    }
}
