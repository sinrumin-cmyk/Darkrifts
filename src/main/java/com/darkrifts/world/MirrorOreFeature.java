package com.darkrifts.world;

import com.darkrifts.DarkDimensions;
import com.darkrifts.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A small vein of Mirror Ore. Added to every overworld biome by a biome modifier, but it only does something in the
 * Dark Mirror, so the real overworld never gets this ore.
 */
public class MirrorOreFeature extends Feature<NoneFeatureConfiguration> {
    public MirrorOreFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        if (!DarkDimensions.isDark(level.getLevel().dimension())) {
            return false;
        }
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        BlockState ore = ModBlocks.MIRROR_ORE.get().defaultBlockState();
        int placed = 0;
        double radius = 1.2 + random.nextDouble() * 1.1;
        int r = (int) Math.ceil(radius);
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (d > radius || random.nextFloat() > 0.7F) {
                        continue;
                    }
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                        continue;
                    }
                    BlockState existing = level.getBlockState(pos);
                    if (existing.is(BlockTags.STONE_ORE_REPLACEABLES) || existing.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES)) {
                        level.setBlock(pos, ore, 2);
                        placed++;
                    }
                }
            }
        }
        return placed > 0;
    }
}
