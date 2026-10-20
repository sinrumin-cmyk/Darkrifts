package com.darkrifts.registry;

import com.darkrifts.DarkRifts;
import com.darkrifts.block.RiftBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(DarkRifts.MODID);

    public static final DeferredBlock<RiftBlock> RIFT = BLOCKS.register("rift",
            () -> new RiftBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .noCollission()
                    .noOcclusion()
                    .strength(-1.0F, 3600000.0F)
                    .lightLevel(state -> 9)
                    .pushReaction(PushReaction.BLOCK)
                    .noLootTable()));

    public static final DeferredBlock<LiquidBlock> DARK_WATER = BLOCKS.register("dark_water",
            () -> new LiquidBlock(ModFluids.SOURCE.get(), BlockBehaviour.Properties.ofFullCopy(Blocks.WATER)));

    /** Floor of the void prison: unbreakable, glowing, starry. */
    public static final DeferredBlock<Block> VOID_MATTER = BLOCKS.register("void_matter",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(-1.0F, 3600000.0F)
                    .lightLevel(state -> 8)
                    .noLootTable()));

    /** Altar of the ritual that awakens the Rift Reaver. */
    public static final DeferredBlock<com.darkrifts.block.RitualAltarBlock> RITUAL_ALTAR = BLOCKS.register("ritual_altar",
            () -> new com.darkrifts.block.RitualAltarBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(35.0F, 1200.0F)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> 7)
                    .noOcclusion()));

    /** Ore of the mirror dimension. Generated only in the Dark Mirror (see world/MirrorOreFeature). */
    public static final DeferredBlock<Block> MIRROR_ORE = BLOCKS.register("mirror_ore",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(4.5F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> 4)
                    .sound(net.minecraft.world.level.block.SoundType.DEEPSLATE)));

    public static final DeferredBlock<Block> MIRRORITE_BLOCK = BLOCKS.register("mirrorite_block",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(5.0F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(net.minecraft.world.level.block.SoundType.METAL)));

    /** Heart Pedestal at the foot of each tower of the Sanctum (insert a Reaver Heart). */
    public static final DeferredBlock<com.darkrifts.block.SanctumTowerBlock> SANCTUM_TOWER = BLOCKS.register("sanctum_tower",
            () -> new com.darkrifts.block.SanctumTowerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(-1.0F, 3600000.0F)
                    .lightLevel(state -> 10)
                    .noOcclusion()
                    .noLootTable()));

    /** The Egg Altar in the middle of the Sanctum (insert the Dragon Egg once all four towers hold a heart). */
    public static final DeferredBlock<com.darkrifts.block.SanctumCoreBlock> SANCTUM_CORE = BLOCKS.register("sanctum_core",
            () -> new com.darkrifts.block.SanctumCoreBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(-1.0F, 3600000.0F)
                    .lightLevel(state -> 12)
                    .noOcclusion()
                    .noLootTable()));

    private ModBlocks() {}
}
