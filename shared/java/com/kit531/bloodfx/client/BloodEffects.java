package com.kit531.bloodfx.client;

import com.kit531.bloodfx.Compat;
import com.kit531.bloodfx.HitType;
import com.kit531.bloodfx.config.BloodFxConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;

/**
 * Blood drops, spawned on the client only. They are block-break particles of a redstone
 * block, so they are red, fall with gravity and need no extra texture.
 */
public final class BloodEffects {
    private static final double TAU = Math.PI * 2.0;

    private BloodEffects() {
    }

    /**
     * @param hasSource true when the attacker's position is known, so the spray can be aimed
     */
    public static void spawn(ClientLevel level, Player victim, HitType type, boolean hasSource,
                             double sourceX, double sourceZ) {
        BloodFxConfig cfg = BloodFxConfig.get();
        if (!cfg.particlesEnabled || cfg.particleAmount <= 0) {
            return;
        }

        RandomSource rnd = level.getRandom();

        // Direction the blood flies: away from the attacker, or random when unknown.
        double dirX;
        double dirZ;
        if (hasSource) {
            dirX = victim.getX() - sourceX;
            dirZ = victim.getZ() - sourceZ;
            double len = Math.sqrt(dirX * dirX + dirZ * dirZ);
            if (len < 1.0E-4) {
                dirX = 0.0;
                dirZ = 1.0;
            } else {
                dirX /= len;
                dirZ /= len;
            }
        } else {
            double a = rnd.nextDouble() * TAU;
            dirX = Math.cos(a);
            dirZ = Math.sin(a);
        }

        int base = switch (type) {
            case NORMAL -> 8;
            case KNOCKBACK -> 14;
            case CRIT -> 28;
            case SWEEP -> 18;
        };
        int count = Math.round(base * cfg.particleAmount / 100.0f);

        BlockParticleOption blood = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState());
        ParticleEngine engine = Minecraft.getInstance().particleEngine;

        double cx = victim.getX();
        double cy = victim.getY() + victim.getBbHeight() * 0.65;
        double cz = victim.getZ();

        for (int i = 0; i < count; i++) {
            double vx;
            double vy;
            double vz;
            switch (type) {
                case KNOCKBACK -> {
                    // A stream thrown away from the attacker.
                    double speed = 0.18 + rnd.nextDouble() * 0.22;
                    vx = dirX * speed + rnd.nextGaussian() * 0.03;
                    vy = 0.05 + rnd.nextDouble() * 0.15;
                    vz = dirZ * speed + rnd.nextGaussian() * 0.03;
                }
                case CRIT -> {
                    // A big burst in every direction, thrown upward.
                    double a = rnd.nextDouble() * TAU;
                    double speed = 0.08 + rnd.nextDouble() * 0.30;
                    vx = Math.cos(a) * speed;
                    vy = 0.10 + rnd.nextDouble() * 0.30;
                    vz = Math.sin(a) * speed;
                }
                case SWEEP -> {
                    // A wide fan (about 160 degrees) in the direction of the swing.
                    double a = (rnd.nextDouble() - 0.5) * Math.toRadians(160.0);
                    double cos = Math.cos(a);
                    double sin = Math.sin(a);
                    double speed = 0.15 + rnd.nextDouble() * 0.20;
                    vx = (dirX * cos - dirZ * sin) * speed;
                    vy = 0.03 + rnd.nextDouble() * 0.10;
                    vz = (dirX * sin + dirZ * cos) * speed;
                }
                default -> {
                    // Ordinary damage: a small splash.
                    vx = rnd.nextGaussian() * 0.05;
                    vy = 0.06 + rnd.nextDouble() * 0.12;
                    vz = rnd.nextGaussian() * 0.05;
                }
            }

            Particle particle = engine.createParticle(blood,
                    cx + rnd.nextGaussian() * 0.12, cy + rnd.nextGaussian() * 0.15, cz + rnd.nextGaussian() * 0.12,
                    vx, vy, vz);
            if (particle != null) {
                // Vanilla particle constructors add random jitter to the velocity; set the exact one afterwards.
                particle.setParticleSpeed(vx, vy, vz);
                float shade = 0.65f + rnd.nextFloat() * 0.35f;
                Compat.setParticleColor(particle, shade, shade, shade);
                particle.scale(0.7f + rnd.nextFloat() * 0.5f);
                engine.add(particle);
            }
        }
    }
}
