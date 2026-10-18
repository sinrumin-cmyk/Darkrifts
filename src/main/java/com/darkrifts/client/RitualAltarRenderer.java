package com.darkrifts.client;

import com.darkrifts.Shake;
import com.darkrifts.block.RitualAltarBlockEntity;
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
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The visible part of the ritual. Idle: a small End-portal crystal floating over the altar with a faint rune ring.
 * During the ritual (t = ticks since the start, see RitualAltarBlockEntity):
 *  - a rune circle (double ring, dashes, two counter-rotating squares = octagram, 12 glyphs) spreads over the ground,
 *  - the crystal grows into a pulsing End-shader core with a halo and three spinning gimbal rings,
 *  - rays crawl over the ground, a beam of light shoots into the sky and the camera starts to shake.
 */
public class RitualAltarRenderer implements BlockEntityRenderer<RitualAltarBlockEntity> {
    private static final float TAU = (float) (Math.PI * 2.0);

    public RitualAltarRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public boolean shouldRenderOffScreen(RitualAltarBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    @Override
    public void render(RitualAltarBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        boolean active = be.isActive();
        float time = level.getGameTime() + partialTick;
        float t = active ? (level.getGameTime() - be.getStartTime()) + partialTick : -1.0F;
        float p = active ? Mth.clamp(t / RitualAltarBlockEntity.DURATION, 0.0F, 1.0F) : 0.0F;

        if (active) {
            Vec3 center = Vec3.atCenterOf(be.getBlockPos());
            double d = camera.getPosition().distanceTo(center);
            Shake.add((float) (p * p * 0.6 * Mth.clamp(1.0 - d / 32.0, 0.0, 1.0)));
        }

        float fade = active ? Mth.clamp(t / 15.0F, 0.0F, 1.0F) : 0.22F;
        if (active && t > 212.0F) {
            fade *= 0.65F + 0.35F * (float) Math.sin(t * 2.6F);
        }
        float orbY = 1.9F + 0.12F * (float) Math.sin(time * 0.08F);
        float orbS = active ? 0.25F + 0.95F * smooth(Mth.clamp((t - 10.0F) / 190.0F, 0.0F, 1.0F)) : 0.2F;
        float pulse = 1.0F + 0.08F * (float) Math.sin(time * 0.45F) * (active ? 1.0F + p * 2.0F : 0.4F);
        orbS *= pulse;

        Matrix4f m = poseStack.last().pose();
        Vector3f left = camera.getLeftVector();
        Vector3f up = camera.getUpVector();
        float rx = -left.x;
        float ry = -left.y;
        float rz = -left.z;

        // ---------------------------------------------------------------- pass A: additive light
        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());

        float R = active ? 6.0F * ease(Mth.clamp(t / 80.0F, 0.0F, 1.0F)) : 1.5F;
        float yg = 0.035F;
        if (R > 0.05F) {
            // double outer ring
            ring(glow, m, 0.5F, 0.5F, yg, R, 0.12F, 72, 0.0F, 1.0F, 150, 70, 255, (int) (210 * fade));
            ring(glow, m, 0.5F, 0.5F, yg + 0.004F, R * 0.93F, 0.05F, 72, 0.0F, 1.0F, 90, 230, 240, (int) (190 * fade));
            // dashed inner ring, counter-rotating
            ring(glow, m, 0.5F, 0.5F, yg + 0.008F, R * 0.62F, 0.14F, 96, -time * 0.02F, 0.55F, 120, 220, 255, (int) (170 * fade));
            // octagram: two squares turning against each other
            for (int sq = 0; sq < 2; sq++) {
                float base = sq * (TAU / 8.0F) + time * 0.012F * (sq == 0 ? 1.0F : -1.0F);
                for (int e = 0; e < 4; e++) {
                    float a0 = base + e * (TAU / 4.0F);
                    float a1 = a0 + TAU / 4.0F;
                    bar(glow, m, 0.5F + Mth.cos(a0) * R * 0.78F, yg + 0.012F, 0.5F + Mth.sin(a0) * R * 0.78F,
                            0.5F + Mth.cos(a1) * R * 0.78F, 0.5F + Mth.sin(a1) * R * 0.78F, 0.06F,
                            190, 120, 255, (int) (200 * fade));
                }
            }
            // glyphs between the rings
            float gs = R / 6.0F;
            for (int i = 0; i < 12; i++) {
                float a = i * (TAU / 12.0F) + time * 0.006F;
                float gx = 0.5F + Mth.cos(a) * R * 0.87F;
                float gz = 0.5F + Mth.sin(a) * R * 0.87F;
                float tx = -Mth.sin(a);
                float tz = Mth.cos(a);
                float nx = Mth.cos(a);
                float nz = Mth.sin(a);
                float flick = 0.6F + 0.4F * (float) Math.sin(time * 0.2F + i * 1.7F);
                int ga = (int) (230 * fade * flick);
                // three bars per glyph, a different shape for every index
                int shape = i % 4;
                bar(glow, m, gx - tx * 0.25F * gs, yg + 0.016F, gz - tz * 0.25F * gs, gx + tx * 0.25F * gs, gz + tz * 0.25F * gs, 0.05F, 235, 170, 255, ga);
                if (shape != 1) {
                    bar(glow, m, gx - nx * 0.2F * gs, yg + 0.016F, gz - nz * 0.2F * gs, gx + nx * 0.2F * gs, gz + nz * 0.2F * gs, 0.05F, 235, 170, 255, ga);
                }
                if (shape >= 2) {
                    bar(glow, m, gx + tx * 0.25F * gs, yg + 0.016F, gz + tz * 0.25F * gs, gx + tx * 0.25F * gs + nx * 0.3F * gs, gz + tz * 0.25F * gs + nz * 0.3F * gs, 0.05F, 235, 170, 255, ga);
                }
                if (shape == 3) {
                    bar(glow, m, gx - tx * 0.25F * gs, yg + 0.016F, gz - tz * 0.25F * gs, gx - tx * 0.25F * gs - nx * 0.3F * gs, gz - tz * 0.25F * gs - nz * 0.3F * gs, 0.05F, 235, 170, 255, ga);
                }
            }
        }

        // rays crawling over the ground
        if (active && t > 140.0F) {
            float k = Mth.clamp((t - 140.0F) / 60.0F, 0.0F, 1.0F);
            for (int i = 0; i < 10; i++) {
                float a = i * (TAU / 10.0F) + time * 0.02F;
                float len = R * (0.55F + 0.45F * (float) Math.abs(Math.sin(time * 0.15F + i * 2.3F))) * k;
                bar(glow, m, 0.5F + Mth.cos(a) * 0.7F, yg + 0.02F, 0.5F + Mth.sin(a) * 0.7F,
                        0.5F + Mth.cos(a) * len, 0.5F + Mth.sin(a) * len, 0.22F * k, 170, 90, 255, (int) (110 * k));
            }
        }

        // halo around the crystal (camera-facing nested squares)
        float[] halo = {3.6F, 2.5F, 1.6F, 0.9F};
        int[] haloA = {16, 32, 70, 150};
        int[][] haloC = {{110, 40, 220}, {150, 70, 255}, {200, 140, 255}, {255, 240, 255}};
        for (int i = 0; i < halo.length; i++) {
            billboard(glow, m, 0.5F, orbY, 0.5F, rx, ry, rz, up.x, up.y, up.z, orbS * halo[i],
                    haloC[i][0], haloC[i][1], haloC[i][2], (int) (haloA[i] * (active ? 0.6F + 0.4F * fade : 0.5F)));
        }
        // three spinning gimbal rings
        if (active) {
            float rs = orbS * 1.0F;
            for (int k = 0; k < 3; k++) {
                float phi = time * (0.05F + 0.018F * k) + k * 2.1F;
                float c = Mth.cos(phi);
                float s = Mth.sin(phi);
                float[] u;
                float[] v;
                if (k == 0) {
                    u = new float[]{1, 0, 0};
                    v = new float[]{0, c, s};
                } else if (k == 1) {
                    u = new float[]{0, 1, 0};
                    v = new float[]{c, 0, s};
                } else {
                    u = new float[]{0, 0, 1};
                    v = new float[]{c, s, 0};
                }
                ring3(glow, m, 0.5F, orbY, 0.5F, u, v, rs * (1.7F + 0.45F * k), 0.05F + 0.02F * orbS, 40,
                        k == 1 ? 90 : 190, k == 1 ? 230 : 120, 255, (int) (200 * fade));
            }
        }

        // the beam into the sky
        if (active && t > 90.0F) {
            float k = smooth(Mth.clamp((t - 90.0F) / 140.0F, 0.0F, 1.0F));
            float flick = 0.85F + 0.15F * (float) Math.sin(time * 1.3F);
            float w = (0.12F + 0.75F * k) * flick;
            beam(glow, m, 0.5F, orbY, 0.5F, 90.0F, w, 150, 80, 255, (int) (160 * k));
            beam(glow, m, 0.5F, orbY, 0.5F, 90.0F, w * 0.45F, 245, 225, 255, (int) (230 * k));
            // a second thin beam going down to the altar
            beam(glow, m, 0.5F, 0.0F, 0.5F, orbY, w * 0.6F, 190, 120, 255, (int) (200 * k));
        }

        // ---------------------------------------------------------------- pass B: the End-shader crystal
        VertexConsumer core = buffers.getBuffer(RenderType.endPortal());
        poseStack.pushPose();
        poseStack.translate(0.5F, orbY, 0.5F);
        poseStack.mulPose(new org.joml.Quaternionf().rotateAxis(time * 0.045F, 1.0F, 1.0F, 0.0F));
        cube(core, poseStack.last().pose(), orbS * 0.62F);
        poseStack.popPose();
        poseStack.pushPose();
        poseStack.translate(0.5F, orbY, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(time * 2.2F));
        poseStack.mulPose(Axis.XP.rotationDegrees(45.0F));
        cube(core, poseStack.last().pose(), orbS * 0.45F);
        poseStack.popPose();
    }

