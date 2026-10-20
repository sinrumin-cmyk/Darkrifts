package com.darkrifts.client;

import com.darkrifts.DarkRifts;
import com.darkrifts.registry.ModBlockEntities;
import com.darkrifts.registry.ModFluids;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

import java.lang.reflect.Method;

@EventBusSubscriber(modid = DarkRifts.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.RIFT.get(), RiftRenderer::new);
        event.registerEntityRenderer(com.darkrifts.registry.ModEntities.RIFT_SPIKE.get(), RiftSpikeRenderer::new);
        event.registerEntityRenderer(com.darkrifts.registry.ModEntities.RIFT_REAVER.get(), RiftReaverRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ALTAR.get(), RitualAltarRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SANCTUM_TOWER.get(), SanctumTowerRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SANCTUM_CORE.get(), SanctumCoreRenderer::new);
        event.registerEntityRenderer(com.darkrifts.registry.ModEntities.MIRROR_SOVEREIGN.get(), MirrorSovereignRenderer::new);
        event.registerEntityRenderer(com.darkrifts.registry.ModEntities.SINGULARITY.get(), SingularityRenderer::new);
    }

    /** The 3D Rift armor (horns, pauldrons, spikes): see RiftArmorModels / the generated RiftArmorParts. */
    @SubscribeEvent
    public static void registerArmorLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        RiftArmorModels.registerLayers(event);
    }

    @SubscribeEvent
    public static void registerArmorModels(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack, EquipmentSlot slot,
                                                          HumanoidModel<?> original) {
                return RiftArmorModels.get(slot, original);
            }
        }, com.darkrifts.registry.ModItems.RIFT_HELMET.get(), com.darkrifts.registry.ModItems.RIFT_CHESTPLATE.get(),
                com.darkrifts.registry.ModItems.RIFT_LEGGINGS.get(), com.darkrifts.registry.ModItems.RIFT_BOOTS.get());
    }

    /** Screen flash for rift births / jumps / the armor's ward. */
    @SubscribeEvent
    public static void registerGui(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(DarkRifts.MODID, "rift_flash"), FlashOverlay::render);
    }

    /** Both dark realms use the "no vanilla sky" effects; SkyRenderer draws the real one. */
    @SubscribeEvent
    public static void registerSky(RegisterDimensionSpecialEffectsEvent event) {
        event.register(ResourceLocation.fromNamespaceAndPath(DarkRifts.MODID, "dark_sky"), new DarkSkyEffects());
        event.register(ResourceLocation.fromNamespaceAndPath(DarkRifts.MODID, "void_sky"), new DarkSkyEffects());
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(AbilityKeys.OPEN);
    }

    /** Dark water uses the vanilla water textures, tinted nearly black. */
    @SubscribeEvent
    public static void registerFluidLook(RegisterClientExtensionsEvent event) {
        event.registerFluidType(new IClientFluidTypeExtensions() {
            @Override
            public ResourceLocation getStillTexture() {
                return ResourceLocation.withDefaultNamespace("block/water_still");
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return ResourceLocation.withDefaultNamespace("block/water_flow");
            }

            @Override
            public ResourceLocation getOverlayTexture() {
                return ResourceLocation.withDefaultNamespace("block/water_overlay");
            }

            @Override
            public int getTintColor() {
                return 0xFF0E1630;
            }
        }, ModFluids.DARK_WATER_TYPE.get());
    }

    /** Translucent like real water. Done by reflection so a different NeoForge build can't break compilation. */
    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // Custom bows need their own "pull"/"pulling" item properties, otherwise the draw textures never show.
            ItemProperties.register(com.darkrifts.registry.ModItems.MIRROR_BOW.get(),
                    ResourceLocation.withDefaultNamespace("pull"),
                    (stack, level, entity, seed) -> {
                        if (entity == null || entity.getUseItem() != stack) {
                            return 0.0F;
                        }
                        return entity.getTicksUsingItem() / 20.0F;
                    });
            ItemProperties.register(com.darkrifts.registry.ModItems.MIRROR_BOW.get(),
                    ResourceLocation.withDefaultNamespace("pulling"),
                    (stack, level, entity, seed) ->
                            entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F);
            try {
                Method m;
                try {
                    m = ItemBlockRenderTypes.class.getMethod("setRenderLayer", Fluid.class, RenderType.class);
                } catch (NoSuchMethodException e) {
                    m = ItemBlockRenderTypes.class.getDeclaredMethod("setRenderLayer", Fluid.class, RenderType.class);
                    m.setAccessible(true);
                }
                m.invoke(null, ModFluids.SOURCE.get(), RenderType.translucent());
                m.invoke(null, ModFluids.FLOWING.get(), RenderType.translucent());
            } catch (Throwable ignored) {
                // stays opaque: still dark water, just not see-through
            }
        });
    }
}
