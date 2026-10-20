package com.darkrifts.registry;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.SimpleTier;

/**
 * Mirrorite: the first tier of the mod. A bit better than diamond (it mines the same blocks), a bit weaker than
 * netherite on paper - its strength is the special effects of the weapons (see ArsenalEvents).
 */
public final class ModTiers {
    public static final Tier MIRRORITE = new SimpleTier(
            BlockTags.INCORRECT_FOR_DIAMOND_TOOL,
            1900,       // durability (diamond 1561, netherite 2031)
            8.5F,       // mining speed (diamond 8, netherite 9)
            3.5F,       // attack damage bonus (diamond 3, netherite 4)
            17,         // enchantability
            () -> Ingredient.of(ModItems.MIRRORITE_INGOT.get()));

    private ModTiers() {}
}
