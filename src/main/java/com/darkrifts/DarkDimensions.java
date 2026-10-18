package com.darkrifts;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

/**
 * The dark realm is a single "mirror" of the overworld: same seed, same terrain, same caves, but built from dark stone,
 * dead and ruined. The dimension JSON lives in data/darkrifts/dimension/mirror.json (see tools/generate_assets.py).
 */
public final class DarkDimensions {
    public static final int COUNT = 1;
    public static final ResourceLocation MIRROR = ResourceLocation.fromNamespaceAndPath(DarkRifts.MODID, "mirror");

    /** The void prison: a flat world of "end portal" matter under a starry void sky. See world/Seal. */
    public static final ResourceLocation VOID = ResourceLocation.fromNamespaceAndPath(DarkRifts.MODID, "void_prison");

    private DarkDimensions() {}

    public static ResourceKey<Level> voidKey() {
        return key(VOID);
    }

    public static boolean isVoid(ResourceKey<Level> key) {
        return key.location().equals(VOID);
    }

    public static ResourceLocation id(int index) {
        return MIRROR;
    }

    public static ResourceKey<Level> key(ResourceLocation id) {
        return ResourceKey.create(Registries.DIMENSION, id);
    }

    public static boolean isDark(ResourceKey<Level> key) {
        return key.location().equals(MIRROR);
    }

    /** Where a freshly torn rift leads. Kept as a method so more realms can be added later. */
    public static ResourceLocation random(RandomSource random, ResourceKey<Level> exclude) {
        if (isDark(exclude)) {
            return Level.OVERWORLD.location();
        }
        return MIRROR;
    }
}
