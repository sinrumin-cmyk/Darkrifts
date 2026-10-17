package com.darkrifts.world;

import com.darkrifts.DarkDimensions;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Added (via biome modifier) to every overworld biome as the very last decoration step. In the real overworld it does
 * nothing; in the mirror dimension it darkens and ruins the chunk. Because trees and structures of neighbouring chunks
 * can spill over chunk borders, the whole 3x3 generation region is processed (the pass is idempotent).
 */
public class MirrorFeature extends Feature<NoneFeatureConfiguration> {
    public MirrorFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        if (!DarkDimensions.isDark(level.getLevel().dimension())) {
            return false;
        }
        int cx = context.origin().getX() >> 4;
        int cz = context.origin().getZ() >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (level.hasChunk(cx + dx, cz + dz)) {
                    MirrorPalette.processChunk(level, cx + dx, cz + dz);
                }
            }
        }
        return true;
    }
}
