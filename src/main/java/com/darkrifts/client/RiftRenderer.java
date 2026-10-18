package com.darkrifts.client;

import com.darkrifts.block.RiftBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Draws a rift as ONE thin, ragged ribbon (a crack in reality) instead of a clump of shards.
 *
 * Every rift cell owns a few pieces of the line (stored in the block entity). Each piece is subdivided into a
 * jagged polyline and drawn as a camera-facing strip with three layers: a faint violet halo, a bright violet-white
 * rim and a dark core rendered with the End-portal shader (a window into the void).
 *
 * Opening animation (first ~1.5 s of every piece): the line zips open from its start point, a wide white-violet
 * flash collapses onto it, and thin cracks spread outwards from the tear and fade out.
 */
public class RiftRenderer implements BlockEntityRenderer<RiftBlockEntity> {
    private static final int SUB = 5; // sub-segments per piece

    public RiftRenderer(BlockEntityRendererProvider.Context context) {}

    /** A fully computed strip, ready to be emitted in the glow and core passes. */
    private static final class Strip {
        Vec3[] p;
        float[] w;
        float flash;
        float fade = 1.0F;
        boolean crack;
    }

    @Override
    public void render(RiftBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        BlockPos origin = be.getBlockPos();
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Matrix4f pose = poseStack.last().pose();
        float time = level.getGameTime() + partialTick;

        List<double[]> segs = be.getSegments();
        if (segs.isEmpty()) {
            // Rift without stored line (older saves): a short vertical piece through the cell.
            double cx = origin.getX() + 0.5;
            double cy = origin.getY() + 0.5;
            double cz = origin.getZ() + 0.5;
            segs = List.of(new double[]{cx, cy - 0.55, cz, cx, cy + 0.55, cz, be.getSpawnTime(), 0.0, 1.1});
        }

        List<Strip> strips = new ArrayList<>();
        boolean flat = be.isFlat();
        for (double[] s : segs) {
            build(strips, s, time, flat);
        }
        if (strips.isEmpty()) {
            return;
        }

        // Pass 1: glow layers (additive "lightning" type). Never hold two buffers at once.
        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
        for (Strip st : strips) {
            if (st.crack) {
                int a = (int) (200 * st.fade);
                strip(glow, pose, origin, st.p, st.w, cam, 1.0F, 0.0F, 235, 190, 255, a, 0.012, flat);
                strip(glow, pose, origin, st.p, st.w, cam, 3.0F, 0.01F, 130, 60, 255, (int) (70 * st.fade), 0.02, flat);
                continue;
            }
            int haloA = Math.min(255, 40 + (int) (st.flash * 150));
            int rimA = Math.min(255, 150 + (int) (st.flash * 105));
            int rimG = Math.min(255, 150 + (int) (st.flash * 105));
            strip(glow, pose, origin, st.p, st.w, cam, 2.6F + st.flash * 1.6F, 0.05F, 105, 35, 235, haloA, 0.03, flat);
            strip(glow, pose, origin, st.p, st.w, cam, 1.35F + st.flash * 0.6F, 0.025F, 225, rimG, 255, rimA, 0.015, flat);
        }
        // Pass 2: the void core.
        VertexConsumer core = buffers.getBuffer(RenderType.endPortal());
        for (Strip st : strips) {
            if (!st.crack) {
                strip(core, pose, origin, st.p, st.w, cam, 1.0F, 0.0F, -1, 0, 0, 0, 0.0, flat);
            }
        }
    }

