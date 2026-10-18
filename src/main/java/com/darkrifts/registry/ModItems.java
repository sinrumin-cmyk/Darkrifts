package com.darkrifts.registry;

import com.darkrifts.DarkRifts;
import com.darkrifts.item.RiftArmorItem;
import com.darkrifts.item.RiftBladeItem;
import net.minecraft.world.item.ArmorItem;
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

    public static final DeferredItem<BlockItem> VOID_MATTER = ITEMS.register("void_matter",
            () -> new BlockItem(ModBlocks.VOID_MATTER.get(), new Item.Properties()));

    public static final DeferredItem<BucketItem> DARK_WATER_BUCKET = ITEMS.register("dark_water_bucket",
            () -> new BucketItem(ModFluids.SOURCE.get(), new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

    public static final DeferredItem<RiftArmorItem> RIFT_HELMET = armor("rift_helmet", ArmorItem.Type.HELMET);
    public static final DeferredItem<RiftArmorItem> RIFT_CHESTPLATE = armor("rift_chestplate", ArmorItem.Type.CHESTPLATE);
    public static final DeferredItem<RiftArmorItem> RIFT_LEGGINGS = armor("rift_leggings", ArmorItem.Type.LEGGINGS);
    public static final DeferredItem<RiftArmorItem> RIFT_BOOTS = armor("rift_boots", ArmorItem.Type.BOOTS);

    public static final DeferredItem<BlockItem> RITUAL_ALTAR = ITEMS.register("ritual_altar",
            () -> new BlockItem(ModBlocks.RITUAL_ALTAR.get(), new Item.Properties().rarity(Rarity.EPIC)));

    public static final DeferredItem<net.neoforged.neoforge.common.DeferredSpawnEggItem> RIFT_REAVER_SPAWN_EGG =
            ITEMS.register("rift_reaver_spawn_egg", () -> new net.neoforged.neoforge.common.DeferredSpawnEggItem(
                    ModEntities.RIFT_REAVER, 0x120824, 0xDC6EFF, new Item.Properties()));

    private static DeferredItem<RiftArmorItem> armor(String name, ArmorItem.Type type) {
        return ITEMS.register(name, () -> new RiftArmorItem(type, new Item.Properties()
                .durability(type.getDurability(37))
                .rarity(Rarity.EPIC)
                .fireResistant()));
    }

    private ModItems() {}
}
