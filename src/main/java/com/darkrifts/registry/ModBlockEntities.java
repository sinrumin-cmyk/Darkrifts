package com.darkrifts.registry;

import com.darkrifts.DarkRifts;
import com.darkrifts.block.RiftBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, DarkRifts.MODID);

    public static final Supplier<BlockEntityType<RiftBlockEntity>> RIFT = BLOCK_ENTITIES.register("rift",
            () -> BlockEntityType.Builder.of(RiftBlockEntity::new, ModBlocks.RIFT.get()).build(null));

    public static final Supplier<BlockEntityType<com.darkrifts.block.RitualAltarBlockEntity>> ALTAR =
            BLOCK_ENTITIES.register("ritual_altar", () -> BlockEntityType.Builder.of(
                    com.darkrifts.block.RitualAltarBlockEntity::new, ModBlocks.RITUAL_ALTAR.get()).build(null));

    public static final Supplier<BlockEntityType<com.darkrifts.block.SanctumTowerBlockEntity>> SANCTUM_TOWER =
            BLOCK_ENTITIES.register("sanctum_tower", () -> BlockEntityType.Builder.of(
                    com.darkrifts.block.SanctumTowerBlockEntity::new, ModBlocks.SANCTUM_TOWER.get()).build(null));

    public static final Supplier<BlockEntityType<com.darkrifts.block.SanctumCoreBlockEntity>> SANCTUM_CORE =
            BLOCK_ENTITIES.register("sanctum_core", () -> BlockEntityType.Builder.of(
                    com.darkrifts.block.SanctumCoreBlockEntity::new, ModBlocks.SANCTUM_CORE.get()).build(null));

    private ModBlockEntities() {}
}
