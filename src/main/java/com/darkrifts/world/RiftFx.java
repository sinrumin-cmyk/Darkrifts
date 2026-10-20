package com.darkrifts.world;

import com.darkrifts.entity.RiftSpike;
import com.darkrifts.net.FxPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Ritual-grade effects for every rift: the birth show (scripted over ~46 ticks, like the altar ritual), the living
 * tear (sparks, energy pulses and arcs running along the line), the unstable last seconds, the collapse, the jump
 * through a rift and the Rift Ward of the armor. Everything is particles + sounds + one tiny packet (shake / flash),
 * driven from the server tick.
 */
public final class RiftFx {
    private static final DustParticleOptions VIOLET = new DustParticleOptions(new Vector3f(0.65F, 0.2F, 1.0F), 1.5F);
    private static final DustParticleOptions PALE = new DustParticleOptions(new Vector3f(0.9F, 0.7F, 1.0F), 1.0F);
    private static final DustParticleOptions TEAL = new DustParticleOptions(new Vector3f(0.2F, 0.9F, 0.95F), 1.2F);
    private static final DustParticleOptions BLACK = new DustParticleOptions(new Vector3f(0.05F, 0.0F, 0.1F), 2.0F);

    private static final int SHOW_TICKS = 46;
    private static final List<Show> SHOWS = new ArrayList<>();

    private RiftFx() {}

    private static final class Show {
        final ServerLevel level;
        final Vec3 center;
        final double ground;
        final double power;
        int age;

        Show(ServerLevel level, Vec3 center, double ground, double power) {
            this.level = level;
            this.center = center;
            this.ground = ground;
            this.power = power;
        }
    }

    public static void clear() {
        SHOWS.clear();
    }

    // ------------------------------------------------------------------ network / helpers

    /** Camera shake + coloured flash for every player within {@code radius} (falls off with distance). */
    public static void screen(ServerLevel level, Vec3 at, double radius, float shake, float flash, int rgb) {
        for (ServerPlayer p : level.players()) {
            double d = p.position().distanceTo(at);
            if (d > radius) {
                continue;
            }
            float k = (float) (1.0 - d / radius);
            PacketDistributor.sendToPlayer(p, new FxPayload(shake * k, flash * k * k, rgb));
        }
    }

