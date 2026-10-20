package com.darkrifts.block;

import com.darkrifts.DarkDimensions;
import com.darkrifts.entity.MirrorSovereign;
import com.darkrifts.entity.RiftSpike;
import com.darkrifts.registry.ModBlockEntities;
import com.darkrifts.registry.ModEntities;
import com.darkrifts.world.MirrorSanctum;
import com.darkrifts.world.RiftFx;
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
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * The awakening of the endgame boss (320 ticks, everything a function of t):
 *   0-90    a colossal rune circle burns into the plateau, the octagram lights up
 *   0-320   the four towers throw arcs of lightning at the core, beads of energy run along the four spokes
 *   50-320  a column of light rises, void spirals are sucked into the core, the ground cracks, the camera shakes
 *   100-320 lightning, heartbeat, darkness for those in the arena
 *   320     the core shatters, a gigantic rift tears open in the sky and the Mirror Sovereign climbs out of it
 * The client draws the orb, rings, spokes and beams in SanctumCoreRenderer from {@link #getStartTime()}.
 */
public class SanctumCoreBlockEntity extends BlockEntity {
    public static final int DURATION = 320;
    private static final DustParticleOptions VIOLET = new DustParticleOptions(new Vector3f(0.65F, 0.2F, 1.0F), 1.6F);
    private static final DustParticleOptions TEAL = new DustParticleOptions(new Vector3f(0.2F, 0.9F, 0.95F), 1.2F);
    private static final DustParticleOptions BLACK = new DustParticleOptions(new Vector3f(0.05F, 0.0F, 0.1F), 2.4F);
    private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(1.0F, 0.15F, 0.2F), 1.8F);

    private boolean awakening;
    private long startTime;

    public SanctumCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SANCTUM_CORE.get(), pos, state);
    }

    public boolean isAwakening() {
        return awakening;
    }

    public long getStartTime() {
        return startTime;
    }

    // ------------------------------------------------------------------ starting

    public void tryAwaken(ServerPlayer player, ItemStack egg) {
        if (!(level instanceof ServerLevel sl)) {
            return;
        }
        if (awakening) {
            return;
        }
        if (!DarkDimensions.isDark(sl.dimension())) {
            return;
        }
        BlockPos[] pedestals = MirrorSanctum.pedestals(worldPosition);
        int n = 0;
        for (BlockPos p : pedestals) {
            if (sl.getBlockEntity(p) instanceof SanctumTowerBlockEntity t && t.isFilled()) {
                n++;
            }
        }
        if (n < 4) {
            player.displayClientMessage(Component.translatable("message.darkrifts.core_silent", n)
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!sl.getEntitiesOfClass(MirrorSovereign.class, new AABB(worldPosition).inflate(160.0)).isEmpty()) {
            player.displayClientMessage(Component.translatable("message.darkrifts.sovereign_awake")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!player.getAbilities().instabuild) {
            egg.shrink(1);
        }
        awakening = true;
        startTime = sl.getGameTime();
        setChanged();
        sl.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        sl.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 6.0F, 0.4F);
        sl.playSound(null, worldPosition, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 5.0F, 0.5F);
        sl.playSound(null, worldPosition, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.BLOCKS, 8.0F, 0.5F);
        for (ServerPlayer p : sl.players()) {
            if (p.distanceToSqr(Vec3.atCenterOf(worldPosition)) < 90.0 * 90.0) {
                p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
                p.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("message.darkrifts.sanctum_begin")
                        .withStyle(ChatFormatting.DARK_RED)));
                p.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("message.darkrifts.sanctum_begin.sub")
                        .withStyle(ChatFormatting.GRAY)));
            }
        }
    }

    // ------------------------------------------------------------------ the show

    public static void serverTick(Level level, BlockPos pos, BlockState state, SanctumCoreBlockEntity be) {
        if (!be.awakening || !(level instanceof ServerLevel sl)) {
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
        double cy = worldPosition.getY();
        double gy = cy - 2.0 + 0.15;                    // plateau surface
        double prog = t / (double) DURATION;
        Vec3 orb = new Vec3(cx, cy + 3.0, cz);

        // --- sound
        if (t % 20 == 0 && t < 300) {
            sl.playSound(null, worldPosition, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 5.0F, (float) (0.5 + prog));
            sl.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 5.0F, (float) (0.5 + prog));
        }
        if (t >= 80 && t % (t < 200 ? 24 : 10) == 0) {
            sl.playSound(null, worldPosition, SoundEvents.WARDEN_HEARTBEAT, SoundSource.BLOCKS, 6.0F, (float) (0.6 + prog * 0.4));
        }
        if (t == 60 || t == 140 || t == 220 || t == 285) {
            sl.playSound(null, worldPosition, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.BLOCKS, 8.0F, 0.45F);
        }
        if (t == 300) {
            sl.playSound(null, worldPosition, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.BLOCKS, 8.0F, 0.6F);
        }

        // --- colossal rune circle, rings and the octagram
        if (t % 3 == 0) {
            double spread = Math.min(1.0, t / 90.0);
            ringDust(sl, VIOLET, cx, cz, gy, 22.0 * spread, 88, t * 0.02);
            ringDust(sl, TEAL, cx, cz, gy + 0.02, 16.9 * spread, 64, -t * 0.03);
            ringDust(sl, VIOLET, cx, cz, gy + 0.02, 9.8 * spread, 40, t * 0.04);
            for (int e = 0; e < 8; e++) {
                double a0 = e * Math.PI / 4.0 + Math.PI / 8.0;
                double a1 = ((e + 3) % 8) * Math.PI / 4.0 + Math.PI / 8.0;
                double f = rnd.nextDouble();
                double x = cx + (Math.cos(a0) * (1 - f) + Math.cos(a1) * f) * 22.5 * spread;
                double z = cz + (Math.sin(a0) * (1 - f) + Math.sin(a1) * f) * 22.5 * spread;
                sl.sendParticles(ParticleTypes.END_ROD, x, gy + 0.1, z, 1, 0.02, 0.05, 0.02, 0.0);
                sl.sendParticles(TEAL, x, gy + 0.1, z, 1, 0, 0, 0, 0);
            }
        }

        // --- the four towers hurl lightning at the core; energy beads run along the spokes
        for (int k = 0; k < 4; k++) {
            int[] d = {k == 1 ? 1 : k == 3 ? -1 : 0, k == 0 ? -1 : k == 2 ? 1 : 0};
            double tx = cx + d[0] * MirrorSanctum.TOWER_DIST;
            double tz = cz + d[1] * MirrorSanctum.TOWER_DIST;
            double top = cy + 34.0;
            sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, tx, top + 0.5, tz, 2, 0.15, 0.3, 0.15, 0.03);
            sl.sendParticles(ParticleTypes.END_ROD, tx, top + 1.0 + rnd.nextDouble() * 6.0 * Math.min(1.0, prog * 2), tz, 1, 0.05, 0.3, 0.05, 0.02);
            if (t % 4 == k) {
                for (int s = 1; s < 16; s++) {
                    double f = s / 16.0;
                    double sag = Math.sin(f * Math.PI) * 4.0 * (1.0 - prog * 0.5);
                    sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, tx + (orb.x - tx) * f + (rnd.nextDouble() - 0.5) * 0.5,
                            top + (orb.y - top) * f - sag + (rnd.nextDouble() - 0.5) * 0.5,
                            tz + (orb.z - tz) * f + (rnd.nextDouble() - 0.5) * 0.5, 1, 0, 0, 0, 0);
                }
            }
            double bead = (t * 0.06 + k * 0.25) % 1.0;
            double bx = tx + (cx - tx) * bead;
            double bz = tz + (cz - tz) * bead;
            sl.sendParticles(ParticleTypes.END_ROD, bx, gy + 0.2, bz, 2, 0.05, 0.05, 0.05, 0.0);
            sl.sendParticles(TEAL, bx, gy + 0.3, bz, 1, 0.1, 0.05, 0.1, 0.0);
        }

        // --- void spirals pulled into the orb
        if (t >= 30) {
            double k = Math.min(1.0, (t - 30) / 250.0);
            for (int arm = 0; arm < 4; arm++) {
                for (int s = 0; s < 3; s++) {
                    double a = t * 0.24 + arm * Math.PI / 2.0 + s * 0.3;
                    double r = Math.max(0.5, 22.0 * (1.0 - k) - s * 0.5);
                    double h = 0.3 + (r / 22.0) * 4.0 + Math.sin(t * 0.1 + arm) * 0.4;
                    sl.sendParticles(ParticleTypes.REVERSE_PORTAL, cx + Math.cos(a) * r, gy + h, cz + Math.sin(a) * r, 1, 0, 0, 0, 0);
                    if (s == 0) {
                        sl.sendParticles(BLACK, cx + Math.cos(a) * r, gy + h, cz + Math.sin(a) * r, 1, 0, 0, 0, 0);
                    }
                }
            }
        }

        // --- the orb charging, the column of light
        sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, orb.x, orb.y, orb.z, 2 + t / 20, 0.9, 0.9, 0.9, 0.25);
        if (t % 4 == 0) {
            sl.sendParticles(ParticleTypes.END_ROD, orb.x, orb.y, orb.z, 3 + t / 25, 0.2, 0.2, 0.2, 0.15 + prog * 0.3);
        }
        if (t >= 50 && t % 2 == 0) {
            for (int i = 0; i < 8; i++) {
                sl.sendParticles(ParticleTypes.END_ROD, cx + (rnd.nextDouble() - 0.5) * 1.2, orb.y + rnd.nextDouble() * 60.0,
                        cz + (rnd.nextDouble() - 0.5) * 1.2, 1, 0, 0.1, 0, 0.0);
            }
            sl.sendParticles(RED, cx, orb.y + 2.0 + rnd.nextDouble() * 20.0, cz, 2, 0.5, 0.3, 0.5, 0.0);
        }

        // --- cracks in the plateau
        if (t >= 20 && t < 280 && t % 5 == 0) {
            double a = rnd.nextDouble() * Math.PI * 2.0;
            double r = 4.0 + rnd.nextDouble() * 20.0;
            double sy = RiftSpike.crack(sl, cx + Math.cos(a) * r, gy, cz + Math.sin(a) * r, 1, 90);
            sl.sendParticles(ParticleTypes.REVERSE_PORTAL, cx + Math.cos(a) * r, sy + 0.2, cz + Math.sin(a) * r, 8, 0.4, 0.1, 0.4, 0.1);
        }

        // --- lightning
        if (t >= 100 && ((t < 250 && t % 30 == 0) || (t >= 250 && t % 8 == 0))) {
            double a = rnd.nextDouble() * Math.PI * 2.0;
            double r = 5.0 + rnd.nextDouble() * 20.0;
            RiftFx.bolt(sl, cx + Math.cos(a) * r, gy, cz + Math.sin(a) * r);
        }

        // --- the world shakes harder and harder; darkness in the arena
        if (t % 5 == 0) {
            RiftFx.screen(sl, orb, 90.0, 0.25F + (float) prog * 1.5F, t > 290 ? 0.5F : 0.0F, 0xFFB0B0);
        }
        if (t >= 220 && t % 20 == 0) {
            for (ServerPlayer p : sl.players()) {
                if (p.distanceToSqr(cx, cy, cz) < 28.0 * 28.0) {
                    p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 50, 0, false, false, false));
                }
            }
        }
    }

    private static void ringDust(ServerLevel sl, DustParticleOptions dust, double cx, double cz, double y, double r,
                                 int n, double rot) {
        if (r < 0.3) {
            return;
        }
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2.0 / n + rot;
            sl.sendParticles(dust, cx + Math.cos(a) * r, y, cz + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
    }

    /** The core shatters, the sky tears, the Sovereign comes out of it. */
    private void finale(ServerLevel sl) {
        awakening = false;
        RandomSource rnd = sl.getRandom();
        double cx = worldPosition.getX() + 0.5;
        double cz = worldPosition.getZ() + 0.5;
        double ground = worldPosition.getY();
        BlockPos pos = worldPosition;

        sl.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);   // the altar is consumed (it comes back when the Sovereign dies)

        MirrorSovereign boss = ModEntities.MIRROR_SOVEREIGN.get().create(sl);
        if (boss != null) {
            double riftY = ground + MirrorSovereign.RIFT_UP;
            boss.moveTo(cx, riftY + MirrorSovereign.START_UP, cz, 0.0F, 0.0F);
            boss.setCore(pos);
            boss.startEmerge(ground + MirrorSovereign.HOVER, riftY);
            sl.addFreshEntity(boss);
        }

        sl.playSound(null, pos, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.BLOCKS, 10.0F, 0.5F);
        sl.playSound(null, pos, SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.BLOCKS, 10.0F, 0.4F);
        sl.playSound(null, pos, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 8.0F, 0.4F);
        sl.sendParticles(ParticleTypes.FLASH, cx, ground + 3.0, cz, 1, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, cx, ground + 2.0, cz, 6, 4.0, 2.0, 4.0, 0.0);
        sl.sendParticles(ParticleTypes.REVERSE_PORTAL, cx, ground + 3.0, cz, 600, 3.0, 4.0, 3.0, 0.6);
        sl.sendParticles(ParticleTypes.END_ROD, cx, ground + 3.0, cz, 240, 0.4, 0.4, 0.4, 1.2);
        for (int ring = 0; ring < 5; ring++) {
            ringDust(sl, ring % 2 == 0 ? VIOLET : RED, cx, cz, ground - 1.8, 3.0 + ring * 4.0, 60 + ring * 12, 0.0);
        }
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6.0;
            RiftFx.bolt(sl, cx + Math.cos(a) * 8.0, ground, cz + Math.sin(a) * 8.0);
        }
        RiftWaves.start(sl, new Vec3(cx, ground + 1.0, cz), 22.0);
        RiftFx.screen(sl, new Vec3(cx, ground, cz), 100.0, 3.0F, 1.0F, 0xFFFFFF);
        for (int i = 0; i < 14; i++) {
            double a = rnd.nextDouble() * Math.PI * 2.0;
            double r = 4.0 + rnd.nextDouble() * 18.0;
            RiftSpike.crack(sl, cx + Math.cos(a) * r, ground - 2.0, cz + Math.sin(a) * r, 0, 120);
        }
        setChanged();
    }

    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(30.0, 0.0, 30.0).expandTowards(0.0, 70.0, 0.0).expandTowards(0.0, -4.0, 0.0);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("Awakening", awakening);
        tag.putLong("StartTime", startTime);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        awakening = tag.getBoolean("Awakening");
        startTime = tag.getLong("StartTime");
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
