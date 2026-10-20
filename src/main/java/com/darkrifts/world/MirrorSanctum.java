package com.darkrifts.world;

import com.darkrifts.DarkDimensions;
import com.darkrifts.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.ArrayDeque;

/**
 * THE SANCTUM: the endgame arena that exists only in the Dark Mirror, centred on x=1000, z=1000.
 *
 *  - a 27-block plateau on an inverted-cone foundation, runes of crying obsidian burnt into the floor
 *    (octagram, rings, four spokes), an outer wall with four diagonal gates and ramps down to the terrain,
 *  - FOUR TOWERS (north, east, south, west), 30 blocks high, each with a Heart Pedestal inside (insert a Reaver Heart),
 *  - in the centre a dais with the Egg Altar (insert the Dragon Egg once all four hearts are in),
 *  - below the plateau a crypt (dungeon): wither-skeleton spawners, ancient loot, sculk, reached by a long stair.
 *
 * It is built the first time a player comes within 96 blocks (chunks are generated first), a few thousand blocks per
 * tick so the server never stalls. {@code /darkrifts sanctum} builds it at once and teleports you there.
 */
public final class MirrorSanctum {
    public static final int CX = 1000;
    public static final int CZ = 1000;
    public static final int TOWER_DIST = 19;
    public static final int RADIUS = 27;
    /** Core block sits this far above the plateau block; pedestals one above it. */
    public static final int CORE_DY = 3;
    private static final int[][] DIRS = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}}; // N E S W

    private static final ArrayDeque<Runnable> STEPS = new ArrayDeque<>();
    private static boolean running;

    private MirrorSanctum() {}

    // ------------------------------------------------------------------ persistent state

    public static final class Data extends SavedData {
        public boolean built;
        public int y0;

        public Data() {}

        public static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data d = new Data();
            d.built = tag.getBoolean("Built");
            d.y0 = tag.getInt("Y0");
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putBoolean("Built", built);
            tag.putInt("Y0", y0);
            return tag;
        }
    }

    public static Data data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), "darkrifts_sanctum");
    }

    public static boolean isMirror(Level level) {
        return DarkDimensions.isDark(level.dimension());
    }

    public static BlockPos corePos(int y0) {
        return new BlockPos(CX, y0 + CORE_DY, CZ);
    }

    /** The four heart pedestals (N, E, S, W) belonging to a core at {@code core}. */
    public static BlockPos[] pedestals(BlockPos core) {
        BlockPos[] out = new BlockPos[4];
        for (int i = 0; i < 4; i++) {
            out[i] = core.offset(DIRS[i][0] * TOWER_DIST, 1 - CORE_DY, DIRS[i][1] * TOWER_DIST);
        }
        return out;
    }

    // ------------------------------------------------------------------ driving the build

    public static void clear() {
        STEPS.clear();
        running = false;
    }

    public static boolean isRunning() {
        return running;
    }

    /** Called every server tick. */
    public static void tick(MinecraftServer server) {
        ServerLevel mirror = server.getLevel(DarkDimensions.key(DarkDimensions.MIRROR));
        if (mirror == null) {
            return;
        }
        if (running) {
            long end = System.nanoTime() + 7_000_000L;
            while (!STEPS.isEmpty() && System.nanoTime() < end) {
                STEPS.poll().run();
            }
            if (STEPS.isEmpty()) {
                running = false;
            }
            return;
        }
        if (server.getTickCount() % 40 != 0) {
            return;
        }
        for (ServerPlayer p : mirror.players()) {
            double dx = p.getX() - CX;
            double dz = p.getZ() - CZ;
            if (dx * dx + dz * dz < 96.0 * 96.0) {
                if (!data(mirror).built) {
                    start(mirror);
                }
                return;
            }
        }
    }

    /** Starts the (time-sliced) build. Returns false if it is already built or being built. */
    public static boolean start(ServerLevel level) {
        if (running || data(level).built) {
            return false;
        }
        running = true;
        int c0x = (CX - RADIUS - 3) >> 4;
        int c1x = (CX + RADIUS + 3) >> 4;
        int c0z = (CZ - RADIUS - 3) >> 4;
        int c1z = (CZ + RADIUS + 3) >> 4;
        for (int x = c0x; x <= c1x; x++) {
            for (int z = c0z; z <= c1z; z++) {
                final int fx = x;
                final int fz = z;
                STEPS.add(() -> level.getChunk(fx, fz));
            }
        }
        STEPS.add(() -> plan(level));
        return true;
    }

    /** Builds everything at once (command). */
    public static void buildNow(ServerLevel level) {
        if (data(level).built) {
            return;
        }
        if (!running) {
            start(level);
        }
        while (!STEPS.isEmpty()) {
            STEPS.poll().run();
        }
        running = false;
    }

    // ------------------------------------------------------------------ the plan

    private static void plan(ServerLevel level) {
        int h = level.getHeight(Heightmap.Types.WORLD_SURFACE, CX, CZ);
        int y0 = Mth.clamp(h - 1, level.getMinBuildHeight() + 26, level.getMaxBuildHeight() - 76);
        RandomSource rnd = level.getRandom();

        for (int dy = 1; dy <= 46; dy++) {
            final int y = y0 + dy;
            STEPS.add(() -> clearSlice(level, y));
        }
        for (int dy = -18; dy <= 0; dy++) {
            final int y = y0 + dy;
            STEPS.add(() -> foundationSlice(level, y0, y));
        }
        STEPS.add(() -> runes(level, y0));
        STEPS.add(() -> wall(level, y0));
        STEPS.add(() -> dais(level, y0));
        for (int i = 0; i < 4; i++) {
            final int[] d = DIRS[i];
            for (int band = 0; band < 3; band++) {
                final int b = band;
                STEPS.add(() -> tower(level, y0, d[0], d[1], b));
            }
        }
        for (int i = 0; i < 4; i++) {
            final int k = i;
            STEPS.add(() -> gateAndRamp(level, y0, k));
        }
        STEPS.add(() -> crypt(level, y0, rnd));
        STEPS.add(() -> {
            level.setBlock(corePos(y0), ModBlocks.SANCTUM_CORE.get().defaultBlockState(), 3);
            for (BlockPos p : pedestals(corePos(y0))) {
                level.setBlock(p, ModBlocks.SANCTUM_TOWER.get().defaultBlockState(), 3);
            }
            Data d = data(level);
            d.built = true;
            d.y0 = y0;
            d.setDirty();
        });
    }

    // ------------------------------------------------------------------ helpers

    private static void set(ServerLevel level, int x, int y, int z, BlockState state) {
        level.setBlock(new BlockPos(x, y, z), state, 18);
    }

    private static void set(ServerLevel level, int x, int y, int z, Block block) {
        set(level, x, y, z, block.defaultBlockState());
    }

    private static int hash(int x, int z) {
        int h = x * 73856093 ^ z * 19349663;
        h ^= h >>> 13;
        return h & 0x7FFFFFFF;
    }

    private static Block floorBlock(int x, int z) {
        int h = hash(x, z) % 20;
        return h < 11 ? Blocks.POLISHED_BLACKSTONE_BRICKS : h < 16 ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS
                : h < 19 ? Blocks.POLISHED_BLACKSTONE : Blocks.BLACKSTONE;
    }

    private static void line(ServerLevel level, double x0, double z0, double x1, double z1, int y, Block block) {
        int steps = (int) Math.ceil(Math.max(Math.abs(x1 - x0), Math.abs(z1 - z0))) * 2;
        for (int i = 0; i <= steps; i++) {
            double f = i / (double) steps;
            set(level, (int) Math.floor(x0 + (x1 - x0) * f + 0.5), y, (int) Math.floor(z0 + (z1 - z0) * f + 0.5), block);
        }
    }

    // ------------------------------------------------------------------ stages

    private static void clearSlice(ServerLevel level, int y) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int r = RADIUS + 1;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r) {
                    continue;
                }
                p.set(CX + dx, y, CZ + dz);
                if (!level.getBlockState(p).isAir()) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), 18);
                }
            }
        }
    }

    private static void foundationSlice(ServerLevel level, int y0, int y) {
        double rad = RADIUS - (y0 - y) * 0.45;
        int r = (int) Math.ceil(rad);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > rad) {
                    continue;
                }
                int x = CX + dx;
                int z = CZ + dz;
                if (y == y0) {
                    set(level, x, y, z, floorBlock(x, z));
                } else {
                    set(level, x, y, z, hash(x + y, z) % 9 == 0 ? Blocks.DEEPSLATE : Blocks.BLACKSTONE);
                }
            }
        }
    }

    /** Crying obsidian runes burnt into the plateau: rings, the octagram, the four spokes. */
    private static void runes(ServerLevel level, int y0) {
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > RADIUS) {
                    continue;
                }
                Block b = null;
                if (d >= 25.0) {
                    b = Blocks.OBSIDIAN;
                } else if ((d >= 16.5 && d < 17.4) || (d >= 9.4 && d < 10.2)) {
                    b = Blocks.CRYING_OBSIDIAN;
                } else if (d >= 21.2 && d < 21.9 && (hash(dx, dz) % 3) != 0) {
                    b = Blocks.AMETHYST_BLOCK;
                } else if ((dx == 0 || dz == 0) && d >= 7.0 && d <= TOWER_DIST - 4) {
                    b = Blocks.CRYING_OBSIDIAN;
                }
                if (b != null) {
                    set(level, CX + dx, y0, CZ + dz, b);
                }
            }
        }
        // {8/3} star between eight points on a circle of radius 22.5
        double[] px = new double[8];
        double[] pz = new double[8];
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4.0 + Math.PI / 8.0;
            px[k] = CX + Math.cos(a) * 22.5;
            pz[k] = CZ + Math.sin(a) * 22.5;
        }
        for (int k = 0; k < 8; k++) {
            int j = (k + 3) % 8;
            line(level, px[k], pz[k], px[j], pz[j], y0, Blocks.CRYING_OBSIDIAN);
        }
        // eight glyph stones on the star points
        for (int k = 0; k < 8; k++) {
            set(level, (int) Math.round(px[k]), y0, (int) Math.round(pz[k]), Blocks.GILDED_BLACKSTONE);
        }
    }

    /** Outer wall with merlons, buttresses and four diagonal gates. */
    private static void wall(ServerLevel level, int y0) {
        for (int dx = -27; dx <= 27; dx++) {
            for (int dz = -27; dz <= 27; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d < 24.4 || d > 26.3) {
                    continue;
                }
                int x = CX + dx;
                int z = CZ + dz;
                boolean gate = Math.abs(Math.abs(dx) - Math.abs(dz)) <= 1;
                for (int y = 1; y <= 4; y++) {
                    if (gate && y <= 3) {
                        set(level, x, y0 + y, z, Blocks.AIR);
                    } else {
                        set(level, x, y0 + y, z, y == 4 || hash(x, z + y) % 7 == 0
                                ? Blocks.POLISHED_BLACKSTONE_BRICKS : Blocks.DEEPSLATE_BRICKS);
                    }
                }
                if (!gate && (((dx >> 1) + (dz >> 1)) & 1) == 0) {
                    set(level, x, y0 + 5, z, Blocks.POLISHED_BLACKSTONE_BRICK_WALL);
                }
            }
        }
        // buttress pillars every 22.5 degrees (skipping the gates) with a light on top
        for (int k = 0; k < 16; k++) {
            double a = k * Math.PI / 8.0;
            int x = CX + (int) Math.round(Math.cos(a) * 25.3);
            int z = CZ + (int) Math.round(Math.sin(a) * 25.3);
            if (Math.abs(Math.abs(x - CX) - Math.abs(z - CZ)) <= 2) {
                continue;
            }
            for (int y = 1; y <= 8; y++) {
                set(level, x, y0 + y, z, y % 3 == 0 ? Blocks.CRYING_OBSIDIAN : Blocks.OBSIDIAN);
            }
            set(level, x, y0 + 9, z, Blocks.END_ROD);
        }
    }

    /** The dais with the Egg Altar spot, and eight pillars around it. */
    private static void dais(ServerLevel level, int y0) {
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -7; dz <= 7; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > 7.0) {
                    continue;
                }
                set(level, CX + dx, y0 + 1, CZ + dz, d > 6.0 ? Blocks.CRYING_OBSIDIAN : Blocks.OBSIDIAN);
                if (d <= 5.0) {
                    set(level, CX + dx, y0 + 2, CZ + dz, d > 4.2 ? Blocks.CRYING_OBSIDIAN
                            : (hash(dx, dz) % 4 == 0 ? Blocks.GILDED_BLACKSTONE : Blocks.POLISHED_BLACKSTONE_BRICKS));
                }
            }
        }
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4.0 + Math.PI / 8.0;
            int x = CX + (int) Math.round(Math.cos(a) * 11.0);
            int z = CZ + (int) Math.round(Math.sin(a) * 11.0);
            for (int y = 1; y <= 7; y++) {
                set(level, x, y0 + y, z, y % 3 == 0 ? Blocks.CRYING_OBSIDIAN : Blocks.POLISHED_BLACKSTONE_BRICKS);
            }
            set(level, x, y0 + 8, z, Blocks.OBSIDIAN);
            set(level, x, y0 + 9, z, Blocks.END_ROD);
        }
    }

    /** One tower of the four; built in three vertical bands (0: base, 1: shaft, 2: roof and spire). */
    private static void tower(ServerLevel level, int y0, int dirX, int dirZ, int band) {
        int tx = CX + dirX * TOWER_DIST;
        int tz = CZ + dirZ * TOWER_DIST;
        int yFrom = band == 0 ? 1 : band == 1 ? 11 : 22;
        int yTo = band == 0 ? 10 : band == 1 ? 21 : 36;
        for (int y = yFrom; y <= yTo; y++) {
            for (int ox = -4; ox <= 4; ox++) {
                for (int oz = -4; oz <= 4; oz++) {
                    int m = Math.max(Math.abs(ox), Math.abs(oz));
                    int x = tx + ox;
                    int z = tz + oz;
                    int yy = y0 + y;
                    if (y < 22) {
                        if (m < 3) {
                            // hollow shaft; floor details only at the bottom
                            // (the pedestal at the centre is placed at the very end of the build)
                            set(level, x, yy, z, y == 1 && Math.abs(ox) == 2 && Math.abs(oz) == 2
                                    ? Blocks.SOUL_LANTERN : Blocks.AIR);
                            continue;
                        }
                        boolean corner = Math.abs(ox) == 4 && Math.abs(oz) == 4;
                        BlockState s;
                        if (corner) {
                            s = (y % 4 == 0 ? Blocks.CRYING_OBSIDIAN : Blocks.OBSIDIAN).defaultBlockState();
                        } else if (y % 5 == 0) {
                            s = Blocks.DEEPSLATE_TILES.defaultBlockState();
                        } else if (((y >= 6 && y <= 8) || (y >= 14 && y <= 16)) && (ox == 0 || oz == 0)) {
                            s = Blocks.PURPLE_STAINED_GLASS.defaultBlockState();
                        } else {
                            s = (hash(x, z + y) % 6 == 0 ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS
                                    : Blocks.POLISHED_BLACKSTONE_BRICKS).defaultBlockState();
                        }
                        // the door faces the centre of the plateau
                        boolean doorSide = (dirX != 0 && ox * -dirX >= 3) || (dirZ != 0 && oz * -dirZ >= 3);
                        int along = dirX != 0 ? oz : ox;
                        if (doorSide && Math.abs(along) <= 1 && y <= 4) {
                            s = Blocks.AIR.defaultBlockState();
                        }
                        set(level, x, yy, z, s);
                    } else {
                        // roof ring and stepped spire
                        int layer = (y - 22) / 3;
                        int half = 4 - layer;
                        if (half < 0) {
                            continue;
                        }
                        if (m <= half) {
                            boolean centre = ox == 0 && oz == 0;
                            if (centre) {
                                set(level, x, yy, z, y >= 33 ? Blocks.END_ROD : Blocks.AIR);
                            } else {
                                set(level, x, yy, z, m == half && (y - 22) % 3 == 0 ? Blocks.CRYING_OBSIDIAN : Blocks.POLISHED_BLACKSTONE_BRICKS);
                            }
                        }
                    }
                }
            }
        }
    }

    /** A ramp 3 blocks wide that leaves the gate and runs down until it meets open ground. */
    private static void gateAndRamp(ServerLevel level, int y0, int k) {
        int sx = (k & 1) == 0 ? 1 : -1;
        int sz = (k & 2) == 0 ? 1 : -1;
        for (int i = 28; i <= 64; i++) {
            int x = CX + (int) Math.round(sx * i / Math.sqrt(2.0));
            int z = CZ + (int) Math.round(sz * i / Math.sqrt(2.0));
            int yy = y0 - (i - 27) / 2;
            boolean airAbove = true;
            for (int ax = -1; ax <= 1; ax++) {
                for (int az = -1; az <= 1; az++) {
                    for (int dy = 1; dy <= 3; dy++) {
                        if (!level.getBlockState(new BlockPos(x + ax, yy + dy, z + az)).isAir()) {
                            airAbove = false;
                        }
                    }
                }
            }
            boolean groundUnder = level.getBlockState(new BlockPos(x, yy - 1, z)).isSolid();
            for (int ax = -1; ax <= 1; ax++) {
                for (int az = -1; az <= 1; az++) {
                    set(level, x + ax, yy, z + az, Blocks.POLISHED_BLACKSTONE_BRICKS);
                    for (int dy = 1; dy <= 3; dy++) {
                        set(level, x + ax, yy + dy, z + az, Blocks.AIR);
                    }
                }
            }
            if (i % 6 == 0) {
                set(level, x + sz * 2, yy + 1, z - sx * 2, Blocks.SOUL_LANTERN);
            }
            if (i > 30 && airAbove && groundUnder) {
                break;
            }
        }
    }

    /** The dungeon below the plateau. */
    private static void crypt(ServerLevel level, int y0, RandomSource rnd) {
        int floor = y0 - 13;
        // hall
        for (int dx = -10; dx <= 10; dx++) {
            for (int dz = -10; dz <= 10; dz++) {
                int x = CX + dx;
                int z = CZ + dz;
                set(level, x, floor, z, hash(x, z) % 5 == 0 ? Blocks.DEEPSLATE_TILES : Blocks.POLISHED_DEEPSLATE);
                for (int y = floor + 1; y <= y0 - 5; y++) {
                    set(level, x, y, z, Blocks.AIR);
                }
                // sculk creeping over the floor
                if (hash(x * 3, z * 7) % 9 == 0) {
                    set(level, x, floor, z, Blocks.SCULK);
                }
            }
        }
        // ribbed ceiling beams and pillars
        for (int dx = -10; dx <= 10; dx += 4) {
            for (int dz = -10; dz <= 10; dz++) {
                set(level, CX + dx, y0 - 5, CZ + dz, Blocks.DEEPSLATE_BRICKS);
            }
        }
        for (int px : new int[]{-5, 5}) {
            for (int pz : new int[]{-5, 5}) {
                for (int y = floor + 1; y <= y0 - 5; y++) {
                    set(level, CX + px, y, CZ + pz, y % 3 == 0 ? Blocks.CRYING_OBSIDIAN : Blocks.DEEPSLATE_TILES);
                }
                set(level, CX + px + 1, floor + 1, CZ + pz, Blocks.SOUL_LANTERN);
            }
        }
        // altar of bones in the middle
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                set(level, CX + dx, floor + 1, CZ + dz, Math.abs(dx) + Math.abs(dz) <= 2 ? Blocks.BONE_BLOCK : Blocks.AIR);
            }
        }
        set(level, CX, floor + 2, CZ, Blocks.SOUL_LANTERN);
        // loot and guardians
        ResourceKey<LootTable> loot = ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.withDefaultNamespace("chests/ancient_city"));
        int[][] corners = {{-9, -9}, {9, -9}, {-9, 9}, {9, 9}};
        Direction[] faces = {Direction.SOUTH, Direction.SOUTH, Direction.NORTH, Direction.NORTH};
        for (int i = 0; i < 4; i++) {
            BlockPos cp = new BlockPos(CX + corners[i][0], floor + 1, CZ + corners[i][1]);
            level.setBlock(cp, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, faces[i]), 18);
            BlockEntity be = level.getBlockEntity(cp);
            if (be instanceof RandomizableContainerBlockEntity c) {
                c.setLootTable(loot, rnd.nextLong());
            }
        }
        EntityType<?>[] mobs = {EntityType.WITHER_SKELETON, EntityType.WITHER_SKELETON, EntityType.SKELETON, EntityType.SKELETON};
        int[][] spawners = {{-10, 0}, {10, 0}, {0, -10}, {0, 10}};
        for (int i = 0; i < 4; i++) {
            BlockPos sp = new BlockPos(CX + spawners[i][0] / 10 * 9, floor + 1, CZ + spawners[i][1] / 10 * 9);
            level.setBlock(sp, Blocks.SPAWNER.defaultBlockState(), 18);
            if (level.getBlockEntity(sp) instanceof SpawnerBlockEntity spawner) {
                spawner.setEntityId(mobs[i], rnd);
            }
        }
        // the long stair: from the plateau (east side) down into the hall
        for (int i = 0; i <= 12; i++) {
            int x = CX + 23 - i;
            int yb = y0 - 1 - i;
            for (int z = CZ - 1; z <= CZ + 1; z++) {
                for (int y = yb + 1; y <= yb + 4; y++) {
                    set(level, x, y, z, Blocks.AIR);
                }
                set(level, x, yb, z, Blocks.DEEPSLATE_TILE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
                set(level, x, yb - 1, z, Blocks.DEEPSLATE_BRICKS);
            }
            set(level, x, yb + 1, CZ - 2, Blocks.DEEPSLATE_BRICKS);
            set(level, x, yb + 1, CZ + 2, Blocks.DEEPSLATE_BRICKS);
            if (i % 4 == 2) {
                set(level, x, yb + 2, CZ - 2, Blocks.SOUL_LANTERN);
                set(level, x, yb + 2, CZ + 2, Blocks.SOUL_LANTERN);
            }
        }
        // the stair mouth on the plateau: two obsidian posts
        for (int dz : new int[]{-2, 2}) {
            for (int y = 1; y <= 4; y++) {
                set(level, CX + 24, y0 + y, CZ + dz, y == 4 ? Blocks.END_ROD : Blocks.OBSIDIAN);
            }
        }
    }
}
