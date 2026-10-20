package com.darkrifts.net;

import com.darkrifts.DarkRifts;
import com.darkrifts.Flash;
import com.darkrifts.Shake;
import com.darkrifts.ability.Abilities;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = DarkRifts.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class ModNetwork {
    private ModNetwork() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(SelectAbilityPayload.TYPE, SelectAbilityPayload.CODEC, ModNetwork::handle);
        registrar.playToClient(FxPayload.TYPE, FxPayload.CODEC, ModNetwork::handleFx);
    }

    private static void handle(SelectAbilityPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Abilities.select(player, payload.id());
            }
        });
    }

    /** Only touches the common Shake / Flash holders, so it is safe on a dedicated server's class loader. */
    private static void handleFx(FxPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Shake.add(payload.shake());
            Flash.add(payload.flash(), payload.rgb());
        });
    }
}
