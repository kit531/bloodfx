package com.kit531.bloodfx;

import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** Code that is named differently in Minecraft 26.x than in 1.21.x. */
public final class Compat {
    private Compat() {
    }

    /** Creates a sound event for assets/bloodfx/sounds.json without registering it. */
    public static SoundEvent sound(String path) {
        return SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(BloodFxClient.MOD_ID, path));
    }
}
