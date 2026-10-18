package com.darkrifts.client;

import com.darkrifts.DarkDimensions;
import com.darkrifts.DarkRifts;
import com.darkrifts.registry.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Void matter looks like the End portal: every exposed face of a void_matter block near the player is covered with
 * a quad drawn by the vanilla End-portal shader (moving starfield / tunnel of the void).
 * The face list is cached and rebuilt only when the camera has moved a few blocks.
 */
@EventBusSubscriber(modid = DarkRifts.MODID, value = Dist.CLIENT)
public final class VoidMatterRenderer {
    private static final int RADIUS = 44;
    private static final int DOWN = 6;
    private static final int UP = 3;
    private static final float OFFSET = 0.01F;

    /** x, y, z, direction index of every exposed face. */
    private static final List<int[]> FACES = new ArrayList<>();
    private static Level cachedLevel;
    private static BlockPos cachedCenter;
    private static long cachedTime;

    private VoidMatterRenderer() {}

    @SubscribeEvent
    public static void onStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null || !DarkDimensions.isVoid(level.dimension())) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        BlockPos center = BlockPos.containing(cam);
        if (level != cachedLevel || cachedCenter == null || level.getGameTime() - cachedTime > 100
                || Math.abs(center.getX() - cachedCenter.getX()) > 4
                || Math.abs(center.getZ() - cachedCenter.getZ()) > 4
                || Math.abs(center.getY() - cachedCenter.getY()) > 2) {
            rebuild(level, center);
        }

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f pose = poseStack.last().pose();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.endPortal());
        for (int[] f : FACES) {
            face(vc, pose, f[0], f[1], f[2], Direction.from3DDataValue(f[3]));
        }
        buffers.endBatch(RenderType.endPortal());
        poseStack.popPose();
    }

    private static void rebuild(Level level, BlockPos center) {
        FACES.clear();
        cachedLevel = level;
        cachedCenter = center;
        cachedTime = level.getGameTime();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
        int minY = Math.max(level.getMinBuildHeight(), center.getY() - DOWN);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, center.getY() + UP);
        for (int x = center.getX() - RADIUS; x <= center.getX() + RADIUS; x++) {
            for (int z = center.getZ() - RADIUS; z <= center.getZ() + RADIUS; z++) {
                for (int y = minY; y <= maxY; y++) {
                    p.set(x, y, z);
                    BlockState s = level.getBlockState(p);
                    if (!s.is(ModBlocks.VOID_MATTER.get())) {
                        continue;
                    }
                    for (Direction d : Direction.values()) {
                        n.set(x + d.getStepX(), y + d.getStepY(), z + d.getStepZ());
                        BlockState o = level.getBlockState(n);
                        if (!o.is(ModBlocks.VOID_MATTER.get()) && !o.canOcclude()) {
                            FACES.add(new int[]{x, y, z, d.get3DDataValue()});
                        }
                    }
                }
            }
        }
    }

    private static void face(VertexConsumer vc, Matrix4f m, int x, int y, int z, Direction d) {
        float x0 = x;
        float x1 = x + 1.0F;
        float y0 = y;
        float y1 = y + 1.0F;
        float z0 = z;
        float z1 = z + 1.0F;
        switch (d) {
            case UP -> {
                float yy = y1 + OFFSET;
                vc.addVertex(m, x0, yy, z0);
                vc.addVertex(m, x0, yy, z1);
                vc.addVertex(m, x1, yy, z1);
                vc.addVertex(m, x1, yy, z0);
            }
            case DOWN -> {
                float yy = y0 - OFFSET;
                vc.addVertex(m, x0, yy, z1);
                vc.addVertex(m, x0, yy, z0);
                vc.addVertex(m, x1, yy, z0);
                vc.addVertex(m, x1, yy, z1);
            }
            case NORTH -> {
                float zz = z0 - OFFSET;
                vc.addVertex(m, x1, y0, zz);
                vc.addVertex(m, x0, y0, zz);
                vc.addVertex(m, x0, y1, zz);
                vc.addVertex(m, x1, y1, zz);
            }
            case SOUTH -> {
                float zz = z1 + OFFSET;
                vc.addVertex(m, x0, y0, zz);
                vc.addVertex(m, x1, y0, zz);
                vc.addVertex(m, x1, y1, zz);
                vc.addVertex(m, x0, y1, zz);
            }
            case WEST -> {
                float xx = x0 - OFFSET;
                vc.addVertex(m, xx, y0, z0);
                vc.addVertex(m, xx, y0, z1);
                vc.addVertex(m, xx, y1, z1);
                vc.addVertex(m, xx, y1, z0);
            }
            case EAST -> {
                float xx = x1 + OFFSET;
                vc.addVertex(m, xx, y0, z1);
                vc.addVertex(m, xx, y0, z0);
                vc.addVertex(m, xx, y1, z0);
                vc.addVertex(m, xx, y1, z1);
            }
        }
    }
}
