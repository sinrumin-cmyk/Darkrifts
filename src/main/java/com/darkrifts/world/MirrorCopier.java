package com.darkrifts.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * When a brand-new mirror chunk appears, looks at the same chunk in the overworld (if it is currently loaded)
 * and copies the man-made blocks players built there - ruined: doors, windows, torches and containers vanish,
 * and about half of the walls crumble. Natural terrain is not copied (it is identical thanks to the shared seed).
 */
public final class MirrorCopier {
    private MirrorCopier() {}

    public static void copy(ServerLevel mirror, ChunkPos cp) {
        ServerLevel overworld = mirror.getServer().overworld();
        LevelChunk src = overworld.getChunkSource().getChunkNow(cp.x, cp.z);
        LevelChunk dst = mirror.getChunkSource().getChunkNow(cp.x, cp.z);
        if (src == null || dst == null) {
            return;
        }
        int minY = Math.max(src.getMinBuildHeight(), dst.getMinBuildHeight());
        int maxY = Math.min(src.getMaxBuildHeight(), dst.getMaxBuildHeight());
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();

        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                for (int y = minY; y < maxY; y++) {
                    p.set(cp.getMinBlockX() + dx, y, cp.getMinBlockZ() + dz);
                    BlockState s = src.getBlockState(p);
                    if (s.isAir() || MirrorPalette.category(s.getBlock()) != MirrorPalette.DECAY) {
                        continue;
                    }
                    BlockState here = dst.getBlockState(p);
                    if (!(here.isAir() || here.canBeReplaced())) {
                        continue; // only fill empty space; never overwrite terrain
                    }
                    BlockState ruined = MirrorPalette.ruin(s, MirrorPalette.hash(p.getX(), y, p.getZ()));
                    BlockState place = MirrorPalette.darkenWater(ruined == null ? s : ruined);
                    if (!place.isAir()) {
                        mirror.setBlock(p, place, 18);
                    }
                }
            }
        }
    }
}
