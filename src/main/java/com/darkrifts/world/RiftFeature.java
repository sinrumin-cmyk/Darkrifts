package com.darkrifts.world;

import com.darkrifts.DarkDimensions;
import com.darkrifts.block.RiftBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** A ragged, permanent vertical tear standing on the ground. Leads to a random dark dimension. */
public class RiftFeature extends Feature<NoneFeatureConfiguration> {
    public RiftFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();

        // Don't spawn on water / lava surfaces.
        if (!level.getBlockState(origin.below()).getFluidState().isEmpty()
                || !level.getBlockState(origin.below()).isSolid()) {
            return false;
        }

        // In the overworld a natural rift leads into the mirror; inside the mirror it leads back home.
        boolean inMirror = DarkDimensions.isDark(level.getLevel().dimension());
        ResourceLocation target = inMirror ? null : DarkDimensions.random(random, level.getLevel().dimension());
        boolean alongX = random.nextBoolean();
        Direction side = alongX ? Direction.EAST : Direction.SOUTH;

        int height = 5 + random.nextInt(6);
        int offset = 0;
        int placed = 0;
        for (int i = 0; i < height; i++) {
            BlockPos pos = origin.above(i).relative(side, offset);
            if (level.isEmptyBlock(pos)) {
                RiftBlock.place(level, pos, target, inMirror, 0L);
                placed++;
                if (random.nextInt(3) == 0) {
                    BlockPos extra = pos.relative(side, random.nextBoolean() ? 1 : -1);
                    if (level.isEmptyBlock(extra)) {
                        RiftBlock.place(level, extra, target, inMirror, 0L);
                    }
                }
            }
            if (random.nextInt(3) == 0) {
                offset += random.nextInt(3) - 1;
                offset = Math.max(-2, Math.min(2, offset));
            }
        }
        return placed > 0;
    }
}
