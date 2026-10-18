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

    private ModBlockEntities() {}
}