    // ------------------------------------------------------------------ helpers

    private static float smooth(float x) {
        return x * x * (3.0F - 2.0F * x);
    }

    private static float ease(float x) {
        return 1.0F - (1.0F - x) * (1.0F - x) * (1.0F - x);
    }

    private static void v(VertexConsumer vc, Matrix4f m, float x, float y, float z, int r, int g, int b, int a) {
        vc.addVertex(m, x, y, z).setColor(r, g, b, Mth.clamp(a, 0, 255));
    }

    /** One quad, emitted with both windings so it never gets culled. */
    private static void quad(VertexConsumer vc, Matrix4f m, float[] p1, float[] p2, float[] p3, float[] p4,
                             int r, int g, int b, int a) {
        v(vc, m, p1[0], p1[1], p1[2], r, g, b, a);
        v(vc, m, p2[0], p2[1], p2[2], r, g, b, a);
        v(vc, m, p3[0], p3[1], p3[2], r, g, b, a);
        v(vc, m, p4[0], p4[1], p4[2], r, g, b, a);
        v(vc, m, p4[0], p4[1], p4[2], r, g, b, a);
        v(vc, m, p3[0], p3[1], p3[2], r, g, b, a);
        v(vc, m, p2[0], p2[1], p2[2], r, g, b, a);
        v(vc, m, p1[0], p1[1], p1[2], r, g, b, a);
    }

