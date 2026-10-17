package com.darkrifts;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

/**
 * The pool of dark dimensions. The matching JSON files (data/darkrifts/dimension/dark_01.json ...)
 * are produced by tools/generate_assets.py - keep COUNT in sync with that script.
 */
public final class DarkDimensions {
    public static final int COUNT = 12;

    private DarkDimensions() {}

    public static ResourceLocation id(int index) {
        return ResourceLocation.fromNamespaceAndPath(DarkRifts.MODID, String.format("dark_%02d", index + 1));
    }

    public static ResourceKey<Level> key(ResourceLocation id) {
        return ResourceKey.create(Registries.DIMENSION, id);
    }

    public static boolean isDark(ResourceKey<Level> key) {
        ResourceLocation loc = key.location();
        return loc.getNamespace().equals(DarkRifts.MODID) && loc.getPath().startsWith("dark_");
    }

    /** Picks a random dark dimension, never the one we are currently standing in. */
    public static ResourceLocation random(RandomSource random, ResourceKey<Level> exclude) {
        for (int i = 0; i < 8; i++) {
            ResourceLocation candidate = id(random.nextInt(COUNT));
            if (!candidate.equals(exclude.location())) {
                return candidate;
            }
        }
        return id(random.nextInt(COUNT));
    }
}
