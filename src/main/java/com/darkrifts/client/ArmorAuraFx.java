package com.darkrifts.client;

import com.darkrifts.DarkRifts;
import com.darkrifts.item.RiftArmorItem;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Vector3f;

/**
 * Wearing the full Rift set is visible: void matter orbits the feet, light climbs off the shoulder spikes, the core
 * in the chest pulses, and a fast runner leaves a trail of torn space. Client-side particles only (no packets).
 */
@EventBusSubscriber(modid = DarkRifts.MODID, value = Dist.CLIENT)
public final class ArmorAuraFx {
    private static final DustParticleOptions VIOLET = new DustParticleOptions(new Vector3f(0.7F, 0.25F, 1.0F), 1.0F);
    private static final DustParticleOptions HOT = new DustParticleOptions(new Vector3f(1.0F, 0.75F, 1.0F), 0.8F);

    private ArmorAuraFx() {}

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused() || mc.player == null) {
            return;
        }
        RandomSource rnd = mc.level.getRandom();
        long gt = mc.level.getGameTime();
        for (Player p : mc.level.players()) {
            if (p.distanceToSqr(mc.player) > 48.0 * 48.0 || !RiftArmorItem.hasFullSet(p)) {
                continue;
            }
            boolean self = p == mc.player && mc.options.getCameraType().isFirstPerson();
            double yaw = Math.toRadians(p.yBodyRot);
            double t = gt * 0.18;
            // orbit around the feet
            for (int i = 0; i < 2; i++) {
                double a = t + i * Math.PI;
                mc.level.addParticle(ParticleTypes.REVERSE_PORTAL, p.getX() + Math.cos(a) * 0.9, p.getY() + 0.1,
                        p.getZ() + Math.sin(a) * 0.9, 0.0, 0.03, 0.0);
            }
            if (self) {
                continue; // keep the first person view clean
            }
            // light climbing off the shoulders (they stick out to the sides of the body)
            if (gt % 2 == 0) {
                double side = rnd.nextBoolean() ? 1.0 : -1.0;
                double sx = p.getX() + Math.cos(yaw) * 0.55 * side;
                double sz = p.getZ() + Math.sin(yaw) * 0.55 * side;
                mc.level.addParticle(ParticleTypes.END_ROD, sx, p.getY() + p.getBbHeight() * 0.95, sz, 0.0, 0.05 + rnd.nextDouble() * 0.04, 0.0);
            }
            // the core in the chest pulsing
            if (gt % 6 == 0) {
                double fx = p.getX() - Math.sin(yaw) * 0.3;
                double fz = p.getZ() + Math.cos(yaw) * 0.3;
                mc.level.addParticle(HOT, fx, p.getY() + p.getBbHeight() * 0.7, fz, 0.0, 0.0, 0.0);
            }
            if (rnd.nextInt(5) == 0) {
                mc.level.addParticle(VIOLET, p.getX() + (rnd.nextDouble() - 0.5) * 0.8, p.getY() + rnd.nextDouble() * p.getBbHeight(),
                        p.getZ() + (rnd.nextDouble() - 0.5) * 0.8, 0.0, 0.0, 0.0);
            }
            if (rnd.nextInt(20) == 0) {
                mc.level.addParticle(ParticleTypes.SCULK_SOUL, p.getX(), p.getY() + 1.0, p.getZ(), 0.0, 0.04, 0.0);
            }
            // trail of torn space behind a fast runner
            double speed = p.getDeltaMovement().horizontalDistanceSqr();
            if (speed > 0.03) {
                for (int i = 0; i < 3; i++) {
                    mc.level.addParticle(ParticleTypes.REVERSE_PORTAL, p.getX() + (rnd.nextDouble() - 0.5) * 0.6,
                            p.getY() + rnd.nextDouble() * 1.8, p.getZ() + (rnd.nextDouble() - 0.5) * 0.6,
                            -p.getDeltaMovement().x * 0.3, 0.0, -p.getDeltaMovement().z * 0.3);
                }
            }
        }
    }
}
