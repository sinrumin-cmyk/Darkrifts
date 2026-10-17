package com.darkrifts.item;

import com.darkrifts.Config;
import com.darkrifts.DarkDimensions;
import com.darkrifts.block.RiftBlock;
import com.darkrifts.world.RiftWaves;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import com.darkrifts.block.RiftBlockEntity;
import com.darkrifts.registry.ModBlocks;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Hold right mouse button, look at the sky and drag your view down (or in any direction):
 * the rift tears open live along the path your crosshair traces, while you are still holding.
 */
public class RiftBladeItem extends SwordItem {
    private static final int COOLDOWN_TICKS = 40;

    public RiftBladeItem(Properties properties) {
        super(Tiers.NETHERITE, properties);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BLOCK;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            // Sneak + right click on a rift seals it.
            if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer sp) {
                closeRift(serverLevel, sp, stack, hand);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    /** Runs every tick while the button is held: extends the line and tears the rift open along it. */
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        if (!(level instanceof ServerLevel serverLevel) || !(entity instanceof ServerPlayer player)) {
            return;
        }
        long now = level.getGameTime();
        RiftStroke stroke = RiftStroke.update(player, now, Config.STROKE_RADIUS.get());
        if (stroke == null) {
            return;
        }
        RandomSource random = serverLevel.getRandom();

        if (!stroke.started) {
            stroke.started = true;
            stroke.target = DarkDimensions.random(random, serverLevel.dimension());
            int lifetime = Config.RIFT_LIFETIME_SECONDS.get() * 20;
            stroke.expireAt = lifetime > 0 ? now + lifetime : 0L;
            // The sky splits: a bolt of (harmless) lightning strikes where the tear begins.
            strike(serverLevel, stroke.lastPoint());
            serverLevel.sendParticles(ParticleTypes.FLASH, stroke.lastPoint().x, stroke.lastPoint().y,
                    stroke.lastPoint().z, 1, 0, 0, 0, 0);
            serverLevel.playSound(null, BlockPos.containing(stroke.lastPoint()),
                    SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.7F, 1.9F);
        }

        int cap = Config.MAX_RIFT_BLOCKS.get();
        int newBlocks = 0;
        Vec3 from = stroke.previousPoint();
        Vec3 to = stroke.lastPoint();
        double len = from.distanceTo(to);
        if (len > 0.04) {
            double uBase = stroke.length;
            // Split long moves into short pieces so every piece is owned by a cell right next to it.
            int pieces = Math.max(1, (int) Math.ceil(len / 1.1));
            for (int i = 0; i < pieces; i++) {
                Vec3 a = from.lerp(to, i / (double) pieces);
                Vec3 b = from.lerp(to, (i + 1) / (double) pieces);
                BlockPos owner = null;
                for (BlockPos pos : line(a, b)) {
                    if (!serverLevel.isInWorldBounds(pos)) {
                        continue;
                    }
                    BlockState existing = serverLevel.getBlockState(pos);
                    if (existing.is(ModBlocks.RIFT.get())) {
                        if (owner == null) {
                            owner = pos;
                        }
                        continue;
                    }
                    if (stroke.placed >= cap || !(existing.isAir() || existing.canBeReplaced())) {
                        continue;
                    }
                    RiftBlock.place(serverLevel, pos, stroke.target, false, stroke.expireAt);
                    stroke.placed++;
                    newBlocks++;
                    burst(serverLevel, pos, random);
                    if (owner == null) {
                        owner = pos;
                    }
                }
                if (owner != null) {
                    RiftBlock.addSegment(serverLevel, owner, a, b, now, uBase + len * i / pieces, 0.0);
                    stroke.owners.add(owner);
                }
            }
            stroke.length += len;
        }

        if (newBlocks > 0) {
            // Crackling that rises in pitch as the rift grows.
            float pitch = 0.5F + Math.min(1.2F, stroke.placed / 120.0F);
            serverLevel.playSound(null, BlockPos.containing(stroke.lastPoint()),
                    SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.45F, pitch + random.nextFloat() * 0.2F);
        }

        // The blade wears down as the rift grows.
        int due = stroke.placed / 12 - stroke.damaged;
        if (due > 0) {
            stroke.damaged += due;
            EquipmentSlot slot = player.getUsedItemHand() == InteractionHand.MAIN_HAND
                    ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
            stack.hurtAndBreak(due, player, slot);
        }
    }

