package com.darkrifts.client;

import com.darkrifts.DarkRifts;
import com.darkrifts.Flash;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Full-screen coloured flash (rift births, jumps, wards). Strength lives in the common {@link Flash} holder. */
@EventBusSubscriber(modid = DarkRifts.MODID, value = Dist.CLIENT)
public final class FlashOverlay {
    private FlashOverlay() {}

    @SubscribeEvent
    public static void decay(ClientTickEvent.Post event) {
        float v = Flash.value;
        Flash.value = v < 0.01F ? 0.0F : v * 0.84F;
    }

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        float v = Flash.value;
        if (v < 0.01F) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        int rgb = Flash.rgb & 0xFFFFFF;
        int a = (int) (Mth.clamp(v, 0.0F, 1.0F) * 190.0F);
        graphics.fill(0, 0, w, h, (a << 24) | rgb);
        // brighter towards the middle of the screen
        int ca = (int) (Mth.clamp(v, 0.0F, 1.0F) * 110.0F);
        graphics.fill(w / 4, h / 4, w - w / 4, h - h / 4, (ca << 24) | 0xFFFFFF);
    }
}