    /** Computes the strips (main line + opening cracks) for one stored piece of the line. */
    private static void build(List<Strip> out, double[] s, float time, boolean flat) {
        Vec3 a = new Vec3(s[0], s[1], s[2]);
        Vec3 b = new Vec3(s[3], s[4], s[5]);
        float age = Math.max(0.0F, time - (float) s[6]);
        double total = s.length > 8 ? s[8] : 0.0;
        long h = hash(Double.doubleToLongBits(s[0]) * 31L + Double.doubleToLongBits(s[2]) + (long) (s[1] * 977.0));

        // The line zips open: 0 -> 1 over ~5 ticks, with a slight overshoot-free ease-out.
        float t = Mth.clamp(age / 5.0F, 0.0F, 1.0F);
        float prog = 1.0F - (1.0F - t) * (1.0F - t);
        Vec3 end = a.lerp(b, prog);
        Vec3 dir = b.subtract(a);
        if (dir.lengthSqr() < 1.0E-6) {
            return;
        }
        Vec3 d = dir.normalize();
        Vec3 u1 = d.cross(Math.abs(d.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
        Vec3 u2 = d.cross(u1).normalize();
        if (flat) {
            // ground crack: jitter only sideways along the ground, never into the block
            d = new Vec3(d.x, 0, d.z).normalize();
            u1 = d.cross(new Vec3(0, 1, 0)).normalize();
            u2 = new Vec3(0, 0, 0);
        }

        float flash = Math.max(0.0F, 1.0F - age / 20.0F);
        float settle = Mth.clamp(age / 12.0F, 0.0F, 1.0F);
        float amp = 0.08F * (1.0F + flash * 1.2F);

        Strip main = new Strip();
        main.p = new Vec3[SUB + 1];
        main.w = new float[SUB + 1];
        main.flash = flash;
        for (int i = 0; i <= SUB; i++) {
            Vec3 q = a.lerp(end, i / (double) SUB);
            if (i > 0 && i < SUB) {
                double j1 = (rand(h, i * 8) - 0.5) * 2.0 * amp;
                double j2 = (rand(h, i * 8 + 4) - 0.5) * 2.0 * amp;
                double flick = 1.0 + 0.2 * Math.sin(time * 0.35 + i * 1.9 + rand(h, 5) * 6.28);
                q = q.add(u1.scale(j1 * flick)).add(u2.scale(j2 * flick));
            }
            main.p[i] = q;
            // Width is a smooth function of world position, so neighbouring pieces join without steps.
            double n = 0.5 + 0.5 * Math.sin(q.x * 3.1 + q.y * 2.3 + q.z * 1.7)
                    * Math.sin(q.x * 1.3 - q.y * 1.1 + q.z * 2.9 + 1.0);
            float pulse = 1.0F + 0.10F * (float) Math.sin(time * 0.18 + q.y * 1.5);
            // Fat in the middle of the whole rift, needle-thin at both ends.
            double pos = s[7] + dir.length() * (i / (double) SUB);
            float env;
            float maxW;
            if (total > 0.0) {
                double x = Mth.clamp(pos / total, 0.0, 1.0);
                env = Math.max(0.07F, (float) Math.pow(Math.sin(Math.PI * x), 0.8));
                maxW = Mth.clamp(0.10F + 0.014F * (float) total, 0.15F, 0.38F) * (flat ? 1.4F : 1.0F);
            } else {
                env = Mth.clamp(0.07F + (float) pos / 3.0F, 0.07F, 1.0F); // still being drawn
                maxW = 0.20F;
            }
            main.w[i] = maxW * env * (0.78F + 0.22F * (float) n) * pulse * (0.3F + 0.7F * settle)
                    * (1.0F + flash * 0.6F);
        }
        out.add(main);

        // Reality cracking: thin cracks spread from the middle of the piece for the first ~1.5 s.
        if (age < 30.0F) {
            Vec3 mid = a.lerp(end, 0.5);
            float spread = Mth.clamp(age / 10.0F, 0.0F, 1.0F);
            spread = 1.0F - (1.0F - spread) * (1.0F - spread);
            float fade = Mth.clamp(1.0F - (age - 8.0F) / 22.0F, 0.0F, 1.0F);
            int count = 2 + (int) (rand(h, 40) * 2.0F);
            for (int k = 0; k < count; k++) {
                double ang = rand(h, 48 + k * 3) * Math.PI * 2.0;
                double len = (0.35 + 0.65 * rand(h, 16 + k * 6)) * spread;
                Vec3 side = flat ? u1.scale(Math.cos(ang)).add(d.scale(Math.sin(ang)))
                        : u1.scale(Math.cos(ang)).add(u2.scale(Math.sin(ang)));
                Strip c = new Strip();
                c.crack = true;
                c.fade = fade;
                c.p = new Vec3[]{mid, mid.add(side.scale(len * 0.5)).add(d.scale((rand(h, 24 + k) - 0.5) * 0.2)),
                        mid.add(side.scale(len))};
                c.w = new float[]{0.03F, 0.016F, 0.0F};
                out.add(c);
            }
        }
    }

    /** Emits a camera-facing strip along the polyline p; layers are pushed away from the camera by {@code push}. */
    private static void strip(VertexConsumer vc, Matrix4f pose, BlockPos origin, Vec3[] p, float[] w, Vec3 cam,
                              float scale, float add, int r, int g, int b, int a, double push, boolean flat) {
        int n = p.length;
        Vec3[] left = new Vec3[n];
        Vec3[] right = new Vec3[n];
        for (int i = 0; i < n; i++) {
            Vec3 toCam = cam.subtract(p[i]);
            double dist = toCam.length();
            if (dist < 1.0E-4) {
                return;
            }
            toCam = toCam.scale(1.0 / dist);
            if (flat) {
                toCam = new Vec3(0, 1, 0); // lies on the ground: the ribbon is horizontal, layers stack upwards
            }
            Vec3 t = p[Math.min(n - 1, i + 1)].subtract(p[Math.max(0, i - 1)]);
            Vec3 side = t.cross(toCam);
            if (side.lengthSqr() < 1.0E-8) {
                side = new Vec3(0, 1, 0).cross(toCam);
                if (side.lengthSqr() < 1.0E-8) {
                    side = new Vec3(1, 0, 0);
                }
            }
            side = side.normalize().scale(w[i] * scale + add);
            Vec3 c = p[i].subtract(toCam.scale(push));
            left[i] = c.add(side);
            right[i] = c.subtract(side);
        }
        for (int i = 0; i < n - 1; i++) {
            quad(vc, pose, origin, left[i], right[i], right[i + 1], left[i + 1], r, g, b, a);
            quad(vc, pose, origin, left[i + 1], right[i + 1], right[i], left[i], r, g, b, a); // back face
        }
    }

    private static void quad(VertexConsumer vc, Matrix4f pose, BlockPos o, Vec3 v1, Vec3 v2, Vec3 v3, Vec3 v4,
                             int r, int g, int b, int a) {
        vertex(vc, pose, o, v1, r, g, b, a);
        vertex(vc, pose, o, v2, r, g, b, a);
        vertex(vc, pose, o, v3, r, g, b, a);
        vertex(vc, pose, o, v4, r, g, b, a);
    }

    private static void vertex(VertexConsumer vc, Matrix4f pose, BlockPos origin, Vec3 p,
                               int r, int g, int b, int a) {
        VertexConsumer v = vc.addVertex(pose,
                (float) (p.x - origin.getX()), (float) (p.y - origin.getY()), (float) (p.z - origin.getZ()));
        if (r >= 0) {
            v.setColor(r, g, b, Mth.clamp(a, 0, 255));
        }
    }

    private static long hash(long v) {
        v *= 0x9E3779B97F4A7C15L;
        v ^= v >>> 29;
        v *= 0xBF58476D1CE4E5B9L;
        v ^= v >>> 32;
        return v;
    }

    /** Stable pseudo-random value 0..1 for (hash, k). */
    private static float rand(long h, int k) {
        return ((hash(h + k * 0x632BE59BD9B4E019L) >>> 24) & 0xFF) / 255.0F;
    }
}
