package com.darkrifts.client;

import com.darkrifts.DarkDimensions;
import com.darkrifts.DarkRifts;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.Random;

/**
 * Custom skybox for the dark realms:
 *  - mirror: a black-violet dome glowing at the horizon, twinkling stars and a huge torn rift across the sky;
 *  - void prison: a pitch-black dome with a teal glow and a dense field of coloured stars.
 * Everything is drawn once per frame right after the (disabled) vanilla sky, with depth writes off.
 */
@EventBusSubscriber(modid = DarkRifts.MODID, value = Dist.CLIENT)
public final class SkyRenderer {
    private static final float R = 80.0F;
    private static final int STARS_MIRROR = 420;
    private static final int STARS_VOID = 800;

    private static float[][] starsMirror;
    private static float[][] starsVoid;
    private static float[][] tear;

    private SkyRenderer() {}

    @SubscribeEvent
    public static void onStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        boolean mirror = DarkDimensions.isDark(mc.level.dimension());
        boolean prison = DarkDimensions.isVoid(mc.level.dimension());
        if (!mirror && !prison) {
            return;
        }
        if (event.getCamera().getFluidInCamera() != FogType.NONE) {
            return;
        }
        if (starsMirror == null) {
            init();
        }
        float time = (System.currentTimeMillis() % 2_000_000L) / 50.0F;
        Matrix4f m = new Matrix4f(event.getModelViewMatrix());

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();

        // 1) the dome
        RenderSystem.defaultBlendFunc();
        float pulse = 0.85F + 0.15F * (float) Math.sin(time * 0.03);
        if (mirror) {
            dome(m, 0.004F, 0.0F, 0.012F, 0.075F * pulse, 0.012F * pulse, 0.14F * pulse);
        } else {
            dome(m, 0.0F, 0.0F, 0.004F, 0.0F, 0.055F * pulse, 0.07F * pulse);
        }

