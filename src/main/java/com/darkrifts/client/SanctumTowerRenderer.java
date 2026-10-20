package com.darkrifts.client;

import com.darkrifts.block.SanctumTowerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The Heart Pedestal. Empty: a dim rune ring on the floor and a faint ember above the socket.
 * With a heart: the ring burns, a crystal heart floats and spins inside gimbal rings, a pink-white pillar of light
 * runs up the whole tower, and the first second after the insertion a flare ring races outwards.
 */
public class SanctumTowerRenderer implements BlockEntityRenderer<SanctumTowerBlockEntity> {
    public SanctumTowerRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public boolean shouldRenderOffScreen(SanctumTowerBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 160;
    }

    @Override
    public void render(SanctumTowerBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        boolean filled = be.isFilled();
        float time = level.getGameTime() + partialTick;
        float age = filled ? level.getGameTime() - be.getFilledAt() + partialTick : 0.0F;
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Matrix4f m = poseStack.last().pose();
        Vector3f left = camera.getLeftVector();
        Vector3f up = camera.getUpVector();
        float rx = -left.x;
        float ry = -left.y;
        float rz = -left.z;

        float fade = filled ? Mth.clamp(age / 12.0F, 0.0F, 1.0F) : 0.0F;
        float pulse = 0.85F + 0.15F * (float) Math.sin(time * 0.25F);
        float base = filled ? (0.65F + 0.35F * fade) * pulse : 0.28F;
        float orbY = 1.75F + 0.1F * (float) Math.sin(time * 0.08F);
        float R = 1.9F;

        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
        // floor runes
        int cr = filled ? 255 : 150;
        int cg = filled ? 110 : 70;
        int cb = filled ? 235 : 255;
        FxDraw.ring(glow, m, 0.5F, 0.5F, 0.03F, R, 0.1F, 48, 0.0F, 1.0F, cr, cg, cb, (int) (200 * base));
        FxDraw.ring(glow, m, 0.5F, 0.5F, 0.034F, R * 0.7F, 0.06F, 48, time * (filled ? 0.03F : 0.008F), 0.6F, 120, 230, 240, (int) (170 * base));
        for (int sq = 0; sq < 2; sq++) {
            float b = sq * (FxDraw.TAU / 8.0F) + time * 0.012F * (sq == 0 ? 1.0F : -1.0F);
            for (int e = 0; e < 4; e++) {
                float a0 = b + e * (FxDraw.TAU / 4.0F);
                float a1 = a0 + FxDraw.TAU / 4.0F;
                FxDraw.bar(glow, m, 0.5F + Mth.cos(a0) * R * 0.8F, 0.04F, 0.5F + Mth.sin(a0) * R * 0.8F,
                        0.5F + Mth.cos(a1) * R * 0.8F, 0.5F + Mth.sin(a1) * R * 0.8F, 0.04F, 200, 130, 255, (int) (170 * base));
            }
        }
        for (int i = 0; i < 6; i++) {
            float a = i * (FxDraw.TAU / 6.0F) + time * 0.01F;
            float gx = 0.5F + Mth.cos(a) * R * 0.88F;
            float gz = 0.5F + Mth.sin(a) * R * 0.88F;
            float tx = -Mth.sin(a) * 0.22F;
            float tz = Mth.cos(a) * 0.22F;
            int ga = (int) (220 * base * (0.6F + 0.4F * (float) Math.sin(time * 0.2F + i * 1.7F)));
            FxDraw.bar(glow, m, gx - tx, 0.045F, gz - tz, gx + tx, gz + tz, 0.05F, 235, 170, 255, ga);
        }

        if (filled) {
            // flare right after the heart went in
            if (age < 30.0F) {
                float k = age / 30.0F;
                FxDraw.ring(glow, m, 0.5F, 0.5F, 0.05F, 0.6F + 7.0F * FxDraw.ease(k), 0.3F * (1.0F - k) + 0.06F, 64, 0.0F, 1.0F,
                        255, 190, 245, (int) (230 * (1.0F - k)));
            }
            // halo, gimbal rings
            float[] halo = {2.6F, 1.8F, 1.1F, 0.6F};
            int[] haloA = {14, 30, 70, 150};
            int[][] haloC = {{200, 60, 200}, {240, 90, 230}, {255, 160, 245}, {255, 240, 255}};
            for (int i = 0; i < halo.length; i++) {
                FxDraw.billboard(glow, m, 0.5F, orbY, 0.5F, rx, ry, rz, up.x, up.y, up.z, halo[i] * 0.55F * pulse,
                        haloC[i][0], haloC[i][1], haloC[i][2], (int) (haloA[i] * fade));
            }
            FxDraw.gimbal(glow, m, 0.5F, orbY, 0.5F, 0.35F, time, 255, 120, 235, (int) (210 * fade));
            // the pillar of light up the shaft and above the tower
            float flick = 0.85F + 0.15F * (float) Math.sin(time * 1.3F);
            FxDraw.beam(glow, m, 0.5F, orbY, 0.5F, 70.0F, 0.18F * flick, 255, 110, 235, (int) (150 * fade));
            FxDraw.beam(glow, m, 0.5F, orbY, 0.5F, 70.0F, 0.07F, 255, 235, 255, (int) (210 * fade));
        } else {
            FxDraw.billboard(glow, m, 0.5F, orbY, 0.5F, rx, ry, rz, up.x, up.y, up.z, 0.35F * pulse, 150, 80, 255, 40);
        }

        if (filled) {
            VertexConsumer core = buffers.getBuffer(RenderType.endPortal());
            float s = 0.3F * fade * pulse;
            poseStack.pushPose();
            poseStack.translate(0.5F, orbY, 0.5F);
            poseStack.mulPose(new org.joml.Quaternionf().rotateAxis(time * 0.05F, 0.7071F, 0.7071F, 0.0F));
            FxDraw.cube(core, poseStack.last().pose(), s);
            poseStack.popPose();
            poseStack.pushPose();
            poseStack.translate(0.5F, orbY, 0.5F);
            poseStack.mulPose(Axis.YP.rotationDegrees(time * 2.6F));
            poseStack.mulPose(Axis.XP.rotationDegrees(45.0F));
            FxDraw.cube(core, poseStack.last().pose(), s * 0.72F);
            poseStack.popPose();
        }
    }
}
