package com.kit531.bloodfx.mixin;

import com.kit531.bloodfx.client.TintHolder;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Adds a "tint row" field to the render state so it can travel from extraction to rendering. */
@Mixin(LivingEntityRenderState.class)
public abstract class LivingEntityRenderStateMixin implements TintHolder {
    @Unique
    private int bloodfx$row = -1;

    @Override
    public int bloodfx$getRow() {
        return this.bloodfx$row;
    }

    @Override
    public void bloodfx$setRow(int row) {
        this.bloodfx$row = row;
    }
}
