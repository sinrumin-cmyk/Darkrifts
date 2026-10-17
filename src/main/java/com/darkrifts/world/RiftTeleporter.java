package com.darkrifts.world;

import com.darkrifts.DarkDimensions;
import com.darkrifts.block.RiftBlock;
import com.darkrifts.block.RiftBlockEntity;
import com.darkrifts.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;

public final class RiftTeleporter {
    private static final String ORIGIN_TAG = "darkrifts_origin";
    private static final int COOLDOWN_TICKS = 120;

    private RiftTeleporter() {}

    /** Called when an entity walks into a rift block. */
    public static void useRift(Entity entity, ServerLevel from, BlockPos riftPos, RiftBlockEntity rift) {
        if (!entity.isAlive() || entity.isRemoved() || entity.level() != from) {
            return;
        }
        if (rift.isReturning()) {
            goHome(entity, from);
        } else {
            travelTo(entity, from, rift.getOrPickTarget(from), riftPos);
        }
    }

    /** Sends the entity into the given dark dimension, building a return rift at the arrival point. */
    public static void travelTo(Entity entity, ServerLevel from, ResourceLocation targetId, BlockPos riftPos) {
        MinecraftServer server = from.getServer();
        ServerLevel dest = server.getLevel(DarkDimensions.key(targetId));
        if (dest == null) {
            if (entity instanceof ServerPlayer player) {
                player.displayClientMessage(Component.translatable("message.darkrifts.dimension_missing", targetId.toString()), true);
            }
            return;
        }
        // Remember where the journey started (only for the first hop, so chained rifts still lead home).
        if (!DarkDimensions.isDark(from.dimension())) {
            saveOrigin(entity, from);
        }

        RandomSource random = dest.getRandom();
        BlockPos base = new BlockPos(riftPos.getX(), 0, riftPos.getZ());
        BlockPos arrival = findArrival(dest, base, random);
        float yaw = placeReturnRift(dest, arrival, random);

        from.playSound(null, entity.blockPosition(), SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.4F, 1.4F);
        Vec3 pos = new Vec3(arrival.getX() + 0.5, arrival.getY(), arrival.getZ() + 0.5);
        move(entity, dest, pos, yaw);
    }

    /** Sends the entity back to where it entered the dark realm (or the overworld spawn). */
    public static void goHome(Entity entity, ServerLevel from) {
        MinecraftServer server = from.getServer();
        CompoundTag tag = entity.getPersistentData().getCompound(ORIGIN_TAG);

        ServerLevel dest = null;
        Vec3 pos = null;
        float yaw = entity.getYRot();
        if (tag.contains("dim")) {
            ResourceLocation id = ResourceLocation.tryParse(tag.getString("dim"));
            if (id != null) {
                dest = server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
            }
            if (dest != null) {
                pos = new Vec3(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"));
                yaw = tag.getFloat("yaw");
            }
        }
        if (dest == null) {
            dest = server.overworld();
            BlockPos spawn = dest.getSharedSpawnPos();
            dest.getChunk(spawn.getX() >> 4, spawn.getZ() >> 4);
            BlockPos top = dest.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn);
            pos = Vec3.atBottomCenterOf(top);
        } else {
            dest.getChunk(Mth.floor(pos.x) >> 4, Mth.floor(pos.z) >> 4);
        }
        from.playSound(null, entity.blockPosition(), SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.4F, 1.0F);
        move(entity, dest, pos, yaw);
    }

    private static void move(Entity entity, ServerLevel dest, Vec3 pos, float yaw) {
        DimensionTransition transition = new DimensionTransition(dest, pos, Vec3.ZERO, yaw, 0.0F,
                e -> e.setPortalCooldown(COOLDOWN_TICKS));
        entity.changeDimension(transition);
    }

    private static void saveOrigin(Entity entity, ServerLevel from) {
        BlockPos ground = groundBelow(from, entity.blockPosition());
        CompoundTag tag = new CompoundTag();
        tag.putString("dim", from.dimension().location().toString());
        tag.putDouble("x", ground.getX() + 0.5);
        tag.putDouble("y", ground.getY());
        tag.putDouble("z", ground.getZ() + 0.5);
        tag.putFloat("yaw", entity.getYRot());
        entity.getPersistentData().put(ORIGIN_TAG, tag);
    }

