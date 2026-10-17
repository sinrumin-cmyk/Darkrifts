package com.darkrifts;

import com.darkrifts.registry.ModBlockEntities;
import com.darkrifts.registry.ModBlocks;
import com.darkrifts.registry.ModCreativeTabs;
import com.darkrifts.registry.ModFeatures;
import com.darkrifts.registry.ModItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(DarkRifts.MODID)
public class DarkRifts {
    public static final String MODID = "darkrifts";

    public DarkRifts(IEventBus modBus, ModContainer container) {
        com.darkrifts.registry.ModFluids.FLUID_TYPES.register(modBus);
        com.darkrifts.registry.ModFluids.FLUIDS.register(modBus);
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModFeatures.FEATURES.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
        container.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }
}
