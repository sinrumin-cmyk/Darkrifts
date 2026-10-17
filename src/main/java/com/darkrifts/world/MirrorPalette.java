package com.darkrifts.world;

import com.darkrifts.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BambooSaplingBlock;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.GrowingPlantBlock;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns freshly generated overworld terrain into the dark mirror:
 * soil/sand become tuff, andesite, stone and deepslate; plants and leaves die; trees turn into black dead trunks;
 * and anything man-made (villages, ...) is ruined - doors and windows vanish, walls crumble, containers are gone.
 * All decisions depend only on the block position, so running the pass twice gives the same result.
 */
public final class MirrorPalette {
    /** How far below the surface of each column we look (trees, soil layers, sea floor). */
    private static final int SCAN_DEPTH = 48;

    public static final byte NONE = 0;
    /** Rots away completely: doors, glass, torches, containers (anything with a block entity)... */
    public static final byte ROT = 1;
    /** Crumbles partially: planks, stairs, slabs, walls, bricks... */
    public static final byte DECAY = 2;

    private static final String[] ROT_TOKENS = {
            "door", "gate", "torch", "lantern", "ladder", "carpet", "candle", "button", "pressure_plate", "rail",
            "flower_pot", "scaffolding", "bell", "banner", "sign", "glass", "crafting_table", "bookshelf",
            "hay_block", "iron_bars", "chain", "campfire"
    };
    private static final String[] DECAY_TOKENS = {
            "planks", "stairs", "slab", "wall", "fence", "brick", "wool", "concrete", "cobblestone", "quartz",
            "polished", "cut_", "chiseled", "smooth_stone"
    };

    private static final Map<Block, Byte> CATEGORY = new ConcurrentHashMap<>();

    private MirrorPalette() {}

    // ------------------------------------------------------------------ world generation pass

