// Reads what is playing from the Windows media session (the one behind the media keys and the volume flyout)
// and prints one JSON line per update. Reads commands (playpause, next, previous, restart, volume_up, volume_down)
// from stdin. No Spotify account, API key or Premium is needed.
// Based on the CC0 SpotifyOverlayHelper, rewritten: the cover is only sent when it changes.
using System.Text.Json;
using Windows.Media.Control;

namespace RiftKingMusicHelper;

internal static class Program
{
    private static async Task Main()
    {
        Console.OutputEncoding = new System.Text.UTF8Encoding(false);

        GlobalSystemMediaTransportControlsSessionManager manager;
        try
        {
            manager = await GlobalSystemMediaTransportControlsSessionManager.RequestAsync();
        }
        catch
        {
            Send(new { active = false });
            return;
        }

        _ = Task.Run(() => ReadCommands(manager));

        string lastKey = "";
        string artwork = "";
        int artworkTries = 0;
        float volume = -1f;
        long lastVolumeCheck = 0;

        while (true)
        {
            try
            {
                var session = manager.GetCurrentSession();
                if (session == null)
                {
                    lastKey = "";
                    Send(new { active = false });
                    await Task.Delay(500);
                    continue;
                }

                var props = await session.TryGetMediaPropertiesAsync();
                var timeline = session.GetTimelineProperties();
                var playback = session.GetPlaybackInfo();

                string title = props?.Title ?? "";
                string artist = props?.Artist ?? "";
                string album = props?.AlbumTitle ?? "";
                string key = title + "|" + artist + "|" + album;

                if (key != lastKey)
                {
                    lastKey = key;
                    artwork = "";
                    artworkTries = 0;
                }

                // The cover can arrive a moment after the title, so try a few times; send it only once.
                string? artworkToSend = null;
                if (artwork.Length == 0 && artworkTries < 20)
                {
                    artworkTries++;
                    artwork = await ReadThumbnail(props);
                    if (artwork.Length > 0) artworkToSend = artwork;
                }

                bool playing = playback.PlaybackStatus ==
                               GlobalSystemMediaTransportControlsSessionPlaybackStatus.Playing;

                double end = (timeline.EndTime - timeline.StartTime).TotalMilliseconds;
                double position = (timeline.Position - timeline.StartTime).TotalMilliseconds;
                // The position is only valid at LastUpdatedTime: add the time that has passed since then.
                if (playing && timeline.LastUpdatedTime.Year > 2000)
                {
                    double passed = (DateTimeOffset.UtcNow - timeline.LastUpdatedTime).TotalMilliseconds;
                    if (passed > 0 && passed < 3_600_000) position += passed;
                }
                if (end > 0) position = Math.Min(position, end);
                if (position < 0) position = 0;

                long now = Environment.TickCount64;
                if (now - lastVolumeCheck > 1000)
                {
                    lastVolumeCheck = now;
                    volume = VolumeControl.Get() ?? -1f;
                }

                Send(new
                {
                    active = true,
                    title,
                    artist,
                    album,
                    status = playing ? "Playing" : "Paused",
                    positionMs = (long)position,
                    durationMs = (long)Math.Max(0, end),
                    artwork = artworkToSend,
                    volume
                });
            }
            catch
            {
                Send(new { active = false });
            }

            await Task.Delay(250);
        }
    }

    private static async Task<string> ReadThumbnail(GlobalSystemMediaTransportControlsSessionMediaProperties? props)
    {
        if (props?.Thumbnail == null) return "";
        try
        {
            using var stream = await props.Thumbnail.OpenReadAsync();
            if (stream.Size == 0) return "";
            using var memory = new MemoryStream();
            await stream.AsStreamForRead().CopyToAsync(memory);
            return Convert.ToBase64String(memory.ToArray());
        }
        catch
        {
            return "";
        }
    }

    private static async Task ReadCommands(GlobalSystemMediaTransportControlsSessionManager manager)
    {
        while (true)
        {
            string? command = Console.ReadLine();
            if (command == null) Environment.Exit(0); // the game closed our input: stop

            try
            {
                if (command == "volume_up") { VolumeControl.Adjust(0.02f); continue; }
                if (command == "volume_down") { VolumeControl.Adjust(-0.02f); continue; }

                var session = manager.GetCurrentSession();
                if (session == null) continue;

                switch (command)
                {
                    case "playpause":
                        await session.TryTogglePlayPauseAsync();
                        break;
                    case "next":
                        await session.TrySkipNextAsync();
                        break;
                    case "previous":
                        // like the media key: restart the song first, a second press goes to the previous one
                        await session.TryChangePlaybackPositionAsync(0);
                        await Task.Delay(150);
                        await session.TrySkipPreviousAsync();
                        break;
                    case "restart":
                        await session.TryChangePlaybackPositionAsync(0);
                        break;
                }
            }
            catch
            {
                // a failed command must never stop the helper
            }
        }
    }

    private static void Send(object value)
    {
        Console.WriteLine(JsonSerializer.Serialize(value));
        Console.Out.Flush();
    }
}
