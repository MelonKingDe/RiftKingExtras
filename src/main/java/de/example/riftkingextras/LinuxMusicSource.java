package de.example.riftkingextras;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Linux: asks Spotify through MPRIS with the "playerctl" tool (install it with your package manager).
 * Works for the free version of Spotify.
 */
final class LinuxMusicSource implements MusicSource {
    private static final String FORMAT =
            "{{status}}\t{{title}}\t{{artist}}\t{{album}}\t{{mpris:length}}\t{{position}}\t{{mpris:artUrl}}\t{{volume}}";

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    private volatile boolean running = false;
    private volatile String status = "starting";
    private String lastArtUrl = "";
    private String lastKey = "";
    private Thread thread;

    @Override
    public void start() {
        if (running) return;
        running = true;
        thread = new Thread(this::run, "riftkingextras-music-linux");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    public String status() {
        return status;
    }

    @Override
    public void send(String command) {
        List<String> args = switch (command) {
            case "playpause" -> List.of("play-pause");
            case "next" -> List.of("next");
            case "previous" -> List.of("previous");
            case "restart" -> List.of("position", "0");
            case "volume_up" -> List.of("volume", "0.05+");
            case "volume_down" -> List.of("volume", "0.05-");
            default -> null;
        };
        if (args == null) return;
        Thread t = new Thread(() -> {
            try {
                playerctl(args);
            } catch (IOException | InterruptedException ignored) {
                // nothing to do
            }
        }, "riftkingextras-music-command");
        t.setDaemon(true);
        t.start();
    }

    /** Runs playerctl for Spotify; returns its output or null if it failed (e.g. Spotify is not running). */
    private static String playerctl(List<String> args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("playerctl");
        command.add("--player=spotify");
        command.addAll(args);
        Process p = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return p.waitFor() == 0 ? out : null;
    }

    private void run() {
        while (running) {
            long wait = 500;
            try {
                String out = playerctl(List.of("metadata", "--format", FORMAT));
                if (out == null) {
                    status = "Spotify is not running (playerctl found)";
                    MusicManager.push(MusicState.Snapshot.INACTIVE, null);
                    wait = 2000;
                } else {
                    status = "MPRIS via playerctl";
                    handle(out.strip());
                }
            } catch (IOException e) {
                status = "playerctl not found: install it with your package manager";
                MusicManager.push(MusicState.Snapshot.INACTIVE, null);
                wait = 5000;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static long micros(String s) {
        try {
            return s.isBlank() ? 0 : Long.parseLong(s.trim()) / 1000; // microseconds -> milliseconds
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void handle(String line) {
        String[] f = line.split("\t", -1);
        if (f.length < 8 || f[1].isBlank()) {
            MusicManager.push(MusicState.Snapshot.INACTIVE, null);
            return;
        }
        float volume = -1f;
        try {
            volume = Float.parseFloat(f[7].trim());
        } catch (NumberFormatException ignored) {
            // no volume
        }
        MusicState.Snapshot s = new MusicState.Snapshot(true, f[1], f[2], f[3], "Playing".equals(f[0]),
                micros(f[5]), micros(f[4]), volume, System.currentTimeMillis());

        byte[] artwork = null;
        String url = f[6].trim();
        String key = s.key();
        if (!key.equals(lastKey)) {
            lastKey = key;
            lastArtUrl = "";
        }
        if (!url.isEmpty() && !url.equals(lastArtUrl)) {
            artwork = download(url);
            if (artwork != null) lastArtUrl = url;
        }
        MusicManager.push(s, artwork);
    }

    private static byte[] download(String url) {
        try {
            if (url.startsWith("file:")) return Files.readAllBytes(Path.of(URI.create(url)));
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8)).GET().build();
            HttpResponse<byte[]> res = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
            return res.statusCode() == 200 ? res.body() : null;
        } catch (IOException | InterruptedException | RuntimeException e) {
            return null;
        }
    }
}
