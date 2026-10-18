package com.darkrifts.client;

import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.phys.Vec3;

/**
 * Special effects for the mirror and the void prison: no vanilla sun, moon, stars or clouds at all.
 * The sky itself is drawn by {@link SkyRenderer}.
 */
public class DarkSkyEffects extends DimensionSpecialEffects {
    public DarkSkyEffects() {
        super(Float.NaN, true, DimensionSpecialEffects.SkyType.NONE, false, false);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
        return fogColor;
    }

    @Override
    public boolean isFoggyAt(int x, int y) {
        return false;
    }
}
