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
 * Draws the rift as a jagged, camera-facing crack instead of cubes.
 * Every rift block becomes a node (jittered by a position hash); neighbouring nodes are joined by ribbons.
 * The ribbon core uses the End-portal shader (a window into "space"); two additive layers add a purple glow.
 */
public class RiftRenderer implements BlockEntityRenderer<RiftBlockEntity> {

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

        VertexConsumer core = buffers.getBuffer(RenderType.endPortal());
        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());

        Vec3 a = node(pos, time);
        float widthA = width(pos, time);

        // Collect links to neighbours (faces + edges only, no pure corners).
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
                    if (np.asLong() < pos.asLong()) {
                        continue; // the neighbour draws this segment
                    }
                    Vec3 b = node(np, time);
                    float widthB = width(np, time);
                    segment(core, glow, pose, pos, cam, a, b, widthA, widthB);
                }
            }
        }

        if (links == 0) {
            // Lonely cell: a short sparkle-crack so it still reads as a tear.
            long h = hash(pos.asLong() ^ 0x5DEECE66DL);
            Vec3 dir = new Vec3(rand(h, 0) - 0.5, rand(h, 16) - 0.5, rand(h, 32) - 0.5).normalize().scale(0.4);
            segment(core, glow, pose, pos, cam, a.subtract(dir), a.add(dir), 0.02F, 0.02F);
        }
    }

    private static void segment(VertexConsumer core, VertexConsumer glow, Matrix4f pose, BlockPos origin,
                                Vec3 cam, Vec3 a, Vec3 b, float wa, float wb) {
        // soft outer glow, brighter inner glow, then the portal-shader core
        ribbon(glow, pose, origin, cam, a, b, wa * 3.2, wb * 3.2, 110, 30, 210, 38);
        ribbon(glow, pose, origin, cam, a, b, wa * 1.9, wb * 1.9, 190, 110, 255, 70);
        ribbon(core, pose, origin, cam, a, b, wa, wb, -1, 0, 0, 0);
    }

    /** Emits a camera-facing, double-sided quad strip from a to b. r < 0 means "position only". */
    private static void ribbon(VertexConsumer vc, Matrix4f pose, BlockPos origin, Vec3 cam,
                               Vec3 a, Vec3 b, double wa, double wb, int r, int g, int bl, int alpha) {
        Vec3 dir = b.subtract(a);
        Vec3 mid = a.add(b).scale(0.5);
        Vec3 side = dir.cross(cam.subtract(mid));
        if (side.lengthSqr() < 1.0E-8) {
            return;
        }
        side = side.normalize();
        Vec3[] c = {
                a.add(side.scale(wa)), a.subtract(side.scale(wa)),
                b.subtract(side.scale(wb)), b.add(side.scale(wb))
        };
        for (int pass = 0; pass < 2; pass++) { // both windings, so culling never hides the ribbon
            for (int i = 0; i < 4; i++) {
                Vec3 p = c[pass == 0 ? i : 3 - i];
                VertexConsumer v = vc.addVertex(pose,
                        (float) (p.x - origin.getX()), (float) (p.y - origin.getY()), (float) (p.z - origin.getZ()));
                if (r >= 0) {
                    v.setColor(r, g, bl, alpha);
                }
            }
        }
    }

    /** World-space position of a rift cell's node: cell centre + stable random jitter + slow wobble. */
    private static Vec3 node(BlockPos p, float time) {
        long h = hash(p.asLong());
        double wob = time * 0.07;
        return new Vec3(
                p.getX() + 0.5 + (rand(h, 0) - 0.5) * 0.7 + Math.sin(wob + rand(h, 8) * 6.28) * 0.04,
                p.getY() + 0.5 + (rand(h, 16) - 0.5) * 0.7 + Math.sin(wob * 1.3 + rand(h, 24) * 6.28) * 0.04,
                p.getZ() + 0.5 + (rand(h, 32) - 0.5) * 0.7 + Math.sin(wob * 0.9 + rand(h, 40) * 6.28) * 0.04);
    }

    /** Half-width of the crack at a cell, in blocks; pulses gently. */
    private static float width(BlockPos p, float time) {
        long h = hash(p.asLong() * 31L + 7L);
        float base = 0.06F + 0.07F * rand(h, 0);
        return base * (1.0F + 0.18F * (float) Math.sin(time * 0.11 + rand(h, 12) * 6.28));
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
     * The crack ribbons stretch into neighbouring cells, so don't cull this renderer too early.
     * (Deliberately no @Override: if a NeoForge version names this differently it is simply unused.)
     */
    public net.minecraft.world.phys.AABB getRenderBoundingBox(RiftBlockEntity be) {
        return new net.minecraft.world.phys.AABB(be.getBlockPos()).inflate(1.5);
    }
}
