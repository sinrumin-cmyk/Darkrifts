package com.darkrifts;

import com.darkrifts.block.RiftBlock;
import com.darkrifts.item.RiftStroke;
import com.darkrifts.world.MirrorCopier;
import com.darkrifts.world.RiftTeleporter;
import com.darkrifts.world.RiftWaves;
import com.darkrifts.world.Seal;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.TickTask;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

@EventBusSubscriber(modid = DarkRifts.MODID)
public final class ModEvents {
    private ModEvents() {}

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        RiftStroke.discard(event.getEntity().getUUID());
        com.darkrifts.ability.Abilities.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (event.isWasDeath() && Seal.isSealed(event.getOriginal())) {
            event.getEntity().getPersistentData().putBoolean(Seal.TAG, true);
            if (event.getOriginal().getPersistentData().hasUUID(Seal.SEALER_TAG)) {
                event.getEntity().getPersistentData().putUUID(Seal.SEALER_TAG,
                        event.getOriginal().getPersistentData().getUUID(Seal.SEALER_TAG));
            }
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Seal.enforce(player);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Seal.enforce(player);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        RiftStroke.clearAll();
        RiftWaves.clear();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        RiftWaves.tick();
    }

    /**
     * No mobs on top of the (ruined) structures of the mirror: villagers, golems, pillagers, witches and
     * everything else that structures normally spawn.
     */
    @SubscribeEvent
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (!(event.getLevel().getLevel() instanceof ServerLevel level) || !DarkDimensions.isDark(level.dimension())) {
            return;
        }
        MobSpawnType type = event.getSpawnType();
        if (type == MobSpawnType.STRUCTURE) {
            event.setSpawnCancelled(true);
        } else if (type == MobSpawnType.NATURAL
                && !level.structureManager().getAllStructuresAt(BlockPos.containing(event.getX(), event.getY(), event.getZ())).isEmpty()) {
            event.setSpawnCancelled(true);
        }
    }

    /** Removes mobs that structure generation already placed into a fresh mirror chunk. */
    private static void purgeStructureMobs(ServerLevel level, ChunkPos cp) {
        if (level.structureManager().getAllStructuresAt(cp.getMiddleBlockPosition(64)).isEmpty()) {
            return;
        }
        AABB box = new AABB(cp.getMinBlockX(), level.getMinBuildHeight(), cp.getMinBlockZ(),
                cp.getMaxBlockX() + 1, level.getMaxBuildHeight(), cp.getMaxBlockZ() + 1);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, box)) {
            if (!mob.isPersistenceRequired() && !mob.hasCustomName()) {
                mob.discard();
            }
        }
    }

    /** A brand-new chunk of the mirror: copy (ruined) player builds from the overworld chunk, if it is loaded. */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!event.isNewChunk() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!DarkDimensions.isDark(level.dimension())) {
            return;
        }
        ChunkPos pos = event.getChunk().getPos();
        level.getServer().execute(() -> MirrorCopier.copy(level, pos));
        // Structure mobs appear a moment after the chunk does, so look again a bit later (twice).
        var server = level.getServer();
        server.tell(new TickTask(server.getTickCount() + 10, () -> purgeStructureMobs(level, pos)));
        server.tell(new TickTask(server.getTickCount() + 100, () -> purgeStructureMobs(level, pos)));
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("darkrifts")
                // /darkrifts open  -> permanent rift 3 blocks in front of you
                .then(Commands.literal("open").requires(source -> source.hasPermission(2)).executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    ServerLevel level = player.serverLevel();
                    ResourceLocation target = DarkDimensions.random(level.getRandom(), level.dimension());
                    BlockPos base = player.blockPosition().relative(player.getDirection(), 3);
                    RiftBlock.placeColumn(level, base, 5, target, false, 0L, level.getGameTime(), level.getRandom());
                    ctx.getSource().sendSuccess(() -> Component.literal("Opened a rift to the mirror realm"), false);
                    return 1;
                }))
                // /darkrifts visit -> jump straight into the mirror realm
                .then(Commands.literal("visit").requires(source -> source.hasPermission(2)).executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    RiftTeleporter.travelTo(player, player.serverLevel(), DarkDimensions.MIRROR, player.blockPosition());
                    return 1;
                }))
                // /darkrifts seal <player> -> seal a player in the void prison
                .then(Commands.literal("seal").requires(source -> source.hasPermission(2)).then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            Seal.seal(target.serverLevel(), target, null);
                            return 1;
                        })))
                // /darkrifts unseal <player> -> only the one who sealed that player can release them
                .then(Commands.literal("unseal").then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> {
                            ServerPlayer by = ctx.getSource().getPlayerOrException();
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            if (!Seal.isSealed(target)) {
                                ctx.getSource().sendFailure(Component.translatable("message.darkrifts.not_sealed"));
                                return 0;
                            }
                            if (!Seal.canUnseal(target, by)) {
                                ctx.getSource().sendFailure(Component.translatable("message.darkrifts.not_your_seal"));
                                return 0;
                            }
                            Seal.unseal(target);
                            return 1;
                        })))
                // /darkrifts home -> return to where you entered the dark realm
                .then(Commands.literal("home").requires(source -> source.hasPermission(2)).executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    RiftTeleporter.goHome(player, player.serverLevel());
                    return 1;
                })));
    }
}
