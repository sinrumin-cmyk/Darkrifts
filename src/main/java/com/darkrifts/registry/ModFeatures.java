package com.darkrifts.registry;

import com.darkrifts.DarkRifts;
import com.darkrifts.world.MirrorFeature;
import com.darkrifts.world.RiftFeature;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, DarkRifts.MODID);

    public static final Supplier<RiftFeature> RIFT = FEATURES.register("rift", RiftFeature::new);

    /** Darkens and ruins chunks of the mirror dimension (no-op elsewhere). */
    public static final Supplier<MirrorFeature> DARKEN = FEATURES.register("darken", MirrorFeature::new);

    /** Veins of Mirror Ore (no-op outside the mirror dimension). */
    public static final Supplier<com.darkrifts.world.MirrorOreFeature> MIRROR_ORE =
            FEATURES.register("mirror_ore", com.darkrifts.world.MirrorOreFeature::new);

    private ModFeatures() {}
}
