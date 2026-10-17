package com.darkrifts;

import com.darkrifts.block.RiftBlock;
import com.darkrifts.item.RiftStroke;
import com.darkrifts.world.MirrorCopier;
import com.darkrifts.world.RiftTeleporter;
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
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        RiftStroke.clearAll();
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
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("darkrifts")
                .requires(source -> source.hasPermission(2))
                // /darkrifts open  -> permanent rift 3 blocks in front of you
                .then(Commands.literal("open").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    ServerLevel level = player.serverLevel();
                    ResourceLocation target = DarkDimensions.random(level.getRandom(), level.dimension());
                    BlockPos base = player.blockPosition().relative(player.getDirection(), 3);
                    for (int i = 0; i < 4; i++) {
                        RiftBlock.place(level, base.above(i), target, false, 0L);
                    }
                    ctx.getSource().sendSuccess(() -> Component.literal("Opened a rift to the mirror realm"), false);
                    return 1;
                }))
                // /darkrifts visit -> jump straight into the mirror realm
                .then(Commands.literal("visit").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    RiftTeleporter.travelTo(player, player.serverLevel(), DarkDimensions.MIRROR, player.blockPosition());
                    return 1;
                }))
                // /darkrifts home -> return to where you entered the dark realm
                .then(Commands.literal("home").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    RiftTeleporter.goHome(player, player.serverLevel());
                    return 1;
                })));
    }
}
