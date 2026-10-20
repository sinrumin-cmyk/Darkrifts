package com.darkrifts.net;

import com.darkrifts.DarkRifts;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server -> client: camera shake and a coloured screen flash (rift births, rift jumps, wards, boss moments). */
public record FxPayload(float shake, float flash, int rgb) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<FxPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(DarkRifts.MODID, "fx"));
    public static final StreamCodec<ByteBuf, FxPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeFloat(p.shake());
                buf.writeFloat(p.flash());
                buf.writeInt(p.rgb());
            },
            buf -> new FxPayload(buf.readFloat(), buf.readFloat(), buf.readInt()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
