package com.hanco.buildai.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Server-to-client status: server remains authoritative for material and construction counts. */
public record ProjectStatusPayload(String title,String phase,int placed,int total,int supplied,int required,
    String materials,BlockPos chest) implements CustomPayload {
    public static final CustomPayload.Id<ProjectStatusPayload> ID=
        new CustomPayload.Id<>(Identifier.of("buildai","project_status"));
    public static final PacketCodec<RegistryByteBuf,ProjectStatusPayload> CODEC=PacketCodec.of(
        (p,b)->{
            b.writeString(p.title(),160);b.writeString(p.phase(),40);
            b.writeVarInt(p.placed());b.writeVarInt(p.total());
            b.writeVarInt(p.supplied());b.writeVarInt(p.required());
            b.writeString(p.materials(),2048);b.writeBlockPos(p.chest());
        },
        b->new ProjectStatusPayload(b.readString(160),b.readString(40),
            b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readString(2048),b.readBlockPos()));
    @Override public CustomPayload.Id<? extends CustomPayload> getId(){return ID;}
}
