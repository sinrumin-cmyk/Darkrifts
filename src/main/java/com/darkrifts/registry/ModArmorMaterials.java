package com.darkrifts.registry;

import com.darkrifts.DarkRifts;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.List;

/**
 * Rift armor: 26 armor points (netherite: 20), toughness 4, knockback resistance 15% per piece.
 * Worn textures: assets/darkrifts/textures/models/armor/rift_layer_1.png and rift_layer_2.png (128x128, see
 * client/RiftArmorModels for the 3D model that wraps them).
 */
public final class ModArmorMaterials {
    public static final DeferredRegister<ArmorMaterial> MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, DarkRifts.MODID);

    public static final Holder<ArmorMaterial> RIFT = MATERIALS.register("rift", () -> new ArmorMaterial(
            Util.make(new EnumMap<>(ArmorItem.Type.class), map -> {
                map.put(ArmorItem.Type.BOOTS, 4);
                map.put(ArmorItem.Type.LEGGINGS, 8);
                map.put(ArmorItem.Type.CHESTPLATE, 10);
                map.put(ArmorItem.Type.HELMET, 4);
                map.put(ArmorItem.Type.BODY, 14);
            }),
            25,
            SoundEvents.ARMOR_EQUIP_NETHERITE,
            () -> Ingredient.of(Items.ECHO_SHARD),
            List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(DarkRifts.MODID, "rift"))),
            4.0F,
            0.15F));

    private ModArmorMaterials() {}
}
