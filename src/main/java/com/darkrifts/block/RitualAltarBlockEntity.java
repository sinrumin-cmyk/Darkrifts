package com.darkrifts.block;

import com.darkrifts.entity.RiftReaver;
import com.darkrifts.entity.RiftSpike;
import com.darkrifts.registry.ModBlockEntities;
import com.darkrifts.registry.ModEntities;
import com.darkrifts.world.RiftWaves;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * Runs the ritual (everything is a function of the tick counter {@code t}):
 *   0-100   a rune circle spreads over the ground, enchant glyphs rise
 *   30-230  four pillars of soul fire, an octagram burns, spiral arms of void are sucked into the altar
 *   20-200  cracks open in the ground around the altar
 *   110-230 lightning, heartbeat, darkness for those standing close
 *   230     the altar bursts into a tear, a shockwave, and the Rift Reaver climbs out
 * The client draws the floating orb, the rings and the beam in RitualAltarRenderer from {@link #getStartTime()}.
 */
public class RitualAltarBlockEntity extends BlockEntity {
    public static final int DURATION = 230;
    private static final DustParticleOptions VIOLET = new DustParticleOptions(new Vector3f(0.65F, 0.2F, 1.0F), 1.5F);
    private static final DustParticleOptions TEAL = new DustParticleOptions(new Vector3f(0.2F, 0.9F, 0.95F), 1.2F);
    private static final DustParticleOptions BLACK = new DustParticleOptions(new Vector3f(0.05F, 0.0F, 0.1F), 2.2F);

    private boolean active;
    private long startTime;
    @Nullable
    private UUID starter;

    public RitualAltarBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ALTAR.get(), pos, state);
    }

    public boolean isActive() {
        return active;
    }

    public long getStartTime() {
        return startTime;
    }

    // ------------------------------------------------------------------ starting

    public boolean tryStart(ServerPlayer player, ItemStack star) {
        if (active) {
            player.displayClientMessage(Component.translatable("message.darkrifts.ritual_busy"), true);
            return false;
        }
        if (!hasRoom(level)) {
            player.displayClientMessage(Component.translatable("message.darkrifts.ritual_no_room")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!player.getAbilities().instabuild) {
            star.shrink(1);
        }
        active = true;
        startTime = level.getGameTime();
        starter = player.getUUID();
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        if (level instanceof ServerLevel sl) {
            sl.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 3.0F, 0.5F);
            sl.playSound(null, worldPosition, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 2.0F, 0.6F);
            for (ServerPlayer p : sl.players()) {
                if (p.distanceToSqr(Vec3.atCenterOf(worldPosition)) < 48.0 * 48.0) {
                    p.displayClientMessage(Component.translatable("message.darkrifts.ritual_begin")
                            .withStyle(ChatFormatting.DARK_PURPLE), true);
                }
            }
        }
        return true;
    }

    private boolean hasRoom(Level level) {
        for (int dy = 1; dy <= 6; dy++) {
            if (!level.getBlockState(worldPosition.above(dy)).getCollisionShape(level, worldPosition.above(dy)).isEmpty()) {
                return false;
            }
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 1; dy <= 3; dy++) {
                    BlockPos p = worldPosition.offset(dx, dy, dz);
                    if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ the ritual

    public static void serverTick(Level level, BlockPos pos, BlockState state, RitualAltarBlockEntity be) {
        if (!be.active || !(level instanceof ServerLevel sl)) {
            return;
        }
        int t = (int) (level.getGameTime() - be.startTime);
        if (t >= DURATION) {
            be.finale(sl);
            return;
        }
        be.script(sl, t);
    }

    private void script(ServerLevel sl, int t) {
        RandomSource rnd = sl.getRandom();
        double cx = worldPosition.getX() + 0.5;
        double cz = worldPosition.getZ() + 0.5;
        double y0 = worldPosition.getY();
        double prog = t / (double) DURATION;

        // --- sounds
        if (t % 20 == 0 && t < 200) {
            sl.playSound(null, worldPosition, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 3.0F, (float) (0.5 + prog));
            sl.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 3.0F, (float) (0.5 + prog));
        }
        if (t >= 110 && t % (t < 180 ? 18 : 9) == 0) {
            sl.playSound(null, worldPosition, SoundEvents.WARDEN_HEARTBEAT, SoundSource.BLOCKS, 4.0F, (float) (0.7 + prog * 0.3));
        }
        if (t == 90 || t == 160) {
            sl.playSound(null, worldPosition, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.BLOCKS, 3.0F, 0.5F);
        }

        // --- rune circle spreading over the ground
        if (t <= 100 && t % 2 == 0) {
            double r = 6.0 * Math.min(1.0, t / 80.0);
            for (int i = 0; i < 36; i++) {
                double a = i * Math.PI * 2.0 / 36.0 + t * 0.03;
                sl.sendParticles(VIOLET, cx + Math.cos(a) * r, y0 + 0.1, cz + Math.sin(a) * r, 1, 0, 0, 0, 0);
            }
            for (int i = 0; i < 12; i++) {
                double a = i * Math.PI * 2.0 / 12.0 - t * 0.05;
                double rr = r * 0.6;
                sl.sendParticles(ParticleTypes.ENCHANT, cx + Math.cos(a) * rr, y0 + 0.3, cz + Math.sin(a) * rr, 2, 0.1, 0.4, 0.1, 0.4);
            }
        } else if (t > 100 && t % 4 == 0) {
            for (int i = 0; i < 24; i++) {
                double a = i * Math.PI * 2.0 / 24.0 + t * 0.03;
                sl.sendParticles(VIOLET, cx + Math.cos(a) * 6.0, y0 + 0.1, cz + Math.sin(a) * 6.0, 1, 0, 0, 0, 0);
            }
        }

        // --- four pillars of soul fire
        if (t >= 30) {
            double strength = Math.min(1.0, (t - 30) / 60.0);
            for (int k = 0; k < 4; k++) {
                double a = k * Math.PI / 2.0 + Math.PI / 4.0;
                double px = cx + Math.cos(a) * 5.0;
                double pz = cz + Math.sin(a) * 5.0;
                sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, px, y0 + rnd.nextDouble() * 9.0 * strength, pz, 2, 0.08, 0.3, 0.08, 0.01);
                sl.sendParticles(ParticleTypes.END_ROD, px, y0 + rnd.nextDouble() * 10.0 * strength, pz, 1, 0.05, 0.5, 0.05, 0.02);
                if (t % 3 == 0) {
                    sl.sendParticles(TEAL, px, y0 + rnd.nextDouble() * 8.0 * strength, pz, 2, 0.15, 0.4, 0.15, 0.0);
                }
                // arcs of energy from every pillar to the altar
                if (t % 6 == k) {
                    for (int s = 1; s < 10; s++) {
                        double f = s / 10.0;
                        sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, px + (cx - px) * f, y0 + 4.0 * strength * (1 - f) + 1.0,
                                pz + (cz - pz) * f, 1, 0.05, 0.05, 0.05, 0.0);
                    }
                }
            }
        }

        // --- the octagram
        if (t >= 40 && t % 3 == 0) {
            for (int sq = 0; sq < 2; sq++) {
                double base = sq * Math.PI / 4.0 + t * 0.012 * (sq == 0 ? 1 : -1);
                for (int e = 0; e < 4; e++) {
                    double a0 = base + e * Math.PI / 2.0;
                    double a1 = a0 + Math.PI / 2.0;
                    double f = rnd.nextDouble();
                    double x = cx + (Math.cos(a0) * (1 - f) + Math.cos(a1) * f) * 4.6;
                    double z = cz + (Math.sin(a0) * (1 - f) + Math.sin(a1) * f) * 4.6;
                    sl.sendParticles(TEAL, x, y0 + 0.12, z, 1, 0, 0, 0, 0);
                    sl.sendParticles(ParticleTypes.END_ROD, x, y0 + 0.15, z, 1, 0.02, 0.02, 0.02, 0.0);
                }
            }
        }

        // --- spirals of void sucked into the altar
        if (t >= 50) {
            double k = (t - 50) / 180.0;
            for (int arm = 0; arm < 3; arm++) {
                for (int s = 0; s < 3; s++) {
                    double a = t * 0.28 + arm * 2.094 + s * 0.35;
                    double r = Math.max(0.3, 6.5 * (1.0 - k) - s * 0.25);
                    double h = 0.2 + (r / 6.5) * 1.5 + Math.sin(t * 0.1 + arm) * 0.3;
                    sl.sendParticles(ParticleTypes.REVERSE_PORTAL, cx + Math.cos(a) * r, y0 + h, cz + Math.sin(a) * r, 1, 0, 0, 0, 0.0);
                    if (s == 0) {
                        sl.sendParticles(BLACK, cx + Math.cos(a) * r, y0 + h, cz + Math.sin(a) * r, 1, 0, 0, 0, 0);
                    }
                }
            }
        }

        // --- the altar itself charging up
        sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, cx, y0 + 1.9, cz, 1 + t / 25, 0.6, 0.6, 0.6, 0.2);
        if (t % 5 == 0) {
            sl.sendParticles(ParticleTypes.END_ROD, cx, y0 + 1.9, cz, 2 + t / 30, 0.1, 0.1, 0.1, 0.12 + prog * 0.2);
        }

        // --- cracks opening in the ground
        if (t >= 20 && t < 200 && t % 10 == 0) {
            double a = rnd.nextDouble() * Math.PI * 2.0;
            double r = 1.8 + rnd.nextDouble() * 4.6;
            double sy = RiftSpike.crack(sl, cx + Math.cos(a) * r, y0, cz + Math.sin(a) * r, 2, 70);
            sl.sendParticles(ParticleTypes.REVERSE_PORTAL, cx + Math.cos(a) * r, sy + 0.2, cz + Math.sin(a) * r, 8, 0.4, 0.1, 0.4, 0.1);
        }

        // --- lightning from the sky
        if (t == 120 || t == 150 || t == 175 || t == 195 || t == 210 || t == 222) {
            for (int i = 0; i < 2; i++) {
                double a = rnd.nextDouble() * Math.PI * 2.0;
                double r = 6.0 + rnd.nextDouble() * 4.0;
                bolt(sl, cx + Math.cos(a) * r, y0, cz + Math.sin(a) * r);
            }
        }

        // --- darkness for everybody close to the altar
        if (t >= 150 && t % 20 == 0) {
            for (ServerPlayer p : sl.players()) {
                if (p.distanceToSqr(cx, y0, cz) < 14.0 * 14.0) {
                    p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 50, 0, false, false, false));
                }
            }
        }
    }

    private static void bolt(ServerLevel sl, double x, double y, double z) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(sl);
        if (bolt != null) {
            bolt.moveTo(x, y, z);
            bolt.setVisualOnly(true);
            sl.addFreshEntity(bolt);
        }
    }

    private void finale(ServerLevel sl) {
        active = false;
        RandomSource rnd = sl.getRandom();
        double cx = worldPosition.getX() + 0.5;
        double cz = worldPosition.getZ() + 0.5;
        double y0 = worldPosition.getY();
        long now = sl.getGameTime();
        ServerPlayer starterPlayer = starter == null ? null : sl.getServer().getPlayerList().getPlayer(starter);
        BlockPos pos = worldPosition;

        // the altar is consumed
        sl.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);

        // the tear it leaves behind
        RiftBlock.placeDecorColumn(sl, pos, 6, now + 90, now, rnd);

        // the boss climbs out of it
        RiftReaver boss = ModEntities.RIFT_REAVER.get().create(sl);
        if (boss != null) {
            boss.moveTo(cx, y0 - 3.0, cz, rnd.nextFloat() * 360.0F, 0.0F);
            boss.startEmerge(y0);
            sl.addFreshEntity(boss);
        }

        // the show
        sl.playSound(null, pos, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.BLOCKS, 6.0F, 0.6F);
        sl.playSound(null, pos, SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.BLOCKS, 6.0F, 0.5F);
        sl.playSound(null, pos, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 4.0F, 0.5F);
        sl.sendParticles(ParticleTypes.FLASH, cx, y0 + 1.5, cz, 1, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, cx, y0 + 1.5, cz, 3, 2.0, 1.0, 2.0, 0.0);
        sl.sendParticles(ParticleTypes.REVERSE_PORTAL, cx, y0 + 1.5, cz, 400, 1.5, 2.5, 1.5, 0.5);
        sl.sendParticles(ParticleTypes.END_ROD, cx, y0 + 1.5, cz, 120, 0.3, 0.3, 0.3, 0.9);
        for (int ring = 0; ring < 3; ring++) {
            for (int i = 0; i < 48; i++) {
                double a = i * Math.PI * 2.0 / 48.0;
                sl.sendParticles(VIOLET, cx + Math.cos(a) * (2 + ring * 2), y0 + 0.2, cz + Math.sin(a) * (2 + ring * 2), 1, 0, 0.2, 0, 0);
            }
        }
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3.0;
            bolt(sl, cx + Math.cos(a) * 3.5, y0, cz + Math.sin(a) * 3.5);
        }
        RiftWaves.start(sl, new Vec3(cx, y0 + 1.0, cz), 16.0);
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI * 2.0 / 10.0;
            LivingOwner.burst(sl, starterPlayer, boss, cx + Math.cos(a) * 7.0, y0, cz + Math.sin(a) * 7.0, 2 + rnd.nextInt(6));
        }
        for (ServerPlayer p : sl.players()) {
            if (p.distanceToSqr(cx, y0, cz) < 64.0 * 64.0) {
                p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 20));
                p.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("message.darkrifts.ritual_awakens")
                        .withStyle(ChatFormatting.DARK_PURPLE)));
            }
        }
        setChanged();
    }

    /** Spikes belong to the player who ran the ritual (so they only hurt others), or to the boss if he left. */
    private static final class LivingOwner {
        static void burst(ServerLevel sl, @Nullable ServerPlayer player, @Nullable RiftReaver boss,
                          double x, double y, double z, int delay) {
            net.minecraft.world.entity.LivingEntity owner = player != null ? player : boss;
            if (owner != null) {
                RiftSpike.burst(sl, owner, x, y, z, delay);
            }
        }
    }

    // ------------------------------------------------------------------ client view

    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(9.0, 0.0, 9.0).expandTowards(0.0, 90.0, 0.0);
    }

    // ------------------------------------------------------------------ persistence / sync

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("Active", active);
        tag.putLong("Start", startTime);
        if (starter != null) {
            tag.putUUID("Starter", starter);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        active = tag.getBoolean("Active");
        startTime = tag.getLong("Start");
        starter = tag.hasUUID("Starter") ? tag.getUUID("Starter") : null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
