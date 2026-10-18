package com.darkrifts.entity;

import com.darkrifts.Shake;
import com.darkrifts.block.RiftBlock;
import com.darkrifts.world.RiftWaves;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The boss of the ritual. It climbs out of a tear in the ground (EMERGE ticks, invulnerable), then fights with a
 * melee swing and three abilities: ground slam, a line of spikes and a pull. Below half health it goes berserk.
 * Model / animations / textures: geo/animations/textures "rift_reaver" (GeckoLib).
 */
public class RiftReaver extends Monster implements GeoEntity {
    public static final int EMERGE_TICKS = 80;
    private static final double RISE_PER_TICK = 3.0 / EMERGE_TICKS;

    private static final EntityDataAccessor<Integer> EMERGE =
            SynchedEntityData.defineId(RiftReaver.class, EntityDataSerializers.INT);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.rift_reaver.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.rift_reaver.walk");
    private static final RawAnimation EMERGE_ANIM = RawAnimation.begin().thenPlayAndHold("animation.rift_reaver.emerge");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("animation.rift_reaver.attack");
    private static final RawAnimation SLAM = RawAnimation.begin().thenPlay("animation.rift_reaver.slam");

    private static final DustParticleOptions VIOLET = new DustParticleOptions(new Vector3f(0.7F, 0.25F, 1.0F), 1.6F);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.translatable("entity.darkrifts.rift_reaver"),
            BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_12);

    private double groundY;
    private int cooldown = 60;
    private int slamTimer;
    private boolean enraged;

    public RiftReaver(EntityType<? extends RiftReaver> type, Level level) {
        super(type, level);
        this.xpReward = 600;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 350.0)
                .add(Attributes.ATTACK_DAMAGE, 14.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
                .add(Attributes.ARMOR, 10.0)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.ATTACK_KNOCKBACK, 1.2);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, false));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(EMERGE, 0);
    }

    // ------------------------------------------------------------------ emerging from the rift

    /** Call right after creating the boss 3 blocks below {@code ground}: it climbs out over EMERGE_TICKS. */
    public void startEmerge(double ground) {
        this.groundY = ground;
        entityData.set(EMERGE, EMERGE_TICKS);
        if (level() instanceof ServerLevel sl) {
            sl.playSound(null, getX(), ground, getZ(), SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 4.0F, 0.6F);
        }
    }

    public boolean isEmerging() {
        return entityData.get(EMERGE) > 0;
    }

    @Override
    public void tick() {
        int em = entityData.get(EMERGE);
        if (em > 0) {
            noPhysics = true;
            setNoGravity(true);
            setDeltaMovement(Vec3.ZERO);
            if (level() instanceof ServerLevel sl) {
                setPos(getX(), getY() + RISE_PER_TICK, getZ());
                emergeFx(sl, em);
                entityData.set(EMERGE, em - 1);
                if (em - 1 == 0) {
                    finishEmerge(sl);
                }
            } else {
                Shake.add(0.5F + 0.5F * (1.0F - em / (float) EMERGE_TICKS));
            }
        }
        super.tick();
    }

    private void emergeFx(ServerLevel sl, int em) {
        double gy = groundY + 0.15;
        sl.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), gy, getZ(), 6, 0.9, 0.2, 0.9, 0.15);
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OBSIDIAN.defaultBlockState()),
                getX(), gy, getZ(), 8, 1.0, 0.1, 1.0, 0.25);
        sl.sendParticles(VIOLET, getX(), gy + 0.5, getZ(), 4, 0.9, 0.6, 0.9, 0.0);
        if (em % 20 == 0) {
            sl.playSound(null, getX(), gy, getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 1.5F, 0.5F);
        }
        if (em % 10 == 0) {
            double a = random.nextDouble() * Math.PI * 2.0;
            double r = 2.0 + random.nextDouble() * 3.0;
            RiftSpike.crack(sl, getX() + Math.cos(a) * r, groundY, getZ() + Math.sin(a) * r, 0, 50);
        }
    }

    private void finishEmerge(ServerLevel sl) {
        noPhysics = false;
        setNoGravity(false);
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 6.0F, 0.55F);
        RiftWaves.start(sl, position().add(0, 0.5, 0), 12.0);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4.0;
            RiftSpike.burst(sl, this, getX() + Math.cos(a) * 4.5, getY(), getZ() + Math.sin(a) * 4.5, 4 + random.nextInt(4));
        }
        for (ServerPlayer p : sl.players()) {
            if (p.distanceToSqr(this) < 64.0 * 64.0) {
                p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
                p.connection.send(new ClientboundSetTitleTextPacket(
                        Component.translatable("entity.darkrifts.rift_reaver").withStyle(ChatFormatting.DARK_PURPLE)));
            }
        }
        cooldown = 50;
    }

    @Override
    public boolean isImmobile() {
        return isEmerging() || super.isImmobile();
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (isEmerging() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return true;
        }
        return super.isInvulnerableTo(source);
    }

    @Override
    public boolean isPushable() {
        return !isEmerging() && super.isPushable();
    }

    // ------------------------------------------------------------------ combat

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        bossEvent.setProgress(getHealth() / getMaxHealth());
        if (isEmerging() || !(level() instanceof ServerLevel sl)) {
            return;
        }
        if (!enraged && getHealth() < getMaxHealth() * 0.5F) {
            enrage(sl);
        }
        if (slamTimer > 0 && --slamTimer == 0) {
            slam(sl);
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        LivingEntity target = getTarget();
        if (target != null && target.isAlive() && distanceToSqr(target) < 28.0 * 28.0 && slamTimer == 0) {
            cast(sl, target);
        }
    }

    private void enrage(ServerLevel sl) {
        enraged = true;
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.33);
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 5.0F, 0.5F);
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 1.5, getZ(), 2, 1.0, 1.0, 1.0, 0.0);
        sl.sendParticles(VIOLET, getX(), getY() + 1.5, getZ(), 60, 1.5, 1.5, 1.5, 0.0);
        RiftWaves.start(sl, position().add(0, 0.5, 0), 9.0);
        for (ServerPlayer p : sl.players()) {
            if (p.distanceToSqr(this) < 48.0 * 48.0) {
                p.displayClientMessage(Component.translatable("message.darkrifts.reaver_enraged")
                        .withStyle(ChatFormatting.DARK_PURPLE), true);
            }
        }
    }

    private void cast(ServerLevel sl, LivingEntity target) {
        lookAt(target, 60.0F, 60.0F);
        switch (random.nextInt(3)) {
            case 0 -> {
                triggerAnim("action", "slam");
                slamTimer = 16;
                sl.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 3.0F, 0.7F);
            }
            case 1 -> {
                triggerAnim("action", "attack");
                Vec3 from = position();
                Vec3 to = target.position();
                Vec3 dir = to.subtract(from).multiply(1, 0, 1);
                double len = dir.length();
                if (len > 0.1) {
                    dir = dir.scale(1.0 / len);
                    for (int i = 1; i * 2.0 < len + 2.0 && i <= 12; i++) {
                        Vec3 p = from.add(dir.scale(i * 2.0));
                        RiftSpike.burst(sl, this, p.x, target.getY(), p.z, i * 2);
                    }
                }
                for (int i = 0; i < 5; i++) {
                    double a = i * Math.PI * 2.0 / 5.0;
                    RiftSpike.burst(sl, this, target.getX() + Math.cos(a) * 1.6, target.getY(),
                            target.getZ() + Math.sin(a) * 1.6, 10 + i);
                }
                sl.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 2.5F, 0.8F);
            }
            default -> {
                sl.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 3.0F, 0.6F);
                for (Player p : sl.getEntitiesOfClass(Player.class, getBoundingBox().inflate(18.0))) {
                    if (p.isCreative() || p.isSpectator()) {
                        continue;
                    }
                    Vec3 d = position().subtract(p.position());
                    p.setDeltaMovement(p.getDeltaMovement().add(d.normalize().scale(1.4)).add(0, 0.3, 0));
                    p.hurtMarked = true;
                    for (int k = 1; k <= 10; k++) {
                        Vec3 q = p.position().add(d.scale(k / 10.0)).add(0, 1.0, 0);
                        sl.sendParticles(ParticleTypes.REVERSE_PORTAL, q.x, q.y, q.z, 2, 0.1, 0.1, 0.1, 0.02);
                    }
                }
                slamTimer = 22;
                triggerAnim("action", "slam");
            }
        }
        cooldown = enraged ? 45 + random.nextInt(35) : 90 + random.nextInt(50);
    }

    private void slam(ServerLevel sl) {
        float dmg = enraged ? 18.0F : 13.0F;
        Vec3 c = position();
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(7.0, 2.0, 7.0))) {
            if (e == this || e.isAlliedTo(this) || e.distanceToSqr(this) > 49.0) {
                continue;
            }
            e.hurt(damageSources().mobAttack(this), dmg);
            Vec3 d = e.position().subtract(c).multiply(1, 0, 1);
            if (d.lengthSqr() > 1.0E-4) {
                e.setDeltaMovement(e.getDeltaMovement().add(d.normalize().scale(1.1)).add(0, 0.55, 0));
                e.hurtMarked = true;
            }
        }
        sl.playSound(null, c.x, c.y, c.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 4.0F, 0.6F);
        sl.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.HOSTILE, 4.0F, 0.6F);
        for (int ring = 1; ring <= 3; ring++) {
            double r = ring * 2.2;
            for (int i = 0; i < 26; i++) {
                double a = i * Math.PI * 2.0 / 26.0;
                sl.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x + Math.cos(a) * r, c.y + 0.2, c.z + Math.sin(a) * r,
                        1, 0.05, 0.3, 0.05, 0.2);
                sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OBSIDIAN.defaultBlockState()),
                        c.x + Math.cos(a) * r, c.y + 0.2, c.z + Math.sin(a) * r, 2, 0.1, 0.1, 0.1, 0.3);
            }
        }
        for (int i = 0; i < 6; i++) {
            double a = random.nextDouble() * Math.PI * 2.0;
            double r = 2.0 + random.nextDouble() * 4.0;
            RiftSpike.crack(sl, c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r, 0, 40);
        }
        RiftWaves.start(sl, c.add(0, 0.4, 0), 8.0);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit) {
            triggerAnim("action", "attack");
            if (target instanceof LivingEntity living) {
                living.setDeltaMovement(living.getDeltaMovement().add(0, 0.35, 0));
                living.hurtMarked = true;
            }
        }
        return hit;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel sl) {
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDER_DRAGON_DEATH, SoundSource.HOSTILE, 5.0F, 0.6F);
            sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 1.5, getZ(), 3, 1.5, 1.5, 1.5, 0.0);
            sl.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 1.5, getZ(), 200, 1.5, 2.0, 1.5, 0.3);
            RiftWaves.start(sl, position().add(0, 0.5, 0), 10.0);
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    // ------------------------------------------------------------------ boss bar

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        bossEvent.setName(getDisplayName());
    }

    // ------------------------------------------------------------------ sounds

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.WARDEN_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WARDEN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WARDEN_DEATH;
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        playSound(SoundEvents.WARDEN_STEP, 1.2F, 0.7F);
    }

    // ------------------------------------------------------------------ GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 3, this::mainAnimation));
        controllers.add(new AnimationController<>(this, "action", 0, state -> PlayState.STOP)
                .triggerableAnim("attack", ATTACK)
                .triggerableAnim("slam", SLAM));
    }

    private PlayState mainAnimation(AnimationState<RiftReaver> state) {
        if (entityData.get(EMERGE) > 0) {
            return state.setAndContinue(EMERGE_ANIM);
        }
        return state.setAndContinue(state.isMoving() ? WALK : IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // ------------------------------------------------------------------ persistence

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        enraged = tag.getBoolean("Enraged");
        entityData.set(EMERGE, tag.getInt("Emerge"));
        groundY = tag.getDouble("GroundY");
        if (enraged) {
            getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.33);
        }
        if (hasCustomName()) {
            bossEvent.setName(getDisplayName());
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Enraged", enraged);
        tag.putInt("Emerge", entityData.get(EMERGE));
        tag.putDouble("GroundY", groundY);
    }
}
