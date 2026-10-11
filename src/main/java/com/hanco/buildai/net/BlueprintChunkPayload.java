package com.hanco.buildai.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Up to 20KiB per chunk; validated and reassembled on the server. */
public record BlueprintChunkPayload(String uploadId,int index,int count,int surfaceLayer,byte[] bytes) implements CustomPayload {
    public static final CustomPayload.Id<BlueprintChunkPayload> ID =
        new CustomPayload.Id<>(Identifier.of("buildai","blueprint_chunk"));
    public static final PacketCodec<RegistryByteBuf,BlueprintChunkPayload> CODEC=PacketCodec.of(
        (p,b)->{b.writeString(p.uploadId(),48);b.writeVarInt(p.index());b.writeVarInt(p.count());
                 b.writeVarInt(p.surfaceLayer());b.writeByteArray(p.bytes());},
        b->new BlueprintChunkPayload(b.readString(48),b.readVarInt(),b.readVarInt(),
                                     b.readVarInt(),b.readByteArray(22000)));
    @Override public CustomPayload.Id<? extends CustomPayload> getId(){return ID;}
}
