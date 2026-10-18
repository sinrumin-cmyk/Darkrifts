package com.darkrifts.client;

import com.darkrifts.DarkRifts;
import com.darkrifts.entity.RiftReaver;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Assets: geo/animations "rift_reaver", textures/entity/rift_reaver.png (+ _glowmask.png for eyes, chest rift, veins). */
public class RiftReaverRenderer extends GeoEntityRenderer<RiftReaver> {
    public RiftReaverRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(ResourceLocation.fromNamespaceAndPath(DarkRifts.MODID, "rift_reaver")));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 1.4F;
    }
}
