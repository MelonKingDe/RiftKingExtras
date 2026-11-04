package de.example.riftkingextras;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

/** Keys for the music: next, previous, restart, play/pause, volume. All can be changed under Options > Controls. */
final class MusicKeybinds {
    private static KeyMapping next, previous, restart, playPause, volumeUp, volumeDown;

    private MusicKeybinds() {}

    static void register(KeyMapping.Category category) {
        next = key("key.riftkingextras.music_next", InputConstants.KEY_RIGHT, category);
        previous = key("key.riftkingextras.music_previous", InputConstants.KEY_LEFT, category);
        restart = key("key.riftkingextras.music_restart", InputConstants.KEY_DOWN, category);
        playPause = key("key.riftkingextras.music_play_pause", InputConstants.KEY_UP, category);
        volumeUp = key("key.riftkingextras.music_volume_up", InputConstants.KEY_PAGEUP, category);
        volumeDown = key("key.riftkingextras.music_volume_down", InputConstants.KEY_PAGEDOWN, category);
    }

    private static KeyMapping key(String name, int defaultKey, KeyMapping.Category category) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(name, InputConstants.Type.KEYSYM, defaultKey, category));
    }

    /** Called every client tick. */
    static void tick() {
        if (next == null) return;
        boolean on = MusicConfig.enabled;
        while (next.consumeClick()) if (on) MusicManager.send("next");
        while (previous.consumeClick()) if (on) MusicManager.send("previous");
        while (restart.consumeClick()) if (on) MusicManager.send("restart");
        while (playPause.consumeClick()) if (on) MusicManager.send("playpause");
        while (volumeUp.consumeClick()) if (on) MusicManager.send("volume_up");
        while (volumeDown.consumeClick()) if (on) MusicManager.send("volume_down");
    }
}
