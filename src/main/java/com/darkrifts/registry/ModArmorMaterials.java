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

/** Worn textures: assets/darkrifts/textures/models/armor/rift_layer_1.png and rift_layer_2.png. */
public final class ModArmorMaterials {
    public static final DeferredRegister<ArmorMaterial> MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, DarkRifts.MODID);

    public static final Holder<ArmorMaterial> RIFT = MATERIALS.register("rift", () -> new ArmorMaterial(
            Util.make(new EnumMap<>(ArmorItem.Type.class), map -> {
                map.put(ArmorItem.Type.BOOTS, 3);
                map.put(ArmorItem.Type.LEGGINGS, 6);
                map.put(ArmorItem.Type.CHESTPLATE, 8);
                map.put(ArmorItem.Type.HELMET, 3);
                map.put(ArmorItem.Type.BODY, 11);
            }),
            18,
            SoundEvents.ARMOR_EQUIP_NETHERITE,
            () -> Ingredient.of(Items.ECHO_SHARD),
            List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(DarkRifts.MODID, "rift"))),
            3.0F,
            0.1F));

    private ModArmorMaterials() {}
}
