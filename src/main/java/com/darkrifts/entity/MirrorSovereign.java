package com.darkrifts.entity;

import com.darkrifts.registry.ModBlocks;
import com.darkrifts.world.MirrorSanctum;
import com.darkrifts.world.RiftFx;
import com.darkrifts.world.RiftWaves;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * THE MIRROR SOVEREIGN - the stationary endgame boss. Exists only in the Dark Mirror's Sanctum (x1000, z1000).
 * It tears out of a gigantic rift above the Sanctum, lands on the dais and never moves again: it rules the arena
 * with area attacks instead. Levitating, with its own model (tools/sovereign_model.py).
 *
 *  Attacks: Void Rain (spikes rain on every player), Rift Beams (telegraphed lines of void), Shock Rings
 *           (jump them), Singularity (drags everyone in, then a huge slam), Tower Storm (lightning from the four
 *           towers). Phase 2 (66%) and phase 3 (33%) shorten the cooldowns and chain attacks.
 */
public class MirrorSovereign extends RiftReaver {
    /** Where its sky rift is above the dais, and how far above the rift it starts (it is huge). */
    public static final double RIFT_UP = 26.0;
    public static final double START_UP = 8.6;
    public static final float MODEL_SCALE = 1.6F;
    /** It does not walk: it levitates this far above the dais. */
    public static final double HOVER = 3.5;

    private static final RawAnimation S_IDLE = RawAnimation.begin().thenLoop("animation.mirror_sovereign.idle");
    private static final RawAnimation S_AMBIENT = RawAnimation.begin().thenLoop("animation.mirror_sovereign.ambient");
    private static final RawAnimation S_EMERGE = RawAnimation.begin().thenPlayAndHold("animation.mirror_sovereign.emerge");
    private static final RawAnimation S_ATTACK = RawAnimation.begin().thenPlay("animation.mirror_sovereign.attack");
    private static final RawAnimation S_SLAM = RawAnimation.begin().thenPlay("animation.mirror_sovereign.slam");