    /** A flat ring on the ground. {@code fill} < 1 makes dashes. */
    private static void ring(VertexConsumer vc, Matrix4f m, float cx, float cz, float y, float radius, float width,
                             int segs, float rot, float fill, int r, int g, int b, int a) {
        float step = TAU / segs;
        float h = width / 2.0F;
        for (int i = 0; i < segs; i++) {
            float a0 = rot + i * step;
            float a1 = a0 + step * fill;
            float c0 = Mth.cos(a0);
            float s0 = Mth.sin(a0);
            float c1 = Mth.cos(a1);
            float s1 = Mth.sin(a1);
            quad(vc, m,
                    new float[]{cx + c0 * (radius - h), y, cz + s0 * (radius - h)},
                    new float[]{cx + c0 * (radius + h), y, cz + s0 * (radius + h)},
                    new float[]{cx + c1 * (radius + h), y, cz + s1 * (radius + h)},
                    new float[]{cx + c1 * (radius - h), y, cz + s1 * (radius - h)}, r, g, b, a);
        }
    }

    /** A flat bar on the ground between two points. */
    private static void bar(VertexConsumer vc, Matrix4f m, float x0, float y, float z0, float x1, float z1, float w,
                            int r, int g, int b, int a) {
        float dx = x1 - x0;
        float dz = z1 - z0;
        float len = Mth.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-4F) {
            return;
        }
        float nx = -dz / len * w / 2.0F;
        float nz = dx / len * w / 2.0F;
        quad(vc, m, new float[]{x0 + nx, y, z0 + nz}, new float[]{x0 - nx, y, z0 - nz},
                new float[]{x1 - nx, y, z1 - nz}, new float[]{x1 + nx, y, z1 + nz}, r, g, b, a);
    }

    /** A ring (tube made of two crossing strips) in the plane spanned by the unit vectors u and v. */
    private static void ring3(VertexConsumer vc, Matrix4f m, float cx, float cy, float cz, float[] u, float[] v,
                              float radius, float width, int segs, int r, int g, int b, int a) {
        float nx = u[1] * v[2] - u[2] * v[1];
        float ny = u[2] * v[0] - u[0] * v[2];
        float nz = u[0] * v[1] - u[1] * v[0];
        float h = width / 2.0F;
        float step = TAU / segs;
        for (int i = 0; i < segs; i++) {
            float a0 = i * step;
            float a1 = a0 + step;
            float[] p0 = pt(cx, cy, cz, u, v, radius, a0);
            float[] p1 = pt(cx, cy, cz, u, v, radius, a1);
            float r0x = Mth.cos(a0) * u[0] + Mth.sin(a0) * v[0];
            float r0y = Mth.cos(a0) * u[1] + Mth.sin(a0) * v[1];
            float r0z = Mth.cos(a0) * u[2] + Mth.sin(a0) * v[2];
            float r1x = Mth.cos(a1) * u[0] + Mth.sin(a1) * v[0];
            float r1y = Mth.cos(a1) * u[1] + Mth.sin(a1) * v[1];
            float r1z = Mth.cos(a1) * u[2] + Mth.sin(a1) * v[2];
            quad(vc, m, new float[]{p0[0] - r0x * h, p0[1] - r0y * h, p0[2] - r0z * h},
                    new float[]{p0[0] + r0x * h, p0[1] + r0y * h, p0[2] + r0z * h},
                    new float[]{p1[0] + r1x * h, p1[1] + r1y * h, p1[2] + r1z * h},
                    new float[]{p1[0] - r1x * h, p1[1] - r1y * h, p1[2] - r1z * h}, r, g, b, a);
            quad(vc, m, new float[]{p0[0] - nx * h, p0[1] - ny * h, p0[2] - nz * h},
                    new float[]{p0[0] + nx * h, p0[1] + ny * h, p0[2] + nz * h},
                    new float[]{p1[0] + nx * h, p1[1] + ny * h, p1[2] + nz * h},
                    new float[]{p1[0] - nx * h, p1[1] - ny * h, p1[2] - nz * h}, r, g, b, a);
        }
    }

    private static float[] pt(float cx, float cy, float cz, float[] u, float[] v, float radius, float a) {
        float c = Mth.cos(a) * radius;
        float s = Mth.sin(a) * radius;
        return new float[]{cx + u[0] * c + v[0] * s, cy + u[1] * c + v[1] * s, cz + u[2] * c + v[2] * s};
    }

    /** A square that always faces the camera. */
    private static void billboard(VertexConsumer vc, Matrix4f m, float cx, float cy, float cz,
                                  float rx, float ry, float rz, float ux, float uy, float uz, float size,
                                  int r, int g, int b, int a) {
        quad(vc, m,
                new float[]{cx - rx * size - ux * size, cy - ry * size - uy * size, cz - rz * size - uz * size},
                new float[]{cx + rx * size - ux * size, cy + ry * size - uy * size, cz + rz * size - uz * size},
                new float[]{cx + rx * size + ux * size, cy + ry * size + uy * size, cz + rz * size + uz * size},
                new float[]{cx - rx * size + ux * size, cy - ry * size + uy * size, cz - rz * size + uz * size}, r, g, b, a);
    }

    /** A vertical beam made of two crossing quads; it fades out towards the top. */
    private static void beam(VertexConsumer vc, Matrix4f m, float cx, float y0, float cz, float y1, float w,
                             int r, int g, int b, int a) {
        for (int k = 0; k < 2; k++) {
            float dx = k == 0 ? w : 0.0F;
            float dz = k == 0 ? 0.0F : w;
            v(vc, m, cx - dx, y0, cz - dz, r, g, b, a);
            v(vc, m, cx + dx, y0, cz + dz, r, g, b, a);
            v(vc, m, cx + dx, y1, cz + dz, r, g, b, 0);
            v(vc, m, cx - dx, y1, cz - dz, r, g, b, 0);
            v(vc, m, cx - dx, y1, cz - dz, r, g, b, 0);
            v(vc, m, cx + dx, y1, cz + dz, r, g, b, 0);
            v(vc, m, cx + dx, y0, cz + dz, r, g, b, a);
            v(vc, m, cx - dx, y0, cz - dz, r, g, b, a);
        }
    }

    /** Cube centred on the origin, positions only (End portal render type). */
    private static void cube(VertexConsumer vc, Matrix4f m, float s) {
        float[][] f = {
                {-s, -s, -s, s, -s, -s, s, -s, s, -s, -s, s},
                {-s, s, -s, -s, s, s, s, s, s, s, s, -s},
                {-s, -s, -s, -s, s, -s, s, s, -s, s, -s, -s},
                {-s, -s, s, s, -s, s, s, s, s, -s, s, s},
                {-s, -s, -s, -s, -s, s, -s, s, s, -s, s, -s},
                {s, -s, -s, s, s, -s, s, s, s, s, -s, s},
        };
        for (float[] q : f) {
            for (int i = 0; i < 4; i++) {
                vc.addVertex(m, q[i * 3], q[i * 3 + 1], q[i * 3 + 2]);
            }
        }
    }
}
