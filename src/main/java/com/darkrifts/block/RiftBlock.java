package com.darkrifts.block;

import com.darkrifts.registry.ModBlockEntities;
import com.darkrifts.registry.ModBlocks;
import com.darkrifts.world.RiftTeleporter;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.TickTask;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class RiftBlock extends BaseEntityBlock {
    public static final MapCodec<RiftBlock> CODEC = simpleCodec(RiftBlock::new);
    private static final VoxelShape SHAPE = box(2, 2, 2, 14, 14, 14);

    public RiftBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    /** Places a rift block together with its block entity data. Works for worldgen levels too. */
    public static void place(LevelAccessor level, BlockPos pos, @Nullable ResourceLocation target,
                             boolean returning, long expireAt) {
        level.setBlock(pos, ModBlocks.RIFT.get().defaultBlockState(), 2);
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof RiftBlockEntity rift) {
            rift.configure(target, returning, expireAt);
        }
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        // The visible crack is drawn by RiftRenderer (block entity renderer), not by a block model.
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacentState, Direction direction) {
        return adjacentState.is(this);
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RiftBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModBlockEntities.RIFT.get(), RiftBlockEntity::serverTick);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        // Hand-placed rifts (creative) lead to a random dark dimension, permanently.
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof RiftBlockEntity rift) {
            rift.getOrPickTarget(level);
        }
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!entity.isAlive() || entity.isPassenger() || entity.isVehicle()
                || entity.isOnPortalCooldown() || !entity.canUsePortal(false)) {
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof RiftBlockEntity rift)) {
            return;
        }
        // Block re-triggering while the (deferred) teleport is pending.
        entity.setPortalCooldown(60);
        var server = serverLevel.getServer();
        server.tell(new TickTask(server.getTickCount() + 1,
                () -> RiftTeleporter.useRift(entity, serverLevel, pos, rift)));
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.8;
        double y = pos.getY() + 0.5 + (random.nextDouble() - 0.5) * 0.8;
        double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.8;
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.REVERSE_PORTAL, x, y, z,
                    (random.nextDouble() - 0.5) * 0.1, (random.nextDouble() - 0.5) * 0.1, (random.nextDouble() - 0.5) * 0.1);
        }
        if (random.nextInt(8) == 0) {
            level.addParticle(ParticleTypes.SQUID_INK, x, y, z, 0.0, 0.01, 0.0);
        }
        if (random.nextInt(24) == 0) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, 0.0, 0.0, 0.0);
        }
    }
}
