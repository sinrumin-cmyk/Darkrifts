package com.darkrifts.registry;

import com.darkrifts.DarkRifts;
import com.darkrifts.entity.RiftSpike;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, DarkRifts.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<RiftSpike>> RIFT_SPIKE = ENTITIES.register("rift_spike",
            () -> EntityType.Builder.<RiftSpike>of(RiftSpike::new, MobCategory.MISC)
                    .sized(0.8F, 2.2F)
                    .clientTrackingRange(8)
                    .updateInterval(2)
                    .fireImmune()
                    .noSummon()
                    .build("rift_spike"));

    private ModEntities() {}
}
