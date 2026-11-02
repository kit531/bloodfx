package com.kit531.bloodfx.mixin;

import com.kit531.bloodfx.HitType;
import com.kit531.bloodfx.client.OverlayHook;
import com.kit531.bloodfx.client.OverlayRecolor;
import com.kit531.bloodfx.config.BloodFxConfig;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The game's overlay texture is 16x16. Rows 0-7 are all vanilla's red hurt colour but the
 * game only ever samples row 3, so the other rows are free. We paint one of them for each
 * hit type, in that hit type's colour.
 *
 * <p>The shader computes {@code mix(overlay.rgb, model.rgb, overlay.a)}, so the texel alpha is
 * the weight of the <em>model</em>: vanilla uses alpha 178 (about 70% model, 30% red). A
 * stronger tint therefore means a LOWER alpha.
 */
@Mixin(OverlayTexture.class)
public abstract class OverlayTextureMixin implements OverlayRecolor {
    @Shadow
    @Final
    private DynamicTexture texture;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void bloodfx$onInit(CallbackInfo ci) {
        OverlayHook.attach(this);
    }

    @Override
    public void bloodfx$recolor() {
        NativeImage image = this.texture.getPixels();
        if (image == null) {
            return;
        }
        BloodFxConfig cfg = BloodFxConfig.get();
        float strength = Math.max(0.02f, Math.min(0.95f, 0.30f * cfg.tintBrightness));
        int alpha = Math.round(255.0f * (1.0f - strength));

        for (HitType type : HitType.values()) {
            int argb = (alpha << 24) | cfg.colorRgb(type);
            for (int x = 0; x < 16; x++) {
                image.setPixel(x, type.overlayRow, argb);
            }
        }
        this.texture.upload();
    }
}
