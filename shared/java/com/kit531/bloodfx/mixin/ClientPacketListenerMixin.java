package com.kit531.bloodfx.mixin;

import com.kit531.bloodfx.client.HitDetector;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets {@link HitDetector} see the attack sounds the server sends. */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "handleSoundEvent", at = @At("HEAD"))
    private void bloodfx$onSound(ClientboundSoundPacket packet, CallbackInfo ci) {
        HitDetector.onSound(packet);
    }
}
