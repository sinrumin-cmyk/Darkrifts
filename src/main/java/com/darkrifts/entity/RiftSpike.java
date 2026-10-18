package com.darkrifts.entity;

import com.darkrifts.registry.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

/**
 * A dark crystal spike (GeckoLib model + animation). It hides under the ground for {@code warmup} ticks
 * (the rift above it is tearing open), then bursts out, hits everything standing on it and sinks back.
 */
public class RiftSpike extends Entity implements GeoEntity {
    private static final EntityDataAccessor<Integer> WARMUP =
            SynchedEntityData.defineId(RiftSpike.class, EntityDataSerializers.INT);
    private static final RawAnimation HIDDEN = RawAnimation.begin().thenLoop("animation.rift_spike.hidden");
    private static final RawAnimation EMERGE = RawAnimation.begin().thenPlayAndHold("animation.rift_spike.emerge");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private UUID ownerId;
    private int life;

    public RiftSpike(EntityType<? extends RiftSpike> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public RiftSpike(Level level, double x, double y, double z, float yaw, int warmup, LivingEntity owner) {
        this(ModEntities.RIFT_SPIKE.get(), level);
        setPos(x, y, z);
        setYRot(yaw);
        this.entityData.set(WARMUP, warmup);
        this.ownerId = owner.getUUID();
    }

    /** Height of the walkable surface under (x, z), looking from y downwards (a few blocks) and upwards (2 blocks). */
    public static double surfaceY(Level level, double x, double y, double z) {
        net.minecraft.core.BlockPos.MutableBlockPos m = new net.minecraft.core.BlockPos.MutableBlockPos();
        int fy = net.minecraft.util.Mth.floor(y);
        for (int dy = 2; dy >= -5; dy--) {
            m.set(net.minecraft.util.Mth.floor(x), fy + dy, net.minecraft.util.Mth.floor(z));
            net.minecraft.world.phys.shapes.VoxelShape shape = level.getBlockState(m).getCollisionShape(level, m);
            if (!shape.isEmpty() && level.getBlockState(m.above()).isAir()) {
                return m.getY() + shape.max(net.minecraft.core.Direction.Axis.Y);
            }
        }
        return y;
    }

    /** A temporary crack lying on the ground at (x, z). Returns the surface height. */
    public static double crack(ServerLevel level, double x, double y, double z, int delay, int lifetime) {
        double sy = surfaceY(level, x, y, z);
        net.minecraft.core.BlockPos cell = net.minecraft.core.BlockPos.containing(x, Math.floor(sy - 1.0E-4) + 1.0, z);
        long now = level.getGameTime();
        com.darkrifts.block.RiftBlock.placeGroundCrack(level, cell, sy, level.getRandom().nextDouble() * Math.PI,
                1.5 + level.getRandom().nextDouble() * 0.9, now + lifetime, now + delay, level.getRandom());
        return sy;
    }

    /** A ground crack and, once it has torn open, a spike bursting out of it. */
    public static void burst(ServerLevel level, LivingEntity owner, double x, double y, double z, int delay) {
        double sy = crack(level, x, y, z, delay, 45 + delay);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, sy + 0.15, z, 10, 0.5, 0.1, 0.5, 0.12);
        level.addFreshEntity(new RiftSpike(level, x, sy, z, level.getRandom().nextFloat() * 360.0F, 10 + delay, owner));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(WARMUP, 0);
    }

    @Override
    public void tick() {
        super.tick();
        life++;
        if (level().isClientSide) {
            return;
        }
        int warmup = entityData.get(WARMUP);
        if (life == warmup + 3) {
            strike();
        }
        if (life >= warmup + 26) {
            discard();
        }
    }

    private void strike() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        LivingEntity owner = ownerId != null && serverLevel.getEntity(ownerId) instanceof LivingEntity l ? l : null;
        AABB box = getBoundingBox().inflate(0.35, 0.0, 0.35);
        for (LivingEntity target : serverLevel.getEntitiesOfClass(LivingEntity.class, box)) {
            if (target == owner || (owner != null && owner.isAlliedTo(target))) {
                continue;
            }
            target.hurt(owner == null ? damageSources().magic() : damageSources().indirectMagic(this, owner), 7.0F);
        }
        serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE,
                1.2F, 0.6F + random.nextFloat() * 0.3F);
        serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 0.6, getZ(), 8, 0.3, 0.5, 0.3, 0.1);
    }

    // ------------------------------------------------------------------ GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, this::animate));
    }

    private PlayState animate(AnimationState<RiftSpike> state) {
        // "max(.., 3)": the warmup value arrives a tick after the entity itself - stay hidden until we know it.
        boolean waiting = life < Math.max(entityData.get(WARMUP), 3);
        return state.setAndContinue(waiting ? HIDDEN : EMERGE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // ------------------------------------------------------------------ persistence

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) {
            ownerId = tag.getUUID("Owner");
        }
        life = tag.getInt("Life");
        entityData.set(WARMUP, tag.getInt("Warmup"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerId != null) {
            tag.putUUID("Owner", ownerId);
        }
        tag.putInt("Life", life);
        tag.putInt("Warmup", entityData.get(WARMUP));
    }

    /** Spikes are momentary: never written to disk. */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
