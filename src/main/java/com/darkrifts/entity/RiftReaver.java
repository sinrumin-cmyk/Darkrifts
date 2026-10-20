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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LightningBolt;
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
 * The boss of the ritual. It tears its way out of a rift in the sky and is lowered to the ground (invulnerable until it lands), then fights with a
 * melee swing and three abilities: ground slam, a line of spikes and a pull. Below half health it goes berserk.
 * Model / animations / textures: geo/animations/textures "rift_reaver" (GeckoLib).
 */
public class RiftReaver extends Monster implements GeoEntity {
    /** Ticks (since the rift appeared) at which the boss starts to sink through it / lands / the rift is gone. */
    public static final int DESCENT_START = 50;
    public static final int LAND_AGE = 170;
    public static final int RIFT_END_AGE = 200;
    /** Height of the sky rift above the ground, and the half sizes of the fully open tear (blocks). */
    public static final double RIFT_HEIGHT = 14.0;
    public static final float RIFT_A = 4.8F;
    public static final float RIFT_B = 3.0F;
    private static final double START_OFFSET = 4.8;

    /** How far above the tear the boss starts (bigger bosses start higher). */
    protected double startOffset() {
        return START_OFFSET;
    }

    /** Range in which the boss starts casting abilities. */
    protected double castRange() {
        return 28.0;
    }

    /** Game time at which the sky rift appeared; -1 = no rift. Everything about the entrance is a function of this. */
    private static final EntityDataAccessor<Long> RIFT_START =
            SynchedEntityData.defineId(RiftReaver.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Float> RIFT_Y =
            SynchedEntityData.defineId(RiftReaver.class, EntityDataSerializers.FLOAT);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.rift_reaver.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.rift_reaver.walk");
    private static final RawAnimation AMBIENT = RawAnimation.begin().thenLoop("animation.rift_reaver.ambient");
    private static final RawAnimation EMERGE_ANIM = RawAnimation.begin().thenPlayAndHold("animation.rift_reaver.emerge");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("animation.rift_reaver.attack");
    private static final RawAnimation SLAM = RawAnimation.begin().thenPlay("animation.rift_reaver.slam");

    private static final DustParticleOptions VIOLET = new DustParticleOptions(new Vector3f(0.7F, 0.25F, 1.0F), 1.6F);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    protected final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.translatable("entity.darkrifts.rift_reaver"),
            BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_12);

    protected double groundY;
    protected double anchorX;
    protected double anchorZ;
    protected boolean landed = true;
    protected int cooldown = 60;
    protected int slamTimer;
    protected boolean enraged;

    public RiftReaver(EntityType<? extends RiftReaver> type, Level level) {
        super(type, level);
        this.xpReward = 400;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 300.0)
                .add(Attributes.ATTACK_DAMAGE, 12.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
                .add(Attributes.ARMOR, 8.0)
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
        builder.define(RIFT_START, -1L);
        builder.define(RIFT_Y, 0.0F);
    }

    // ------------------------------------------------------------------ the entrance: tearing out of a rift in the sky

    /**
     * Call right after creating the boss at (x, riftY + 4.8, z): a rift opens in the sky at {@code riftY}, slowly
     * widens while the boss tears its way out and is lowered to {@code ground}.
     */
    public void startEmerge(double ground, double riftY) {
        this.groundY = ground;
        this.anchorX = getX();
        this.anchorZ = getZ();
        this.landed = false;
        entityData.set(RIFT_Y, (float) riftY);
        entityData.set(RIFT_START, level().getGameTime());
        if (level() instanceof ServerLevel sl) {
            sl.playSound(null, anchorX, riftY, anchorZ, SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 8.0F, 0.5F);
            sl.playSound(null, anchorX, riftY, anchorZ, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 8.0F, 0.45F);
        }
    }

