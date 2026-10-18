package com.darkrifts.ability;

import com.darkrifts.block.RiftBlock;
import com.darkrifts.world.RiftWaves;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import com.darkrifts.world.Seal;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Selection state and the server-side effect of every ability. */
public final class Abilities {
    /** What the local client believes is selected (mirrored to the server by a packet). */
    public static volatile Ability clientSelected = Ability.RIFT;

    private static final Map<UUID, Ability> SELECTED = new ConcurrentHashMap<>();
    private static final DustParticleOptions VOID_DUST = new DustParticleOptions(new Vector3f(0.18F, 0.0F, 0.35F), 1.8F);
    private static final DustParticleOptions VIOLET_DUST = new DustParticleOptions(new Vector3f(0.65F, 0.2F, 1.0F), 1.2F);

    private Abilities() {}

    public static Ability selected(Player player) {
        return SELECTED.getOrDefault(player.getUUID(), Ability.RIFT);
    }

    public static void select(ServerPlayer player, int id) {
        Ability[] all = Ability.values();
        if (id < 0 || id >= all.length) {
            return;
        }
        SELECTED.put(player.getUUID(), all[id]);
        player.displayClientMessage(Component.translatable("message.darkrifts.ability_selected",
                Component.translatable(all[id].key())), true);
    }

    public static void forget(UUID id) {
        SELECTED.remove(id);
    }

    /** Casts an instant ability (everything except RIFT, which is drawn by holding the button). */
    public static void cast(Ability ability, ServerLevel level, ServerPlayer player, ItemStack stack, InteractionHand hand) {
        boolean ok = switch (ability) {
            case SPIKES -> spikes(level, player);
            case STEP -> step(level, player);
            case PULL -> pull(level, player);
            case CLOAK -> cloak(level, player);
            case SEAL -> seal(level, player);
            case WAVE -> wave(level, player);
            case SLASH -> slash(level, player);
            case STRIKE -> strike(level, player);
            case DRAIN -> drain(level, player);
            case SHIELD -> shield(level, player);
            default -> false;
        };
        if (!ok) {
            return;
        }
        player.getCooldowns().addCooldown(stack.getItem(), ability.cooldown);
        EquipmentSlot slot = hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
        stack.hurtAndBreak(3, player, slot);
    }

    // ------------------------------------------------------------------ spikes

    private static boolean spikes(ServerLevel level, ServerPlayer player) {
        RandomSource random = level.getRandom();
        Vec3 c = player.position();
        long now = level.getGameTime();
        level.playSound(null, player.blockPosition(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.8F, 1.6F);
        double[] radii = {2.5, 4.5};
        int[] counts = {6, 10};
        for (int ring = 0; ring < radii.length; ring++) {
            for (int i = 0; i < counts[ring]; i++) {
                double ang = (i + ring * 0.5) * (Math.PI * 2.0 / counts[ring]);
                double x = c.x + Math.cos(ang) * radii[ring];
                double z = c.z + Math.sin(ang) * radii[ring];
                double y = groundY(level, x, c.y, z);
                int delay = (int) (radii[ring] * 2.0) + random.nextInt(4);
                riftWithSpikes(level, player, x, y, z, delay, random);
            }
        }
        return true;
    }

    /** Opens a real (temporary) rift on the ground and, once it has torn open, a spike bursts out of it. */
    private static void riftWithSpikes(ServerLevel level, ServerPlayer player, double x, double y, double z,
                                       int delay, RandomSource random) {
        long now = level.getGameTime();
        RiftBlock.placeDecorColumn(level, BlockPos.containing(x, y, z), 3, now + 45, now + delay, random);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y + 0.6, z, 10, 0.25, 0.5, 0.25, 0.12);
        level.addFreshEntity(new EvokerFangs(level, x, y, z, random.nextFloat() * 360.0F, 10 + delay, player));
    }

