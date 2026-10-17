package com.darkrifts.item;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side record of the rift a player is drawing right now with the Rift Blade.
 * Every tick the look direction is projected onto a sphere around the point where the stroke started, so the line
 * stays fixed in the world even if the player walks around. The rift blocks are placed live, while the player holds
 * the button - this object remembers where the stroke has been and what it already placed.
 */
public final class RiftStroke {
    private static final Map<UUID, RiftStroke> ACTIVE = new HashMap<>();
    private static final int MAX_POINTS = 600;

    private final Vec3 anchor;
    private final List<Vec3> points = new ArrayList<>();
    private long lastTick;

    /** Everything the stroke has placed so far (used to avoid duplicates and to enforce the block cap). */
    public final Set<BlockPos> cells = new HashSet<>();
    /** Length of the line drawn so far (blocks) and the cells that own pieces of it. */
    public double length;
    public final Set<BlockPos> owners = new HashSet<>();
    public int placed;
    public int damaged;
    /** Chosen when the first block is placed; all blocks of one rift lead to the same place and collapse together. */
    @Nullable
    public ResourceLocation target;
    public long expireAt;
    public boolean started;

    private RiftStroke(Vec3 anchor, long tick) {
        this.anchor = anchor;
        this.lastTick = tick;
    }

    /** Continues the player's stroke (or starts a new one). Returns the stroke if a new point was added, else null. */
    @Nullable
    public static RiftStroke update(ServerPlayer player, long gameTime, double radius) {
        RiftStroke stroke = ACTIVE.get(player.getUUID());
        if (stroke == null || gameTime - stroke.lastTick > 4) {
            stroke = new RiftStroke(player.getEyePosition(), gameTime);
            ACTIVE.put(player.getUUID(), stroke);
        }
        stroke.lastTick = gameTime;
        if (stroke.points.size() >= MAX_POINTS) {
            return null;
        }
        Vec3 point = stroke.anchor.add(player.getLookAngle().scale(radius));
        if (!stroke.points.isEmpty() && point.distanceTo(stroke.points.get(stroke.points.size() - 1)) < 0.35) {
            return null;
        }
        stroke.points.add(point);
        return stroke;
    }

    public static RiftStroke finish(ServerPlayer player) {
        return ACTIVE.remove(player.getUUID());
    }

    public static void discard(UUID id) {
        ACTIVE.remove(id);
    }

    public static void clearAll() {
        ACTIVE.clear();
    }

    public Vec3 lastPoint() {
        return points.get(points.size() - 1);
    }

    /** The point before the latest one, or the latest one itself if the stroke has just started. */
    public Vec3 previousPoint() {
        return points.size() >= 2 ? points.get(points.size() - 2) : points.get(points.size() - 1);
    }

    /** Average of all points - roughly the middle of the rift. */
    public Vec3 center() {
        Vec3 sum = Vec3.ZERO;
        for (Vec3 p : points) {
            sum = sum.add(p);
        }
        return points.isEmpty() ? anchor : sum.scale(1.0 / points.size());
    }

    public int size() {
        return points.size();
    }
}
