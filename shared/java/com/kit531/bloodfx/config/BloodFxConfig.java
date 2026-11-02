package com.kit531.bloodfx.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.kit531.bloodfx.HitType;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * All user settings. Stored as JSON in {@code config/bloodfx.json} and editable from the
 * Mod Menu config screen.
 */
public final class BloodFxConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static BloodFxConfig instance = new BloodFxConfig();
    private static boolean dirty = true;

    // ---- general ---------------------------------------------------------------------
    public boolean enabled = true;

    // ---- particles -------------------------------------------------------------------
    public boolean particlesEnabled = true;
    /** Percent of the default particle count, 0-500. */
    public int particleAmount = 100;

    // ---- sounds ----------------------------------------------------------------------
    public boolean soundsEnabled = true;
    /** 0.0 - 1.0, multiplied with each hit type's own volume. */
    public float masterVolume = 1.0f;

    // ---- colour tint on the hit player -----------------------------------------------
    public boolean tintEnabled = true;
    /** How long the tint stays, as a multiple of vanilla's 10 tick hurt flash. 2.0 = twice as long. */
    public float tintDurationMultiplier = 2.0f;
    /** 1.0 = same strength as vanilla's red flash (30% blend). Higher = stronger colour. */
    public float tintBrightness = 1.5f;

    // ---- remote updates --------------------------------------------------------------
    /** 0 = off, 1 = tell me when an update exists, 2 = download and install it automatically. */
    public int updateMode = 2;

    // ---- per hit type ----------------------------------------------------------------
    public TypeSettings normal = new TypeSettings("#C00000", 0, 1.0f);
    public TypeSettings knockback = new TypeSettings("#FF7A00", 1, 1.0f);
    public TypeSettings crit = new TypeSettings("#FFD400", 1, 1.0f);
    public TypeSettings sweep = new TypeSettings("#B040FF", 1, 1.0f);

    public static final class TypeSettings {
        /** Tint colour as #RRGGBB. */
        public String color = "#C00000";
        /** 0 = only the vanilla sound, 1-4 = one of the mod's own sounds (played on top). */
        public int sound = 1;
        /** 0.0 - 1.5 */
        public float volume = 1.0f;

        public TypeSettings() {
        }

        public TypeSettings(String color, int sound, float volume) {
            this.color = color;
            this.sound = sound;
            this.volume = volume;
        }
    }

    public TypeSettings of(HitType type) {
        return switch (type) {
            case NORMAL -> normal;
            case KNOCKBACK -> knockback;
            case CRIT -> crit;
            case SWEEP -> sweep;
        };
    }

    /** Tint colour of a hit type as 0xRRGGBB. */
    public int colorRgb(HitType type) {
        String s = of(type).color;
        try {
            return Integer.parseInt(s.startsWith("#") ? s.substring(1) : s, 16) & 0xFFFFFF;
        } catch (RuntimeException e) {
            return 0xC00000;
        }
    }

    private void sanitize() {
        if (normal == null) normal = new TypeSettings("#C00000", 0, 1.0f);
        if (knockback == null) knockback = new TypeSettings("#FF7A00", 1, 1.0f);
        if (crit == null) crit = new TypeSettings("#FFD400", 1, 1.0f);
        if (sweep == null) sweep = new TypeSettings("#B040FF", 1, 1.0f);

        particleAmount = Math.max(0, Math.min(500, particleAmount));
        masterVolume = clamp(masterVolume, 0.0f, 1.0f);
        tintDurationMultiplier = clamp(tintDurationMultiplier, 0.1f, 10.0f);
        tintBrightness = clamp(tintBrightness, 0.1f, 3.0f);
        updateMode = Math.max(0, Math.min(2, updateMode));

        for (HitType type : HitType.values()) {
            TypeSettings s = of(type);
            if (s.color == null) s.color = "#C00000";
            s.sound = Math.max(0, Math.min(4, s.sound));
            s.volume = clamp(s.volume, 0.0f, 1.5f);
        }
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    // ---- access / persistence --------------------------------------------------------

    public static BloodFxConfig get() {
        return instance;
    }

    /** Marks the config as changed so the tint texture gets rebuilt on the next tick. */
    public static void markDirty() {
        dirty = true;
    }

    /** Returns true once after every change. */
    public static boolean consumeDirty() {
        boolean was = dirty;
        dirty = false;
        return was;
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("bloodfx.json");
    }

    public static void load() {
        Path path = file();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                BloodFxConfig loaded = GSON.fromJson(reader, BloodFxConfig.class);
                if (loaded != null) {
                    instance = loaded;
                }
            } catch (IOException | RuntimeException e) {
                System.err.println("[bloodfx] Could not read " + path + ", using defaults: " + e);
            }
        }
        instance.sanitize();
        markDirty();
        save();
    }

    public static void save() {
        instance.sanitize();
        Path path = file();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(instance, writer);
            }
        } catch (IOException e) {
            System.err.println("[bloodfx] Could not write " + path + ": " + e);
        }
        markDirty();
    }
}
