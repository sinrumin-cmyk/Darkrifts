package com.darkrifts.block;

import com.darkrifts.registry.ModBlockEntities;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/** One heart pedestal. Holds whether a Reaver Heart has been inserted; the renderer shows it floating and burning. */
public class SanctumTowerBlockEntity extends BlockEntity {
    private static final DustParticleOptions PINK = new DustParticleOptions(new Vector3f(1.0F, 0.35F, 0.9F), 1.4F);
    private boolean filled;
    private long filledAt;

    public SanctumTowerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SANCTUM_TOWER.get(), pos, state);
    }

    public boolean isFilled() {
        return filled;
    }

    public long getFilledAt() {
        return filledAt;
    }

    public void setFilled(boolean value) {
        this.filled = value;
        if (level != null) {
            this.filledAt = level.getGameTime();
        }
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void insertHeart(ServerPlayer player, ItemStack heart) {
        if (filled || !(level instanceof ServerLevel sl)) {
            return;
        }
        if (!player.getAbilities().instabuild) {
            heart.shrink(1);
        }
        setFilled(true);
        Vec3 c = Vec3.atCenterOf(worldPosition);
        sl.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 3.0F, 0.6F);
        sl.playSound(null, worldPosition, SoundEvents.WARDEN_HEARTBEAT, SoundSource.BLOCKS, 4.0F, 0.7F);
        sl.playSound(null, worldPosition, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.BLOCKS, 4.0F, 0.8F);
        RiftFx.bolt(sl, c.x, c.y, c.z);
        sl.sendParticles(ParticleTypes.FLASH, c.x, c.y + 1.2, c.z, 1, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 1.0, c.z, 1, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y + 1.2, c.z, 120, 0.8, 1.2, 0.8, 0.5);
        sl.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 1.2, c.z, 60, 0.2, 0.4, 0.2, 0.7);
        for (int i = 0; i < 40; i++) {
            double a = i * Math.PI * 2.0 / 40.0;
            sl.sendParticles(PINK, c.x + Math.cos(a) * 2.2, c.y - 0.4, c.z + Math.sin(a) * 2.2, 1, 0, 0.1, 0, 0);
        }
        RiftWaves.start(sl, c.add(0, 1.0, 0), 9.0);
        RiftFx.screen(sl, c, 48.0, 1.0F, 0.5F, 0xFFB0F0);
        int n = countFilled(sl);
        for (ServerPlayer p : sl.players()) {
            if (p.distanceToSqr(c) < 60.0 * 60.0) {
                p.displayClientMessage(Component.translatable("message.darkrifts.tower_filled", n)
                        .withStyle(ChatFormatting.LIGHT_PURPLE), true);
            }
        }
        if (n == 4) {
            sl.playSound(null, worldPosition, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.BLOCKS, 8.0F, 0.5F);
            for (ServerPlayer p : sl.players()) {
                if (p.distanceToSqr(c) < 80.0 * 80.0) {
                    p.displayClientMessage(Component.translatable("message.darkrifts.towers_ready")
                            .withStyle(ChatFormatting.DARK_PURPLE), false);
                }
            }
        }
    }

    /** How many of the four pedestals around the core currently hold a heart. */
    public int countFilled(ServerLevel sl) {
        MirrorSanctum.Data d = MirrorSanctum.data(sl);
        if (!d.built) {
            return filled ? 1 : 0;
        }
        int n = 0;
        for (BlockPos p : MirrorSanctum.pedestals(MirrorSanctum.corePos(d.y0))) {
            if (sl.getBlockEntity(p) instanceof SanctumTowerBlockEntity t && t.filled) {
                n++;
            }
        }
        return n;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SanctumTowerBlockEntity be) {
        if (!be.filled || !(level instanceof ServerLevel sl) || level.getGameTime() % 5 != 0) {
            return;
        }
        double x = pos.getX() + 0.5;
        double z = pos.getZ() + 0.5;
        double t = level.getGameTime() * 0.1;
        // a column of light and souls climbing the shaft, with a spiral around it
        for (int i = 0; i < 3; i++) {
            double h = sl.getRandom().nextDouble() * 30.0;
            double a = t + h * 0.4 + i * 2.1;
            sl.sendParticles(ParticleTypes.END_ROD, x + Math.cos(a) * 0.9, pos.getY() + 1.0 + h, z + Math.sin(a) * 0.9, 1, 0, 0.05, 0, 0.01);
        }
        sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, pos.getY() + 1.4, z, 2, 0.15, 0.2, 0.15, 0.02);
        sl.sendParticles(PINK, x, pos.getY() + 1.4, z, 2, 0.3, 0.3, 0.3, 0.0);
        if (level.getGameTime() % 40 == 0) {
            sl.playSound(null, pos, SoundEvents.WARDEN_HEARTBEAT, SoundSource.BLOCKS, 1.5F, 0.8F);
        }
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(4.0, 0.0, 4.0).expandTowards(0.0, 40.0, 0.0);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("Filled", filled);
        tag.putLong("FilledAt", filledAt);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        filled = tag.getBoolean("Filled");
        filledAt = tag.getLong("FilledAt");
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
