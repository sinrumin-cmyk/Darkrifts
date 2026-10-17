package com.darkrifts.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side record of the line a player is currently drawing with the Rift Blade.
 * Every tick the look direction is projected onto a sphere around the point where the stroke started,
 * so the drawn line stays fixed in the world even if the player walks around.
 */
public final class RiftStroke {
    private static final Map<UUID, RiftStroke> ACTIVE = new HashMap<>();
    private static final int MAX_POINTS = 400;

    private final Vec3 anchor;
    private final List<Vec3> points = new ArrayList<>();
    private long lastTick;
    private double length;

    private RiftStroke(Vec3 anchor, long tick) {
        this.anchor = anchor;
        this.lastTick = tick;
    }

    /** Continues the player's stroke (or starts a new one). Returns the new point if one was added, else null. */
    public static Vec3 update(ServerPlayer player, long gameTime, double radius) {
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
        if (!stroke.points.isEmpty()) {
            Vec3 last = stroke.points.get(stroke.points.size() - 1);
            double d = point.distanceTo(last);
            if (d < 0.35) {
                return null;
            }
            stroke.length += d;
        }
        stroke.points.add(point);
        return point;
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

    public List<Vec3> points() {
        return points;
    }

    public double length() {
        return length;
    }
}
