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

    public static final DeferredHolder<EntityType<?>, EntityType<com.darkrifts.entity.RiftReaver>> RIFT_REAVER =
            ENTITIES.register("rift_reaver",
                    () -> EntityType.Builder.<com.darkrifts.entity.RiftReaver>of(com.darkrifts.entity.RiftReaver::new,
                                    MobCategory.MONSTER)
                            .sized(2.4F, 4.4F)
                            .clientTrackingRange(12)
                            .updateInterval(2)
                            .fireImmune()
                            .build("rift_reaver"));

    /** The stationary endgame boss of the Sanctum (no spawn egg: it comes out of the awakening). */
    public static final DeferredHolder<EntityType<?>, EntityType<com.darkrifts.entity.MirrorSovereign>> MIRROR_SOVEREIGN =
            ENTITIES.register("mirror_sovereign",
                    () -> EntityType.Builder.<com.darkrifts.entity.MirrorSovereign>of(com.darkrifts.entity.MirrorSovereign::new,
                                    MobCategory.MONSTER)
                            .sized(4.4F, 8.0F)
                            .clientTrackingRange(20)
                            .updateInterval(2)
                            .fireImmune()
                            .build("mirror_sovereign"));

    private ModEntities() {}
}