    /** Finds the first standable spot under (or at) the given position, so we never return to mid-air. */
    private static BlockPos groundBelow(ServerLevel level, BlockPos start) {
        int minY = level.getMinBuildHeight() + 1;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(start.getX(), Math.min(start.getY(), level.getMaxBuildHeight() - 2), start.getZ());
        for (int y = p.getY(); y >= minY; y--) {
            p.setY(y);
            if (isStandable(level, p)) {
                return p.immutable();
            }
        }
        return start;
    }

    private static boolean isStandable(ServerLevel level, BlockPos feet) {
        BlockState feetState = level.getBlockState(feet);
        BlockState headState = level.getBlockState(feet.above());
        if (!feetState.isAir() || !headState.isAir()) {
            return false;
        }
        BlockPos below = feet.below();
        BlockState floor = level.getBlockState(below);
        return floor.isFaceSturdy(level, below, Direction.UP)
                && floor.getFluidState().isEmpty()
                && !floor.is(BlockTags.LEAVES)
                && !floor.is(Blocks.MAGMA_BLOCK);
    }

    /**
     * Looks for a safe place to arrive near {@code base}. Generates chunks as needed.
     * Falls back to a small blackstone platform if the dimension has no usable ground (void, ocean...).
     */
    public static BlockPos findArrival(ServerLevel level, BlockPos base, RandomSource random) {
        int minY = level.getMinBuildHeight() + 2;
        int maxY = level.getMaxBuildHeight() - 3;

        for (int attempt = 0; attempt < 12; attempt++) {
            int range = attempt < 4 ? 6 : 48;
            int x = base.getX() + random.nextInt(range * 2 + 1) - range;
            int z = base.getZ() + random.nextInt(range * 2 + 1) - range;
            level.getChunk(x >> 4, z >> 4);

            int top = maxY;
            if (!level.dimensionType().hasCeiling()) {
                top = Math.min(maxY, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 1);
            }
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, top, z);
            for (int y = top; y >= minY; y--) {
                p.setY(y);
                if (isStandable(level, p)) {
                    return p.immutable();
                }
            }
        }

        // Fallback platform
        int y = Mth.clamp(level.getSeaLevel() + 10, minY + 8, maxY - 8);
        BlockPos center = new BlockPos(base.getX(), y, base.getZ());
        level.getChunk(center.getX() >> 4, center.getZ() >> 4);
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                BlockPos floor = center.offset(dx, -1, dz);
                level.setBlock(floor, (Math.abs(dx) + Math.abs(dz)) % 5 == 0
                        ? Blocks.GILDED_BLACKSTONE.defaultBlockState()
                        : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), 3);
                for (int dy = 0; dy < 4; dy++) {
                    BlockPos air = center.offset(dx, dy, dz);
                    if (level.getBlockState(air).getDestroySpeed(level, air) >= 0) {
                        level.setBlock(air, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
        return center;
    }

    /**
     * Builds a small permanent return rift a few blocks from the arrival point.
     * Returns the yaw the arriving entity should have (looking away from the rift).
     */
    private static float placeReturnRift(ServerLevel level, BlockPos arrival, RandomSource random) {
        Direction dir = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        BlockPos base = arrival.relative(dir, 3);
        level.getChunk(base.getX() >> 4, base.getZ() >> 4);

        if (!hasRiftNearby(level, base)) {
            for (int i = 0; i < 4; i++) {
                BlockPos p = base.above(i);
                BlockState existing = level.getBlockState(p);
                if (existing.getDestroySpeed(level, p) < 0) {
                    continue; // bedrock etc.
                }
                RiftBlock.place(level, p, null, true, 0L);
            }
            // Ground the rift so it doesn't float over a hole.
            BlockPos under = base.below();
            if (level.getBlockState(under).canBeReplaced()) {
                level.setBlock(under, Blocks.POLISHED_BLACKSTONE.defaultBlockState(), 3);
            }
            level.playSound(null, base, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 0.3F, 1.8F);
        }
        return dir.getOpposite().toYRot();
    }

    private static boolean hasRiftNearby(ServerLevel level, BlockPos center) {
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-5, -3, -5), center.offset(5, 5, 5))) {
            if (level.getBlockState(p).is(ModBlocks.RIFT.get())) {
                return true;
            }
        }
        return false;
    }
}
