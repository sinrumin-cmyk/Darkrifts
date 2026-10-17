package com.darkrifts.item;

import com.darkrifts.Config;
import com.darkrifts.DarkDimensions;
import com.darkrifts.block.RiftBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Hold right mouse button, look at the sky and drag your view down (or in any direction):
 * the path your crosshair traces becomes a rift.
 */
public class RiftBladeItem extends SwordItem {
    private static final int MIN_USE_TICKS = 6;
    private static final double MIN_LENGTH = 3.0;
    private static final int COOLDOWN_TICKS = 40;

    public RiftBladeItem(Properties properties) {
        super(Tiers.NETHERITE, properties);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BLOCK;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        if (!(level instanceof ServerLevel serverLevel) || !(entity instanceof ServerPlayer player)) {
            return;
        }
        Vec3 point = RiftStroke.update(player, level.getGameTime(), Config.STROKE_RADIUS.get());
        if (point != null) {
            serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, point.x, point.y, point.z, 3, 0.05, 0.05, 0.05, 0.02);
            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(level instanceof ServerLevel serverLevel) || !(entity instanceof ServerPlayer player)) {
            return;
        }
        RiftStroke stroke = RiftStroke.finish(player);
        int usedTicks = getUseDuration(stack) - timeLeft;
        if (stroke == null || usedTicks < MIN_USE_TICKS || stroke.length() < MIN_LENGTH) {
            player.displayClientMessage(Component.translatable("message.darkrifts.stroke_too_short"), true);
            return;
        }

        Set<BlockPos> cells = rasterize(stroke.points(), serverLevel.getRandom(), Config.MAX_RIFT_BLOCKS.get());
        ResourceLocation target = DarkDimensions.random(serverLevel.getRandom(), serverLevel.dimension());
        int lifetime = Config.RIFT_LIFETIME_SECONDS.get() * 20;
        long expireAt = lifetime > 0 ? serverLevel.getGameTime() + lifetime : 0L;

        int placed = 0;
        for (BlockPos pos : cells) {
            if (!serverLevel.isInWorldBounds(pos)) {
                continue;
            }
            BlockState existing = serverLevel.getBlockState(pos);
            if (existing.isAir() || existing.canBeReplaced()) {
                RiftBlock.place(serverLevel, pos, target, false, expireAt);
                placed++;
            }
        }

        if (placed < 3) {
            player.displayClientMessage(Component.translatable("message.darkrifts.no_room"), true);
            return;
        }

        BlockPos center = BlockPos.containing(stroke.points().get(stroke.points().size() / 2));
        serverLevel.playSound(null, center, SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.8F, 1.5F);
        serverLevel.playSound(null, center, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 0.5F);
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        EquipmentSlot slot = entity.getUsedItemHand() == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
        stack.hurtAndBreak(Math.max(1, placed / 12), player, slot);
    }

    /** Turns the traced polyline into a set of block cells, with a bit of jitter so the tear looks ragged. */
    private static Set<BlockPos> rasterize(List<Vec3> points, RandomSource random, int max) {
        Set<BlockPos> cells = new LinkedHashSet<>();
        for (int i = 1; i < points.size() && cells.size() < max; i++) {
            Vec3 a = points.get(i - 1);
            Vec3 b = points.get(i);
            int steps = Math.max(1, (int) Math.ceil(a.distanceTo(b) / 0.4));
            for (int s = 0; s <= steps; s++) {
                Vec3 q = a.lerp(b, s / (double) steps);
                Vec3 jittered = q.add(
                        (random.nextDouble() - 0.5) * 0.7,
                        (random.nextDouble() - 0.5) * 0.7,
                        (random.nextDouble() - 0.5) * 0.7);
                BlockPos cell = BlockPos.containing(jittered);
                cells.add(cell);
                if (random.nextInt(4) == 0) {
                    cells.add(cell.relative(net.minecraft.core.Direction.getRandom(random)));
                }
                if (cells.size() >= max) {
                    break;
                }
            }
        }
        return cells;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.darkrifts.rift_blade.1").withStyle(net.minecraft.ChatFormatting.DARK_PURPLE));
        tooltip.add(Component.translatable("tooltip.darkrifts.rift_blade.2").withStyle(net.minecraft.ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
