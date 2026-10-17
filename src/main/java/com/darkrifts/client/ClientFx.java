package com.darkrifts.client;

import com.darkrifts.DarkDimensions;
import com.darkrifts.DarkRifts;
import com.mojang.blaze3d.shaders.FogShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Thick, almost black fog in the mirror dimension (and murky dark water when you dive). */
@EventBusSubscriber(modid = DarkRifts.MODID, value = Dist.CLIENT)
public final class ClientFx {
    /** How far you can see on land in the mirror, in blocks. */
    private static final float FOG_DISTANCE = 22.0F;

    private ClientFx() {}

    private static boolean inMirror() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && DarkDimensions.isDark(mc.level.dimension());
    }

    @SubscribeEvent
    public static void fogColor(ViewportEvent.ComputeFogColor event) {
        if (!inMirror()) {
            return;
        }
        if (event.getCamera().getFluidInCamera() == FogType.WATER) {
            event.setRed(0.004F);
            event.setGreen(0.008F);
            event.setBlue(0.022F);
        } else {
            event.setRed(0.008F);
            event.setGreen(0.004F);
            event.setBlue(0.016F);
        }
    }

    @SubscribeEvent
    public static void fogDensity(ViewportEvent.RenderFog event) {
        if (!inMirror()) {
            return;
        }
        FogType type = event.getCamera().getFluidInCamera();
        if (type == FogType.WATER) {
            event.setNearPlaneDistance(-6.0F);
            event.setFarPlaneDistance(Math.min(event.getFarPlaneDistance(), 14.0F));
            event.setCanceled(true);
        } else if (type == FogType.NONE && event.getMode() == FogRenderer.FogMode.FOG_TERRAIN) {
            event.setNearPlaneDistance(0.0F);
            event.setFarPlaneDistance(Math.min(event.getFarPlaneDistance(), FOG_DISTANCE));
            event.setFogShape(FogShape.SPHERE);
            event.setCanceled(true);
        }
    }
}
