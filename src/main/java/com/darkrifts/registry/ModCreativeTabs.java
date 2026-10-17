package com.darkrifts.registry;

import com.darkrifts.DarkRifts;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, DarkRifts.MODID);

    public static final Supplier<CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.darkrifts"))
                    .icon(() -> ModItems.RIFT_BLADE.get().getDefaultInstance())
                    .displayItems((params, output) -> {
                        output.accept(ModItems.RIFT_BLADE.get());
                        output.accept(ModItems.RIFT_BLOCK.get());
                    })
                    .build());

    private ModCreativeTabs() {}
}
