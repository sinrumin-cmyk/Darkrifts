package com.darkrifts.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/** Small drawing helpers for additive "light" geometry (same RenderType.lightning() trick as the ritual altar). */
public final class FxDraw {
    public static final float TAU = (float) (Math.PI * 2.0);

    private FxDraw() {}

    public static float smooth(float x) {
        return x * x * (3.0F - 2.0F * x);
    }

    public static float ease(float x) {
        return 1.0F - (1.0F - x) * (1.0F - x) * (1.0F - x);
    }

    public static void v(VertexConsumer vc, Matrix4f m, float x, float y, float z, int r, int g, int b, int a) {
        vc.addVertex(m, x, y, z).setColor(r, g, b, Mth.clamp(a, 0, 255));
    }

    /** One quad, emitted with both windings so it never gets culled. */
    public static void quad(VertexConsumer vc, Matrix4f m, float[] p1, float[] p2, float[] p3, float[] p4,
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
    public static void ring(VertexConsumer vc, Matrix4f m, float cx, float cz, float y, float radius, float width,
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
    public static void bar(VertexConsumer vc, Matrix4f m, float x0, float y, float z0, float x1, float z1, float w,
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

    /** A square that always faces the camera. */
    public static void billboard(VertexConsumer vc, Matrix4f m, float cx, float cy, float cz,
                                 float rx, float ry, float rz, float ux, float uy, float uz, float size,
                                 int r, int g, int b, int a) {
        quad(vc, m,
                new float[]{cx - rx * size - ux * size, cy - ry * size - uy * size, cz - rz * size - uz * size},
                new float[]{cx + rx * size - ux * size, cy + ry * size - uy * size, cz + rz * size - uz * size},
                new float[]{cx + rx * size + ux * size, cy + ry * size + uy * size, cz + rz * size + uz * size},
                new float[]{cx - rx * size + ux * size, cy - ry * size + uy * size, cz - rz * size + uz * size}, r, g, b, a);
    }

    /** A vertical beam made of two crossing quads; alpha {@code a} at y0, fading to 0 at y1. */
    public static void beam(VertexConsumer vc, Matrix4f m, float cx, float y0, float cz, float y1, float w,
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

    /** A ring (tube made of two crossing strips) in the plane spanned by the unit vectors u and v. */
    public static void ring3(VertexConsumer vc, Matrix4f m, float cx, float cy, float cz, float[] u, float[] v,
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

    /** Cube centred on the origin, positions only (End portal render type). */
    public static void cube(VertexConsumer vc, Matrix4f m, float s) {
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

    /** The three spinning gimbal rings of the ritual orbs, centred on (cx, cy, cz). */
    public static void gimbal(VertexConsumer vc, Matrix4f m, float cx, float cy, float cz, float scale, float time,
                              int r0, int g0, int b0, int a) {
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
            ring3(vc, m, cx, cy, cz, u, v, scale * (1.7F + 0.45F * k), 0.05F + 0.02F * scale, 40,
                    k == 1 ? r0 - 60 : r0, k == 1 ? g0 + 90 : g0, b0, a);
        }
    }
}
