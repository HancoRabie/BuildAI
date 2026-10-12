package com.hanco.buildai.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Placement requests are suggestions only; server independently validates target and permissions. */
public record PlacePayload(BlockPos pos,int quarterTurns) implements CustomPayload {
    public static final CustomPayload.Id<PlacePayload> ID=new CustomPayload.Id<>(Identifier.of("buildai","select_horizontal"));
    public static final PacketCodec<RegistryByteBuf,PlacePayload> CODEC=PacketCodec.of(
        (p,b)->{b.writeBlockPos(p.pos());b.writeVarInt(p.quarterTurns());},
        b->new PlacePayload(b.readBlockPos(),b.readVarInt()));
    @Override public CustomPayload.Id<? extends CustomPayload> getId(){return ID;}
}
