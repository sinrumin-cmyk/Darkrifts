package com.darkrifts.entity;

import com.darkrifts.registry.ModEntities;
import com.darkrifts.world.RiftFx;
import com.darkrifts.world.RiftWaves;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;
import java.util.UUID;

/**
 * A real black hole: a solid black sphere with a violet accretion disc (drawn by SingularityRenderer).
 * It drags every enemy around it in, hurts the ones that get close and collapses in a blast.
 */
public class Singularity extends Entity {
    public static final int DURATION = 56;
    public static final double RANGE = 7.0;

    private static final DustParticleOptions VIOLET = new DustParticleOptions(new Vector3f(0.65F, 0.2F, 1.0F), 1.2F);
    private static final DustParticleOptions VOID = new DustParticleOptions(new Vector3f(0.18F, 0.0F, 0.35F), 1.8F);

    @Nullable
    private UUID ownerId;
    private int age;

    public Singularity(EntityType<? extends Singularity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /** {@code center} is the middle of the sphere. */
    public Singularity(Level level, Vec3 center, @Nullable LivingEntity owner) {
        this(ModEntities.SINGULARITY.get(), level);
        setPos(center.x, center.y - getBbHeight() * 0.5, center.z);
        if (owner != null) {
            this.ownerId = owner.getUUID();
        }
    }

    public int age() {
        return age;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        age++;
        if (!(level() instanceof ServerLevel sl)) {
            return;
        }
        Vec3 c = position().add(0, getBbHeight() * 0.5, 0);
        RandomSource random = sl.getRandom();
        LivingEntity owner = owner(sl);

        if (age == 1) {
            sl.playSound(null, BlockPos.containing(c), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 1.0F, 0.5F);
        }
        // matter streaming in
        if (age % 2 == 0) {
            for (int k = 0; k < 5; k++) {
                double theta = random.nextDouble() * Math.PI * 2.0;
                double phi = Math.acos(2.0 * random.nextDouble() - 1.0);
                double dx = Math.sin(phi) * Math.cos(theta);
                double dy = Math.cos(phi);
                double dz = Math.sin(phi) * Math.sin(theta);
                sl.sendParticles(k % 2 == 0 ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.PORTAL,
                        c.x + dx * 6, c.y + dy * 6, c.z + dz * 6, 0, -dx, -dy, -dz, 0.9);
            }
        }
        if (age % 2 == 0 && age < DURATION) {
            for (LivingEntity e : enemies(sl, owner, c, RANGE)) {
                Vec3 d = c.subtract(e.getBoundingBox().getCenter());
                double dist = Math.max(0.5, d.length());
                double pull = 0.12 + (1.0 - Math.min(1.0, dist / RANGE)) * 0.3;
                e.setDeltaMovement(e.getDeltaMovement().scale(0.75).add(d.normalize().scale(pull * 1.6)));
                e.hurtMarked = true;
            }
        }
        if (age > 0 && age % 10 == 0 && age < DURATION) {
            sl.playSound(null, BlockPos.containing(c), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.2F, 0.7F);
            for (LivingEntity e : enemies(sl, owner, c, 3.5)) {
                e.hurt(damage(sl, owner), 2.5F);
            }
            RiftWaves.start(sl, c, 2.5);
        }
        if (age >= DURATION) {
            explode(sl, owner, c);
            discard();
        }
    }

    private void explode(ServerLevel sl, @Nullable LivingEntity owner, Vec3 c) {
        sl.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 80, 0.3, 0.3, 0.3, 0.7);
        for (int i = 0; i < 30; i++) {
            double a = i * (Math.PI * 2.0 / 30.0);
            sl.sendParticles(VIOLET, c.x + Math.cos(a) * 2.0, c.y, c.z + Math.sin(a) * 2.0, 1, 0, 0, 0, 0);
        }
        for (int i = 0; i < 40; i++) {
            double a = i * (Math.PI * 2.0 / 40.0);
            sl.sendParticles(VOID, c.x + Math.cos(a) * 4.0, c.y, c.z + Math.sin(a) * 4.0, 1, 0, 0, 0, 0);
        }
        RiftWaves.start(sl, c, 9.0);
        RiftFx.screen(sl, c, 40.0, 1.6F, 0.6F, 0xD0A0FF);
        sl.playSound(null, BlockPos.containing(c), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.2F, 0.6F);
        for (LivingEntity e : enemies(sl, owner, c, RANGE)) {
            e.hurt(damage(sl, owner), 9.0F);
            Vec3 away = e.getBoundingBox().getCenter().subtract(c);
            away = away.lengthSqr() < 1.0E-4 ? new Vec3(0, 1, 0) : away.normalize();
            e.setDeltaMovement(away.x * 1.4, 0.5, away.z * 1.4);
            e.hurtMarked = true;
        }
    }

    private DamageSource damage(ServerLevel sl, @Nullable LivingEntity owner) {
        return owner != null ? sl.damageSources().indirectMagic(this, owner) : sl.damageSources().magic();
    }

    @Nullable
    private LivingEntity owner(ServerLevel sl) {
        if (ownerId != null && sl.getEntity(ownerId) instanceof LivingEntity living) {
            return living;
        }
        return null;
    }

    private List<LivingEntity> enemies(ServerLevel sl, @Nullable LivingEntity owner, Vec3 c, double r) {
        return sl.getEntitiesOfClass(LivingEntity.class, new AABB(c.x - r, c.y - r, c.z - r, c.x + r, c.y + r, c.z + r),
                e -> e != owner && e.isAlive() && !e.isSpectator() && (owner == null || !e.isAlliedTo(owner))
                        && e.getBoundingBox().getCenter().distanceToSqr(c) <= r * r);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
