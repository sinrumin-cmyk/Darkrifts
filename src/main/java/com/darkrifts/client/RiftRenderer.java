package com.darkrifts.client;

import com.darkrifts.block.RiftBlockEntity;
import com.darkrifts.registry.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Thaumcraft-style flux rift: instead of cubes, every rift block draws one jagged, spiky "shard" that always
 * faces the camera and is stretched along the direction of the crack. Overlapping shards of neighbouring
 * blocks merge into a ragged tear.
 *
 * Each shard has three layers (back to front): a faint violet halo, a bright violet-white rim, and a dark core
 * rendered with the End-portal shader (a window into the void). The layers are pushed slightly apart along the
 * view direction so they never z-fight.
 */
public class RiftRenderer implements BlockEntityRenderer<RiftBlockEntity> {
    private static final int RING = 14; // vertices around a shard

    public RiftRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(RiftBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Matrix4f pose = poseStack.last().pose();
        float time = level.getGameTime() + partialTick;

        long h = hash(pos.asLong());
        Vec3 c = node(pos, h, time);

        // Direction of the crack = averaged direction to linked neighbours (sign-agnostic).
        Vec3 axis = null;
        int links = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int dist = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                    if (dist == 0 || dist == 3) {
                        continue;
                    }
                    BlockPos np = pos.offset(dx, dy, dz);
                    if (!level.getBlockState(np).is(ModBlocks.RIFT.get())) {
                        continue;
                    }
                    links++;
                    Vec3 d = node(np, hash(np.asLong()), time).subtract(c);
                    if (d.lengthSqr() < 1.0E-6) {
                        continue;
                    }
                    d = d.normalize();
                    axis = axis == null ? d : axis.add(d.dot(axis) < 0 ? d.scale(-1) : d);
                }
            }
        }
        if (axis == null || axis.lengthSqr() < 1.0E-6) {
            axis = new Vec3(rand(h, 0) - 0.5, rand(h, 8) - 0.2, rand(h, 16) - 0.5);
            if (axis.lengthSqr() < 1.0E-6) {
                axis = new Vec3(0, 1, 0);
            }
        }
        axis = axis.normalize();

        // Camera-facing basis: f toward camera, u along the crack (projected), v perpendicular.
        Vec3 f = cam.subtract(c);
        if (f.lengthSqr() < 1.0E-6) {
            return;
        }
        f = f.normalize();
        Vec3 u = axis.subtract(f.scale(axis.dot(f)));
        if (u.lengthSqr() < 1.0E-4) {
            u = f.cross(new Vec3(0, 1, 0));
            if (u.lengthSqr() < 1.0E-4) {
                u = f.cross(new Vec3(1, 0, 0));
            }
        }
        u = u.normalize();
        Vec3 v = f.cross(u).normalize();

        float pulse = 1.0F + 0.12F * (float) Math.sin(time * 0.13 + rand(h, 24) * 6.28);
        float halfLen = (links <= 1 ? 0.6F : 0.95F) * (0.85F + 0.3F * rand(h, 32)) * pulse;
        float halfWid = (0.17F + 0.12F * rand(h, 40)) * pulse;

        VertexConsumer core = buffers.getBuffer(RenderType.endPortal());
        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());

        shard(core, glow, pose, pos, c, f, u, v, halfLen, halfWid, h, time);
        // A short spike across the crack, slowly turning - gives the star-burst look.
        shard(core, glow, pose, pos, c, f, v, u.scale(-1), halfLen * 0.42F, halfWid * 0.8F, hash(h), time);
    }

    private static void shard(VertexConsumer core, VertexConsumer glow, Matrix4f pose, BlockPos origin,
                              Vec3 c, Vec3 f, Vec3 u, Vec3 v, float halfLen, float halfWid, long h, float time) {
        // Irregular radii; the two tips (i = 0 and RING/2) stay at full length.
        float[] r = new float[RING];
        for (int i = 0; i < RING; i++) {
            float jag = 0.45F + 0.55F * rand(h, (i * 5) % 56);
            if (i % 2 == 0) {
                jag = Math.min(1.0F, jag + 0.25F);
            }
            jag *= 1.0F + 0.10F * (float) Math.sin(time * 0.2 + i * 1.7);
            r[i] = (i == 0 || i == RING / 2) ? 1.0F : jag;
        }

        // layer: scale, push-away, colour (r = -1 means position only / portal shader)
        fan(glow, pose, origin, c.subtract(f.scale(0.030)), u, v, halfLen * 1.75F, halfWid * 2.2F, r, 110, 40, 230, 42);
        fan(glow, pose, origin, c.subtract(f.scale(0.015)), u, v, halfLen * 1.25F, halfWid * 1.45F, r, 225, 170, 255, 120);
        fan(core, pose, origin, c, u, v, halfLen, halfWid, r, -1, 0, 0, 0);
    }

    /** Emits a double-sided triangle fan (as degenerate quads) around the centre. */
    private static void fan(VertexConsumer vc, Matrix4f pose, BlockPos origin, Vec3 c, Vec3 u, Vec3 v,
                            float halfLen, float halfWid, float[] r, int red, int green, int blue, int alpha) {
        Vec3[] p = new Vec3[RING];
        for (int i = 0; i < RING; i++) {
            double ang = i * (Math.PI * 2.0 / RING);
            p[i] = c.add(u.scale(Math.cos(ang) * halfLen * r[i])).add(v.scale(Math.sin(ang) * halfWid * r[i]));
        }
        for (int i = 0; i < RING; i++) {
            Vec3 a = p[i];
            Vec3 b = p[(i + 1) % RING];
            vertex(vc, pose, origin, c, red, green, blue, alpha);
            vertex(vc, pose, origin, a, red, green, blue, alpha);
            vertex(vc, pose, origin, b, red, green, blue, alpha);
            vertex(vc, pose, origin, c, red, green, blue, alpha);
            // reverse winding so culling never hides it
            vertex(vc, pose, origin, c, red, green, blue, alpha);
            vertex(vc, pose, origin, b, red, green, blue, alpha);
            vertex(vc, pose, origin, a, red, green, blue, alpha);
            vertex(vc, pose, origin, c, red, green, blue, alpha);
        }
    }

    private static void vertex(VertexConsumer vc, Matrix4f pose, BlockPos origin, Vec3 p,
                               int r, int g, int b, int a) {
        VertexConsumer v = vc.addVertex(pose,
                (float) (p.x - origin.getX()), (float) (p.y - origin.getY()), (float) (p.z - origin.getZ()));
        if (r >= 0) {
            v.setColor(r, g, b, a);
        }
    }

    /** World-space node of a rift cell: cell centre + stable jitter + slow wobble. */
    private static Vec3 node(BlockPos p, long h, float time) {
        double wob = time * 0.07;
        return new Vec3(
                p.getX() + 0.5 + (rand(h, 0) - 0.5) * 0.6 + Math.sin(wob + rand(h, 8) * 6.28) * 0.04,
                p.getY() + 0.5 + (rand(h, 16) - 0.5) * 0.6 + Math.sin(wob * 1.3 + rand(h, 24) * 6.28) * 0.04,
                p.getZ() + 0.5 + (rand(h, 32) - 0.5) * 0.6 + Math.sin(wob * 0.9 + rand(h, 40) * 6.28) * 0.04);
    }

    private static long hash(long v) {
        v *= 0x9E3779B97F4A7C15L;
        v ^= v >>> 29;
        v *= 0xBF58476D1CE4E5B9L;
        v ^= v >>> 32;
        return v;
    }

    /** 0..1 value taken from 8 bits of the hash starting at the given bit offset. */
    private static float rand(long h, int shift) {
        return ((h >>> shift) & 0xFF) / 255.0F;
    }

    /**
     * The shards stretch into neighbouring cells, so don't cull this renderer too early.
     * (Deliberately no @Override: if a NeoForge version names this differently it is simply unused.)
     */
    public net.minecraft.world.phys.AABB getRenderBoundingBox(RiftBlockEntity be) {
        return new net.minecraft.world.phys.AABB(be.getBlockPos()).inflate(1.5);
    }
}