    /** A lightning bolt that does no damage and starts no fire: just the flash and the thunder. */
    public static void bolt(ServerLevel level, double x, double y, double z) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(x, y, z);
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
    }

    /** Surface below (or at) the point, searching up to 48 blocks down; falls back to the point itself. */
    public static double groundY(ServerLevel level, double x, double y, double z) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(Math.floor(x), Math.floor(y), Math.floor(z));
        int min = Math.max(level.getMinBuildHeight(), p.getY() - 48);
        for (int yy = p.getY(); yy >= min; yy--) {
            p.setY(yy);
            BlockState s = level.getBlockState(p);
            if (!s.isAir() && !s.getCollisionShape(level, p).isEmpty()) {
                return yy + 1.0;
            }
        }
        return y;
    }

    // ------------------------------------------------------------------ birth

    /**
     * A freshly finished rift: thunder, flash and shake right now, then a 46 tick show - rune circle on the
     * ground, soul-fire pillars, rays of light, void spirals pulled into the tear, cracks, more lightning.
     * {@code power} is roughly the size of the rift (1..6).
     */
    public static void birth(ServerLevel level, Vec3 c, double power) {
        double ground = groundY(level, c.x, c.y, c.z);
        double p = Math.max(1.0, Math.min(6.0, power));
        level.playSound(null, BlockPos.containing(c), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.BLOCKS, 5.0F, 0.6F);
        level.playSound(null, BlockPos.containing(c), SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.BLOCKS, 3.0F, 0.5F);
        level.playSound(null, BlockPos.containing(c), SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 3.0F, 0.55F);
        level.playSound(null, BlockPos.containing(c), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.BLOCKS, 3.0F, 0.5F);
        level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0.5, 0.5, 0.5, 0.0);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, (int) (160 * p), 1.0 + p * 0.4, 1.6 + p * 0.5, 1.0 + p * 0.4, 0.5);
        level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, (int) (60 * p), 0.3, 0.3, 0.3, 0.7);
        for (int i = 0; i < 2 + (int) p; i++) {
            double a = level.getRandom().nextDouble() * Math.PI * 2.0;
            double r = 3.0 + level.getRandom().nextDouble() * (3.0 + p);
            bolt(level, c.x + Math.cos(a) * r, ground, c.z + Math.sin(a) * r);
        }
        screen(level, c, 48.0, 1.6F + (float) p * 0.15F, 0.85F, 0xE6C8FF);
        RiftWaves.start(level, c, 6.0 + p * 2.2);
        SHOWS.add(new Show(level, c, ground, p));
    }

    /** Runs the scripted birth shows; called every server tick from ModEvents. */
    public static void tick() {
        Iterator<Show> it = SHOWS.iterator();
        while (it.hasNext()) {
            Show s = it.next();
            s.age++;
            if (s.age > SHOW_TICKS) {
                it.remove();
                continue;
            }
            script(s);
        }
    }

    private static void script(Show s) {
        ServerLevel sl = s.level;
        RandomSource rnd = sl.getRandom();
        int t = s.age;
        double cx = s.center.x;
        double cz = s.center.z;
        double gy = s.ground;
        double rr = 3.0 + s.power * 0.9;          // radius of the rune circle
        double prog = t / (double) SHOW_TICKS;

        // rune circle spreading over the ground (double ring + octagram)
        double r = rr * Math.min(1.0, t / 18.0);
        if (t % 2 == 0) {
            for (int i = 0; i < 40; i++) {
                double a = i * Math.PI * 2.0 / 40.0 + t * 0.05;
                sl.sendParticles(VIOLET, cx + Math.cos(a) * r, gy + 0.1, cz + Math.sin(a) * r, 1, 0, 0, 0, 0);
            }
            for (int i = 0; i < 24; i++) {
                double a = i * Math.PI * 2.0 / 24.0 - t * 0.04;
                sl.sendParticles(TEAL, cx + Math.cos(a) * r * 0.72, gy + 0.12, cz + Math.sin(a) * r * 0.72, 1, 0, 0, 0, 0);
            }
            for (int sq = 0; sq < 2; sq++) {
                double base = sq * Math.PI / 4.0 + t * 0.03 * (sq == 0 ? 1 : -1);
                for (int e = 0; e < 4; e++) {
                    double a0 = base + e * Math.PI / 2.0;
                    double a1 = a0 + Math.PI / 2.0;
                    for (int k = 0; k < 3; k++) {
                        double f = rnd.nextDouble();
                        double x = cx + (Math.cos(a0) * (1 - f) + Math.cos(a1) * f) * r * 0.85;
                        double z = cz + (Math.sin(a0) * (1 - f) + Math.sin(a1) * f) * r * 0.85;
                        sl.sendParticles(ParticleTypes.END_ROD, x, gy + 0.14, z, 1, 0.02, 0.02, 0.02, 0.0);
                    }
                }
            }
        }
        // soul-fire pillars at the four corners, rising and fading
        if (t >= 4 && t <= 38) {
            double rise = Math.min(1.0, (t - 4) / 14.0);
            for (int k = 0; k < 4; k++) {
                double a = k * Math.PI / 2.0 + Math.PI / 4.0;
                double px = cx + Math.cos(a) * rr * 0.85;
                double pz = cz + Math.sin(a) * rr * 0.85;
                sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, px, gy + rnd.nextDouble() * 7.0 * rise, pz, 2, 0.08, 0.3, 0.08, 0.01);
                sl.sendParticles(ParticleTypes.END_ROD, px, gy + rnd.nextDouble() * 8.0 * rise, pz, 1, 0.05, 0.4, 0.05, 0.02);
                if (t % 4 == k) {
                    for (int q = 1; q < 8; q++) {          // arc from every pillar to the tear
                        double f = q / 8.0;
                        sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, px + (cx - px) * f,
                                gy + 1.0 + (s.center.y - gy) * f * f, pz + (cz - pz) * f, 1, 0.05, 0.05, 0.05, 0.0);
                    }
                }
            }
        }
        // spirals of void pulled into the tear
        if (t >= 6 && t <= 40) {
            double k = (t - 6) / 34.0;
            for (int arm = 0; arm < 3; arm++) {
                double a = t * 0.3 + arm * 2.094;
                double rad = Math.max(0.3, rr * (1.0 - k));
                double h = s.center.y - gy;
                double y = gy + 0.3 + h * (0.2 + 0.8 * k) + Math.sin(t * 0.2 + arm) * 0.3;
                sl.sendParticles(ParticleTypes.REVERSE_PORTAL, cx + Math.cos(a) * rad, y, cz + Math.sin(a) * rad, 2, 0, 0, 0, 0.02);
                sl.sendParticles(BLACK, cx + Math.cos(a) * rad, y, cz + Math.sin(a) * rad, 1, 0, 0, 0, 0);
            }
        }
        // a column of light shooting up through the tear
        if (t >= 2 && t <= 26 && t % 2 == 0) {
            for (int i = 0; i < 6; i++) {
                sl.sendParticles(PALE, cx + (rnd.nextDouble() - 0.5) * 0.6, s.center.y + rnd.nextDouble() * 18.0 * Math.sin(prog * Math.PI),
                        cz + (rnd.nextDouble() - 0.5) * 0.6, 1, 0, 0, 0, 0);
            }
            sl.sendParticles(ParticleTypes.END_ROD, s.center.x, s.center.y, s.center.z, 3, 0.2, 0.2, 0.2, 0.35);
        }
        // cracks opening in the ground
        if (t >= 3 && t <= 30 && t % 4 == 0) {
            double a = rnd.nextDouble() * Math.PI * 2.0;
            double rad = 1.5 + rnd.nextDouble() * (rr - 1.0);
            double sy = RiftSpike.crack(sl, cx + Math.cos(a) * rad, gy, cz + Math.sin(a) * rad, 1, 80);
            sl.sendParticles(ParticleTypes.REVERSE_PORTAL, cx + Math.cos(a) * rad, sy + 0.2, cz + Math.sin(a) * rad, 8, 0.4, 0.1, 0.4, 0.1);
        }
        // more lightning + a second shock
        if (t == 12 || t == 24 || t == 36) {
            double a = rnd.nextDouble() * Math.PI * 2.0;
            double rad = rr + rnd.nextDouble() * 3.0;
            bolt(sl, cx + Math.cos(a) * rad, gy, cz + Math.sin(a) * rad);
        }
        if (t == 14) {
            RiftWaves.start(sl, s.center, 5.0 + s.power * 1.6);
            sl.playSound(null, BlockPos.containing(s.center), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.BLOCKS, 1.5F, 0.7F);
            screen(sl, s.center, 40.0, 0.9F, 0.35F, 0xB48CFF);
        }
        // the last pulse
        if (t == SHOW_TICKS) {
            sl.sendParticles(ParticleTypes.END_ROD, s.center.x, s.center.y, s.center.z, (int) (50 * s.power), 0.4, 0.4, 0.4, 0.6);
            sl.playSound(null, BlockPos.containing(s.center), SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 2.0F, 0.6F);
        }
    }

    // ------------------------------------------------------------------ the stroke itself (Rift Blade)

    /** Sparks that follow the tip of the line while it is being drawn. */
    public static void stroke(ServerLevel level, Vec3 tip, RandomSource rnd) {
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, tip.x, tip.y, tip.z, 3, 0.15, 0.15, 0.15, 0.3);
        level.sendParticles(ParticleTypes.END_ROD, tip.x, tip.y, tip.z, 1, 0.05, 0.05, 0.05, 0.12);
        level.sendParticles(VIOLET, tip.x, tip.y, tip.z, 2, 0.12, 0.12, 0.12, 0.0);
        if (rnd.nextInt(6) == 0) {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, tip.x, tip.y, tip.z, 6, 0.3, 0.3, 0.3, 0.2);
        }
    }

    // ------------------------------------------------------------------ the living tear

    /**
     * Called now and then by every rift cell for each piece of the tear line it owns. {@code heat} 0 = calm,
     * 1 = unstable (last seconds before it collapses), 2 = about to burst.
     */
    public static void ambient(ServerLevel level, double[] seg, long gameTime, RandomSource rnd, int heat) {
        double f = rnd.nextDouble();
        double x = seg[0] + (seg[3] - seg[0]) * f;
        double y = seg[1] + (seg[4] - seg[1]) * f;
        double z = seg[2] + (seg[5] - seg[2]) * f;
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y, z, 1 + heat, 0.2, 0.2, 0.2, 0.06 + heat * 0.05);
        level.sendParticles(VIOLET, x, y, z, 1, 0.15, 0.15, 0.15, 0.0);
        // an energy pulse travelling along the piece
        double s = (gameTime % 30) / 30.0;
        level.sendParticles(ParticleTypes.END_ROD, seg[0] + (seg[3] - seg[0]) * s, seg[1] + (seg[4] - seg[1]) * s,
                seg[2] + (seg[5] - seg[2]) * s, 1, 0.02, 0.02, 0.02, 0.0);
        if (rnd.nextInt(4) == 0) {
            level.sendParticles(ParticleTypes.SCULK_SOUL, x, y, z, 1, 0.1, 0.1, 0.1, 0.03);
        }
        if (rnd.nextInt(7) == 0) {
            level.sendParticles(BLACK, x, y, z, 1, 0.3, 0.3, 0.3, 0.0);
        }
        if (heat > 0) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 2 + heat * 2, 0.25, 0.25, 0.25, 0.25);
            level.sendParticles(ParticleTypes.END_ROD, x, y, z, heat, 0.15, 0.15, 0.15, 0.2);
        }
    }

    /** A little bolt of electricity jumping from a point of the tear to somewhere nearby. */
    public static void arc(ServerLevel level, Vec3 from, RandomSource rnd) {
        Vec3 dir = new Vec3(rnd.nextDouble() - 0.5, rnd.nextDouble() - 0.5, rnd.nextDouble() - 0.5).normalize().scale(1.8 + rnd.nextDouble() * 2.2);
        Vec3 to = from.add(dir);
        for (int i = 1; i <= 9; i++) {
            double f = i / 9.0;
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, from.x + dir.x * f + (rnd.nextDouble() - 0.5) * 0.4,
                    from.y + dir.y * f + (rnd.nextDouble() - 0.5) * 0.4, from.z + dir.z * f + (rnd.nextDouble() - 0.5) * 0.4,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
        level.sendParticles(ParticleTypes.END_ROD, to.x, to.y, to.z, 3, 0.1, 0.1, 0.1, 0.1);
        level.playSound(null, BlockPos.containing(from), SoundEvents.SCULK_CLICKING, SoundSource.BLOCKS, 0.7F, 0.6F + rnd.nextFloat() * 0.5F);
    }

    /** A low hum / rumble that goes with a standing rift. */
    public static void hum(ServerLevel level, Vec3 at, RandomSource rnd, int heat) {
        level.playSound(null, BlockPos.containing(at), SoundEvents.PORTAL_AMBIENT, SoundSource.BLOCKS, 0.7F + heat * 0.4F, 0.5F + rnd.nextFloat() * 0.2F);
        if (heat > 0) {
            level.playSound(null, BlockPos.containing(at), SoundEvents.WARDEN_TENDRIL_CLICKS, SoundSource.BLOCKS, 1.0F, 0.5F + heat * 0.2F);
        }
    }

    // ------------------------------------------------------------------ collapse

    /** The tear implodes: matter is sucked in, a flash, a wave. */
    public static void collapse(ServerLevel level, Vec3 c, boolean big) {
        if (!big) {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 10, 0.3, 0.3, 0.3, 0.15);
            level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 2, 0.2, 0.2, 0.2, 0.1);
            return;
        }
        level.playSound(null, BlockPos.containing(c), SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 2.0F, 0.4F);
        level.playSound(null, BlockPos.containing(c), SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.BLOCKS, 1.5F, 0.6F);
        level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 3, 0.6, 0.6, 0.6, 0.0);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 140, 1.2, 2.0, 1.2, 0.5);
        level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 60, 0.3, 0.3, 0.3, 0.6);
        for (int ring = 0; ring < 3; ring++) {
            for (int i = 0; i < 36; i++) {
                double a = i * Math.PI * 2.0 / 36.0;
                double rad = 1.5 + ring * 1.4;
                level.sendParticles(VIOLET, c.x + Math.cos(a) * rad, c.y, c.z + Math.sin(a) * rad, 1, 0, 0.1, 0, 0);
            }
        }
        RiftWaves.start(level, c, 7.0);
        screen(level, c, 32.0, 0.8F, 0.3F, 0xB48CFF);
    }

    // ------------------------------------------------------------------ travelling

    /** Someone is being swallowed by a rift. */
    public static void depart(ServerLevel level, Vec3 at) {
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 1.0, at.z, 80, 0.4, 0.9, 0.4, 0.4);
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1.0, at.z, 24, 0.3, 0.6, 0.3, 0.3);
        level.sendParticles(VIOLET, at.x, at.y + 1.0, at.z, 20, 0.4, 0.7, 0.4, 0.0);
        level.playSound(null, BlockPos.containing(at), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 0.5F);
        level.playSound(null, BlockPos.containing(at), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 0.6F, 1.6F);
        screen(level, at, 24.0, 0.5F, 0.0F, 0xB48CFF);
    }

    /** Someone steps out of a rift. */
    public static void arrive(ServerLevel level, Vec3 at) {
        level.sendParticles(ParticleTypes.FLASH, at.x, at.y + 1.0, at.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 1.0, at.z, 120, 0.5, 1.0, 0.5, 0.5);
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1.0, at.z, 40, 0.3, 0.5, 0.3, 0.45);
        for (int ring = 0; ring < 2; ring++) {
            for (int i = 0; i < 32; i++) {
                double a = i * Math.PI * 2.0 / 32.0;
                level.sendParticles(VIOLET, at.x + Math.cos(a) * (1.4 + ring * 1.4), at.y + 0.15, at.z + Math.sin(a) * (1.4 + ring * 1.4), 1, 0, 0.1, 0, 0);
            }
        }
        level.playSound(null, BlockPos.containing(at), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.8F, 1.4F);
        level.playSound(null, BlockPos.containing(at), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 0.7F);
        RiftWaves.start(level, at.add(0, 1.0, 0), 5.0);
        screen(level, at, 24.0, 0.9F, 0.7F, 0xE6C8FF);
    }

    // ------------------------------------------------------------------ armor

    /** The Rift Ward of the armor going off around its wearer. */
    public static void ward(ServerLevel level, Vec3 c) {
        level.playSound(null, BlockPos.containing(c), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.5F, 0.8F);
        level.playSound(null, BlockPos.containing(c), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 1.0F, 1.2F);
        level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 100, 0.6, 0.9, 0.6, 0.45);
        level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 30, 0.3, 0.5, 0.3, 0.5);
        for (int ring = 0; ring < 3; ring++) {
            for (int i = 0; i < 36; i++) {
                double a = i * Math.PI * 2.0 / 36.0;
                double rad = 1.5 + ring * 1.6;
                level.sendParticles(VIOLET, c.x + Math.cos(a) * rad, c.y - 0.8, c.z + Math.sin(a) * rad, 1, 0, 0.1, 0, 0);
            }
        }
        RiftWaves.start(level, c, 6.0);
        screen(level, c, 16.0, 0.7F, 0.25F, 0xB48CFF);
    }
}
