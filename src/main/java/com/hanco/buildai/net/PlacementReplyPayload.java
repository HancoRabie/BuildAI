package com.hanco.buildai.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Confirmed placement review state, emitted only after authoritative server validation. */
public record PlacementReplyPayload(boolean accepted, boolean locked, String message, BlockPos origin)
    implements CustomPayload {
    public static final Id<PlacementReplyPayload> ID =
        new Id<>(Identifier.of("buildai","placement_reply"));
    public static final PacketCodec<RegistryByteBuf,PlacementReplyPayload> CODEC = PacketCodec.of(
        (p,b)->{
            b.writeBoolean(p.accepted());b.writeBoolean(p.locked());
            b.writeString(p.message(),256);b.writeBlockPos(p.origin());
        },
        b->new PlacementReplyPayload(b.readBoolean(),b.readBoolean(),b.readString(256),b.readBlockPos()));
    @Override public Id<? extends CustomPayload> getId(){return ID;}
}
