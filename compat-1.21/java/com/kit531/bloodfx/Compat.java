package com.kit531.bloodfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/**
 * Code that is named differently in Minecraft 1.21.11 than in 26.x. 1.21.11 is still obfuscated at
 * runtime, so unlike the 26.x version nothing here can be looked up by name with reflection.
 */
public final class Compat {
    private Compat() {
    }

    /** Creates a sound event for assets/bloodfx/sounds.json without registering it. */
    public static SoundEvent sound(String path) {
        return SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(BloodFxClient.MOD_ID, path));
    }

    public static void setScreen(Minecraft mc, Screen screen) {
        mc.setScreen(screen);
    }

    /** Shows a line in the local chat only. */
    public static void chat(Minecraft mc, Component text) {
        if (mc.player != null) {
            mc.player.displayClientMessage(text, false);
        }
    }

    public static void setParticleColor(Particle particle, float r, float g, float b) {
        if (particle instanceof SingleQuadParticle quad) {
            quad.setColor(r, g, b);
        }
    }
}
