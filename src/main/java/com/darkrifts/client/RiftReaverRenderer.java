package com.darkrifts.client;

import com.darkrifts.DarkRifts;
import com.darkrifts.entity.RiftReaver;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Assets: geo/animations "rift_reaver", textures/entity/rift_reaver.png (+ _glowmask.png for eyes, chest core, runes).
 * While the boss is coming out of the sky it also draws the rift above it (see SkyRiftRenderer).
 */
public class RiftReaverRenderer extends GeoEntityRenderer<RiftReaver> {
    public RiftReaverRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(ResourceLocation.fromNamespaceAndPath(DarkRifts.MODID, "rift_reaver")));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 1.6F;
    }

    @Override
    public void render(RiftReaver entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        if (!entity.isRiftActive()) {
            return;
        }
        float age = entity.getRiftAge(partialTick);
        float open = RiftReaver.riftOpen(age);
        if (open <= 0.0F) {
            return;
        }
        double y = Mth.lerp(partialTick, entity.yOld, entity.getY());
        poseStack.pushPose();
        poseStack.translate(0.0, entity.getRiftY() - y, 0.0);
        SkyRiftRenderer.draw(poseStack, bufferSource, open, entity.level().getGameTime() + partialTick);
        poseStack.popPose();
    }

    /** The tear is far above the boss: don't cull the whole thing when the boss itself is off screen. */
    @Override
    public boolean shouldRender(RiftReaver entity, Frustum frustum, double camX, double camY, double camZ) {
        return entity.isRiftActive() || super.shouldRender(entity, frustum, camX, camY, camZ);
    }
}
