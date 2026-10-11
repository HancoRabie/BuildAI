package com.hanco.buildai.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server acknowledgement. The client MUST wait for this before arming placement. */
public record BlueprintReadyPayload(boolean accepted, String message) implements CustomPayload {
    public static final Id<BlueprintReadyPayload> ID =
        new Id<>(Identifier.of("buildai","blueprint_ready"));
    public static final PacketCodec<RegistryByteBuf,BlueprintReadyPayload> CODEC=PacketCodec.of(
        (payload,buffer)->{
            buffer.writeBoolean(payload.accepted());
            buffer.writeString(payload.message(),256);
        },buffer->new BlueprintReadyPayload(buffer.readBoolean(),buffer.readString(256)));
    @Override public Id<? extends CustomPayload> getId(){return ID;}
}
