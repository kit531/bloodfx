package com.kit531.bloodfx.client;

/** Implemented (via mixin) by the entity render state to carry the tint row to the renderer. */
public interface TintHolder {
    /** Row of the overlay texture to use, or -1 for no custom tint. */
    int bloodfx$getRow();

    void bloodfx$setRow(int row);
}
