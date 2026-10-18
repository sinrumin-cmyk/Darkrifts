package com.darkrifts.world;

import com.darkrifts.DarkDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;

/**
 * The seal: a sealed player lives in the void prison dimension for good. Dying there respawns them there
 * (forced respawn point + a safety net on respawn/login), and rifts refuse to carry them out.
 * An operator can lift it with /darkrifts unseal.
 */
public final class Seal {
    public static final String TAG = "darkrifts_sealed";
    /** UUID of the player who sealed this one: only they can lift the seal. */
    public static final String SEALER_TAG = "darkrifts_sealer";
    /** Where sealed players stand: the top of the 4-block void floor. */
    public static final BlockPos SPAWN = new BlockPos(0, 4, 0);

    private Seal() {}

    public static boolean isSealed(Entity entity) {
        return entity.getPersistentData().getBoolean(TAG);
    }

    /** Pushes the target into the void prison. {@code caster} may be null (commands). */
    public static void seal(ServerLevel from, Entity target, ServerPlayer caster) {
        MinecraftServer server = from.getServer();
        ServerLevel dest = server.getLevel(DarkDimensions.voidKey());
        if (dest == null) {
            if (caster != null) {
                caster.displayClientMessage(Component.translatable("message.darkrifts.dimension_missing",
                        DarkDimensions.VOID.toString()), true);
            }
            return;
        }
        if (target instanceof ServerPlayer player) {
            player.getPersistentData().putBoolean(TAG, true);
            if (caster != null) {
                player.getPersistentData().putUUID(SEALER_TAG, caster.getUUID());
            } else {
                player.getPersistentData().remove(SEALER_TAG);
            }
            player.setRespawnPosition(DarkDimensions.voidKey(), SPAWN, 0.0F, true, false);
            player.displayClientMessage(Component.translatable("message.darkrifts.you_are_sealed"), false);
        } else if (target instanceof Mob mob) {
            mob.setPersistenceRequired();
        }
        double x = 0.5 + (from.getRandom().nextDouble() - 0.5) * 12.0;
        double z = 0.5 + (from.getRandom().nextDouble() - 0.5) * 12.0;
        move(target, dest, new Vec3(x, SPAWN.getY(), z));
        if (caster != null) {
            caster.displayClientMessage(Component.translatable("message.darkrifts.sealed", target.getDisplayName()), true);
        }
    }

    /** Safety net: put a sealed player back into the prison if anything moved them out. */
    public static void enforce(ServerPlayer player) {
        if (!isSealed(player) || DarkDimensions.isVoid(player.level().dimension())) {
            return;
        }
        ServerLevel dest = player.getServer().getLevel(DarkDimensions.voidKey());
        if (dest != null) {
            move(player, dest, new Vec3(SPAWN.getX() + 0.5, SPAWN.getY(), SPAWN.getZ() + 0.5));
        }
    }

    /** Only the player who sealed the target may unseal it (operators only for seals made by commands). */
    public static boolean canUnseal(ServerPlayer target, ServerPlayer by) {
        if (!target.getPersistentData().hasUUID(SEALER_TAG)) {
            return by.hasPermissions(2);
        }
        return target.getPersistentData().getUUID(SEALER_TAG).equals(by.getUUID());
    }

    public static void unseal(ServerPlayer player) {
        player.getPersistentData().remove(TAG);
        player.getPersistentData().remove(SEALER_TAG);
        player.setRespawnPosition(Level.OVERWORLD, null, 0.0F, false, false);
        if (DarkDimensions.isVoid(player.level().dimension())) {
            RiftTeleporter.goHome(player, player.serverLevel());
        }
        player.displayClientMessage(Component.translatable("message.darkrifts.seal_removed"), false);
    }

    private static void move(Entity entity, ServerLevel dest, Vec3 pos) {
        dest.getChunk(Mth.floor(pos.x) >> 4, Mth.floor(pos.z) >> 4);
        entity.changeDimension(new DimensionTransition(dest, pos, Vec3.ZERO, entity.getYRot(), 0.0F,
                e -> e.setPortalCooldown(60)));
    }
}
