package com.darkrifts.item;

import com.darkrifts.registry.ModArmorMaterials;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.ChatFormatting;

import java.util.List;

/** One piece of the Rift armor. The full set gives a bonus (see ModEvents / RiftArmorEvents). */
public class RiftArmorItem extends ArmorItem {
    public RiftArmorItem(Type type, Properties properties) {
        super(ModArmorMaterials.RIFT, type, properties);
    }

    public static boolean hasFullSet(LivingEntity entity) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                EquipmentSlot.FEET}) {
            if (!(entity.getItemBySlot(slot).getItem() instanceof RiftArmorItem)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.darkrifts.rift_armor").withStyle(ChatFormatting.DARK_PURPLE));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
