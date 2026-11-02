package com.kit531.bloodfx.client;

import com.kit531.bloodfx.HitType;
import com.kit531.bloodfx.config.BloodFxConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

/** Which players currently have a hit-coloured tint, and for how much longer. */
public final class TintState {
    private record Tint(HitType type, long endTick) {
    }

    private static final Map<Integer, Tint> ACTIVE = new HashMap<>();

    private TintState() {
    }

    public static void start(Player player, HitType type) {
        BloodFxConfig cfg = BloodFxConfig.get();
        if (!cfg.tintEnabled) {
            return;
        }
        // Vanilla keeps the red hurt flash for hurtDuration (10) ticks; we scale that.
        int vanilla = Math.max(player.hurtDuration, 1);
        long ticks = Math.max(1L, Math.round(vanilla * (double) cfg.tintDurationMultiplier));
        ACTIVE.put(player.getId(), new Tint(type, HitDetector.tick() + ticks));
    }

    /** Overlay texture row to render this entity with, or -1 for vanilla rendering. */
    public static int rowFor(LivingEntity entity) {
        if (ACTIVE.isEmpty() || !(entity instanceof Player)) {
            return -1;
        }
        Tint tint = ACTIVE.get(entity.getId());
        if (tint == null) {
            return -1;
        }
        if (!BloodFxConfig.get().enabled || !BloodFxConfig.get().tintEnabled
                || HitDetector.tick() >= tint.endTick()) {
            ACTIVE.remove(entity.getId());
            return -1;
        }
        return tint.type().overlayRow;
    }

    public static void clear() {
        ACTIVE.clear();
    }
}
