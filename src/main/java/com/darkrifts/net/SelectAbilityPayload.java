package com.darkrifts.net;

import com.darkrifts.DarkRifts;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server: the player picked an ability in the radial menu. */
public record SelectAbilityPayload(int id) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SelectAbilityPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(DarkRifts.MODID, "select_ability"));
    public static final StreamCodec<ByteBuf, SelectAbilityPayload> CODEC =
            ByteBufCodecs.VAR_INT.map(SelectAbilityPayload::new, SelectAbilityPayload::id);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
