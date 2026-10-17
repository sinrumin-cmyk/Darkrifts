package com.darkrifts.client;

import com.darkrifts.DarkRifts;
import com.darkrifts.registry.ModBlockEntities;
import com.darkrifts.registry.ModFluids;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

import java.lang.reflect.Method;

@EventBusSubscriber(modid = DarkRifts.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.RIFT.get(), RiftRenderer::new);
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