        // 2) stars and the rift (additive)
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        stars(m, mirror ? starsMirror : starsVoid, time, mirror);
        if (mirror) {
            tear(m, time);
        }

        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
    }

    // ------------------------------------------------------------------ geometry data

    private static void init() {
        starsMirror = makeStars(STARS_MIRROR, 1337L);
        starsVoid = makeStars(STARS_VOID, 4242L);
        // the tear: a jagged arc from the lower left to the upper right of the sky
        Random r = new Random(99L);
        int n = 44;
        tear = new float[n][5]; // dirX, dirY, dirZ, halfWidth, sparkle phase
        for (int i = 0; i < n; i++) {
            double u = i / (double) (n - 1);
            double az = Math.toRadians(-75.0 + 150.0 * u + (r.nextDouble() - 0.5) * 5.0);
            double el = Math.toRadians(14.0 + 46.0 * Math.sin(Math.PI * u * 0.85) + (r.nextDouble() - 0.5) * 7.0);
            tear[i][0] = (float) (Math.cos(el) * Math.cos(az));
            tear[i][1] = (float) Math.sin(el);
            tear[i][2] = (float) (Math.cos(el) * Math.sin(az));
            tear[i][3] = (float) (0.02 + 0.075 * Math.pow(Math.sin(Math.PI * u), 0.75) * (0.8 + 0.4 * r.nextDouble()));
            tear[i][4] = r.nextFloat() * 6.28F;
        }
    }

    private static float[][] makeStars(int count, long seed) {
        Random r = new Random(seed);
        float[][] s = new float[count][6]; // dir xyz, size, phase, tint
        for (int i = 0; i < count; i++) {
            double y = r.nextDouble() * 1.15 - 0.15;
            double az = r.nextDouble() * Math.PI * 2.0;
            double rad = Math.sqrt(Math.max(0.0, 1.0 - y * y));
            s[i][0] = (float) (Math.cos(az) * rad);
            s[i][1] = (float) y;
            s[i][2] = (float) (Math.sin(az) * rad);
            s[i][3] = 0.25F + r.nextFloat() * r.nextFloat() * 0.9F;
            s[i][4] = r.nextFloat() * 6.28F;
            s[i][5] = r.nextFloat();
        }
        return s;
    }

    // ------------------------------------------------------------------ drawing

    private static void dome(Matrix4f m, float tr, float tg, float tb, float hr, float hg, float hb) {
        float[] elev = {-45.0F, -12.0F, 0.0F, 7.0F, 18.0F, 35.0F, 60.0F, 90.0F};
        int seg = 24;
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int band = 0; band < elev.length - 1; band++) {
            for (int s = 0; s < seg; s++) {
                float a0 = (float) (s * Math.PI * 2.0 / seg);
                float a1 = (float) ((s + 1) * Math.PI * 2.0 / seg);
                domeVertex(b, m, elev[band], a0, tr, tg, tb, hr, hg, hb);
                domeVertex(b, m, elev[band], a1, tr, tg, tb, hr, hg, hb);
                domeVertex(b, m, elev[band + 1], a1, tr, tg, tb, hr, hg, hb);
                domeVertex(b, m, elev[band + 1], a0, tr, tg, tb, hr, hg, hb);
            }
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
    }

    private static void domeVertex(BufferBuilder b, Matrix4f m, float elevDeg, float az,
                                   float tr, float tg, float tb, float hr, float hg, float hb) {
        double e = Math.toRadians(elevDeg);
        float x = (float) (Math.cos(e) * Math.cos(az)) * R;
        float y = (float) Math.sin(e) * R;
        float z = (float) (Math.cos(e) * Math.sin(az)) * R;
        float k = (float) Math.sqrt(Math.max(0.0, Math.sin(e))); // 0 at the horizon, 1 at the zenith
        k = k * k * (3.0F - 2.0F * k);
        b.addVertex(m, x, y, z).setColor(hr + (tr - hr) * k, hg + (tg - hg) * k, hb + (tb - hb) * k, 1.0F);
    }

    private static void stars(Matrix4f m, float[][] list, float time, boolean mirror) {
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (float[] s : list) {
            float dx = s[0];
            float dy = s[1];
            float dz = s[2];
            // tangent frame of the star on the dome
            float rx = dz;
            float rz = -dx;
            float rl = (float) Math.sqrt(rx * rx + rz * rz);
            if (rl < 1.0E-4F) {
                continue;
            }
            rx /= rl;
            rz /= rl;
            float ux = -dy * rz;
            float uy = dz * rx - dx * rz;
            float uz = dy * rx;
            float size = s[3] * 0.55F;
            float twinkle = 0.45F + 0.55F * (float) Math.sin(time * (0.05F + s[5] * 0.1F) + s[4]);
            float cr;
            float cg;
            float cb;
            if (mirror) {
                cr = 0.8F + 0.2F * s[5];
                cg = 0.65F + 0.2F * s[5];
                cb = 1.0F;
            } else if (s[5] < 0.4F) {
                cr = 0.5F;
                cg = 1.0F;
                cb = 1.0F;
            } else if (s[5] < 0.7F) {
                cr = 0.9F;
                cg = 0.6F;
                cb = 1.0F;
            } else {
                cr = 1.0F;
                cg = 1.0F;
                cb = 1.0F;
            }
            float a = Math.max(0.1F, twinkle);
            float px = dx * R;
            float py = dy * R;
            float pz = dz * R;
            b.addVertex(m, px - rx * size - ux * size, py - uy * size, pz - rz * size - uz * size).setColor(cr, cg, cb, a);
            b.addVertex(m, px + rx * size - ux * size, py - uy * size, pz + rz * size - uz * size).setColor(cr, cg, cb, a);
            b.addVertex(m, px + rx * size + ux * size, py + uy * size, pz + rz * size + uz * size).setColor(cr, cg, cb, a);
            b.addVertex(m, px - rx * size + ux * size, py + uy * size, pz - rz * size + uz * size).setColor(cr, cg, cb, a);
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
    }

    /** The big rift in the sky: violet halo, bright rim, then a black core on top. */
    private static void tear(Matrix4f m, float time) {
        float glow = 0.8F + 0.2F * (float) Math.sin(time * 0.07);
        ribbon(m, 3.6F, 0.5F, 0.1F, 1.0F, 0.13F * glow, time);                 // halo (additive)
        ribbon(m, 1.7F, 0.9F, 0.65F, 1.0F, 0.75F * glow, time);                // rim (additive)
        RenderSystem.defaultBlendFunc();
        ribbon(m, 1.0F, 0.0F, 0.0F, 0.02F, 1.0F, time);                        // void core (opaque)
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
    }

    private static void ribbon(Matrix4f m, float widthScale, float r, float g, float bl, float a, float time) {
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int n = tear.length;
        float[][] left = new float[n][3];
        float[][] right = new float[n][3];
        for (int i = 0; i < n; i++) {
            float[] p = tear[i];
            float[] prev = tear[Math.max(0, i - 1)];
            float[] next = tear[Math.min(n - 1, i + 1)];
            float tx = next[0] - prev[0];
            float ty = next[1] - prev[1];
            float tz = next[2] - prev[2];
            // side = dir x tangent (tangent to the sphere, perpendicular to the line)
            float sx = p[1] * tz - p[2] * ty;
            float sy = p[2] * tx - p[0] * tz;
            float sz = p[0] * ty - p[1] * tx;
            float sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (sl < 1.0E-5F) {
                sl = 1.0F;
            }
            float w = p[3] * widthScale * (1.0F + 0.12F * (float) Math.sin(time * 0.1 + p[4])) * R / sl;
            float cx = p[0] * R;
            float cy = p[1] * R;
            float cz = p[2] * R;
            left[i][0] = cx + sx * w;
            left[i][1] = cy + sy * w;
            left[i][2] = cz + sz * w;
            right[i][0] = cx - sx * w;
            right[i][1] = cy - sy * w;
            right[i][2] = cz - sz * w;
        }
        for (int i = 0; i < n - 1; i++) {
            b.addVertex(m, left[i][0], left[i][1], left[i][2]).setColor(r, g, bl, a);
            b.addVertex(m, right[i][0], right[i][1], right[i][2]).setColor(r, g, bl, a);
            b.addVertex(m, right[i + 1][0], right[i + 1][1], right[i + 1][2]).setColor(r, g, bl, a);
            b.addVertex(m, left[i + 1][0], left[i + 1][1], left[i + 1][2]).setColor(r, g, bl, a);
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
    }
}
