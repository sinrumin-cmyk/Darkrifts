package com.darkrifts.client;

import com.darkrifts.DarkRifts;
import com.darkrifts.entity.RiftSpike;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Assets: geo/entity/rift_spike.geo.json, animations/entity/rift_spike.animation.json,
 * textures/entity/rift_spike.png (+ rift_spike_glowmask.png for the glowing veins).
 */
public class RiftSpikeRenderer extends GeoEntityRenderer<RiftSpike> {
    public RiftSpikeRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(ResourceLocation.fromNamespaceAndPath(DarkRifts.MODID, "rift_spike")));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }
}
