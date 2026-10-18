package com.darkrifts.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

public final class AbilityKeys {
    /** Hold (or tap) to open the radial ability menu while holding the Rift Blade. Rebindable in Controls. */
    public static final KeyMapping OPEN = new KeyMapping("key.darkrifts.abilities", InputConstants.KEY_R,
            "key.categories.darkrifts");

    private AbilityKeys() {}
}