    /** Ticks since the rift appeared (negative if there is none). */
    public float getRiftAge(float partialTick) {
        long start = entityData.get(RIFT_START);
        return start < 0 ? -1.0F : (level().getGameTime() - start) + partialTick;
    }

    public boolean isRiftActive() {
        long start = entityData.get(RIFT_START);
        return start >= 0 && level().getGameTime() - start < RIFT_END_AGE;
    }

    public double getRiftY() {
        return entityData.get(RIFT_Y);
    }

    public boolean isEmerging() {
        long start = entityData.get(RIFT_START);
        return start >= 0 && level().getGameTime() - start < LAND_AGE;
    }

    /** How open the tear is: 0 = none, a thin slit at first, fully open from ~110 ticks, closing after landing. */
    public static float riftOpen(float age) {
        if (age <= 0.0F || age >= RIFT_END_AGE) {
            return 0.0F;
        }
        if (age < 110.0F) {
            float x = age / 110.0F;
            return 0.05F + 0.95F * x * x * (3.0F - 2.0F * x);
        }
        if (age <= LAND_AGE) {
            return 1.0F;
        }
        float x = (age - LAND_AGE) / (RIFT_END_AGE - LAND_AGE);
        return 1.0F - x * x * (3.0F - 2.0F * x);
    }

    @Override
    public void tick() {
        long start = entityData.get(RIFT_START);
        if (start >= 0) {
            long age = level().getGameTime() - start;
            if (age < LAND_AGE) {
                noPhysics = true;
                setNoGravity(true);
                setDeltaMovement(Vec3.ZERO);
                fallDistance = 0.0F;
                if (level() instanceof ServerLevel sl) {
                    double top = entityData.get(RIFT_Y) + startOffset();
                    double p = Mth.clamp((age - DESCENT_START) / (double) (LAND_AGE - DESCENT_START), 0.0, 1.0);
                    double eased = p * p * (3.0 - 2.0 * p);
                    setPos(anchorX, Mth.lerp(eased, top, groundY), anchorZ);
                    emergeFx(sl, (int) age);
                } else {
                    Player near = level().getNearestPlayer(this, 56.0);
                    if (near != null) {
                        float open = riftOpen(age);
                        Shake.add((0.25F + 0.75F * open) * (float) Mth.clamp(1.0 - near.distanceTo(this) / 56.0, 0.15, 1.0));
                    }
                }
            } else if (level() instanceof ServerLevel sl) {
                if (!landed) {
                    land(sl);
                }
                if (age >= RIFT_END_AGE) {
                    entityData.set(RIFT_START, -1L);
                }
            } else if (noPhysics) {
                noPhysics = false; // client: the entrance is over
                setNoGravity(false);
            }
        }
        super.tick();
    }

