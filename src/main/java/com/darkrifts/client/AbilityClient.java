package com.darkrifts.client;

import com.darkrifts.DarkRifts;
import com.darkrifts.ability.Abilities;
import com.darkrifts.item.RiftBladeItem;
import com.darkrifts.net.SelectAbilityPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = DarkRifts.MODID, value = Dist.CLIENT)
public final class AbilityClient {
    private AbilityClient() {}

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        while (AbilityKeys.OPEN.consumeClick()) {
            if (mc.screen == null && mc.player.getMainHandItem().getItem() instanceof RiftBladeItem blade
                    && !blade.isSingleUse()) {
                mc.setScreen(new AbilityScreen());
            }
        }
    }

    /** Tell the server which ability this client has selected (it forgets everything on relog). */
    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        PacketDistributor.sendToServer(new SelectAbilityPayload(Abilities.clientSelected.ordinal()));
    }
}
