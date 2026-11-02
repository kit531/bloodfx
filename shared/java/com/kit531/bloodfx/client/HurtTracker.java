package com.kit531.bloodfx.client;

import com.kit531.bloodfx.config.BloodFxConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Runs once per client tick. Notices the moment a player starts their hurt animation
 * (their {@code hurtTime} goes up) and triggers the blood, the sound and the tint.
 */
public final class HurtTracker {
    private static final Map<Integer, Integer> LAST_HURT_TIME = new HashMap<>();

    private HurtTracker() {
    }

    public static void tick(Minecraft mc) {
        HitDetector.advance();

        if (BloodFxConfig.consumeDirty()) {
            OverlayHook.requestApply();
        }
        OverlayHook.applyIfNeeded();

        ClientLevel level = mc.level;
        if (level == null) {
            reset();
            return;
        }

        BloodFxConfig cfg = BloodFxConfig.get();
        Set<Integer> present = new HashSet<>();

        for (Player player : level.players()) {
            int id = player.getId();
            present.add(id);

            int previous = LAST_HURT_TIME.getOrDefault(id, 0);
            int current = player.hurtTime;
            LAST_HURT_TIME.put(id, current);

            if (cfg.enabled && current > previous && !player.isSpectator()) {
                onHurt(level, player);
            }
        }
        LAST_HURT_TIME.keySet().retainAll(present);
    }

    private static void onHurt(ClientLevel level, Player player) {
        HitDetector.Match match = HitDetector.resolve(player);

        BloodEffects.spawn(level, player, match.type(), match.hasSource(), match.sourceX(), match.sourceZ());
        BloodSounds.play(level, player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ(), match.type());
        TintState.start(player, match.type());
    }

    public static void reset() {
        LAST_HURT_TIME.clear();
        TintState.clear();
        HitDetector.reset();
    }
}
