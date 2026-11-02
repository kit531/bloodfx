package com.kit531.bloodfx.client;

import com.kit531.bloodfx.HitType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayDeque;
import java.util.Iterator;

/**
 * Works out which kind of hit just landed, using only what a client can see.
 *
 * <p>The server plays a different vanilla sound for each special sword hit
 * ({@code entity.player.attack.knockback / crit / sweep}) and sends it to every nearby
 * client. We remember those sounds for a few ticks; when a player's hurt animation starts,
 * the closest recent attack sound tells us what kind of hit it was. That is why the mod
 * works on any server, even one that does not have it installed.
 */
public final class HitDetector {
    private record AttackSound(HitType type, double x, double y, double z, long tick) {
    }

    /** What {@link #resolve} found. {@code hasSource} is false when no attack sound matched. */
    public record Match(HitType type, boolean hasSource, double sourceX, double sourceZ) {
    }

    private static final ArrayDeque<AttackSound> RECENT = new ArrayDeque<>();
    private static final int WINDOW_TICKS = 2;
    private static final double MAX_DISTANCE_SQR = 7.0 * 7.0;

    private static long clientTick;

    private HitDetector() {
    }

    /** Called once per client tick before players are checked. */
    public static void advance() {
        clientTick++;
        Iterator<AttackSound> it = RECENT.iterator();
        while (it.hasNext()) {
            if (clientTick - it.next().tick > WINDOW_TICKS + 2) {
                it.remove();
            }
        }
    }

    public static long tick() {
        return clientTick;
    }

    public static void reset() {
        RECENT.clear();
    }

    /** Called from the sound packet mixin. */
    public static void onSound(ClientboundSoundPacket packet) {
        // The packet handler runs once on the network thread and again on the main thread.
        if (!Minecraft.getInstance().isSameThread()) {
            return;
        }
        HitType type = switch (packet.getSound().value().location().getPath()) {
            case "entity.player.attack.knockback" -> HitType.KNOCKBACK;
            case "entity.player.attack.crit" -> HitType.CRIT;
            case "entity.player.attack.sweep" -> HitType.SWEEP;
            default -> null;
        };
        if (type != null) {
            RECENT.addLast(new AttackSound(type, packet.getX(), packet.getY(), packet.getZ(), clientTick));
        }
    }

    private static int priority(HitType type) {
        return switch (type) {
            case CRIT -> 3;
            case KNOCKBACK -> 2;
            case SWEEP -> 1;
            case NORMAL -> 0;
        };
    }

    public static Match resolve(Player victim) {
        AttackSound best = null;
        for (AttackSound s : RECENT) {
            if (clientTick - s.tick > WINDOW_TICKS) {
                continue;
            }
            if (victim.distanceToSqr(s.x, s.y, s.z) > MAX_DISTANCE_SQR) {
                continue;
            }
            if (best == null || priority(s.type) > priority(best.type)) {
                best = s;
            }
        }
        if (best == null) {
            return new Match(HitType.NORMAL, false, 0, 0);
        }
        return new Match(best.type, true, best.x, best.z);
    }
}