    /** Letting go closes the stroke: a final shockwave, and a cooldown before the next rift. */
    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(level instanceof ServerLevel serverLevel) || !(entity instanceof ServerPlayer player)) {
            return;
        }
        RiftStroke stroke = RiftStroke.finish(player);
        if (stroke == null || stroke.size() == 0) {
            return;
        }
        if (stroke.placed < 1) {
            player.displayClientMessage(Component.translatable("message.darkrifts.no_room"), true);
            return;
        }
        for (BlockPos op : stroke.owners) {
            if (serverLevel.getBlockEntity(op) instanceof RiftBlockEntity rbe) {
                rbe.finishLength(stroke.length);
            }
        }
        Vec3 c = stroke.center();
        BlockPos cp = BlockPos.containing(c);
        serverLevel.playSound(null, cp, SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 1.0F, 1.3F);
        serverLevel.playSound(null, cp, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 0.4F);
        shockwave(serverLevel, c, Math.min(6.0, 1.5 + stroke.placed / 20.0));
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
    }

    // ------------------------------------------------------------------ closing

    /** Finds the rift the player is pointing at and removes every block connected to it. */
    private static void closeRift(ServerLevel level, ServerPlayer player, ItemStack stack, InteractionHand hand) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(8.0));
        BlockHitResult hit = level.clip(new ClipContext(eye, end,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        BlockPos around = hit.getType() == HitResult.Type.MISS ? BlockPos.containing(end) : hit.getBlockPos();

        BlockPos start = null;
        for (BlockPos p : BlockPos.betweenClosed(around.offset(-2, -2, -2), around.offset(2, 2, 2))) {
            if (level.getBlockState(p).is(ModBlocks.RIFT.get())) {
                if (start == null || p.distSqr(around) < start.distSqr(around)) {
                    start = p.immutable();
                }
            }
        }
        if (start == null) {
            return;
        }

        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        int closed = 0;
        boolean keptReturn = false;
        while (!queue.isEmpty() && seen.size() < 4000) {
            BlockPos p = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos n = p.offset(dx, dy, dz);
                        if (!seen.contains(n) && level.getBlockState(n).is(ModBlocks.RIFT.get())) {
                            seen.add(n);
                            queue.add(n);
                        }
                    }
                }
            }
            BlockEntity be = level.getBlockEntity(p);
            if (be instanceof RiftBlockEntity rbe && rbe.isReturning()) {
                keptReturn = true;
                continue;
            }
            level.removeBlock(p, false);
            closed++;
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5,
                    6, 0.3, 0.3, 0.3, 0.1);
            if (closed % 3 == 0) {
                level.sendParticles(ParticleTypes.SMOKE, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5,
                        3, 0.3, 0.3, 0.3, 0.02);
            }
        }
        if (closed > 0) {
            level.playSound(null, start, SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.8F, 0.5F);
            level.playSound(null, start, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 0.6F);
            EquipmentSlot slot = hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
            stack.hurtAndBreak(1, player, slot);
            player.getCooldowns().addCooldown(stack.getItem(), 10);
        } else if (keptReturn) {
            player.displayClientMessage(Component.translatable("message.darkrifts.cant_close"), true);
        }
    }

    // ------------------------------------------------------------------ effects

    /** Particles at a freshly opened rift cell: swirling portal dust, black ink puffs, sparks and purple glints. */
    private static void burst(ServerLevel level, BlockPos pos, RandomSource random) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;
        level.sendParticles(ParticleTypes.PORTAL, x, y, z, 6, 0.3, 0.3, 0.3, 0.8);
        level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(
                new org.joml.Vector3f(0.7F, 0.25F, 1.0F), 1.0F), x, y, z, 3, 0.2, 0.2, 0.2, 0.0);
        if (random.nextInt(3) == 0) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 3, 0.3, 0.3, 0.3, 0.25);
        }
    }

    /** The violet wave that rolls out of the finished rift, plus a burst of light along its centre. */
    private static void shockwave(ServerLevel level, Vec3 c, double power) {
        RiftWaves.start(level, c, 4.0 + power * 1.6);
        RandomSource random = level.getRandom();
        for (int i = 0; i < 28; i++) {
            double theta = random.nextDouble() * Math.PI * 2.0;
            double phi = Math.acos(2.0 * random.nextDouble() - 1.0);
            double dx = Math.sin(phi) * Math.cos(theta);
            double dy = Math.cos(phi);
            double dz = Math.sin(phi) * Math.sin(theta);
            level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 0, dx, dy, dz, 0.3 * power);
            if (i % 2 == 0) {
                level.sendParticles(ParticleTypes.PORTAL, c.x, c.y, c.z, 0, dx, dy, dz, 0.9 * power);
            }
        }
    }

    /** A lightning bolt that does no damage and starts no fire - just the flash and the thunder. */
    private static void strike(ServerLevel level, Vec3 at) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(at.x, at.y, at.z);
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
    }

    // ------------------------------------------------------------------ geometry

    /** Block cells touched by the straight segment a -> b (no jitter: the tear is a single clean line). */
    private static List<BlockPos> line(Vec3 a, Vec3 b) {
        List<BlockPos> cells = new ArrayList<>();
        int steps = Math.max(1, (int) Math.ceil(a.distanceTo(b) / 0.25));
        BlockPos last = null;
        for (int s = 0; s <= steps; s++) {
            BlockPos cell = BlockPos.containing(a.lerp(b, s / (double) steps));
            if (!cell.equals(last)) {
                cells.add(cell);
                last = cell;
            }
        }
        return cells;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.darkrifts.rift_blade.1").withStyle(ChatFormatting.DARK_PURPLE));
        tooltip.add(Component.translatable("tooltip.darkrifts.rift_blade.2").withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
