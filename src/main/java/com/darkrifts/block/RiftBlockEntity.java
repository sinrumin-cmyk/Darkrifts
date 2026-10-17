package com.darkrifts.block;

import com.darkrifts.DarkDimensions;
import com.darkrifts.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class RiftBlockEntity extends BlockEntity {
    @Nullable
    private ResourceLocation target;
    private boolean returning;
    /** Game time at which the rift collapses; 0 = permanent. */
    private long expireAt;

    public RiftBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RIFT.get(), pos, state);
    }

    public void configure(@Nullable ResourceLocation target, boolean returning, long expireAt) {
        this.target = target;
        this.returning = returning;
        this.expireAt = expireAt;
        setChanged();
    }

    public boolean isReturning() {
        return returning;
    }
    /** Lazily rolls a random dark dimension for rifts that were placed without one. */
    public ResourceLocation getOrPickTarget(Level level) {
        if (target == null) {
            target = DarkDimensions.random(level.getRandom(), level.dimension());
            setChanged();
        }
        return target;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RiftBlockEntity be) {
        if (be.expireAt > 0 && level.getGameTime() >= be.expireAt) {
            level.removeBlock(pos, false);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (target != null) {
            tag.putString("Target", target.toString());
        }
        tag.putBoolean("Returning", returning);
        tag.putLong("ExpireAt", expireAt);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        target = tag.contains("Target") ? ResourceLocation.tryParse(tag.getString("Target")) : null;
        returning = tag.getBoolean("Returning");
        expireAt = tag.getLong("ExpireAt");
    }
}
