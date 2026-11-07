package de.example.riftkingextras.mixin;

import de.example.riftkingextras.NetStats;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tells the ping measurement when the server answers a ping request. If the method is not found, nothing happens. */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "handlePongResponse", at = @At("HEAD"), require = 0)
    private void riftkingextras$onPong(@Coerce Object packet, CallbackInfo ci) {
        NetStats.onPong(packet);
    }
}
