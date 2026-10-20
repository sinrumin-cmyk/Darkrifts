package com.darkrifts.client;

import com.darkrifts.entity.Singularity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * The black hole: an opaque black sphere, a swirling violet accretion disc (hot white-lilac inside, deep purple
 * outside, brighter on one side like a real relativistic disc), a thin photon ring and a lensing halo that always
 * face the camera.
 */
public class SingularityRenderer extends EntityRenderer<Singularity> {
    private static final ResourceLocation TEX = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    private static final float TAU = FxDraw.TAU;
    private static final int RINGS = 18;
    private static final int SEG = 72;

    public SingularityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(Singularity entity) {
        return TEX;
    }

    /** The disc is much bigger than the entity's box: never cull it. */
    @Override
    public boolean shouldRender(Singularity entity, Frustum frustum, double camX, double camY, double camZ) {
        return true;
    }

    @Override
    public void render(Singularity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        float age = entity.age() + partialTick;
        float grow = FxDraw.ease(Mth.clamp(age / 8.0F, 0.0F, 1.0F));
        float collapse = Mth.clamp((age - (Singularity.DURATION - 7)) / 7.0F, 0.0F, 1.0F);
        float radius = 0.95F * grow * (1.0F - collapse * collapse);
        if (radius < 0.03F) {
            return;
        }
        float charge = Mth.clamp(age / Singularity.DURATION, 0.0F, 1.0F);
        float boost = 1.0F + 0.7F * charge * charge * charge;

        poseStack.pushPose();
        poseStack.translate(0.0, entity.getBbHeight() * 0.5, 0.0);
        Matrix4f m = poseStack.last().pose();

        // direction to the camera (world axes == local axes here: no rotation is applied)
        Vec3 cam = this.entityRenderDispatcher.camera.getPosition();
        Vec3 center = entity.getPosition(partialTick).add(0.0, entity.getBbHeight() * 0.5, 0.0);
        Vec3 d = cam.subtract(center);
        d = d.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : d.normalize();
        Vec3 ref = Math.abs(d.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = d.cross(ref).normalize();
        Vec3 v = d.cross(u).normalize();
        float[] ua = {(float) u.x, (float) u.y, (float) u.z};
        float[] va = {(float) v.x, (float) v.y, (float) v.z};

        // 1. the event horizon: pure opaque black
        VertexConsumer solid = buffer.getBuffer(RenderType.debugQuads());
        sphere(solid, m, radius, 18, 28);

        // 2. everything that glows (additive)
        VertexConsumer glow = buffer.getBuffer(RenderType.lightning());
        disc(glow, m, radius, age, boost, grow);
        // lensing halo: light from the far side of the disc bent around the sphere
        FxDraw.ring3(glow, m, 0, 0, 0, ua, va, radius * 1.55F, radius * 0.55F, 56,
                110, 40, 200, (int) (70 * boost * grow));
        FxDraw.ring3(glow, m, 0, 0, 0, ua, va, radius * 1.22F, radius * 0.22F, 56,
                190, 90, 255, (int) (120 * boost * grow));
        // photon ring
        FxDraw.ring3(glow, m, 0, 0, 0, ua, va, radius * 1.07F, radius * 0.07F, 64,
                255, 235, 255, (int) (230 * grow));
        poseStack.popPose();
    }

    private static void sphere(VertexConsumer vc, Matrix4f m, float r, int lat, int lon) {
        for (int i = 0; i < lat; i++) {
            float p0 = -Mth.HALF_PI + Mth.PI * i / lat;
            float p1 = -Mth.HALF_PI + Mth.PI * (i + 1) / lat;
            for (int j = 0; j < lon; j++) {
                float a0 = TAU * j / lon;
                float a1 = TAU * (j + 1) / lon;
                FxDraw.quad(vc, m, sp(r, p0, a0), sp(r, p0, a1), sp(r, p1, a1), sp(r, p1, a0), 0, 0, 0, 255);
            }
        }
    }

    private static float[] sp(float r, float lat, float lon) {
        float c = Mth.cos(lat);
        return new float[]{r * c * Mth.cos(lon), r * Mth.sin(lat), r * c * Mth.sin(lon)};
    }

    /** Hot, differentially rotating plasma: white-lilac -> violet -> deep purple -> indigo, fading at both edges. */
    private static void disc(VertexConsumer vc, Matrix4f m, float radius, float age, float boost, float grow) {
        float r0 = radius * 1.45F;
        float r1 = radius * 4.4F;
        float tilt = 0.32F;
        float yaw = age * 0.012F;
        float cosT = Mth.cos(tilt);
        float sinT = Mth.sin(tilt);
        float cosY = Mth.cos(yaw);
        float sinY = Mth.sin(yaw);

        float[][][] pos = new float[RINGS + 1][SEG + 1][3];
        int[][] col = new int[RINGS + 1][SEG + 1];
        for (int i = 0; i <= RINGS; i++) {
            float t = i / (float) RINGS;
            float rad = r0 + (r1 - r0) * t;
            float rel = rad / radius;
            // inner matter orbits much faster than the outer one
            float spin = age * 0.9F / (rel * Mth.sqrt(rel));
            for (int j = 0; j <= SEG; j++) {
                float a = TAU * j / SEG;
                float x = Mth.cos(a) * rad;
                float z = Mth.sin(a) * rad;
                float y1 = -z * sinT;
                float z1 = z * cosT;
                pos[i][j][0] = x * cosY + z1 * sinY;
                pos[i][j][1] = y1;
                pos[i][j][2] = -x * sinY + z1 * cosY;

                float pa = a + spin;
                float n1 = 0.5F + 0.5F * Mth.sin(pa * 3.0F + rel * 2.4F);
                float n2 = 0.5F + 0.5F * Mth.sin(pa * 7.0F - rel * 3.3F + 1.3F);
                float n3 = 0.5F + 0.5F * Mth.sin(pa * 13.0F + rel * 5.1F);
                float bright = 0.30F + 0.70F * (0.5F * n1 + 0.3F * n2 + 0.2F * n3);
                // one side of the disc is brighter (relativistic beaming)
                float doppler = 0.62F + 0.38F * Mth.cos(a + yaw - 0.9F);
                float heat = 1.0F - t;
                float edge = Mth.clamp(t / 0.10F, 0.0F, 1.0F) * Mth.clamp((1.0F - t) / 0.35F, 0.0F, 1.0F);
                float alpha = Mth.clamp(bright * doppler * edge * (0.35F + 0.9F * heat) * boost * grow, 0.0F, 1.0F);
                col[i][j] = palette(heat, alpha);
            }
        }
        for (int i = 0; i < RINGS; i++) {
            for (int j = 0; j < SEG; j++) {
                emit(vc, m, pos[i][j], col[i][j]);
                emit(vc, m, pos[i + 1][j], col[i + 1][j]);
                emit(vc, m, pos[i + 1][j + 1], col[i + 1][j + 1]);
                emit(vc, m, pos[i][j + 1], col[i][j + 1]);
                // back side, so the disc is visible from below too
                emit(vc, m, pos[i][j + 1], col[i][j + 1]);
                emit(vc, m, pos[i + 1][j + 1], col[i + 1][j + 1]);
                emit(vc, m, pos[i + 1][j], col[i + 1][j]);
                emit(vc, m, pos[i][j], col[i][j]);
            }
        }
    }

    private static void emit(VertexConsumer vc, Matrix4f m, float[] p, int argb) {
        FxDraw.v(vc, m, p[0], p[1], p[2], (argb >> 16) & 255, (argb >> 8) & 255, argb & 255, (argb >>> 24) & 255);
    }

    /** heat 1 = innermost: white-lilac; 0.55 = bright violet; 0.2 = deep purple; 0 = indigo. */
    private static int palette(float heat, float alpha) {
        float[] c0 = {70, 30, 170};
        float[] c1 = {120, 40, 220};
        float[] c2 = {185, 80, 255};
        float[] c3 = {245, 215, 255};
        float[] a;
        float[] b;
        float k;
        if (heat < 0.25F) {
            a = c0;
            b = c1;
            k = heat / 0.25F;
        } else if (heat < 0.6F) {
            a = c1;
            b = c2;
            k = (heat - 0.25F) / 0.35F;
        } else {
            a = c2;
            b = c3;
            k = (heat - 0.6F) / 0.4F;
        }
        int r = (int) Mth.lerp(k, a[0], b[0]);
        int g = (int) Mth.lerp(k, a[1], b[1]);
        int bl = (int) Mth.lerp(k, a[2], b[2]);
        int al = (int) (Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F);
        return (al << 24) | (r << 16) | (g << 8) | bl;
    }
}
