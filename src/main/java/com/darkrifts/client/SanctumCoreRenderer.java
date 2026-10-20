package com.darkrifts.client;

import com.darkrifts.Shake;
import com.darkrifts.block.SanctumCoreBlockEntity;
import com.darkrifts.block.SanctumTowerBlockEntity;
import com.darkrifts.world.MirrorSanctum;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The Egg Altar and the whole Sanctum floor, drawn as light:
 *  - rings (r 24, 16.9, 9.8), the {8/3} star that is also built from crying obsidian, twelve glyphs,
 *  - four spokes to the towers: dark until a tower holds a heart, then a bright energy line with travelling pulses,
 *  - a floating End-shader orb that grows with every heart; with all four hearts it burns, with gimbal rings and a beam,
 *  - during the awakening: everything at full power, rays crawling over the plateau, a beam that splits the sky.
 */
public class SanctumCoreRenderer implements BlockEntityRenderer<SanctumCoreBlockEntity> {
    private static final int[][] DIRS = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    public SanctumCoreRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public boolean shouldRenderOffScreen(SanctumCoreBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 192;
    }

    @Override
    public void render(SanctumCoreBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        float time = level.getGameTime() + partialTick;
        boolean active = be.isAwakening();
        float t = active ? (level.getGameTime() - be.getStartTime()) + partialTick : -1.0F;
        float p = active ? Mth.clamp(t / SanctumCoreBlockEntity.DURATION, 0.0F, 1.0F) : 0.0F;

        int filled = 0;
        boolean[] on = new boolean[4];
        BlockPos[] peds = MirrorSanctum.pedestals(be.getBlockPos());
        for (int i = 0; i < 4; i++) {
            if (level.getBlockEntity(peds[i]) instanceof SanctumTowerBlockEntity tw && tw.isFilled()) {
                on[i] = true;
                filled++;
            }
        }
        boolean ready = filled == 4;

        if (active) {
            Vec3 c = Vec3.atCenterOf(be.getBlockPos());
            double d = camera.getPosition().distanceTo(c);
            Shake.add((float) (p * p * 0.9 * Mth.clamp(1.0 - d / 70.0, 0.0, 1.0)));
        }

        float fade = active ? Mth.clamp(t / 20.0F, 0.0F, 1.0F) * 0.5F + 0.5F
                : ready ? 0.8F + 0.2F * (float) Math.sin(time * 0.15F) : 0.28F + 0.1F * filled;
        float gy = -1.97F;                       // plateau surface, relative to the altar block
        Matrix4f m = poseStack.last().pose();
        Vector3f left = camera.getLeftVector();
        Vector3f up = camera.getUpVector();
        float rx = -left.x;
        float ry = -left.y;
        float rz = -left.z;
        float cx = 0.5F;
        float cz = 0.5F;

        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
        float spread = active ? FxDraw.ease(Mth.clamp(t / 90.0F, 0.0F, 1.0F)) : 1.0F;

        // ---- rings
        FxDraw.ring(glow, m, cx, cz, gy, 24.0F * spread, 0.28F, 160, 0.0F, 1.0F, 150, 70, 255, (int) (200 * fade));
        FxDraw.ring(glow, m, cx, cz, gy + 0.004F, 23.2F * spread, 0.1F, 160, 0.0F, 1.0F, 90, 230, 240, (int) (170 * fade));
        FxDraw.ring(glow, m, cx, cz, gy + 0.008F, 16.9F * spread, 0.2F, 120, -time * 0.012F, 0.55F, 120, 220, 255, (int) (180 * fade));
        FxDraw.ring(glow, m, cx, cz, gy + 0.008F, 9.8F * spread, 0.16F, 80, time * 0.02F, 0.6F, 200, 130, 255, (int) (180 * fade));

        // ---- the {8/3} star (matches the crying obsidian lines of the floor)
        for (int e = 0; e < 8; e++) {
            float a0 = e * (FxDraw.TAU / 8.0F) + FxDraw.TAU / 16.0F;
            float a1 = ((e + 3) % 8) * (FxDraw.TAU / 8.0F) + FxDraw.TAU / 16.0F;
            FxDraw.bar(glow, m, cx + Mth.cos(a0) * 22.5F * spread, gy + 0.012F, cz + Mth.sin(a0) * 22.5F * spread,
                    cx + Mth.cos(a1) * 22.5F * spread, cz + Mth.sin(a1) * 22.5F * spread, 0.2F, 190, 120, 255, (int) (190 * fade));
        }

        // ---- twelve glyphs between the star and the outer ring
        float gs = 3.0F;
        for (int i = 0; i < 12; i++) {
            float a = i * (FxDraw.TAU / 12.0F) + time * 0.004F;
            float gx = cx + Mth.cos(a) * 20.0F * spread;
            float gz = cz + Mth.sin(a) * 20.0F * spread;
            float tx = -Mth.sin(a);
            float tz = Mth.cos(a);
            float nx = Mth.cos(a);
            float nz = Mth.sin(a);
            int ga = (int) (230 * fade * (0.6F + 0.4F * (float) Math.sin(time * 0.2F + i * 1.7F)));
            int shape = i % 4;
            FxDraw.bar(glow, m, gx - tx * 0.25F * gs, gy + 0.016F, gz - tz * 0.25F * gs, gx + tx * 0.25F * gs, gz + tz * 0.25F * gs, 0.12F, 235, 170, 255, ga);
            if (shape != 1) {
                FxDraw.bar(glow, m, gx - nx * 0.2F * gs, gy + 0.016F, gz - nz * 0.2F * gs, gx + nx * 0.2F * gs, gz + nz * 0.2F * gs, 0.12F, 235, 170, 255, ga);
            }
            if (shape >= 2) {
                FxDraw.bar(glow, m, gx + tx * 0.25F * gs, gy + 0.016F, gz + tz * 0.25F * gs,
                        gx + tx * 0.25F * gs + nx * 0.3F * gs, gz + tz * 0.25F * gs + nz * 0.3F * gs, 0.12F, 235, 170, 255, ga);
            }
            if (shape == 3) {
                FxDraw.bar(glow, m, gx - tx * 0.25F * gs, gy + 0.016F, gz - tz * 0.25F * gs,
                        gx - tx * 0.25F * gs - nx * 0.3F * gs, gz - tz * 0.25F * gs - nz * 0.3F * gs, 0.12F, 235, 170, 255, ga);
            }
        }

        // ---- the four spokes: dead until the tower holds its heart
        for (int i = 0; i < 4; i++) {
            float ex = cx + DIRS[i][0] * MirrorSanctum.TOWER_DIST;
            float ez = cz + DIRS[i][1] * MirrorSanctum.TOWER_DIST;
            boolean lit = on[i] || active;
            int sr = lit ? 120 : 90;
            int sg = lit ? 235 : 50;
            int sb = lit ? 255 : 160;
            FxDraw.bar(glow, m, cx + DIRS[i][0] * 6.0F, gy + 0.02F, cz + DIRS[i][1] * 6.0F, ex - DIRS[i][0] * 3.0F, ez - DIRS[i][1] * 3.0F,
                    lit ? 0.55F : 0.2F, sr, sg, sb, (int) ((lit ? 150 : 70) * Math.max(fade, 0.5F)));
            FxDraw.bar(glow, m, cx + DIRS[i][0] * 6.0F, gy + 0.025F, cz + DIRS[i][1] * 6.0F, ex - DIRS[i][0] * 3.0F, ez - DIRS[i][1] * 3.0F,
                    0.12F, 235, 250, 255, lit ? 230 : 40);
            if (lit) {
                // energy pulses running from the tower into the core
                for (int k = 0; k < 3; k++) {
                    float f = ((time * 0.02F + k / 3.0F + i * 0.17F) % 1.0F);
                    float px = ex - DIRS[i][0] * 3.0F + (cx + DIRS[i][0] * 6.0F - (ex - DIRS[i][0] * 3.0F)) * f;
                    float pz = ez - DIRS[i][1] * 3.0F + (cz + DIRS[i][1] * 6.0F - (ez - DIRS[i][1] * 3.0F)) * f;
                    FxDraw.billboard(glow, m, px, gy + 0.3F, pz, rx, ry, rz, up.x, up.y, up.z, 0.55F, 200, 255, 255, 200);
                }
            }
        }

        // ---- crawling rays during the awakening
        if (active && t > 100.0F) {
            float k = Mth.clamp((t - 100.0F) / 90.0F, 0.0F, 1.0F);
            for (int i = 0; i < 16; i++) {
                float a = i * (FxDraw.TAU / 16.0F) + time * 0.015F;
                float len = 22.0F * (0.5F + 0.5F * (float) Math.abs(Math.sin(time * 0.1F + i * 2.3F))) * k;
                FxDraw.bar(glow, m, cx + Mth.cos(a) * 3.0F, gy + 0.03F, cz + Mth.sin(a) * 3.0F,
                        cx + Mth.cos(a) * len, cz + Mth.sin(a) * len, 0.35F * k, 255, 90, 110, (int) (100 * k));
            }
            if (t > 250.0F) {                     // the last seconds: a flare ring racing outwards, over and over
                float f = ((t - 250.0F) / 14.0F) % 1.0F;
                FxDraw.ring(glow, m, cx, cz, gy + 0.05F, 2.0F + 22.0F * FxDraw.ease(f), 0.5F * (1.0F - f) + 0.1F, 120, 0.0F, 1.0F,
                        255, 150, 160, (int) (220 * (1.0F - f)));
            }
        }

        // ---- the orb
        float orbY = 2.6F + 0.15F * (float) Math.sin(time * 0.08F);
        float orbS = active ? 0.45F + 1.15F * FxDraw.smooth(Mth.clamp(t / 260.0F, 0.0F, 1.0F))
                : 0.18F + 0.09F * filled + (ready ? 0.1F : 0.0F);
        float pulse = 1.0F + 0.08F * (float) Math.sin(time * 0.45F) * (active ? 1.0F + p * 2.0F : 0.5F);
        orbS *= pulse;
        float[] halo = {3.4F, 2.4F, 1.5F, 0.85F};
        int[] haloA = {16, 32, 70, 150};
        int[][] haloC = active ? new int[][]{{200, 40, 60}, {240, 80, 110}, {255, 170, 190}, {255, 240, 245}}
                : new int[][]{{110, 40, 220}, {150, 70, 255}, {200, 140, 255}, {255, 240, 255}};
        float haloK = active ? 0.6F + 0.4F * fade : 0.15F + 0.2F * filled;
        for (int i = 0; i < halo.length; i++) {
            FxDraw.billboard(glow, m, cx, orbY, cz, rx, ry, rz, up.x, up.y, up.z, orbS * halo[i],
                    haloC[i][0], haloC[i][1], haloC[i][2], (int) (haloA[i] * haloK));
        }
        if (ready || active) {
            FxDraw.gimbal(glow, m, cx, orbY, cz, orbS, time, active ? 255 : 190, active ? 90 : 120, 255, (int) (200 * fade));
        }
        // beams
        if (ready || active) {
            float k = active ? FxDraw.smooth(Mth.clamp((t - 40.0F) / 200.0F, 0.0F, 1.0F)) : 0.35F;
            float flick = 0.85F + 0.15F * (float) Math.sin(time * 1.3F);
            float w = (0.1F + 1.1F * k) * flick;
            FxDraw.beam(glow, m, cx, orbY, cz, 110.0F, w, active ? 255 : 150, active ? 70 : 80, active ? 90 : 255, (int) (170 * Math.max(k, 0.2F)));
            FxDraw.beam(glow, m, cx, orbY, cz, 110.0F, w * 0.42F, 255, 235, 245, (int) (235 * Math.max(k, 0.2F)));
            FxDraw.beam(glow, m, cx, gy, cz, orbY, w * 0.6F, 255, 160, 190, (int) (200 * Math.max(k, 0.2F)));
        }

        // ---- the End-shader crystal
        VertexConsumer core = buffers.getBuffer(RenderType.endPortal());
        poseStack.pushPose();
        poseStack.translate(cx, orbY, cz);
        poseStack.mulPose(new org.joml.Quaternionf().rotateAxis(time * 0.045F, 0.7071F, 0.7071F, 0.0F));
        FxDraw.cube(core, poseStack.last().pose(), orbS * 0.62F);
        poseStack.popPose();
        poseStack.pushPose();
        poseStack.translate(cx, orbY, cz);
        poseStack.mulPose(Axis.YP.rotationDegrees(time * 2.2F));
        poseStack.mulPose(Axis.XP.rotationDegrees(45.0F));
        FxDraw.cube(core, poseStack.last().pose(), orbS * 0.45F);
        poseStack.popPose();
    }
}
