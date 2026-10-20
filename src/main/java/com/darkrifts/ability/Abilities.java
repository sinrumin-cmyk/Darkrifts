package com.darkrifts.ability;

import com.darkrifts.block.RiftBlock;
import com.darkrifts.entity.Singularity;
import com.darkrifts.world.RiftFx;
import com.darkrifts.world.RiftWaves;
import net.minecraft.core.particles.ParticleOptions;
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
import com.darkrifts.entity.RiftSpike;
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
            case LANCE -> lance(level, player);
            case SINGULARITY -> singularity(level, player);
            case WHIRL -> whirl(level, player);
            case CROSS -> cross(level, player);
            case SLAM -> slam(level, player);
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
        BlockPos cell = BlockPos.containing(x, Math.floor(y - 1.0E-4) + 1.0, z);
        RiftBlock.placeGroundCrack(level, cell, y, random.nextDouble() * Math.PI, 1.5 + random.nextDouble() * 0.7,
                now + 45, now + delay, random);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y + 0.15, z, 10, 0.5, 0.1, 0.5, 0.12);
        level.addFreshEntity(new RiftSpike(level, x, y, z, random.nextFloat() * 360.0F, 10 + delay, player));
    }

    private static double groundY(ServerLevel level, double x, double y, double z) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int fy = Mth.floor(y);
        for (int dy = 2; dy >= -4; dy--) {
            m.set(Mth.floor(x), fy + dy, Mth.floor(z));
            net.minecraft.world.phys.shapes.VoxelShape shape = level.getBlockState(m).getCollisionShape(level, m);
            if (!shape.isEmpty() && level.getBlockState(m.above()).isAir()) {
                return m.getY() + shape.max(net.minecraft.core.Direction.Axis.Y);
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
                // the tear you leave behind and the one you arrive through
                ring(level, player.position(), 1.1, 18, 0.1, VIOLET_DUST);
                ring(level, player.position(), 0.6, 12, 1.0, VOID_DUST);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, from.x, from.y, from.z, 40, 0.3, 0.8, 0.3, 0.25);
                ring(level, new Vec3(x, y, z), 1.4, 22, 0.1, VIOLET_DUST);
                RiftWaves.start(level, new Vec3(x, y + 0.5, z), 3.0);
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
                level.addFreshEntity(new RiftSpike(level, sx, groundY(level, sx, player.getY(), sz), sz,
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
        // a bright cut: crescents of light racing along the line, a big one at the blade
        Vec3 tilt = basis(look)[0].scale(Math.cos(0.5)).add(basis(look)[1].scale(Math.sin(0.5)));
        crescent(level, start, look, tilt, 2.8);
        for (double t = 2.5; t < length; t += 2.6) {
            final double at = t;
            schedule(level, (int) (t / 2.6), () -> crescent(level, start.add(look.scale(at)), look, tilt, Math.max(1.0, 2.2 - at * 0.08)));
        }
        schedule(level, 5, () -> {
            level.sendParticles(ParticleTypes.FLASH, end.x, end.y, end.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.END_ROD, end.x, end.y, end.z, 24, 0.2, 0.2, 0.2, 0.35);
            RiftWaves.start(level, end, 3.0);
        });

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
        // the column of void and the rings spreading from the impact
        Vec3 base = target.position();
        for (int i = 0; i < 26; i++) {
            level.sendParticles(i % 2 == 0 ? VIOLET_DUST : VOID_DUST, base.x, base.y + i * 0.5, base.z, 2, 0.25, 0.1, 0.25, 0.0);
            if (i % 3 == 0) {
                level.sendParticles(ParticleTypes.END_ROD, base.x, base.y + i * 0.5, base.z, 1, 0.1, 0.1, 0.1, 0.03);
            }
        }
        for (int r = 1; r <= 4; r++) {
            final int rr = r;
            schedule(level, r * 2, () -> {
                ring(level, base, rr * 1.0, 10 + rr * 6, 0.15, rr % 2 == 0 ? VOID_DUST : VIOLET_DUST);
                level.sendParticles(ParticleTypes.CRIT, base.x, base.y + 0.3, base.z, 8, rr * 0.6, 0.1, rr * 0.6, 0.2);
            });
        }
        RiftFx.screen(level, base, 20.0, 0.8F, 0.25F, 0xE0B0FF);
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
            Vec3 from = e.position().add(0, e.getBbHeight() * 0.5, 0);
            Vec3 to = player.position().add(0, 1.0, 0);
            trail(level, from, to);
            Vec3 d = to.subtract(from);
            for (int k = 0; k < 5; k++) {
                Vec3 q = from.add(d.scale(k / 5.0));
                level.sendParticles(ParticleTypes.SCULK_SOUL, q.x, q.y, q.z, 0, d.x * 0.04, d.y * 0.04 + 0.02, d.z * 0.04, 1.0);
            }
            ring(level, e.position(), 0.9, 12, 0.1, VOID_DUST);
            level.sendParticles(ParticleTypes.SOUL, e.getX(), e.getY() + 0.5, e.getZ(), 6, 0.3, 0.5, 0.3, 0.05);
        }
        level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 14, 0.4, 0.6, 0.4, 0.05);
        ring(level, player.position(), 1.0, 16, 0.1, VIOLET_DUST);
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

    // ------------------------------------------------------------------ shared fx helpers

    private static void schedule(ServerLevel level, int delay, Runnable task) {
        MinecraftServer server = level.getServer();
        server.tell(new TickTask(server.getTickCount() + delay, task));
    }

    /** Two unit vectors perpendicular to dir (and to each other): {side, up}. */
    private static Vec3[] basis(Vec3 dir) {
        Vec3 ref = Math.abs(dir.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 side = dir.cross(ref).normalize();
        Vec3 up = side.cross(dir).normalize();
        return new Vec3[]{side, up};
    }

    private static void ring(ServerLevel level, Vec3 c, double radius, int count, double yOff, ParticleOptions p) {
        for (int i = 0; i < count; i++) {
            double a = i * (Math.PI * 2.0 / count);
            level.sendParticles(p, c.x + Math.cos(a) * radius, c.y + yOff, c.z + Math.sin(a) * radius, 1, 0, 0, 0, 0);
        }
    }

    /** A temporary real rift tearing along the segment a-b (the same rift the Rift Blade draws). */
    private static void decorLine(ServerLevel level, Vec3 a, Vec3 b, int lifetime) {
        java.util.List<BlockPos> cells = new java.util.ArrayList<>();
        BlockPos last = null;
        Vec3 d = b.subtract(a);
        int steps = (int) (d.length() * 2.0) + 1;
        for (int i = 0; i <= steps; i++) {
            BlockPos cell = BlockPos.containing(a.add(d.scale(i / (double) steps)));
            if (!cell.equals(last)) {
                cells.add(cell);
                last = cell;
            }
        }
        long now = level.getGameTime();
        RiftBlock.placeDecorCells(level, cells, now + lifetime, now, level.getRandom());
    }

    /** A temporary real rift lying on the ground. */
    private static void groundCrack(ServerLevel level, double x, double y, double z, double yaw, double len, int lifetime) {
        double gy = groundY(level, x, y, z);
        BlockPos cell = BlockPos.containing(x, Math.floor(gy - 1.0E-4) + 1.0, z);
        long now = level.getGameTime();
        RiftBlock.placeGroundCrack(level, cell, gy, yaw, len, now + lifetime, now, level.getRandom());
    }

    private static void sendMotion(ServerPlayer player) {
        player.hurtMarked = true;
    }

    /** A curved cut bulging forward: a crescent of light in the plane spanned by look and tilt. */
    private static void crescent(ServerLevel level, Vec3 origin, Vec3 look, Vec3 tilt, double radius) {
        for (int i = -12; i <= 12; i++) {
            double a = i / 12.0 * 1.2;
            Vec3 p = origin.add(look.scale(Math.cos(a) * radius * 0.7)).add(tilt.scale(Math.sin(a) * radius));
            level.sendParticles(Math.abs(i) % 2 == 0 ? VIOLET_DUST : VOID_DUST, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0.0);
            level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 0, look.x * 0.2, look.y * 0.2, look.z * 0.2, 1.0);
            if (i % 6 == 0) {
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
        }
    }

    private static void line(ServerLevel level, Vec3 a, Vec3 b) {
        int steps = Math.max(2, (int) (a.distanceTo(b) * 4.0));
        for (int i = 0; i <= steps; i++) {
            Vec3 p = a.lerp(b, i / (double) steps);
            level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.01);
            level.sendParticles(i % 2 == 0 ? VIOLET_DUST : VOID_DUST, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
        }
    }

    private static List<LivingEntity> enemiesAround(ServerLevel level, ServerPlayer player, Vec3 c, double r) {
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(c.x - r, c.y - r, c.z - r, c.x + r, c.y + r, c.z + r),
                e -> e != player && e.isAlive() && !e.isAlliedTo(player) && e.position().distanceToSqr(c) <= r * r);
    }

    // ------------------------------------------------------------------ lance (ranged)

    private static boolean lance(ServerLevel level, ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double length = 32.0;
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(length)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.MISS) {
            length = Math.max(2.0, eye.distanceTo(hit.getLocation()));
        }
        final double len = length;
        final Vec3 end = eye.add(look.scale(len));
        final Vec3 start = eye.add(look.scale(0.8)).add(0, -0.25, 0);
        final Vec3[] b = basis(look);

        decorLine(level, start, end, 20);
        level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.9F, 1.5F);
        level.sendParticles(ParticleTypes.FLASH, start.x, start.y, start.z, 1, 0, 0, 0, 0);
        RiftWaves.start(level, start, 2.5);

        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(2.0),
                t -> t != player && t.isAlive() && !t.isAlliedTo(player))) {
            if (distToSegment(e.getBoundingBox().getCenter(), eye, end) <= 1.3) {
                e.hurt(level.damageSources().indirectMagic(player, player), 14.0F);
                e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0));
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(),
                        16, 0.3, 0.4, 0.3, 0.4);
            }
        }
        // the beam is drawn travelling away from you, 4 blocks per tick
        int segs = (int) Math.ceil(len / 4.0);
        for (int sIdx = 0; sIdx < segs; sIdx++) {
            final double t0 = sIdx * 4.0;
            final double t1 = Math.min(len, (sIdx + 1) * 4.0);
            schedule(level, sIdx, () -> {
                for (double t = t0; t < t1; t += 0.3) {
                    Vec3 p = start.add(look.scale(t));
                    level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
                    double ang = t * 2.2;
                    Vec3 off = b[0].scale(Math.cos(ang) * 0.55).add(b[1].scale(Math.sin(ang) * 0.55));
                    level.sendParticles(VIOLET_DUST, p.x + off.x, p.y + off.y, p.z + off.z, 1, 0, 0, 0, 0);
                    level.sendParticles(VOID_DUST, p.x - off.x, p.y - off.y, p.z - off.z, 1, 0, 0, 0, 0);
                    if (((int) (t / 0.3)) % 5 == 0) {
                        level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.x, p.y, p.z, 2, 0.15, 0.15, 0.15, 0.1);
                    }
                }
            });
        }
        schedule(level, segs, () -> {
            level.sendParticles(ParticleTypes.FLASH, end.x, end.y, end.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.END_ROD, end.x, end.y, end.z, 30, 0.2, 0.2, 0.2, 0.4);
            level.sendParticles(ParticleTypes.EXPLOSION, end.x, end.y, end.z, 2, 0.3, 0.3, 0.3, 0.0);
            RiftWaves.start(level, end, 4.0);
            level.playSound(null, BlockPos.containing(end), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.5F, 1.7F);
        });
        return true;
    }

    // ------------------------------------------------------------------ singularity (ranged)

    private static boolean singularity(ServerLevel level, ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double range = 28.0;
        LivingEntity target = findTarget(level, player, range);
        final Vec3 c;
        if (target != null) {
            c = target.position().add(0, target.getBbHeight() * 0.5, 0);
        } else {
            BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(range)),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            c = hit.getType() == HitResult.Type.MISS ? eye.add(look.scale(range)) : hit.getLocation().add(0, 1.4, 0);
        }
        trail(level, eye.add(look.scale(0.6)).add(0, -0.2, 0), c);
        level.playSound(null, player.blockPosition(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 1.0F, 0.6F);
        // a real black hole (solid sphere + violet disc, see SingularityRenderer) with a crack in the ground under it
        groundCrack(level, c.x, c.y, c.z, level.getRandom().nextDouble() * Math.PI, 3.0, Singularity.DURATION + 10);
        level.addFreshEntity(new Singularity(level, c, player));
        return true;
    }

    // ------------------------------------------------------------------ whirl (melee)

    private static boolean whirl(ServerLevel level, ServerPlayer player) {
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.6F);
        level.playSound(null, player.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 1.0F, 1.4F);
        final int ticks = 12;
        for (int i = 0; i < ticks; i++) {
            final int f = i;
            schedule(level, i, () -> whirlTick(level, player, f, ticks));
        }
        return true;
    }

    private static void whirlTick(ServerLevel level, ServerPlayer player, int f, int ticks) {
        if (!player.isAlive() || player.isRemoved() || player.level() != level) {
            return;
        }
        Vec3 c = player.position();
        double step = Math.PI * 4.0 / ticks;            // two full turns
        double y = 1.0 + Math.sin(f * 0.5) * 0.3;
        for (int blade = 0; blade < 2; blade++) {
            for (int j = 0; j < 9; j++) {
                double a = f * step - j * 0.16 + blade * Math.PI;
                double rr = 3.4 + (j == 0 ? 0.3 : 0.0);
                double x = c.x + Math.cos(a) * rr;
                double z = c.z + Math.sin(a) * rr;
                level.sendParticles(j < 3 ? VIOLET_DUST : VOID_DUST, x, c.y + y, z, 1, 0.02, 0.02, 0.02, 0.0);
                if (j == 0) {
                    level.sendParticles(ParticleTypes.END_ROD, x, c.y + y, z, 1, 0.05, 0.05, 0.05, 0.02);
                    if (f % 2 == 0) {
                        level.sendParticles(ParticleTypes.SWEEP_ATTACK, x, c.y + y, z, 1, 0, 0, 0, 0);
                    }
                }
            }
        }
        ring(level, c, 2.2, 14, 0.1, VOID_DUST);
        if (f == 0) {
            RiftWaves.start(level, c.add(0, 0.5, 0), 5.0);
            // a ring of real rifts is cut into the ground under the blades
            for (int i = 0; i < 8; i++) {
                double ang = i * Math.PI / 4.0;
                groundCrack(level, c.x + Math.cos(ang) * 3.2, c.y, c.z + Math.sin(ang) * 3.2, ang + Math.PI / 2.0, 2.2, 26);
            }
        }
        if (f == 3 || f == 7 || f == 11) {
            for (LivingEntity e : enemiesAround(level, player, c.add(0, 1.0, 0), 4.2)) {
                e.hurt(level.damageSources().indirectMagic(player, player), 6.0F);
                Vec3 away = e.position().subtract(c);
                away = away.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : new Vec3(away.x, 0, away.z).normalize();
                e.setDeltaMovement(away.x * 0.8, 0.3, away.z * 0.8);
                e.hurtMarked = true;
                level.sendParticles(ParticleTypes.CRIT, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 10, 0.3, 0.4, 0.3, 0.3);
            }
            level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8F, 0.8F + f * 0.03F);
        }
        if (f == ticks - 1) {
            ring(level, c, 4.0, 40, 0.15, VIOLET_DUST);
            level.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 1.0, c.z, 30, 1.5, 0.5, 1.5, 0.3);
        }
    }

    // ------------------------------------------------------------------ cross (melee)

    private static boolean cross(ServerLevel level, ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double dist = 3.2;
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(dist)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.MISS) {
            dist = Math.max(1.2, eye.distanceTo(hit.getLocation()) - 0.3);
        }
        final Vec3 center = eye.add(look.scale(dist));
        final Vec3[] b = basis(look);
        final double half = 3.4;
        final Vec3 d1 = b[0].scale(-1).add(b[1]).normalize();
        final Vec3 d2 = b[0].add(b[1]).normalize();

        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 1.3F);
        line(level, center.subtract(d1.scale(half)), center.add(d1.scale(half)));
        decorLine(level, center.subtract(d1.scale(half)), center.add(d1.scale(half)), 24);
        schedule(level, 3, () -> {
            decorLine(level, center.subtract(d2.scale(half)), center.add(d2.scale(half)), 22);
            level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 1.7F);
            line(level, center.subtract(d2.scale(half)), center.add(d2.scale(half)));
        });
        schedule(level, 4, () -> {
            level.sendParticles(ParticleTypes.FLASH, center.x, center.y, center.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, 40, 0.3, 0.3, 0.3, 0.5);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z, 40, 0.4, 0.4, 0.4, 0.4);
            for (int k = 0; k < 4; k++) {
                Vec3 tip = center.add((k % 2 == 0 ? d1 : d2).scale(k < 2 ? half : -half));
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, tip.x, tip.y, tip.z, 1, 0, 0, 0, 0);
            }
            RiftWaves.start(level, center, 5.0);
            RiftFx.screen(level, center, 18.0, 0.6F, 0.2F, 0xFFFFFF);
            level.playSound(null, BlockPos.containing(center), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 0.5F);
            for (LivingEntity e : enemiesAround(level, player, center, 3.8)) {
                e.hurt(level.damageSources().indirectMagic(player, player), 15.0F);
                e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));
                e.setDeltaMovement(look.x * 0.9, 0.3, look.z * 0.9);
                e.hurtMarked = true;
            }
        });
        return true;
    }

    // ------------------------------------------------------------------ slam (melee)

    private static boolean slam(ServerLevel level, ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize();
        player.setDeltaMovement(flat.x * 0.7, 1.05, flat.z * 0.7);
        player.fallDistance = 0.0F;
        sendMotion(player);
        level.playSound(null, player.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 1.0F, 0.8F);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 0.2, player.getZ(), 30, 0.4, 0.1, 0.4, 0.2);
        ring(level, player.position(), 1.3, 20, 0.1, VIOLET_DUST);
        java.util.concurrent.atomic.AtomicBoolean done = new java.util.concurrent.atomic.AtomicBoolean(false);
        for (int i = 3; i <= 44; i++) {
            final int f = i;
            schedule(level, i, () -> slamTick(level, player, f, done));
        }
        return true;
    }

    private static void slamTick(ServerLevel level, ServerPlayer player, int f, java.util.concurrent.atomic.AtomicBoolean done) {
        if (done.get()) {
            return;
        }
        if (!player.isAlive() || player.isRemoved() || player.level() != level) {
            done.set(true);
            return;
        }
        player.fallDistance = 0.0F;
        level.sendParticles(VIOLET_DUST, player.getX(), player.getY() + 0.1, player.getZ(), 4, 0.3, 0.1, 0.3, 0.0);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 0.5, player.getZ(), 3, 0.3, 0.5, 0.3, 0.1);
        if (f == 9) {
            Vec3 m = player.getDeltaMovement();
            player.setDeltaMovement(m.x * 0.3, -2.0, m.z * 0.3);
            sendMotion(player);
            level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 0.5F);
        }
        if ((f >= 11 && player.onGround()) || f == 44) {
            done.set(true);
            slamImpact(level, player);
        }
    }

    private static void slamImpact(ServerLevel level, ServerPlayer player) {
        RandomSource random = level.getRandom();
        Vec3 c = player.position();
        player.fallDistance = 0.0F;
        level.playSound(null, player.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.2F, 0.6F);
        level.playSound(null, player.blockPosition(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 1.0F, 0.7F);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.3, c.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.FLASH, c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
        RiftWaves.start(level, c.add(0, 0.5, 0), 9.0);
        RiftFx.screen(level, c, 30.0, 1.8F, 0.4F, 0xE0C0FF);
        for (LivingEntity e : enemiesAround(level, player, c.add(0, 1.0, 0), 6.5)) {
            e.hurt(level.damageSources().indirectMagic(player, player), 13.0F);
            Vec3 away = e.position().subtract(c);
            away = away.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : new Vec3(away.x, 0, away.z).normalize();
            e.setDeltaMovement(away.x * 1.1, 0.7, away.z * 1.1);
            e.hurtMarked = true;
        }
        // a shockwave ring racing outwards, then real rifts with spikes
        for (int r = 1; r <= 7; r++) {
            final int rr = r;
            schedule(level, r, () -> {
                ring(level, c, rr, 12 + rr * 5, 0.15, rr % 2 == 0 ? VOID_DUST : VIOLET_DUST);
                level.sendParticles(ParticleTypes.CRIT, c.x, c.y + 0.3, c.z, 8, rr * 0.6, 0.1, rr * 0.6, 0.2);
            });
        }
        // the ground splits: a star of long cracks around the landing point
        groundCrack(level, c.x, c.y, c.z, random.nextDouble() * Math.PI, 3.0, 50);
        for (int i = 0; i < 6; i++) {
            double ang = i * Math.PI / 3.0 + random.nextDouble() * 0.4;
            groundCrack(level, c.x + Math.cos(ang) * 1.8, c.y, c.z + Math.sin(ang) * 1.8, ang, 3.2, 50);
        }
        double[] radii = {3.0, 5.5};
        int[] counts = {6, 9};
        for (int ring = 0; ring < radii.length; ring++) {
            for (int i = 0; i < counts[ring]; i++) {
                double ang = (i + ring * 0.5) * (Math.PI * 2.0 / counts[ring]);
                double x = c.x + Math.cos(ang) * radii[ring];
                double z = c.z + Math.sin(ang) * radii[ring];
                riftWithSpikes(level, player, x, groundY(level, x, c.y, z), z, 2 + ring * 2 + random.nextInt(3), random);
            }
        }
    }
}
