package com.kit531.bloodfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import java.lang.reflect.Method;

/** Code that is named differently in Minecraft 26.x than in 1.21.x (or between 26.1, 26.2 and 26.3). */
public final class Compat {
    private static Method setScreenMethod;
    private static boolean setScreenLooked;

    private Compat() {
    }

    /** Creates a sound event for assets/bloodfx/sounds.json without registering it. */
    public static SoundEvent sound(String path) {
        return SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(BloodFxClient.MOD_ID, path));
    }

    /**
     * Opens a screen. The method is called setScreen in some 26.x releases and setScreenAndShow in
     * others; 26.x is not obfuscated, so the names are the same at runtime and can be looked up.
     */
    public static void setScreen(Minecraft mc, Screen screen) {
        Method method = findSetScreen();
        if (method == null) {
            return;
        }
        try {
            method.invoke(mc, screen);
        } catch (ReflectiveOperationException e) {
            System.err.println("[bloodfx] Could not open screen: " + e);
        }
    }

    private static Method findSetScreen() {
        if (!setScreenLooked) {
            setScreenLooked = true;
            for (String name : new String[] {"setScreenAndShow", "setScreen"}) {
                try {
                    setScreenMethod = Minecraft.class.getMethod(name, Screen.class);
                    break;
                } catch (NoSuchMethodException ignored) {
                    // try the next name
                }
            }
        }
        return setScreenMethod;
    }

    /** Shows a line in the local chat only. */
    public static void chat(Minecraft mc, Component text) {
        if (mc.player != null) {
            mc.player.sendSystemMessage(text);
        }
    }

    public static void setParticleColor(Particle particle, float r, float g, float b) {
        if (particle instanceof SingleQuadParticle quad) {
            quad.setColor(r, g, b);
        }
    }
}
