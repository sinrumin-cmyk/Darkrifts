package com.darkrifts.registry;

import com.darkrifts.DarkRifts;
import com.darkrifts.item.RiftArmorItem;
import com.darkrifts.item.RiftBladeItem;
import com.darkrifts.item.MirrorBowItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
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

    public static final DeferredItem<BlockItem> SANCTUM_TOWER = ITEMS.register("sanctum_tower",
            () -> new BlockItem(ModBlocks.SANCTUM_TOWER.get(), new Item.Properties().rarity(Rarity.EPIC)));

    public static final DeferredItem<BlockItem> SANCTUM_CORE = ITEMS.register("sanctum_core",
            () -> new BlockItem(ModBlocks.SANCTUM_CORE.get(), new Item.Properties().rarity(Rarity.EPIC)));

    // ------------------------------------------------------------------ progression: boss -> key -> mirror -> ore

    /** Guaranteed drop of the Rift Reaver. The core of the one-use Mirror Blade. */
    public static final DeferredItem<Item> REAVER_HEART = ITEMS.register("reaver_heart",
            () -> new Item(new Item.Properties().rarity(Rarity.EPIC).fireResistant()) {
                @Override
                public boolean isFoil(ItemStack stack) {
                    return true;
                }
            });

    /** One-use key: draws one rift into the Dark Mirror and shatters when the rift is finished. */
    public static final DeferredItem<RiftBladeItem> MIRROR_BLADE = ITEMS.register("mirror_blade",
            () -> new RiftBladeItem(new Item.Properties()
                    .rarity(Rarity.RARE)
                    .fireResistant()
                    .attributes(SwordItem.createAttributes(Tiers.IRON, 2, -2.4F)), true));

    public static final DeferredItem<BlockItem> MIRROR_ORE = ITEMS.register("mirror_ore",
            () -> new BlockItem(ModBlocks.MIRROR_ORE.get(), new Item.Properties()));

    public static final DeferredItem<Item> RAW_MIRRORITE = ITEMS.register("raw_mirrorite",
            () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> MIRRORITE_INGOT = ITEMS.register("mirrorite_ingot",
            () -> new Item(new Item.Properties().fireResistant()));

    public static final DeferredItem<BlockItem> MIRRORITE_BLOCK = ITEMS.register("mirrorite_block",
            () -> new BlockItem(ModBlocks.MIRRORITE_BLOCK.get(), new Item.Properties().fireResistant()));

    // ------------------------------------------------------------------ mirrorite arsenal

    public static final DeferredItem<SwordItem> MIRRORITE_SWORD = ITEMS.register("mirrorite_sword",
            () -> new SwordItem(ModTiers.MIRRORITE, new Item.Properties()
                    .attributes(SwordItem.createAttributes(ModTiers.MIRRORITE, 3, -2.4F))));

    public static final DeferredItem<PickaxeItem> MIRRORITE_PICKAXE = ITEMS.register("mirrorite_pickaxe",
            () -> new PickaxeItem(ModTiers.MIRRORITE, new Item.Properties()
                    .attributes(PickaxeItem.createAttributes(ModTiers.MIRRORITE, 1.0F, -2.8F))));

    public static final DeferredItem<AxeItem> MIRRORITE_AXE = ITEMS.register("mirrorite_axe",
            () -> new AxeItem(ModTiers.MIRRORITE, new Item.Properties()
                    .attributes(AxeItem.createAttributes(ModTiers.MIRRORITE, 5.0F, -3.0F))));

    public static final DeferredItem<ShovelItem> MIRRORITE_SHOVEL = ITEMS.register("mirrorite_shovel",
            () -> new ShovelItem(ModTiers.MIRRORITE, new Item.Properties()
                    .attributes(ShovelItem.createAttributes(ModTiers.MIRRORITE, 1.5F, -3.0F))));

    public static final DeferredItem<HoeItem> MIRRORITE_HOE = ITEMS.register("mirrorite_hoe",
            () -> new HoeItem(ModTiers.MIRRORITE, new Item.Properties()
                    .attributes(HoeItem.createAttributes(ModTiers.MIRRORITE, -3.0F, 0.0F))));

    /** Ranged weapon: harder-hitting piercing arrows (see ArsenalEvents). */
    public static final DeferredItem<MirrorBowItem> MIRROR_BOW = ITEMS.register("mirror_bow",
            () -> new MirrorBowItem(new Item.Properties().durability(900).rarity(Rarity.RARE)));

    private static DeferredItem<RiftArmorItem> armor(String name, ArmorItem.Type type) {
        return ITEMS.register(name, () -> new RiftArmorItem(type, new Item.Properties()
                .durability(type.getDurability(48))
                .rarity(Rarity.EPIC)
                .fireResistant()));
    }

    private ModItems() {}
}
