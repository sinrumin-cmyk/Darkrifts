package com.darkrifts.registry;

import com.darkrifts.DarkRifts;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Dark water: behaves exactly like water (it is in the minecraft:water fluid tag) but is tinted almost black.
 * The mirror dimension swaps every ocean, river and lake for it.
 */
public final class ModFluids {
    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, DarkRifts.MODID);
    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(Registries.FLUID, DarkRifts.MODID);

    public static final DeferredHolder<FluidType, FluidType> DARK_WATER_TYPE = FLUID_TYPES.register("dark_water",
            () -> new FluidType(FluidType.Properties.create()
                    .descriptionId("block.darkrifts.dark_water")
                    .canSwim(true)
                    .canDrown(true)
                    .canPushEntity(true)
                    .canExtinguish(true)
                    .supportsBoating(true)
                    .canConvertToSource(true)
                    .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                    .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> SOURCE = FLUIDS.register("dark_water",
            () -> new BaseFlowingFluid.Source(ModFluids.PROPS));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING = FLUIDS.register("flowing_dark_water",
            () -> new BaseFlowingFluid.Flowing(ModFluids.PROPS));

    public static final BaseFlowingFluid.Properties PROPS =
            new BaseFlowingFluid.Properties(DARK_WATER_TYPE, SOURCE, FLOWING)
                    .block(ModBlocks.DARK_WATER)
                    .bucket(ModItems.DARK_WATER_BUCKET)
                    .slopeFindDistance(4)
                    .levelDecreasePerBlock(1);

    private ModFluids() {}
}
