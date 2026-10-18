package com.darkrifts;

import com.darkrifts.item.RiftArmorItem;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Full Rift armor set: no fall damage, half magic damage, Speed I. */
@EventBusSubscriber(modid = DarkRifts.MODID)
public final class RiftArmorEvents {
    private RiftArmorEvents() {}

    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (!RiftArmorItem.hasFullSet(event.getEntity())) {
            return;
        }
        if (event.getSource().is(DamageTypeTags.IS_FALL)) {
            event.setCanceled(true);
        } else if (event.getSource().is(DamageTypes.MAGIC) || event.getSource().is(DamageTypes.INDIRECT_MAGIC)) {
            event.setAmount(event.getAmount() * 0.5F);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || player.tickCount % 40 != 0 || !RiftArmorItem.hasFullSet(player)) {
            return;
        }
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 0, true, false, true));
    }
}
