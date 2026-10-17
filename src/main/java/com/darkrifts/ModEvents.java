package com.darkrifts;

import com.darkrifts.block.RiftBlock;
import com.darkrifts.item.RiftStroke;
import com.darkrifts.world.RiftTeleporter;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
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
                    ctx.getSource().sendSuccess(() -> Component.literal("Opened a rift to " + target), false);
                    return 1;
                }))
                // /darkrifts visit <1-12> -> jump straight into that dark dimension
                .then(Commands.literal("visit")
                        .then(Commands.argument("n", IntegerArgumentType.integer(1, DarkDimensions.COUNT))
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    int n = IntegerArgumentType.getInteger(ctx, "n");
                                    RiftTeleporter.travelTo(player, player.serverLevel(), DarkDimensions.id(n - 1), player.blockPosition());
                                    return 1;
                                })))
                // /darkrifts home -> return to where you entered the dark realm
                .then(Commands.literal("home").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    RiftTeleporter.goHome(player, player.serverLevel());
                    return 1;
                })));
    }
}
