package com.darkrifts;

import com.darkrifts.entity.RiftReaver;
import com.darkrifts.registry.ModEntities;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

/** Mod-bus events shared by client and server. */
@EventBusSubscriber(modid = DarkRifts.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class ModBootstrap {
    private ModBootstrap() {}

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.RIFT_REAVER.get(), RiftReaver.createAttributes().build());
        event.put(ModEntities.MIRROR_SOVEREIGN.get(), com.darkrifts.entity.MirrorSovereign.createSovereignAttributes().build());
    }
}
