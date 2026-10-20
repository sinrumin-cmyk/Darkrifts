package com.darkrifts.client;

import com.darkrifts.DarkRifts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

import java.util.EnumMap;
import java.util.Map;

/**
 * The 3D shape of the Rift armor: horns, pauldrons with spikes, a spine, a glowing core, knee and toe spikes.
 * The cubes come from {@link RiftArmorParts} (generated together with the texture sheet by tools/armor_gen.py);
 * every slot has its own model so only its own pieces are drawn.
 */
public final class RiftArmorModels {
    private static final Map<EquipmentSlot, ModelLayerLocation> LAYERS = new EnumMap<>(EquipmentSlot.class);
    private static final Map<EquipmentSlot, HumanoidModel<LivingEntity>> BAKED = new EnumMap<>(EquipmentSlot.class);

    static {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            LAYERS.put(slot, new ModelLayerLocation(
                    ResourceLocation.fromNamespaceAndPath(DarkRifts.MODID, "rift_armor_" + slot.getName()), "main"));
        }
    }

    private RiftArmorModels() {}

    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(LAYERS.get(EquipmentSlot.HEAD), RiftArmorParts::helmet);
        event.registerLayerDefinition(LAYERS.get(EquipmentSlot.CHEST), RiftArmorParts::chest);
        event.registerLayerDefinition(LAYERS.get(EquipmentSlot.LEGS), RiftArmorParts::legs);
        event.registerLayerDefinition(LAYERS.get(EquipmentSlot.FEET), RiftArmorParts::boots);
    }

    /** The model for one armor slot, posed like the wearer's body ({@code original} is the vanilla armor model). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static HumanoidModel<?> get(EquipmentSlot slot, HumanoidModel<?> original) {
        ModelLayerLocation layer = LAYERS.get(slot);
        if (layer == null) {
            return original;
        }
        HumanoidModel<LivingEntity> model = BAKED.get(slot);
        if (model == null) {
            model = new HumanoidModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(layer));
            BAKED.put(slot, model);
        }
        ((HumanoidModel) original).copyPropertiesTo(model);
        model.setAllVisible(false);
        switch (slot) {
            case HEAD -> {
                model.head.visible = true;
                model.hat.visible = true;
            }
            case CHEST -> {
                model.body.visible = true;
                model.rightArm.visible = true;
                model.leftArm.visible = true;
            }
            case LEGS -> {
                model.body.visible = true;
                model.rightLeg.visible = true;
                model.leftLeg.visible = true;
            }
            case FEET -> {
                model.rightLeg.visible = true;
                model.leftLeg.visible = true;
            }
            default -> {
            }
        }
        return model;
    }

    /** Forget the baked models (resource reload): they are re-baked on the next frame. */
    public static void reset() {
        BAKED.clear();
    }
}
