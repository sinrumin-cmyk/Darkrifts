package com.darkrifts.block;

import com.darkrifts.DarkDimensions;
import com.darkrifts.registry.ModBlockEntities;
import com.darkrifts.world.RiftFx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * One cell of a rift. Besides the teleport target it stores the pieces of the single tear line that "belong" to
 * this cell: each piece is {ax, ay, az, bx, by, bz, birthGameTime}. The renderer draws them as one thin ragged ribbon.
 */
public class RiftBlockEntity extends BlockEntity {
    private static final int MAX_SEGMENTS = 14;

    @Nullable
    private ResourceLocation target;
    private boolean returning;
    /** Game time at which the rift collapses; 0 = permanent. */
    private long expireAt;
    /** Combat rifts: purely visual, they never teleport anyone. */
    private boolean decor;
    /** Ground crack: the tear lies flat on the top face of the block below instead of standing upright. */
    private boolean flat;
    private final List<double[]> segments = new ArrayList<>();

    public RiftBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RIFT.get(), pos, state);
    }

    public void configure(@Nullable ResourceLocation target, boolean returning, long expireAt) {
        this.target = target;
        this.returning = returning;
        this.expireAt = expireAt;
        setChanged();
    }

    public boolean isDecor() {
        return decor;
    }

    public void setDecor(boolean decor) {
        this.decor = decor;
        setChanged();
    }

    public boolean isFlat() {
        return flat;
    }

    public void setFlat(boolean flat) {
        this.flat = flat;
        setChanged();
    }

    public boolean isReturning() {
        return returning;
    }

    public List<double[]> getSegments() {
        return segments;
    }

    /** Adds a piece of the tear line to this cell and syncs it to clients. */
    public void addSegment(double ax, double ay, double az, double bx, double by, double bz, long birth,
                           double u, double total) {
        if (segments.size() >= MAX_SEGMENTS) {
            segments.remove(0);
        }
        segments.add(new double[]{ax, ay, az, bx, by, bz, (double) birth, u, total});
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** Called when the stroke ends: tells every piece of the live stroke how long the whole rift turned out. */
    public void finishLength(double total) {
        boolean changed = false;
        for (double[] s : segments) {
            if (s[8] == 0.0) {
                s[8] = total;
                changed = true;
            }
        }
        if (changed) {
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    /** Client-side game time when this block entity appeared (used when a rift has no stored segments). */
    private long spawnTime = Long.MIN_VALUE;

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (spawnTime == Long.MIN_VALUE) {
            spawnTime = level.getGameTime();
        }
    }

    public long getSpawnTime() {
        return spawnTime == Long.MIN_VALUE ? 0L : spawnTime;
    }

    /** Lazily rolls a random dark dimension for rifts that were placed without one. */
    public ResourceLocation getOrPickTarget(Level level) {
        if (target == null) {
            target = DarkDimensions.random(level.getRandom(), level.dimension());
            setChanged();
        }
        return target;
    }

    public long getExpireAt() {
        return expireAt;
    }

    /** True for the one cell that owns the very first piece of the tear line: it runs the once-per-rift effects. */
    public boolean isAnchor() {
        if (decor) {
            return false;
        }
        for (double[] s : segments) {
            if (s[7] == 0.0) {
                return true;
            }
        }
        return false;
    }

    /** Start of the tear line (used by the client to put the rune circle under it). */
    @Nullable
    public double[] getStartPoint() {
        for (double[] s : segments) {
            if (s[7] == 0.0) {
                return new double[]{s[0], s[1], s[2]};
            }
        }
        return null;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RiftBlockEntity be) {
        long now = level.getGameTime();
        if (be.expireAt > 0 && now >= be.expireAt) {
            if (level instanceof ServerLevel sl) {
                be.collapseFx(sl);
            }
            level.removeBlock(pos, false);
            return;
        }
        if (!(level instanceof ServerLevel sl) || be.segments.isEmpty()) {
            return;
        }
        // 0 = calm, 1 = unstable (last 5 s), 2 = about to burst (last second)
        long left = be.expireAt > 0 ? be.expireAt - now : Long.MAX_VALUE;
        int heat = left < 20 ? 2 : left < 100 ? 1 : 0;
        int period = be.decor ? 8 : (heat > 0 ? 2 : 4);
        long phase = (now + (pos.getX() * 31L + pos.getZ() * 17L + pos.getY() * 7L)) & 0x7FFFFFFFL;
        if (phase % period != 0) {
            return;
        }
        var rnd = sl.getRandom();
        int n = Math.min(be.segments.size(), be.decor ? 1 : 3);
        for (int i = 0; i < n; i++) {
            RiftFx.ambient(sl, be.segments.get(rnd.nextInt(be.segments.size())), now, rnd, be.decor ? 0 : heat);
        }
        if (be.decor) {
            return;
        }
        double[] s = be.segments.get(rnd.nextInt(be.segments.size()));
        Vec3 mid = new Vec3((s[0] + s[3]) / 2.0, (s[1] + s[4]) / 2.0, (s[2] + s[5]) / 2.0);
        if (rnd.nextInt(heat > 0 ? 8 : 40) == 0) {
            RiftFx.arc(sl, mid, rnd);
        }
        if (be.isAnchor() && now % (heat > 0 ? 25 : 90) == 0) {
            RiftFx.hum(sl, mid, rnd, heat);
        }
    }

    /** The tear collapses: the anchor cell makes the big implosion, the others a small puff. */
    private void collapseFx(ServerLevel sl) {
        Vec3 c = Vec3.atCenterOf(worldPosition);
        if (!segments.isEmpty()) {
            double[] s = segments.get(0);
            c = new Vec3((s[0] + s[3]) / 2.0, (s[1] + s[4]) / 2.0, (s[2] + s[5]) / 2.0);
        }
        RiftFx.collapse(sl, c, isAnchor());
    }

    /** The tear can stretch a couple of blocks beyond its cell - don't cull it too early. */
    public AABB getRenderBoundingBox() {
        if (isAnchor()) {
            // the rune circle on the ground, the pillar of light and the halo
            return new AABB(worldPosition).inflate(10.0, 0.0, 10.0).expandTowards(0.0, -64.0, 0.0).expandTowards(0.0, 12.0, 0.0);
        }
        return new AABB(worldPosition).inflate(3.0);
    }

    // ------------------------------------------------------------------ persistence / sync

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (target != null) {
            tag.putString("Target", target.toString());
        }
        tag.putBoolean("Returning", returning);
        tag.putBoolean("Decor", decor);
        tag.putBoolean("Flat", flat);
        tag.putLong("ExpireAt", expireAt);
        ListTag list = new ListTag();
        for (double[] s : segments) {
            for (double d : s) {
                list.add(DoubleTag.valueOf(d));
            }
        }
        tag.put("Seg9", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        target = tag.contains("Target") ? ResourceLocation.tryParse(tag.getString("Target")) : null;
        returning = tag.getBoolean("Returning");
        decor = tag.getBoolean("Decor");
        flat = tag.getBoolean("Flat");
        expireAt = tag.getLong("ExpireAt");
        segments.clear();
        ListTag list = tag.getList("Seg9", Tag.TAG_DOUBLE);
        for (int i = 0; i + 8 < list.size(); i += 9) {
            segments.add(new double[]{list.getDouble(i), list.getDouble(i + 1), list.getDouble(i + 2),
                    list.getDouble(i + 3), list.getDouble(i + 4), list.getDouble(i + 5), list.getDouble(i + 6),
                    list.getDouble(i + 7), list.getDouble(i + 8)});
        }
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