    private static double groundY(ServerLevel level, double x, double y, double z) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int fy = Mth.floor(y);
        for (int dy = 2; dy >= -4; dy--) {
            m.set(Mth.floor(x), fy + dy, Mth.floor(z));
            if (!level.getBlockState(m).isAir() && level.getBlockState(m.above()).isAir()) {
                return m.getY() + 1.0;
            }
        }
        return y;
    }

    // ------------------------------------------------------------------ step

    private static boolean step(ServerLevel level, ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double max = 11.0;
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(max)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double reach = hit.getType() == HitResult.Type.MISS ? max : eye.distanceTo(hit.getLocation()) - 0.7;
        double eyeH = player.getEyeHeight();
        for (double t = reach; t >= 1.5; t -= 0.5) {
            double x = eye.x + look.x * t;
            double y = eye.y + look.y * t - eyeH;
            double z = eye.z + look.z * t;
            if (level.noCollision(player, player.getBoundingBox().move(x - player.getX(), y - player.getY(), z - player.getZ()))) {
                Vec3 from = player.position().add(0, 1, 0);
                trail(level, from, new Vec3(x, y + 1, z));
                level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 0.6F);
                player.teleportTo(x, y, z);
                player.fallDistance = 0.0F;
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y + 1, z, 30, 0.4, 0.8, 0.4, 0.2);
                level.sendParticles(ParticleTypes.FLASH, x, y + 1, z, 1, 0, 0, 0, 0);
                level.playSound(null, BlockPos.containing(x, y, z), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.2F);
                return true;
            }
        }
        player.displayClientMessage(Component.translatable("message.darkrifts.no_room"), true);
        return false;
    }

    private static void trail(ServerLevel level, Vec3 a, Vec3 b) {
        int steps = Math.max(2, (int) (a.distanceTo(b) * 2.0));
        for (int i = 0; i <= steps; i++) {
            Vec3 p = a.lerp(b, i / (double) steps);
            level.sendParticles(i % 2 == 0 ? VOID_DUST : VIOLET_DUST, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
            if (i % 3 == 0) {
                level.sendParticles(ParticleTypes.PORTAL, p.x, p.y, p.z, 3, 0.2, 0.2, 0.2, 0.3);
            }
        }
    }

    // ------------------------------------------------------------------ pull

    private static boolean pull(ServerLevel level, ServerPlayer player) {
        Vec3 flat = new Vec3(player.getLookAngle().x, 0, player.getLookAngle().z);
        flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
        Vec3 center = player.position().add(flat.scale(4.0)).add(0, 1.0, 0);
        level.playSound(null, player.blockPosition(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 1.0F, 0.5F);

        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(10.0),
                e -> e != player && e.isAlive() && !e.isAlliedTo(player) && e.distanceToSqr(player) < 100.0);
        for (LivingEntity e : targets) {
            Vec3 d = center.subtract(e.position());
            Vec3 dir = d.lengthSqr() < 1.0E-4 ? Vec3.ZERO : d.normalize();
            double power = Math.min(1.7, 0.5 + d.length() * 0.16);
            e.setDeltaMovement(dir.x * power, 0.35 + dir.y * 0.2, dir.z * power);
            e.hurtMarked = true;
            e.hurt(level.damageSources().indirectMagic(player, player), 5.0F);
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
        }
        // a black hole at the centre, matter streaming into it
        RandomSource random = level.getRandom();
        for (int i = 0; i < 36; i++) {
            double theta = random.nextDouble() * Math.PI * 2.0;
            double phi = Math.acos(2.0 * random.nextDouble() - 1.0);
            double dx = Math.sin(phi) * Math.cos(theta);
            double dy = Math.cos(phi);
            double dz = Math.sin(phi) * Math.sin(theta);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x + dx * 5, center.y + dy * 3, center.z + dz * 5,
                    0, -dx, -dy, -dz, 0.9);
        }
        level.sendParticles(VOID_DUST, center.x, center.y, center.z, 25, 0.5, 0.5, 0.5, 0.0);
        level.sendParticles(ParticleTypes.SQUID_INK, center.x, center.y, center.z, 12, 0.4, 0.4, 0.4, 0.05);
        level.sendParticles(ParticleTypes.FLASH, center.x, center.y, center.z, 1, 0, 0, 0, 0);
        return true;
    }

    // ------------------------------------------------------------------ helpers

    private static LivingEntity findTarget(ServerLevel level, ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(range));
        BlockHitResult blockHit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        if (blockHit.getType() != HitResult.Type.MISS) {
            end = blockHit.getLocation();
        }
        AABB box = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, player, eye, end, box,
                e -> e instanceof LivingEntity && e != player && e.isAlive() && !e.isSpectator() && e.isPickable());
        return hit == null ? null : (LivingEntity) hit.getEntity();
    }

    private static double distToSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double len2 = ab.lengthSqr();
        double t = len2 < 1.0E-6 ? 0.0 : Mth.clamp(p.subtract(a).dot(ab) / len2, 0.0, 1.0);
        return p.distanceTo(a.add(ab.scale(t)));
    }

    // ------------------------------------------------------------------ wave

    private static boolean wave(ServerLevel level, ServerPlayer player) {
        RandomSource random = level.getRandom();
        Vec3 look = player.getLookAngle();
        Vec3 dir = new Vec3(look.x, 0, look.z);
        dir = dir.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : dir.normalize();
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        level.playSound(null, player.blockPosition(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.8F, 1.2F);
        for (int i = 1; i <= 9; i++) {
            Vec3 p = player.position().add(dir.scale(i * 1.7));
            double y = groundY(level, p.x, player.getY(), p.z);
            int delay = i * 2;
            riftWithSpikes(level, player, p.x, y, p.z, delay, random);
            for (int s = -1; s <= 1; s += 2) {
                double sx = p.x + side.x * 1.0 * s;
                double sz = p.z + side.z * 1.0 * s;
                level.addFreshEntity(new EvokerFangs(level, sx, groundY(level, sx, player.getY(), sz), sz,
                        random.nextFloat() * 360.0F, 10 + delay, player));
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ slash

    private static boolean slash(ServerLevel level, ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 start = eye.add(look.scale(0.8));
        double length = 13.0;
        BlockHitResult hit = level.clip(new ClipContext(start, start.add(look.scale(length)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.MISS) {
            length = Math.max(2.0, start.distanceTo(hit.getLocation()) - 0.3);
        }
        Vec3 end = start.add(look.scale(length));

        java.util.List<BlockPos> cells = new java.util.ArrayList<>();
        BlockPos last = null;
        for (double t = 0.0; t <= length; t += 0.5) {
            BlockPos cell = BlockPos.containing(start.add(look.scale(t)));
            if (!cell.equals(last)) {
                cells.add(cell);
                last = cell;
            }
        }
        long now = level.getGameTime();
        RiftBlock.placeDecorCells(level, cells, now + 22, now, level.getRandom());
        level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.8F);
        trail(level, start, end);

        AABB box = new AABB(start, end).inflate(2.0);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
                t -> t != player && t.isAlive() && !t.isAlliedTo(player))) {
            if (distToSegment(e.getBoundingBox().getCenter(), start, end) <= 1.6) {
                e.hurt(level.damageSources().indirectMagic(player, player), 10.0F);
                e.setDeltaMovement(look.x * 0.7, 0.3, look.z * 0.7);
                e.hurtMarked = true;
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(),
                        12, 0.3, 0.4, 0.3, 0.3);
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ strike

    private static boolean strike(ServerLevel level, ServerPlayer player) {
        LivingEntity target = findTarget(level, player, 24.0);
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.darkrifts.no_target"), true);
            return false;
        }
        long now = level.getGameTime();
        RiftBlock.placeDecorColumn(level, target.blockPosition(), 3, now + 30, now, level.getRandom());
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(target.getX(), target.getY(), target.getZ());
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
        target.hurt(level.damageSources().indirectMagic(player, player), 11.0F);
        Vec3 c = target.position().add(0, target.getBbHeight() * 0.5, 0);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 30, 0.4, 0.7, 0.4, 0.3);
        RiftWaves.start(level, c, 3.5);
        return true;
    }

    // ------------------------------------------------------------------ drain

    private static boolean drain(ServerLevel level, ServerPlayer player) {
        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(7.0),
                e -> e != player && e.isAlive() && !e.isAlliedTo(player) && e.distanceToSqr(player) < 49.0);
        if (victims.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.darkrifts.no_target"), true);
            return false;
        }
        victims.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(player)));
        int count = Math.min(5, victims.size());
        for (int i = 0; i < count; i++) {
            LivingEntity e = victims.get(i);
            e.hurt(level.damageSources().indirectMagic(player, player), 4.0F);
            player.heal(2.0F);
            trail(level, e.position().add(0, e.getBbHeight() * 0.5, 0), player.position().add(0, 1.0, 0));
        }
        level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_STARE, SoundSource.PLAYERS, 0.6F, 1.5F);
        return true;
    }

    // ------------------------------------------------------------------ shield

    private static boolean shield(ServerLevel level, ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 120, 0));
        level.playSound(null, player.blockPosition(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.8F, 0.9F);
        RiftWaves.start(level, player.position().add(0, 1.0, 0), 4.0);
        MinecraftServer server = level.getServer();
        int now = server.getTickCount();
        for (int i = 0; i < 16; i++) {
            final int pulse = i;
            server.tell(new TickTask(now + i * 5, () -> shieldPulse(level, player, pulse)));
        }
        return true;
    }

    private static void shieldPulse(ServerLevel level, ServerPlayer player, int pulse) {
        if (!player.isAlive() || player.isRemoved() || player.level() != level) {
            return;
        }
        Vec3 c = player.position().add(0, 1.0, 0);
        for (int k = 0; k < 18; k++) {
            double a = pulse * 0.6 + k * (Math.PI * 2.0 / 18.0);
            double y = c.y + Math.sin(a * 2.0 + pulse) * 0.7;
            level.sendParticles(k % 3 == 0 ? VIOLET_DUST : VOID_DUST,
                    c.x + Math.cos(a) * 3.0, y, c.z + Math.sin(a) * 3.0, 1, 0, 0, 0, 0);
        }
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 6, 1.2, 0.8, 1.2, 0.1);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(3.2),
                t -> t != player && t.isAlive() && !t.isAlliedTo(player))) {
            Vec3 away = e.position().subtract(player.position());
            away = away.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : new Vec3(away.x, 0, away.z).normalize();
            e.setDeltaMovement(away.x * 0.9, 0.3, away.z * 0.9);
            e.hurtMarked = true;
            e.hurt(level.damageSources().indirectMagic(player, player), 2.0F);
        }
    }

    // ------------------------------------------------------------------ seal

    private static boolean seal(ServerLevel level, ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double range = 24.0;
        Vec3 end = eye.add(look.scale(range));
        BlockHitResult blockHit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        if (blockHit.getType() != HitResult.Type.MISS) {
            end = blockHit.getLocation();
        }
        AABB box = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, player, eye, end, box,
                e -> e instanceof LivingEntity && e != player && e.isAlive() && !e.isSpectator() && e.isPickable());
        if (hit == null) {
            player.displayClientMessage(Component.translatable("message.darkrifts.no_target"), true);
            return false;
        }
        LivingEntity target = (LivingEntity) hit.getEntity();
        trail(level, eye.add(look.scale(0.6)).add(0, -0.2, 0), target.position().add(0, target.getBbHeight() * 0.5, 0));
        level.playSound(null, target.blockPosition(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 1.0F, 0.7F);

        // The tear closes around the target for 0.7 s, then it is gone.
        MinecraftServer server = level.getServer();
        int now = server.getTickCount();
        final int frames = 14;
        for (int i = 0; i < frames; i++) {
            final int frame = i;
            server.tell(new TickTask(now + i, () -> {
                if (target.level() instanceof ServerLevel tl && target.isAlive()) {
                    vortex(tl, target, frame, frames);
                }
            }));
        }
        server.tell(new TickTask(now + frames, () -> {
            if (target.isAlive() && !target.isRemoved() && target.level() instanceof ServerLevel tl) {
                Vec3 c = target.position().add(0, target.getBbHeight() * 0.5, 0);
                tl.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
                RiftWaves.start(tl, c, 5.0);
                Seal.seal(tl, target, player);
            }
        }));
        return true;
    }

    private static void vortex(ServerLevel level, LivingEntity target, int frame, int frames) {
        double t = frame / (double) frames;
        double radius = 1.9 * (1.0 - t) + 0.25;
        for (int k = 0; k < 8; k++) {
            double a = frame * 0.9 + k * (Math.PI / 4.0);
            double x = target.getX() + Math.cos(a) * radius;
            double z = target.getZ() + Math.sin(a) * radius;
            double y = target.getY() + t * target.getBbHeight();
            level.sendParticles(VOID_DUST, x, y, z, 1, 0, 0, 0, 0);
            if (k % 2 == 0) {
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y, z, 1, 0, 0, 0, 0);
            }
        }
        if (frame % 4 == 0) {
            level.sendParticles(VIOLET_DUST, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                    6, 0.4, 0.5, 0.4, 0.0);
        }
    }

    // ------------------------------------------------------------------ cloak

    private static boolean cloak(ServerLevel level, ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 160, 0, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 300, 1));
        for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(8.0),
                m -> m.isAlive() && !m.isAlliedTo(player))) {
            mob.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0));
        }
        Vec3 c = player.position().add(0, 1.0, 0);
        level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 0.4F);
        level.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, 40, 0.5, 0.8, 0.5, 0.08);
        level.sendParticles(VOID_DUST, c.x, c.y, c.z, 40, 0.6, 0.9, 0.6, 0.0);
        RiftWaves.start(level, c, 6.0);
        return true;
    }
}
