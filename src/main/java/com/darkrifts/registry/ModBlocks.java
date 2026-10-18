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

    private ModBlocks() {}
}
