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
                        output.accept(ModItems.RIFT_HELMET.get());
                        output.accept(ModItems.RIFT_CHESTPLATE.get());
                        output.accept(ModItems.RIFT_LEGGINGS.get());
                        output.accept(ModItems.RIFT_BOOTS.get());
                        output.accept(ModItems.RITUAL_ALTAR.get());
                        output.accept(ModItems.RIFT_REAVER_SPAWN_EGG.get());
                        output.accept(codex());
                        output.accept(ModItems.RIFT_BLOCK.get());
                        output.accept(ModItems.DARK_WATER_BUCKET.get());
                        output.accept(ModItems.VOID_MATTER.get());
                    })
                    .build());

    /** The guide book: a written book whose pages are translatable (assets lang: book.darkrifts.pageN). */
    public static net.minecraft.world.item.ItemStack codex() {
        java.util.List<net.minecraft.server.network.Filterable<Component>> pages = new java.util.ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            pages.add(net.minecraft.server.network.Filterable.passThrough(
                    Component.translatable("book." + DarkRifts.MODID + ".page" + i)));
        }
        net.minecraft.world.item.ItemStack book = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WRITTEN_BOOK);
        book.set(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT,
                new net.minecraft.world.item.component.WrittenBookContent(
                        net.minecraft.server.network.Filterable.passThrough("Dark Rifts Codex"), "Dark Rifts", 0, pages, true));
        return book;
    }

    private ModCreativeTabs() {}
}
