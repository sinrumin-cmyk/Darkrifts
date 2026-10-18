package com.darkrifts.client;

import com.darkrifts.entity.RiftReaver;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * The tear in the sky that the Rift Reaver climbs out of. It is a horizontal, jagged hole that looks DOWN:
 *  - the hole itself is an opaque End-shader surface (it also hides the part of the boss that is still above it),
 *  - a bright violet rim, thin cracks spreading over the sky around it and a faint cone of light falling to the ground.
 * {@code open} is 0..1 (see RiftReaver.riftOpen). The origin of the pose stack is the centre of the tear.
 */
final class SkyRiftRenderer {
    private static final int N = 48;
    private static final float TAU = (float) (Math.PI * 2.0);

    private SkyRiftRenderer() {}

    static void draw(PoseStack poseStack, MultiBufferSource buffers, float open, float time) {
        if (open <= 0.0F) {
            return;
        }
        float a = RiftReaver.RIFT_A * open;
        float b = RiftReaver.RIFT_B * (float) Math.pow(open, 0.8);
        float[] px = new float[N];
        float[] pz = new float[N];
        for (int i = 0; i < N; i++) {
            float ang = i * TAU / N;
            float jag = 1.0F + 0.17F * (rand(i) - 0.5F) * 2.0F + 0.03F * open * (float) Math.sin(time * 0.3F + i * 1.7F);
            px[i] = Mth.cos(ang) * a * jag;
            pz[i] = Mth.sin(ang) * b * jag;
        }
        Matrix4f m = poseStack.last().pose();
        float vis = Mth.clamp(open * 5.0F, 0.0F, 1.0F);
        float flick = 0.85F + 0.15F * (float) Math.sin(time * 0.9F);

        // ---------------------------------------------------------------- glow (additive)
        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
        // light falling through the hole
        for (int i = 0; i < N; i++) {
            int j = (i + 1) % N;
            float[] c = {150, 90, 255};
            v(glow, m, px[i] * 0.9F, 0.0F, pz[i] * 0.9F, c, (int) (46 * vis * open));
            v(glow, m, px[j] * 0.9F, 0.0F, pz[j] * 0.9F, c, (int) (46 * vis * open));
            v(glow, m, px[j] * 1.8F, -16.0F, pz[j] * 1.8F, c, 0);
            v(glow, m, px[i] * 1.8F, -16.0F, pz[i] * 1.8F, c, 0);
        }
        // rim: two layers fading outwards
        rim(glow, m, px, pz, 1.9F, 0.45F + 0.5F * open, new float[]{130, 60, 255}, (int) (130 * vis * flick));
        rim(glow, m, px, pz, 1.3F, 0.22F + 0.25F * open, new float[]{228, 176, 255}, (int) (235 * vis * flick));
        rim(glow, m, px, pz, 1.06F, 0.07F, new float[]{255, 245, 255}, (int) (255 * vis));
        // cracks in the sky around the hole
        for (int k = 0; k < 16; k++) {
            float ang = (k + rand(100 + k) * 0.8F) * TAU / 16.0F;
            float cr = Mth.cos(ang);
            float sr = Mth.sin(ang);
            float len = (1.2F + 3.4F * rand(200 + k)) * open;
            float x = cr * a * 1.08F;
            float z = sr * b * 1.08F;
            float width = 0.1F;
            for (int s = 0; s < 6; s++) {
                float t = (s + 1) / 6.0F;
                float jx = (rand(300 + k * 7 + s) - 0.5F) * 0.9F * len / 6.0F;
                float jz = (rand(400 + k * 7 + s) - 0.5F) * 0.9F * len / 6.0F;
                float nx = x + cr * len / 6.0F + jx;
                float nz = z + sr * len / 6.0F * (b / Math.max(0.01F, a) * 0.8F + 0.5F) + jz;
                bar(glow, m, x, -0.02F, z, nx, nz, width * (1.0F - t * 0.8F), new float[]{235, 175, 255},
                        (int) (215 * vis * flick * (1.0F - t * 0.5F)));
                x = nx;
                z = nz;
            }
        }

        // ---------------------------------------------------------------- the hole (End shader)
        VertexConsumer core = buffers.getBuffer(RenderType.endPortal());
        for (int i = 0; i < N; i++) {
            int j = (i + 1) % N;
            core.addVertex(m, 0.0F, 0.0F, 0.0F);
            core.addVertex(m, px[i], 0.0F, pz[i]);
            core.addVertex(m, px[j], 0.0F, pz[j]);
            core.addVertex(m, px[j], 0.0F, pz[j]);
            core.addVertex(m, 0.0F, 0.0F, 0.0F);
            core.addVertex(m, px[j], 0.0F, pz[j]);
            core.addVertex(m, px[i], 0.0F, pz[i]);
            core.addVertex(m, px[i], 0.0F, pz[i]);
        }
    }

    /** A strip along the outline, bright at the edge of the hole and fading out. */
    private static void rim(VertexConsumer vc, Matrix4f m, float[] px, float[] pz, float scale, float extra,
                            float[] col, int alpha) {
        for (int i = 0; i < N; i++) {
            int j = (i + 1) % N;
            float[] o0 = outer(px[i], pz[i], scale, extra);
            float[] o1 = outer(px[j], pz[j], scale, extra);
            v(vc, m, px[i], -0.01F, pz[i], col, alpha);
            v(vc, m, px[j], -0.01F, pz[j], col, alpha);
            v(vc, m, o1[0], -0.01F, o1[1], col, 0);
            v(vc, m, o0[0], -0.01F, o0[1], col, 0);
        }
    }

    private static float[] outer(float x, float z, float scale, float extra) {
        float len = Mth.sqrt(x * x + z * z);
        float nx = len < 1.0E-4F ? 0.0F : x / len;
        float nz = len < 1.0E-4F ? 1.0F : z / len;
        return new float[]{x * scale + nx * extra, z * scale + nz * extra};
    }

    private static void bar(VertexConsumer vc, Matrix4f m, float x0, float y, float z0, float x1, float z1, float w,
                            float[] col, int alpha) {
        float dx = x1 - x0;
        float dz = z1 - z0;
        float len = Mth.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-4F) {
            return;
        }
        float nx = -dz / len * w;
        float nz = dx / len * w;
        v(vc, m, x0 + nx, y, z0 + nz, col, alpha);
        v(vc, m, x0 - nx, y, z0 - nz, col, alpha);
        v(vc, m, x1 - nx, y, z1 - nz, col, alpha);
        v(vc, m, x1 + nx, y, z1 + nz, col, alpha);
    }

    private static void v(VertexConsumer vc, Matrix4f m, float x, float y, float z, float[] c, int a) {
        vc.addVertex(m, x, y, z).setColor((int) c[0], (int) c[1], (int) c[2], Mth.clamp(a, 0, 255));
    }

    private static float rand(int k) {
        long h = k * 0x9E3779B97F4A7C15L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return ((h >>> 24) & 0xFF) / 255.0F;
    }
}
