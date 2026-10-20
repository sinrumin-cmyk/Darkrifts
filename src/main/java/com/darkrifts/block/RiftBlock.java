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

    /**
     * Joins already placed rift cells (in order) with one continuous, slightly ragged line.
     * Used by commands, natural rifts and return rifts; the Rift Blade draws its own line live.
     */
    public static void linkCells(LevelAccessor level, java.util.List<BlockPos> cells, long birth, RandomSource random) {
        int n = cells.size();
        if (n == 0) {
            return;
        }
        net.minecraft.world.phys.Vec3[] c = new net.minecraft.world.phys.Vec3[n];
        for (int i = 0; i < n; i++) {
            BlockPos q = cells.get(i);
            c[i] = new net.minecraft.world.phys.Vec3(
                    q.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.4,
                    q.getY() + 0.5,
                    q.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.4);
        }
        net.minecraft.world.phys.Vec3 dir = n > 1 ? c[n - 1].subtract(c[0]).normalize()
                : new net.minecraft.world.phys.Vec3(0, 1, 0);
        net.minecraft.world.phys.Vec3 start = c[0].subtract(dir.scale(0.55));
        net.minecraft.world.phys.Vec3 end = c[n - 1].add(dir.scale(0.55));
        java.util.List<net.minecraft.world.phys.Vec3[]> pieces = new java.util.ArrayList<>();
        java.util.List<BlockPos> owners = new java.util.ArrayList<>();
        pieces.add(new net.minecraft.world.phys.Vec3[]{start, c[0]});
        owners.add(cells.get(0));
        for (int i = 1; i < n; i++) {
            pieces.add(new net.minecraft.world.phys.Vec3[]{c[i - 1], c[i]});
            owners.add(cells.get(i - 1));
        }
        pieces.add(new net.minecraft.world.phys.Vec3[]{c[n - 1], end});
        owners.add(cells.get(n - 1));
        double total = 0.0;
        for (net.minecraft.world.phys.Vec3[] pc : pieces) {
            total += pc[0].distanceTo(pc[1]);
        }
        double u = 0.0;
        for (int i = 0; i < pieces.size(); i++) {
            net.minecraft.world.phys.Vec3[] pc = pieces.get(i);
            addSegment(level, owners.get(i), pc[0], pc[1], birth, u, total);
            u += pc[0].distanceTo(pc[1]);
        }
    }

    /**
     * Opens a temporary, purely visual rift through the given cells (only air cells are used), in the given order.
     * It looks like a real rift but never teleports anyone. Returns how many cells were placed.
     */
    public static int placeDecorCells(LevelAccessor level, java.util.List<BlockPos> candidates, long expireAt,
                                      long birth, RandomSource random) {
        java.util.List<BlockPos> cells = new java.util.ArrayList<>();
        for (BlockPos p : candidates) {
            if (!level.getBlockState(p).isAir()) {
                continue;
            }
            place(level, p, null, false, expireAt);
            if (level.getBlockEntity(p) instanceof RiftBlockEntity rift) {
                rift.setDecor(true);
            }
            cells.add(p);
        }
        linkCells(level, cells, birth, random);
        return cells.size();
    }

    /** A temporary visual rift standing on {@code base}, {@code height} cells tall. */
    public static void placeDecorColumn(LevelAccessor level, BlockPos base, int height, long expireAt, long birth,
                                        RandomSource random) {
        java.util.List<BlockPos> cells = new java.util.ArrayList<>();
        for (int i = 0; i < height; i++) {
            cells.add(base.above(i));
        }
        placeDecorCells(level, cells, expireAt, birth, random);
    }

    /**
     * A temporary crack lying flat ON the top face of a block. {@code cell} is the air cell above the block,
     * {@code y} the exact height of the surface, {@code yaw} the direction of the crack (radians).
     */
    public static void placeGroundCrack(LevelAccessor level, BlockPos cell, double y, double yaw, double length,
                                        long expireAt, long birth, RandomSource random) {
        if (!level.getBlockState(cell).isAir()) {
            return;
        }
        place(level, cell, null, false, expireAt);
        if (!(level.getBlockEntity(cell) instanceof RiftBlockEntity rift)) {
            return;
        }
        rift.setDecor(true);
        rift.setFlat(true);
        double cx = cell.getX() + 0.5;
        double cz = cell.getZ() + 0.5;
        double dx = Math.cos(yaw);
        double dz = Math.sin(yaw);
        double h = length / 2.0;
        double fy = y + 0.06;
        net.minecraft.world.phys.Vec3 a = new net.minecraft.world.phys.Vec3(cx - dx * h, fy, cz - dz * h);
        net.minecraft.world.phys.Vec3 b = new net.minecraft.world.phys.Vec3(cx + dx * h, fy, cz + dz * h);
        double kink = (random.nextDouble() - 0.5) * 0.3;
        net.minecraft.world.phys.Vec3 m = new net.minecraft.world.phys.Vec3(cx - dz * kink, fy, cz + dx * kink);
        rift.addSegment(a.x, a.y, a.z, m.x, m.y, m.z, birth, 0.0, length);
        rift.addSegment(m.x, m.y, m.z, b.x, b.y, b.z, birth, length / 2.0, length);
    }

    /** A permanent vertical tear: {@code height} cells going up from {@code base}. */
    public static void placeColumn(LevelAccessor level, BlockPos base, int height, @Nullable ResourceLocation target,
                                   boolean returning, long expireAt, long birth, RandomSource random) {
        java.util.List<BlockPos> cells = new java.util.ArrayList<>();
        for (int i = 0; i < height; i++) {
            BlockPos p = base.above(i);
            if (level.getBlockState(p).getDestroySpeed(level, p) < 0) {
                continue;
            }
            place(level, p, target, returning, expireAt);
            cells.add(p);
        }
        linkCells(level, cells, birth, random);
    }

    /** Stores a piece of the visible tear line in the rift block at {@code pos}. */
    public static void addSegment(LevelAccessor level, BlockPos pos, net.minecraft.world.phys.Vec3 a,
                                  net.minecraft.world.phys.Vec3 b, long birth, double u, double total) {
        if (level.getBlockEntity(pos) instanceof RiftBlockEntity rift) {
            rift.addSegment(a.x, a.y, a.z, b.x, b.y, b.z, birth, u, total);
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
        if (rift.isDecor()) {
            return; // combat rifts never teleport (and must not put entities on portal cooldown)
        }
        // Block re-triggering while the (deferred) teleport is pending.
        entity.setPortalCooldown(60);
        var server = serverLevel.getServer();
        server.tell(new TickTask(server.getTickCount() + 1,
                () -> RiftTeleporter.useRift(entity, serverLevel, pos, rift)));
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;
        double x = cx + (random.nextDouble() - 0.5) * 0.8;
        double y = cy + (random.nextDouble() - 0.5) * 0.8;
        double z = cz + (random.nextDouble() - 0.5) * 0.8;
        double t = level.getGameTime() * 0.11;

        // void matter spiralling inwards, like the arms of the ritual
        for (int i = 0; i < 2; i++) {
            double a = t + random.nextDouble() * Math.PI * 2.0;
            double r = 1.0 + random.nextDouble() * 1.1;
            level.addParticle(ParticleTypes.REVERSE_PORTAL, cx + Math.cos(a) * r, cy + (random.nextDouble() - 0.5) * 1.4,
                    cz + Math.sin(a) * r, -Math.cos(a) * 0.09 - Math.sin(a) * 0.04, 0.0, -Math.sin(a) * 0.09 + Math.cos(a) * 0.04);
        }
        if (random.nextInt(2) == 0) {
            level.addParticle(ParticleTypes.PORTAL, x, y, z, (random.nextDouble() - 0.5) * 0.6,
                    (random.nextDouble() - 0.5) * 0.6, (random.nextDouble() - 0.5) * 0.6);
        }
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.REVERSE_PORTAL, x, y, z,
                    (random.nextDouble() - 0.5) * 0.1, (random.nextDouble() - 0.5) * 0.1, (random.nextDouble() - 0.5) * 0.1);
        }
        // sparks of light rising out of the tear
        if (random.nextInt(5) == 0) {
            level.addParticle(ParticleTypes.END_ROD, x, y, z, (random.nextDouble() - 0.5) * 0.02, 0.04 + random.nextDouble() * 0.05,
                    (random.nextDouble() - 0.5) * 0.02);
        }
        if (random.nextInt(6) == 0) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, (random.nextDouble() - 0.5) * 0.4,
                    (random.nextDouble() - 0.5) * 0.4, (random.nextDouble() - 0.5) * 0.4);
        }
        if (random.nextInt(8) == 0) {
            level.addParticle(ParticleTypes.SCULK_SOUL, x, y, z, 0.0, 0.03, 0.0);
        }
        // black matter dripping and ash being drawn in
        if (random.nextInt(8) == 0) {
            level.addParticle(ParticleTypes.SQUID_INK, x, y, z, 0.0, -0.02, 0.0);
        }
        if (random.nextInt(10) == 0) {
            level.addParticle(ParticleTypes.ASH, cx + (random.nextDouble() - 0.5) * 5.0, cy + random.nextDouble() * 3.0,
                    cz + (random.nextDouble() - 0.5) * 5.0, 0.0, 0.0, 0.0);
        }
        if (random.nextInt(10) == 0) {
            level.addParticle(new net.minecraft.core.particles.DustParticleOptions(
                    new org.joml.Vector3f(0.6F, 0.2F, 1.0F), 0.9F), x, y, z, 0.0, 0.0, 0.0);
        }
        if (random.nextInt(14) == 0) {
            level.addParticle(new net.minecraft.core.particles.DustParticleOptions(
                    new org.joml.Vector3f(0.2F, 0.9F, 0.95F), 0.8F), x, y, z, 0.0, 0.0, 0.0);
        }
        if (random.nextInt(260) == 0) {
            level.playLocalSound(pos, net.minecraft.sounds.SoundEvents.PORTAL_AMBIENT, net.minecraft.sounds.SoundSource.BLOCKS,
                    0.5F, 0.5F + random.nextFloat() * 0.3F, false);
        }
    }
}
