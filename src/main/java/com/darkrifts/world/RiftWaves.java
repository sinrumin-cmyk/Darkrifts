package com.darkrifts.world;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The violet shockwave that rolls out of a freshly torn rift: an expanding, slowly spinning shell of violet dust
 * (a second, fainter shell follows a few ticks behind), with portal dust sucked into the centre.
 * Driven from the server tick, so it needs no custom networking.
 */
public final class RiftWaves {
    private static final List<Wave> ACTIVE = new ArrayList<>();
    private static final DustParticleOptions VIOLET = new DustParticleOptions(new Vector3f(0.58F, 0.16F, 1.0F), 1.7F);
    private static final DustParticleOptions PALE = new DustParticleOptions(new Vector3f(0.85F, 0.65F, 1.0F), 1.1F);
    private static final int DURATION = 14;
    private static final int POINTS = 46;

    private RiftWaves() {}

    private static final class Wave {
        final ServerLevel level;
        final Vec3 center;
        final double maxRadius;
        int age;

        Wave(ServerLevel level, Vec3 center, double maxRadius) {
            this.level = level;
            this.center = center;
            this.maxRadius = maxRadius;
        }
    }

    public static void start(ServerLevel level, Vec3 center, double maxRadius) {
        ACTIVE.add(new Wave(level, center, maxRadius));
        level.sendParticles(ParticleTypes.FLASH, center.x, center.y, center.z, 1, 0, 0, 0, 0);
    }

    public static void clear() {
        ACTIVE.clear();
    }

    public static void tick() {
        Iterator<Wave> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Wave w = it.next();
            w.age++;
            if (w.age > DURATION + 4) {
                it.remove();
                continue;
            }
            if (w.age <= DURATION) {
                shell(w, w.age, VIOLET, 1);
            }
            if (w.age > 3 && w.age - 3 <= DURATION) {
                shell(w, w.age - 3, PALE, 2);
            }
            // matter being pulled into the tear
            double a = w.level.getRandom().nextDouble() * Math.PI * 2.0;
            double r = w.maxRadius * (1.0 - w.age / (double) DURATION);
            w.level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                    w.center.x + Math.cos(a) * r, w.center.y, w.center.z + Math.sin(a) * r, 2, 0.2, 0.6, 0.2, 0.05);
        }
    }

    private static void shell(Wave w, int age, DustParticleOptions dust, int stride) {
        double t = Math.min(1.0, age / (double) DURATION);
        double radius = w.maxRadius * (1.0 - Math.pow(1.0 - t, 3.0)); // fast start, slow finish
        double golden = Math.PI * (3.0 - Math.sqrt(5.0));
        for (int i = 0; i < POINTS; i += stride) {
            double y = 1.0 - 2.0 * (i + 0.5) / POINTS;
            double ring = Math.sqrt(1.0 - y * y);
            double theta = i * golden + age * 0.35;
            double x = w.center.x + Math.cos(theta) * ring * radius;
            double yy = w.center.y + y * radius;
            double z = w.center.z + Math.sin(theta) * ring * radius;
            w.level.sendParticles(dust, x, yy, z, 1, 0, 0, 0, 0);
        }
        if (age % 3 == 0) {
            // a bright flat ring on the ground plane through the rift
            for (int i = 0; i < 24; i++) {
                double th = i * (Math.PI * 2.0 / 24.0);
                w.level.sendParticles(ParticleTypes.END_ROD,
                        w.center.x + Math.cos(th) * radius, w.center.y, w.center.z + Math.sin(th) * radius,
                        1, 0, 0, 0, 0);
            }
        }
    }
}
