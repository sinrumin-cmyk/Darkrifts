package com.darkrifts.registry;

import com.darkrifts.DarkRifts;
import com.darkrifts.world.RiftFeature;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, DarkRifts.MODID);

    public static final Supplier<RiftFeature> RIFT = FEATURES.register("rift", RiftFeature::new);

    private ModFeatures() {}
}
