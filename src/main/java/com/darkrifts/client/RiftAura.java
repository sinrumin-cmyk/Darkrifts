package com.darkrifts.client;

import com.darkrifts.block.RiftBlockEntity;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The ritual look for a standing rift. Drawn once per rift by its anchor cell: a rune circle on the ground under the
 * start of the tear (double ring, dashed ring, counter-rotating octagram, 12 glyphs), a pillar of light between the
 * circle and the tear, rays crawling over the ground, a halo around the start of the line, and - for the first
 * two seconds - an expanding flare ring. The circle flickers while the rift is about to collapse and fades with it.
 */
public final class RiftAura {
    private static final Map<Long, Float> GROUND = new HashMap<>();
    private static Level cachedLevel;

    private RiftAura() {}

    private static float groundBelow(Level level, BlockPos anchor, double sx, double sy, double sz) {
        if (cachedLevel != level || GROUND.size() > 128) {
            GROUND.clear();
            cachedLevel = level;
        }
        Float known = GROUND.get(anchor.asLong());
        if (known != null) {
            return known;
        }
        Minecraft mc = Minecraft.getInstance();
        float y = (float) sy - 3.0F;
        if (mc.player != null) {
            var hit = level.clip(new ClipContext(new Vec3(sx, sy, sz), new Vec3(sx, sy - 64.0, sz),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            if (hit.getType() != HitResult.Type.MISS) {
                y = (float) hit.getLocation().y;
            }
        }
        GROUND.put(anchor.asLong(), y);
        return y;
    }

    public static void draw(RiftBlockEntity be, Level level, BlockPos origin, Matrix4f m,
                            MultiBufferSource buffers, float time) {
        List<double[]> segs = be.getSegments();
        double[] first = null;
        for (double[] s : segs) {
            if (s[7] == 0.0) {
                first = s;
                break;
            }
        }
        if (first == null) {
            return;
        }
        float age = time - (float) first[6];
        if (age < 0.0F) {
            return;
        }
        double total = 0.0;
        for (double[] s : segs) {
            total = Math.max(total, s[8]);
        }
        float groundY = groundBelow(level, be.getBlockPos(), first[0], first[1], first[2]);
        float ox = origin.getX();
        float oy = origin.getY();
        float oz = origin.getZ();
        float cx = (float) first[0] - ox;
        float cz = (float) first[2] - oz;
        float sy = (float) first[1] - oy;
        float gy = groundY - oy + 0.04F;

        float fade = Mth.clamp(age / 10.0F, 0.0F, 1.0F);
        long exp = be.getExpireAt();
        if (exp > 0) {
            float left = exp - time;
            fade *= Mth.clamp(left / 30.0F, 0.0F, 1.0F);
            if (left < 100.0F) {
                fade *= 0.65F + 0.35F * (float) Math.sin(time * (left < 20.0F ? 2.8F : 1.4F));
            }
        }
        if (fade <= 0.01F) {
            return;
        }

        float R = (2.2F + Mth.clamp((float) total * 0.18F, 0.0F, 3.4F)) * FxDraw.ease(Mth.clamp(age / 24.0F, 0.0F, 1.0F));
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector3f left = camera.getLeftVector();
        Vector3f up = camera.getUpVector();
        float rx = -left.x;
        float ry = -left.y;
        float rz = -left.z;

        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
        if (R > 0.05F) {
            FxDraw.ring(glow, m, cx, cz, gy, R, 0.12F, 64, 0.0F, 1.0F, 150, 70, 255, (int) (200 * fade));
            FxDraw.ring(glow, m, cx, cz, gy + 0.004F, R * 0.92F, 0.05F, 64, 0.0F, 1.0F, 90, 230, 240, (int) (170 * fade));
            FxDraw.ring(glow, m, cx, cz, gy + 0.008F, R * 0.6F, 0.13F, 80, -time * 0.02F, 0.55F, 120, 220, 255, (int) (160 * fade));
            for (int sq = 0; sq < 2; sq++) {
                float base = sq * (FxDraw.TAU / 8.0F) + time * 0.012F * (sq == 0 ? 1.0F : -1.0F);
                for (int e = 0; e < 4; e++) {
                    float a0 = base + e * (FxDraw.TAU / 4.0F);
                    float a1 = a0 + FxDraw.TAU / 4.0F;
                    FxDraw.bar(glow, m, cx + Mth.cos(a0) * R * 0.78F, gy + 0.012F, cz + Mth.sin(a0) * R * 0.78F,
                            cx + Mth.cos(a1) * R * 0.78F, cz + Mth.sin(a1) * R * 0.78F, 0.05F, 190, 120, 255, (int) (190 * fade));
                }
            }
            float gs = R / 6.0F;
            for (int i = 0; i < 12; i++) {
                float a = i * (FxDraw.TAU / 12.0F) + time * 0.006F;
                float gx = cx + Mth.cos(a) * R * 0.86F;
                float gz = cz + Mth.sin(a) * R * 0.86F;
                float tx = -Mth.sin(a);
                float tz = Mth.cos(a);
                float nx = Mth.cos(a);
                float nz = Mth.sin(a);
                int ga = (int) (220 * fade * (0.6F + 0.4F * (float) Math.sin(time * 0.2F + i * 1.7F)));
                int shape = i % 4;
                FxDraw.bar(glow, m, gx - tx * 0.25F * gs, gy + 0.016F, gz - tz * 0.25F * gs, gx + tx * 0.25F * gs, gz + tz * 0.25F * gs, 0.05F, 235, 170, 255, ga);
                if (shape != 1) {
                    FxDraw.bar(glow, m, gx - nx * 0.2F * gs, gy + 0.016F, gz - nz * 0.2F * gs, gx + nx * 0.2F * gs, gz + nz * 0.2F * gs, 0.05F, 235, 170, 255, ga);
                }
                if (shape >= 2) {
                    FxDraw.bar(glow, m, gx + tx * 0.25F * gs, gy + 0.016F, gz + tz * 0.25F * gs,
                            gx + tx * 0.25F * gs + nx * 0.3F * gs, gz + tz * 0.25F * gs + nz * 0.3F * gs, 0.05F, 235, 170, 255, ga);
                }
            }
            // rays crawling over the ground
            for (int i = 0; i < 8; i++) {
                float a = i * (FxDraw.TAU / 8.0F) + time * 0.02F;
                float len = R * (0.5F + 0.45F * (float) Math.abs(Math.sin(time * 0.13F + i * 2.3F)));
                FxDraw.bar(glow, m, cx + Mth.cos(a) * 0.6F, gy + 0.02F, cz + Mth.sin(a) * 0.6F,
                        cx + Mth.cos(a) * len, cz + Mth.sin(a) * len, 0.18F, 170, 90, 255, (int) (95 * fade));
            }
        }
        // birth flare: a bright ring racing outwards
        if (age < 40.0F) {
            float k = age / 40.0F;
            FxDraw.ring(glow, m, cx, cz, gy + 0.02F, 1.0F + R * 3.2F * FxDraw.ease(k), 0.35F * (1.0F - k) + 0.08F, 72, 0.0F, 1.0F,
                    235, 200, 255, (int) (230 * (1.0F - k)));
        }
        // pillar of light between the circle and the tear, and a short one above it
        float flick = 0.85F + 0.15F * (float) Math.sin(time * 1.3F);
        if (sy > gy + 0.5F) {
            FxDraw.beam(glow, m, cx, gy, cz, sy, 0.10F * flick, 150, 80, 255, (int) (120 * fade));
            FxDraw.beam(glow, m, cx, gy, cz, sy, 0.04F, 245, 225, 255, (int) (170 * fade));
        }
        FxDraw.beam(glow, m, cx, sy, cz, sy + 7.0F, 0.12F * flick, 190, 120, 255, (int) (150 * fade));
        // halo around the start of the tear
        float[] halo = {1.9F, 1.3F, 0.8F, 0.4F};
        int[] haloA = {14, 28, 60, 130};
        int[][] haloC = {{110, 40, 220}, {150, 70, 255}, {200, 140, 255}, {255, 240, 255}};
        float pulse = 1.0F + 0.1F * (float) Math.sin(time * 0.4F);
        for (int i = 0; i < halo.length; i++) {
            FxDraw.billboard(glow, m, cx, sy, cz, rx, ry, rz, up.x, up.y, up.z, halo[i] * pulse,
                    haloC[i][0], haloC[i][1], haloC[i][2], (int) (haloA[i] * fade));
        }
    }
}
