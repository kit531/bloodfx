package com.kit531.bloodfx.client;

import com.kit531.bloodfx.Compat;
import com.kit531.bloodfx.HitType;
import com.kit531.bloodfx.config.BloodFxConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * The mod's own hit sounds (assets/bloodfx/sounds/*.ogg). They are played locally on top of
 * the vanilla sound, so choosing "Original" in the settings simply adds nothing.
 */
public final class BloodSounds {
    public static final int VARIANTS = 4;

    private static final SoundEvent[][] EVENTS = new SoundEvent[HitType.values().length][VARIANTS + 1];

    private BloodSounds() {
    }

    /** Returns the sound for a hit type and variant 1-4, or null for "Original" / normal hits. */
    public static SoundEvent get(HitType type, int variant) {
        if (type == HitType.NORMAL || variant < 1 || variant > VARIANTS) {
            return null;
        }
        SoundEvent event = EVENTS[type.ordinal()][variant];
        if (event == null) {
            event = Compat.sound(type.key + "_" + variant);
            EVENTS[type.ordinal()][variant] = event;
        }
        return event;
    }

    public static void play(ClientLevel level, double x, double y, double z, HitType type) {
        BloodFxConfig cfg = BloodFxConfig.get();
        if (!cfg.soundsEnabled) {
            return;
        }
        BloodFxConfig.TypeSettings settings = cfg.of(type);
        SoundEvent event = get(type, settings.sound);
        float volume = cfg.masterVolume * settings.volume;
        if (event == null || volume <= 0.0f) {
            return;
        }
        float pitch = 0.95f + level.random.nextFloat() * 0.10f;
        level.playLocalSound(x, y, z, event, SoundSource.PLAYERS, volume, pitch, false);
    }

    /** Plays a sound without any position, for the settings screen. */
    public static void preview(HitType type, int variant, float volume) {
        SoundEvent event = get(type, variant);
        if (event != null && volume > 0.0f) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, 1.0f, volume));
        }
    }
}