    private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(1.0F, 0.12F, 0.2F), 2.0F);
    private static final DustParticleOptions VIOLET = new DustParticleOptions(new Vector3f(0.7F, 0.25F, 1.0F), 1.8F);
    private static final DustParticleOptions BLACK = new DustParticleOptions(new Vector3f(0.04F, 0.0F, 0.08F), 2.4F);

    private static final class Ring {
        double radius;
        final double speed;
        final float damage;

        Ring(double speed, float damage) {
            this.speed = speed;
            this.damage = damage;
        }
    }

    private static final class Beam {
        final Vec3 from;
        final Vec3 to;
        int age;

        Beam(Vec3 from, Vec3 to) {
            this.from = from;
            this.to = to;
        }
    }

    private final List<Ring> rings = new ArrayList<>();
    private final List<Beam> beams = new ArrayList<>();
    private int phase = 1;
    private int comboLeft;
    private int singularityTimer;
    private int stormTimer;
    @Nullable
    private BlockPos core;

    public MirrorSovereign(EntityType<? extends MirrorSovereign> type, Level level) {
        super(type, level);
        this.xpReward = 3000;
        setNoGravity(true);
        bossEvent.setName(Component.translatable("entity.darkrifts.mirror_sovereign"));
        bossEvent.setColor(BossEvent.BossBarColor.RED);
        bossEvent.setOverlay(BossEvent.BossBarOverlay.NOTCHED_20);
    }

    public static AttributeSupplier.Builder createSovereignAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 1600.0)
                .add(Attributes.ATTACK_DAMAGE, 24.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)          // stationary
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ARMOR, 18.0)
                .add(Attributes.ARMOR_TOUGHNESS, 8.0)
                .add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.ATTACK_KNOCKBACK, 2.0);
    }

    /** Y of the dais surface under the boss. */
    private double floorY() {
        return getY() - HOVER;
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, state ->
                state.setAndContinue(isEmerging() ? S_EMERGE : S_IDLE)));
        controllers.add(new AnimationController<>(this, "ambient", 0, state -> state.setAndContinue(S_AMBIENT)));
        controllers.add(new AnimationController<>(this, "action", 0, state -> PlayState.STOP)
                .triggerableAnim("attack", S_ATTACK)
                .triggerableAnim("slam", S_SLAM));
    }

    public void setCore(BlockPos core) {
        this.core = core;
    }

    @Override
    protected double startOffset() {
        return START_UP;
    }

    @Override
    protected double castRange() {
        return 48.0;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, false));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 48.0F));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    /** Explosions (and everything else) set the velocity directly, so knockback resistance is not enough. */
    @Override
    public void setDeltaMovement(Vec3 motion) {
        if (landed && !isEmerging()) {
            super.setDeltaMovement(Vec3.ZERO);
        } else {
            super.setDeltaMovement(motion);
        }
    }

    @Override
    public void push(double x, double y, double z) {
        // never pushed
    }

    @Override
    public void knockback(double strength, double x, double z) {
        // never knocked back
    }

    // ------------------------------------------------------------------ fighting

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (isEmerging() || !(level() instanceof ServerLevel sl)) {
            return;
        }
        float hp = getHealth() / getMaxHealth();
        if (phase == 1 && hp < 0.66F) {
            enterPhase(sl, 2);
        } else if (phase == 2 && hp < 0.33F) {
            enterPhase(sl, 3);
        }
        // belt and braces: if anything ever moved it, put it back on its hover point
        if (landed && (Math.abs(getX() - anchorX) > 0.05 || Math.abs(getY() - groundY) > 0.05 || Math.abs(getZ() - anchorZ) > 0.05)) {
            setPos(anchorX, groundY, anchorZ);
            hurtMarked = true;
        }
        tickRings(sl);
        tickBeams(sl);
        if (singularityTimer > 0) {
            tickSingularity(sl);
        }
        if (stormTimer > 0) {
            tickStorm(sl);
        }
        // the aura of the boss: heavy embers and void
        if (tickCount % 2 == 0) {
            sl.sendParticles(RED, getX(), getY() + 4.0, getZ(), 2, 2.2, 3.5, 2.2, 0.0);
            sl.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 4.0, getZ(), 4, 2.5, 3.8, 2.5, 0.2);
        }
        if (tickCount % 3 == 0) {
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), floorY() + 0.3, getZ(), 2, 3.0, 0.1, 3.0, 0.01);
        }
        // nobody stays in the arena for free: players who wander off the plateau are reminded who rules it
        if (tickCount % 100 == 0 && getTarget() == null) {
            for (ServerPlayer p : sl.players()) {
                if (p.distanceToSqr(this) < 60.0 * 60.0 && !p.isCreative() && !p.isSpectator()) {
                    setTarget(p);
                    break;
                }
            }
        }
    }

    private void enterPhase(ServerLevel sl, int newPhase) {
        phase = newPhase;
        enraged = newPhase >= 3;
        cooldown = 30;
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 10.0F, 0.4F);
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 10.0F, 0.5F);
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 4.0, getZ(), 4, 3.0, 3.0, 3.0, 0.0);
        sl.sendParticles(RED, getX(), getY() + 4.0, getZ(), 200, 3.0, 4.0, 3.0, 0.0);
        RiftWaves.start(sl, position().add(0, 1.0, 0), 22.0);
        RiftFx.screen(sl, position(), 90.0, 2.5F, 0.6F, 0xFF8080);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4.0;
            RiftFx.bolt(sl, getX() + Math.cos(a) * 10.0, floorY(), getZ() + Math.sin(a) * 10.0);
        }
        for (ServerPlayer p : sl.players()) {
            if (p.distanceToSqr(this) < 80.0 * 80.0) {
                p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 15));
                p.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("message.darkrifts.sovereign_phase" + newPhase)
                        .withStyle(ChatFormatting.DARK_RED)));
                p.connection.send(new ClientboundSetSubtitleTextPacket(Component.empty()));
            }
        }
    }

    @Override
    protected void enrage(ServerLevel sl) {
        // phases are handled in customServerAiStep
        enraged = true;
    }

    @Override
    protected void land(ServerLevel sl) {
        landed = true;
        noPhysics = false;
        setNoGravity(true);
        setPos(anchorX, groundY, anchorZ);            // groundY is the hover height, see MirrorSanctum / the core
        triggerAnim("action", "slam");
        double fy = floorY();
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 10.0F, 0.45F);
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 10.0F, 0.5F);
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), fy + 1.0, getZ(), 6, 5.0, 0.5, 5.0, 0.0);
        RiftWaves.start(sl, new Vec3(getX(), fy + 0.5, getZ()), 26.0);
        RiftFx.screen(sl, position(), 100.0, 3.0F, 0.7F, 0xFFA0A0);
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6.0;
            RiftSpike.burst(sl, this, getX() + Math.cos(a) * 9.0, fy, getZ() + Math.sin(a) * 9.0, 4 + random.nextInt(6));
        }
        for (ServerPlayer p : sl.players()) {
            if (p.distanceToSqr(this) < 90.0 * 90.0) {
                p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 25));
                p.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("entity.darkrifts.mirror_sovereign")
                        .withStyle(ChatFormatting.DARK_RED)));
                p.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("message.darkrifts.sovereign_sub")
                        .withStyle(ChatFormatting.GRAY)));
            }
        }
        cooldown = 70;
    }

    @Override
    protected void cast(ServerLevel sl, LivingEntity target) {
        lookAt(target, 60.0F, 60.0F);
        int pick = random.nextInt(phase == 1 ? 4 : 5);
        switch (pick) {
            case 0 -> voidRain(sl);
            case 1 -> riftBeams(sl);
            case 2 -> shockRings(sl);
            case 3 -> {
                singularityTimer = 60;
                triggerAnim("action", "slam");
                sl.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 8.0F, 0.5F);
            }
            default -> {
                stormTimer = 90;
                sl.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 8.0F, 0.5F);
            }
        }
        if (phase >= 3 && comboLeft == 0 && random.nextInt(3) == 0) {
            comboLeft = 1;
        }
        if (comboLeft > 0) {
            comboLeft--;
            cooldown = 18;
        } else {
            cooldown = phase == 1 ? 110 + random.nextInt(40) : phase == 2 ? 80 + random.nextInt(30) : 55 + random.nextInt(25);
        }
    }

    /** Spikes rain on every player (and a few around them), with a column of red light as a warning. */
    private void voidRain(ServerLevel sl) {
        triggerAnim("action", "attack");
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 8.0F, 0.6F);
        for (Player p : sl.getEntitiesOfClass(Player.class, getBoundingBox().inflate(50.0))) {
            if (p.isCreative() || p.isSpectator()) {
                continue;
            }
            int n = 3 + phase * 2;
            for (int i = 0; i < n; i++) {
                double a = random.nextDouble() * Math.PI * 2.0;
                double r = i == 0 ? 0.0 : 1.5 + random.nextDouble() * 5.0;
                double x = p.getX() + Math.cos(a) * r;
                double z = p.getZ() + Math.sin(a) * r;
                RiftSpike.burst(sl, this, x, p.getY(), z, 14 + i * 3 + random.nextInt(6));
                for (int h = 0; h < 12; h++) {
                    sl.sendParticles(RED, x, p.getY() + h * 1.4, z, 1, 0.05, 0.0, 0.05, 0.0);
                }
            }
        }
    }

    /** Lines of void from the boss to each player's position: telegraphed for a second, then they cut. */
    private void riftBeams(ServerLevel sl) {
        triggerAnim("action", "attack");
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 8.0F, 0.8F);
        Vec3 from = position().add(0.0, 6.0, 0.0);
        for (Player p : sl.getEntitiesOfClass(Player.class, getBoundingBox().inflate(50.0))) {
            if (p.isCreative() || p.isSpectator()) {
                continue;
            }
            for (int i = 0; i < (phase >= 3 ? 3 : 1); i++) {
                Vec3 to = p.position().add((random.nextDouble() - 0.5) * (i * 5.0), 1.0, (random.nextDouble() - 0.5) * (i * 5.0));
                beams.add(new Beam(from, to));
            }
        }
    }

    private void tickBeams(ServerLevel sl) {
        Iterator<Beam> it = beams.iterator();
        while (it.hasNext()) {
            Beam b = it.next();
            b.age++;
            Vec3 d = b.to.subtract(b.from);
            double len = d.length();
            Vec3 dir = d.normalize();
            // telegraph: thin red line; fire: thick white-violet line along an extended ray
            boolean fire = b.age >= 24;
            int steps = (int) (len * (fire ? 3 : 1));
            for (int i = 0; i < steps; i += fire ? 1 : 2) {
                Vec3 q = b.from.add(dir.scale(i / (double) (fire ? 3 : 1) * 1.0));
                sl.sendParticles(fire ? ParticleTypes.END_ROD : ParticleTypes.ELECTRIC_SPARK, q.x, q.y, q.z, 1, 0.05, 0.05, 0.05, 0.0);
                if (fire && i % 2 == 0) {
                    sl.sendParticles(VIOLET, q.x, q.y, q.z, 1, 0.2, 0.2, 0.2, 0.0);
                }
            }
            if (b.age == 24) {
                sl.playSound(null, b.to.x, b.to.y, b.to.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 6.0F, 0.8F);
                Vec3 end = b.from.add(dir.scale(len + 6.0));
                for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(b.from, end).inflate(1.5))) {
                    if (e == this || e.isAlliedTo(this) || e instanceof MirrorSovereign) {
                        continue;
                    }
                    if (distanceToLine(e.position().add(0, e.getBbHeight() / 2.0, 0), b.from, dir, len + 6.0) < 1.8) {
                        e.hurt(damageSources().indirectMagic(this, this), 16.0F + phase * 3.0F);
                        e.setDeltaMovement(e.getDeltaMovement().add(dir.x * 0.6, 0.5, dir.z * 0.6));
                        e.hurtMarked = true;
                    }
                }
            }
            if (b.age > 30) {
                it.remove();
            }
        }
    }

    private static double distanceToLine(Vec3 p, Vec3 origin, Vec3 dir, double length) {
        Vec3 ap = p.subtract(origin);
        double t = Math.max(0.0, Math.min(length, ap.dot(dir)));
        return ap.subtract(dir.scale(t)).length();
    }

    /** Expanding rings along the ground: jump over them (or take the damage). */
    private void shockRings(ServerLevel sl) {
        triggerAnim("action", "slam");
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 8.0F, 0.5F);
        int n = phase == 1 ? 2 : 3;
        for (int i = 0; i < n; i++) {
            Ring r = new Ring(0.55 + i * 0.0, 12.0F + phase * 3.0F);
            r.radius = -i * 6.0;
            rings.add(r);
        }
    }

    private void tickRings(ServerLevel sl) {
        Iterator<Ring> it = rings.iterator();
        while (it.hasNext()) {
            Ring r = it.next();
            r.radius += r.speed;
            if (r.radius < 1.0) {
                continue;
            }
            if (r.radius > 32.0) {
                it.remove();
                continue;
            }
            int pts = (int) Math.max(24, r.radius * 6);
            for (int i = 0; i < pts; i++) {
                double a = i * Math.PI * 2.0 / pts;
                double x = getX() + Math.cos(a) * r.radius;
                double z = getZ() + Math.sin(a) * r.radius;
                sl.sendParticles(RED, x, floorY() + 0.2, z, 1, 0, 0.05, 0, 0.0);
                if (i % 3 == 0) {
                    sl.sendParticles(ParticleTypes.REVERSE_PORTAL, x, floorY() + 0.3, z, 1, 0.1, 0.1, 0.1, 0.05);
                }
                if (i % 6 == 0) {
                    sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OBSIDIAN.defaultBlockState()),
                            x, floorY() + 0.3, z, 1, 0.1, 0.1, 0.1, 0.2);
                }
            }
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(r.radius + 2.0, 3.0, r.radius + 2.0))) {
                if (e == this || e instanceof MirrorSovereign || e.isAlliedTo(this)) {
                    continue;
                }
                double d = Math.sqrt(e.distanceToSqr(getX(), e.getY(), getZ()));
                if (Math.abs(d - r.radius) < 1.3 && e.getY() < floorY() + 1.4) {
                    e.hurt(damageSources().mobAttack(this), r.damage);
                    Vec3 away = e.position().subtract(position()).multiply(1, 0, 1).normalize();
                    e.setDeltaMovement(e.getDeltaMovement().add(away.scale(0.9)).add(0, 0.5, 0));
                    e.hurtMarked = true;
                }
            }
        }
    }

    /** Everything is dragged towards the boss; at the end a huge slam. */
    private void tickSingularity(ServerLevel sl) {
        singularityTimer--;
        double y = getY() + 4.0;
        for (int i = 0; i < 10; i++) {
            double a = random.nextDouble() * Math.PI * 2.0;
            double r = 4.0 + random.nextDouble() * 20.0;
            sl.sendParticles(ParticleTypes.REVERSE_PORTAL, getX() + Math.cos(a) * r, y - 3.0 + random.nextDouble() * 4.0,
                    getZ() + Math.sin(a) * r, 0, -Math.cos(a), 0.0, -Math.sin(a), 0.9);
        }
        sl.sendParticles(BLACK, getX(), y, getZ(), 3, 1.2, 1.2, 1.2, 0.0);
        for (Player p : sl.getEntitiesOfClass(Player.class, getBoundingBox().inflate(40.0))) {
            if (p.isCreative() || p.isSpectator()) {
                continue;
            }
            Vec3 d = position().subtract(p.position()).multiply(1, 0, 1);
            if (d.lengthSqr() > 9.0) {
                p.setDeltaMovement(p.getDeltaMovement().add(d.normalize().scale(0.16)));
                p.hurtMarked = true;
            }
        }
        if (singularityTimer == 0) {
            float dmg = 20.0F + phase * 4.0F;
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(12.0, 3.0, 12.0))) {
                if (e == this || e instanceof MirrorSovereign || e.isAlliedTo(this) || e.distanceToSqr(this) > 144.0) {
                    continue;
                }
                e.hurt(damageSources().mobAttack(this), dmg);
                Vec3 d = e.position().subtract(position()).multiply(1, 0, 1);
                if (d.lengthSqr() > 1.0E-4) {
                    e.setDeltaMovement(e.getDeltaMovement().add(d.normalize().scale(2.0)).add(0, 0.9, 0));
                    e.hurtMarked = true;
                }
            }
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.HOSTILE, 8.0F, 0.5F);
            sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), floorY() + 1.0, getZ(), 4, 3.0, 0.5, 3.0, 0.0);
            RiftWaves.start(sl, position().add(0, 0.5, 0), 20.0);
            RiftFx.screen(sl, position(), 80.0, 2.0F, 0.5F, 0xFF9090);
            triggerAnim("action", "slam");
            for (int i = 0; i < 10; i++) {
                double a = i * Math.PI / 5.0;
                RiftSpike.burst(sl, this, getX() + Math.cos(a) * 6.0, floorY(), getZ() + Math.sin(a) * 6.0, 2 + random.nextInt(4));
            }
        }
    }

    /** Lightning from the four towers onto random spots near the players. */
    private void tickStorm(ServerLevel sl) {
        stormTimer--;
        if (stormTimer % 6 != 0) {
            return;
        }
        for (Player p : sl.getEntitiesOfClass(Player.class, getBoundingBox().inflate(50.0))) {
            if (p.isCreative() || p.isSpectator()) {
                continue;
            }
            double x = p.getX() + (random.nextDouble() - 0.5) * 8.0;
            double z = p.getZ() + (random.nextDouble() - 0.5) * 8.0;
            RiftFx.bolt(sl, x, p.getY(), z);
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(x - 2, p.getY() - 1, z - 2, x + 2, p.getY() + 3, z + 2))) {
                if (e != this && !(e instanceof MirrorSovereign) && !e.isAlliedTo(this)) {
                    e.hurt(damageSources().lightningBolt(), 10.0F + phase * 2.0F);
                }
            }
            sl.sendParticles(RED, x, p.getY() + 0.2, z, 12, 1.0, 0.1, 1.0, 0.0);
        }
    }

    // ------------------------------------------------------------------ death: the Sanctum resets

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!(level() instanceof ServerLevel sl)) {
            return;
        }
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDER_DRAGON_DEATH, SoundSource.HOSTILE, 12.0F, 0.4F);
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 4.0, getZ(), 8, 4.0, 4.0, 4.0, 0.0);
        sl.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 4.0, getZ(), 600, 3.0, 5.0, 3.0, 0.6);
        sl.sendParticles(RED, getX(), getY() + 4.0, getZ(), 300, 3.0, 5.0, 3.0, 0.0);
        RiftWaves.start(sl, position().add(0, 1.0, 0), 30.0);
        RiftFx.screen(sl, position(), 110.0, 3.0F, 1.0F, 0xFFFFFF);
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8.0;
            RiftFx.bolt(sl, getX() + Math.cos(a) * 12.0, floorY(), getZ() + Math.sin(a) * 12.0);
        }
        for (ServerPlayer p : sl.players()) {
            if (p.distanceToSqr(this) < 100.0 * 100.0) {
                p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 80, 30));
                p.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("message.darkrifts.sovereign_dead")
                        .withStyle(ChatFormatting.GOLD)));
                p.connection.send(new ClientboundSetSubtitleTextPacket(Component.empty()));
            }
        }
        // the Sanctum can be used again: the altar returns and the towers forget their hearts
        BlockPos c = core != null ? core : MirrorSanctum.corePos(MirrorSanctum.data(sl).y0);
        if (sl.getBlockState(c).isAir()) {
            sl.setBlock(c, ModBlocks.SANCTUM_CORE.get().defaultBlockState(), 3);
        }
        for (BlockPos p : MirrorSanctum.pedestals(c)) {
            if (sl.getBlockEntity(p) instanceof com.darkrifts.block.SanctumTowerBlockEntity t) {
                t.setFilled(false);
            }
        }
    }

    // ------------------------------------------------------------------ persistence

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Phase", phase);
        if (core != null) {
            tag.putLong("Core", core.asLong());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        phase = Math.max(1, tag.getInt("Phase"));
        if (tag.contains("Core")) {
            core = BlockPos.of(tag.getLong("Core"));
        }
        bossEvent.setName(Component.translatable("entity.darkrifts.mirror_sovereign"));
    }
}
