package com.kit531.bloodfx.mixin;

import com.kit531.bloodfx.client.TintHolder;
import com.kit531.bloodfx.client.TintState;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes a hit player render with the hit type's colour instead of vanilla's red flash,
 * and for longer. The colours live in spare rows of the overlay texture
 * (see {@link OverlayTextureMixin}); here we only pick which row to sample.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

    /** Remember, per entity, which tint row is active this frame. */
    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL")
    )
    private void bloodfx$extract(LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo ci) {
        ((TintHolder) (Object) state).bloodfx$setRow(TintState.rowFor(entity));
    }

    /** Swap the overlay coordinates when a custom tint is active. */
    @Inject(
            method = "getOverlayCoords(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)I",
            at = @At("RETURN"),
            cancellable = true
    )
    private static void bloodfx$overlay(LivingEntityRenderState state, float whiteOverlayProgress,
                                        CallbackInfoReturnable<Integer> cir) {
        int row = ((TintHolder) (Object) state).bloodfx$getRow();
        if (row >= 0) {
            cir.setReturnValue(OverlayTexture.pack(OverlayTexture.u(whiteOverlayProgress), row));
        }
    }
}
