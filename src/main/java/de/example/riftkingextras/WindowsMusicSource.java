package de.example.riftkingextras;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;

/**
 * Windows: starts the small RiftKingMusicHelper.exe that is packed into the jar. It reads the Windows media session
 * (the one behind the media keys) and prints one JSON line per update. Works for the free version of Spotify.
 */
final class WindowsMusicSource implements MusicSource {
    private volatile Process process;
    private volatile BufferedWriter writer;
    private volatile boolean running = false;
    private volatile String status = "starting";
    private Thread thread;

    @Override
    public void start() {
        if (running) return;
        running = true;
        thread = new Thread(this::run, "riftkingextras-music-windows");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void stop() {
        running = false;
        Process p = process;
        if (p != null) p.destroy();
    }

    @Override
    public String status() {
        return status;
    }

    @Override
    public void send(String command) {
        BufferedWriter w = writer;
        if (w == null) return;
        try {
            synchronized (this) {
                w.write(command);
                w.newLine();
                w.flush();
            }
        } catch (IOException ignored) {
            // the helper is gone, it gets restarted by the loop
        }
    }

    private void run() {
        while (running) {
            try {
                Path exe = extractHelper();
                Process p = new ProcessBuilder(exe.toAbsolutePath().toString())
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .start();
                process = p;
                writer = new BufferedWriter(new OutputStreamWriter(p.getOutputStream(), StandardCharsets.UTF_8));
                status = "Windows media session (helper running)";

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while (running && (line = reader.readLine()) != null) handle(line);
                }
                status = "helper stopped, restarting";
            } catch (MissingHelperException e) {
                status = "helper missing: this jar was built without the .NET SDK (see README)";
                running = false;
                return;
            } catch (IOException e) {
                status = "helper failed: " + e.getMessage();
            }
            writer = null;
            MusicManager.push(MusicState.Snapshot.INACTIVE, null);
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static final class MissingHelperException extends IOException {
        MissingHelperException() {
            super("helper not in the jar");
        }
    }

    /** Copies the helper out of the jar into the config folder (an already running copy is simply reused). */
    private static Path extractHelper() throws IOException {
        Path dir = FabricLoader.getInstance().getConfigDir().resolve("riftkingextras");
        Files.createDirectories(dir);
        Path target = dir.resolve("RiftKingMusicHelper.exe");
        try (InputStream in = WindowsMusicSource.class
                .getResourceAsStream("/assets/riftkingextras/bin/RiftKingMusicHelper.exe")) {
            if (in == null) {
                if (Files.exists(target)) return target;
                throw new MissingHelperException();
            }
            try {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                if (!Files.exists(target)) throw e; // locked by a running helper: use that one
            }
        }
        return target;
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    private static long num(JsonObject o, String key) {
        JsonElement e = o.get(key);
        try {
            return e == null || e.isJsonNull() ? 0 : e.getAsLong();
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    private void handle(String line) {
        try {
            JsonObject o = JsonParser.parseString(line).getAsJsonObject();
            JsonElement active = o.get("active");
            if (active == null || !active.getAsBoolean()) {
                MusicManager.push(MusicState.Snapshot.INACTIVE, null);
                return;
            }
            float volume = -1f;
            JsonElement v = o.get("volume");
            if (v != null && !v.isJsonNull()) volume = v.getAsFloat();

            byte[] artwork = null;
            String art = str(o, "artwork");
            if (!art.isEmpty()) artwork = Base64.getDecoder().decode(art);

            MusicManager.push(new MusicState.Snapshot(true, str(o, "title"), str(o, "artist"), str(o, "album"),
                    "Playing".equals(str(o, "status")), num(o, "positionMs"), num(o, "durationMs"), volume,
                    System.currentTimeMillis()), artwork);
        } catch (RuntimeException ignored) {
            // a broken line is skipped
        }
    }
}
