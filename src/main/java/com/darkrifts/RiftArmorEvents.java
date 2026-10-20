package com.darkrifts;

import com.darkrifts.item.RiftArmorItem;
import com.darkrifts.world.RiftFx;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Full Rift armor set (all four pieces):
 *  - no fall damage; magic / wither damage halved; projectiles -25%; explosions -20%
 *  - +4 hearts, Speed II, immune to Wither, Night Vision inside the Dark Mirror
 *  - RIFT WARD: a hit of 5+ damage tears a rift around you (8 s cooldown): the hit is reduced by 40%, you get
 *    Absorption, and every enemy within 5 blocks is blasted away and takes void damage.
 */
@EventBusSubscriber(modid = DarkRifts.MODID)
public final class RiftArmorEvents {
    private static final ResourceLocation HEALTH_ID = ResourceLocation.fromNamespaceAndPath(DarkRifts.MODID, "rift_set_health");
    private static final int WARD_COOLDOWN_TICKS = 160;
    private static final Map<UUID, Long> WARD_READY = new HashMap<>();

    private RiftArmorEvents() {}

    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (!RiftArmorItem.hasFullSet(victim)) {
            return;
        }
        DamageSource source = event.getSource();
        if (source.is(DamageTypeTags.IS_FALL)) {
            event.setCanceled(true);
            return;
        }
        float amount = event.getAmount();
        if (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC) || source.is(DamageTypes.WITHER)) {
            amount *= 0.5F;
        }
        if (source.is(DamageTypeTags.IS_PROJECTILE)) {
            amount *= 0.75F;
        }
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            amount *= 0.8F;
        }
        if (victim instanceof ServerPlayer player && amount >= 5.0F && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            long now = player.level().getGameTime();
            Long ready = WARD_READY.get(player.getUUID());
            if (ready == null || now >= ready) {
                WARD_READY.put(player.getUUID(), now + WARD_COOLDOWN_TICKS);
                amount *= 0.6F;
                ward(player);
            }
        }
        event.setAmount(amount);
    }

    /** The Rift Ward: a burst of void around the wearer. */
    private static void ward(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel sl)) {
            return;
        }
        Vec3 c = player.position().add(0.0, 1.0, 0.0);
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 120, 1, true, true, true));
        RiftFx.ward(sl, c);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(5.0),
                x -> x != player && x instanceof Enemy)) {
            e.hurt(sl.damageSources().indirectMagic(player, player), 6.0F);
            Vec3 d = e.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
            if (d.lengthSqr() > 1.0E-4) {
                e.setDeltaMovement(e.getDeltaMovement().add(d.normalize().scale(1.3)).add(0.0, 0.45, 0.0));
                e.hurtMarked = true;
            }
        }
        player.displayClientMessage(Component.translatable("message.darkrifts.rift_ward")
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            return;
        }
        boolean full = RiftArmorItem.hasFullSet(player);
        if (player.tickCount % 10 == 0) {
            AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
            if (health != null) {
                boolean has = health.hasModifier(HEALTH_ID);
                if (full && !has) {
                    health.addTransientModifier(new AttributeModifier(HEALTH_ID, 8.0, AttributeModifier.Operation.ADD_VALUE));
                } else if (!full && has) {
                    health.removeModifier(HEALTH_ID);
                }
            }
        }
        if (!full) {
            return;
        }
        if (player.hasEffect(MobEffects.WITHER)) {
            player.removeEffect(MobEffects.WITHER);
        }
        if (player.tickCount % 40 == 0) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 1, true, false, true));
            if (DarkDimensions.isDark(player.level().dimension())) {
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, true, false, false));
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        WARD_READY.remove(event.getEntity().getUUID());
    }
}
