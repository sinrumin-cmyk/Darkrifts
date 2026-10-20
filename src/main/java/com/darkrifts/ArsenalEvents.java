package com.darkrifts;

import com.darkrifts.registry.ModItems;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Special effects of the mirrorite arsenal:
 *  - Mirrorite Sword / Axe: 25% chance on hit to slow the victim (Slowness II) and weaken it for 4 seconds.
 *  - Mirror Bow: arrows hit harder (+1.5 damage) and pierce through two targets.
 */
@EventBusSubscriber(modid = DarkRifts.MODID)
public final class ArsenalEvents {
    private static final float PROC_CHANCE = 0.25F;
    private static final double BOW_BONUS_DAMAGE = 1.5;
    private static final byte BOW_PIERCE = 2;

    private ArsenalEvents() {}

    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        Entity source = event.getSource().getEntity();
        if (!(source instanceof LivingEntity attacker) || event.getSource().getDirectEntity() != attacker) {
            return; // melee only
        }
        Item held = attacker.getMainHandItem().getItem();
        if (held != ModItems.MIRRORITE_SWORD.get() && held != ModItems.MIRRORITE_AXE.get()) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || victim.getRandom().nextFloat() >= PROC_CHANCE) {
            return;
        }
        victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1));
        victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0));
    }

    /** Buffs arrows the moment they are shot from a Mirror Bow. */
    @SubscribeEvent
    public static void onArrowShot(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || event.loadedFromDisk()) {
            return;
        }
        if (!(event.getEntity() instanceof AbstractArrow arrow) || !(arrow.getOwner() instanceof LivingEntity shooter)) {
            return;
        }
        if (shooter.getUseItem().getItem() != ModItems.MIRROR_BOW.get()) {
            return;
        }
        arrow.setBaseDamage(arrow.getBaseDamage() + BOW_BONUS_DAMAGE);
        arrow.setPierceLevel(BOW_PIERCE);
    }
}