    /** Processes one chunk of a generation region. Chunks that are already complete (player territory) are skipped. */
    public static void processChunk(WorldGenLevel level, int chunkX, int chunkZ) {
        ChunkAccess chunk = level.getChunk(chunkX, chunkZ);
        if (chunk instanceof LevelChunk || chunk instanceof ImposterProtoChunk) {
            return;
        }
        int x0 = chunkX << 4;
        int z0 = chunkZ << 4;
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight() - 1;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();

        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = x0 + dx;
                int z = z0 + dz;
                int top = Math.min(maxY, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z));
                int bottom = Math.max(minY, top - SCAN_DEPTH);
                for (int y = top; y >= bottom; y--) {
                    p.set(x, y, z);
                    BlockState s = level.getBlockState(p);
                    if (s.isAir()) {
                        continue;
                    }
                    long h = hash(x, y, z);
                    BlockState n = convert(s, h);
                    if (n == null) {
                        n = ruin(s, h);
                    }
                    if (n != null && n != s) {
                        level.setBlock(p, n, 2);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ block rules

    /** Natural blocks -> dark stone / dead nature. Returns null if the block is left alone. */
    @Nullable
    public static BlockState convert(BlockState s, long h) {
        Block b = s.getBlock();
        int r = (int) Math.floorMod(h >>> 20, 100L);

        if (b == Blocks.GRASS_BLOCK || b == Blocks.PODZOL || b == Blocks.MYCELIUM
                || b == Blocks.MOSS_BLOCK || b == Blocks.DIRT_PATH) {
            return surface(r);
        }
        if (b == Blocks.DIRT || b == Blocks.COARSE_DIRT || b == Blocks.ROOTED_DIRT
                || b == Blocks.FARMLAND || b == Blocks.MUD || b == Blocks.CLAY) {
            return sub(r);
        }
        if (s.is(BlockTags.SAND)) {
            return (r < 55 ? Blocks.GRAVEL : Blocks.ANDESITE).defaultBlockState();
        }
        if (b == Blocks.SANDSTONE || b == Blocks.RED_SANDSTONE) {
            return (r < 50 ? Blocks.TUFF : Blocks.ANDESITE).defaultBlockState();
        }
        if (s.is(BlockTags.TERRACOTTA)) {
            return (r < 50 ? Blocks.DEEPSLATE : Blocks.TUFF).defaultBlockState();
        }
        if (b == Blocks.SNOW) {
            return Blocks.AIR.defaultBlockState();
        }
        if (b == Blocks.SNOW_BLOCK || b == Blocks.POWDER_SNOW) {
            return Blocks.TUFF.defaultBlockState();
        }
        if (b == Blocks.ICE) {
            return Blocks.WATER.defaultBlockState();
        }
        if (b == Blocks.PACKED_ICE || b == Blocks.BLUE_ICE) {
            return Blocks.SMOOTH_BASALT.defaultBlockState();
        }
        // Everything alive dies: grass, flowers, crops, kelp, vines, cacti, leaves, giant mushrooms...
        if (b instanceof LeavesBlock || b instanceof BushBlock || b instanceof GrowingPlantBlock
                || b instanceof VineBlock || b instanceof SugarCaneBlock || b instanceof CactusBlock
                || b instanceof BambooStalkBlock || b instanceof BambooSaplingBlock
                || b == Blocks.PUMPKIN || b == Blocks.CARVED_PUMPKIN || b == Blocks.MELON
                || b instanceof HugeMushroomBlock) {
            return removed(s);
        }
        // Trees keep their trunks, but turn black.
        if (s.is(BlockTags.LOGS_THAT_BURN) && s.hasProperty(RotatedPillarBlock.AXIS) && b != Blocks.DARK_OAK_LOG) {
            Block dark = (r % 3 == 0) ? Blocks.STRIPPED_DARK_OAK_LOG : Blocks.DARK_OAK_LOG;
            return dark.defaultBlockState().setValue(RotatedPillarBlock.AXIS, s.getValue(RotatedPillarBlock.AXIS));
        }
        return null;
    }

    /** Man-made blocks -> ruins. Returns null to keep the block unchanged, air to remove it. */
    @Nullable
    public static BlockState ruin(BlockState s, long h) {
        Block b = s.getBlock();
        byte cat = category(b);
        if (cat == NONE) {
            return null;
        }
        if (cat == ROT) {
            return removed(s);
        }
        int r = (int) Math.floorMod(h >>> 8, 100L);
        if (r < 42) {
            return removed(s);
        }
        if (r < 48) {
            return Blocks.COBWEB.defaultBlockState();
        }
        if (b == Blocks.STONE_BRICKS && (r & 1) == 0) {
            return Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
        }
        if (b == Blocks.COBBLESTONE && r % 3 == 0) {
            return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        }
        return null;
    }

    /** NONE / ROT / DECAY classification of a block (cached). Only vanilla blocks are ever touched. */
    public static byte category(Block b) {
        return CATEGORY.computeIfAbsent(b, MirrorPalette::classify);
    }

    private static byte classify(Block b) {
        if (b == ModBlocks.RIFT.get()) {
            return NONE;
        }
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(b);
        if (!"minecraft".equals(key.getNamespace())) {
            return NONE;
        }
        if (b instanceof net.minecraft.world.level.block.EntityBlock) {
            return ROT; // chests, furnaces, beds, signs, spawners... gone (no free loot in the dark)
        }
        String path = key.getPath();
        for (String t : ROT_TOKENS) {
            if (path.contains(t)) {
                return ROT;
            }
        }
        for (String t : DECAY_TOKENS) {
            if (path.contains(t)) {
                return DECAY;
            }
        }
        return NONE;
    }

    private static BlockState removed(BlockState s) {
        return s.getFluidState().isEmpty() ? Blocks.AIR.defaultBlockState() : s.getFluidState().createLegacyBlock();
    }

    private static BlockState surface(int r) {
        Block b;
        if (r < 30) {
            b = Blocks.TUFF;
        } else if (r < 52) {
            b = Blocks.ANDESITE;
        } else if (r < 68) {
            b = Blocks.STONE;
        } else if (r < 82) {
            b = Blocks.COBBLED_DEEPSLATE;
        } else if (r < 92) {
            b = Blocks.DEEPSLATE;
        } else {
            b = Blocks.SMOOTH_BASALT;
        }
        return b.defaultBlockState();
    }

    private static BlockState sub(int r) {
        return (r < 45 ? Blocks.TUFF : r < 75 ? Blocks.ANDESITE : Blocks.STONE).defaultBlockState();
    }

    // ------------------------------------------------------------------ position hash

    public static long hash(int x, int y, int z) {
        long v = BlockPos.asLong(x, y, z) * 0x9E3779B97F4A7C15L;
        v ^= v >>> 29;
        v *= 0xBF58476D1CE4E5B9L;
        v ^= v >>> 32;
        return v;
    }
}