    private void emergeFx(ServerLevel sl, int age) {
        double ry = entityData.get(RIFT_Y);
        float open = riftOpen(age);
        double a = RIFT_A * open;
        double b = RIFT_B * Math.pow(open, 0.8);
        // sparks and void matter along the torn rim
        for (int i = 0; i < 3; i++) {
            double ang = random.nextDouble() * Math.PI * 2.0;
            double px = anchorX + Math.cos(ang) * a * 1.02;
            double pz = anchorZ + Math.sin(ang) * b * 1.02;
            sl.sendParticles(ParticleTypes.REVERSE_PORTAL, px, ry, pz, 2, 0.15, 0.1, 0.15, 0.08);
            if (random.nextInt(2) == 0) {
                sl.sendParticles(ParticleTypes.END_ROD, px, ry - 0.1, pz, 1, 0.05, 0.05, 0.05, 0.02);
            }
            if (random.nextInt(3) == 0) {
                sl.sendParticles(VIOLET, px, ry - 0.3, pz, 1, 0.1, 0.1, 0.1, 0.0);
            }
        }
        // debris falling out of the tear
        if (age % 2 == 0) {
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OBSIDIAN.defaultBlockState()),
                    anchorX + (random.nextDouble() - 0.5) * a * 1.6, ry - 0.2, anchorZ + (random.nextDouble() - 0.5) * b * 1.6,
                    2, 0.3, 0.0, 0.3, 0.0);
        }
        // the sound of reality being torn
        if (age % 14 == 3 && age < 140) {
            sl.playSound(null, anchorX, ry, anchorZ, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 5.0F, 0.5F);
            sl.playSound(null, anchorX, ry, anchorZ, SoundEvents.WARDEN_TENDRIL_CLICKS, SoundSource.HOSTILE, 5.0F, 0.6F);
        }
        if (age == DESCENT_START) {
            sl.playSound(null, anchorX, ry, anchorZ, SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 8.0F, 0.6F);
        }
        if (age >= DESCENT_START && age < LAND_AGE) {
            sl.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 2.0, getZ(), 4, 0.8, 1.6, 0.8, 0.1);
            sl.sendParticles(VIOLET, getX(), getY() + 2.0, getZ(), 2, 0.9, 1.7, 0.9, 0.0);
        }
        // lightning flashes from the tear down to the ground
        if (age % 18 == 6 && age < 150) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(sl);
            if (bolt != null) {
                bolt.moveTo(anchorX + (random.nextDouble() - 0.5) * 12.0, groundY, anchorZ + (random.nextDouble() - 0.5) * 12.0);
                bolt.setVisualOnly(true);
                sl.addFreshEntity(bolt);
            }
        }
        // cracks spreading on the ground below
        if (age % 10 == 0 && age >= 20 && age < 160) {
            double ang = random.nextDouble() * Math.PI * 2.0;
            double r = 2.0 + random.nextDouble() * 4.0;
            RiftSpike.crack(sl, anchorX + Math.cos(ang) * r, groundY, anchorZ + Math.sin(ang) * r, 0, 50);
        }
    }

    /** The boss touches the ground: roar, shockwave, spikes, title. */
    protected void land(ServerLevel sl) {
        landed = true;
        noPhysics = false;
        setNoGravity(false);
        setPos(anchorX, groundY, anchorZ);
        triggerAnim("action", "slam");
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 6.0F, 0.55F);
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 6.0F, 0.5F);
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 0.5, getZ(), 2, 1.5, 0.2, 1.5, 0.0);
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
        if (target != null && target.isAlive() && distanceToSqr(target) < castRange() * castRange() && slamTimer == 0) {
            cast(sl, target);
        }
    }

    protected void enrage(ServerLevel sl) {
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

    protected void cast(ServerLevel sl, LivingEntity target) {
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

    protected void slam(ServerLevel sl) {
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
        controllers.add(new AnimationController<>(this, "ambient", 0, state -> state.setAndContinue(AMBIENT)));
        controllers.add(new AnimationController<>(this, "action", 0, state -> PlayState.STOP)
                .triggerableAnim("attack", ATTACK)
                .triggerableAnim("slam", SLAM));
    }

    private PlayState mainAnimation(AnimationState<RiftReaver> state) {
        if (isEmerging()) {
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
        entityData.set(RIFT_START, tag.contains("RiftStart") ? tag.getLong("RiftStart") : -1L);
        entityData.set(RIFT_Y, tag.getFloat("RiftY"));
        groundY = tag.getDouble("GroundY");
        anchorX = tag.getDouble("AnchorX");
        anchorZ = tag.getDouble("AnchorZ");
        landed = !tag.contains("Landed") || tag.getBoolean("Landed");
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
        tag.putLong("RiftStart", entityData.get(RIFT_START));
        tag.putFloat("RiftY", entityData.get(RIFT_Y));
        tag.putDouble("GroundY", groundY);
        tag.putDouble("AnchorX", anchorX);
        tag.putDouble("AnchorZ", anchorZ);
        tag.putBoolean("Landed", landed);
    }
}
