package com.darkrifts.registry;

import com.darkrifts.DarkRifts;
import com.darkrifts.item.RiftBladeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DarkRifts.MODID);

    public static final DeferredItem<RiftBladeItem> RIFT_BLADE = ITEMS.register("rift_blade",
            () -> new RiftBladeItem(new Item.Properties()
                    .rarity(Rarity.EPIC)
                    .fireResistant()
                    .attributes(SwordItem.createAttributes(Tiers.NETHERITE, 3, -2.4F))));

    /** Creative / operator convenience: a single rift block that leads to a random dark dimension. */
    public static final DeferredItem<BlockItem> RIFT_BLOCK = ITEMS.register("rift",
            () -> new BlockItem(ModBlocks.RIFT.get(), new Item.Properties().rarity(Rarity.EPIC)));

    public static final DeferredItem<BucketItem> DARK_WATER_BUCKET = ITEMS.register("dark_water_bucket",
            () -> new BucketItem(ModFluids.SOURCE.get(), new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

    private ModItems() {}
}
