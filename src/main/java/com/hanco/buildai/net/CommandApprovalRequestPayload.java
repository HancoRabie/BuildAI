package com.hanco.buildai.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** UI intent only. The server recomputes OP permission, world/phase and XYZ. */
public record CommandApprovalRequestPayload(String selection) implements CustomPayload {
    public CommandApprovalRequestPayload {
        if(selection==null || selection.length()>512)
            throw new IllegalArgumentException("Invalid approval selection");
    }
    public static final CustomPayload.Id<CommandApprovalRequestPayload> ID=
        new CustomPayload.Id<>(Identifier.of("buildai","command_approval_request"));
    public static final PacketCodec<RegistryByteBuf,CommandApprovalRequestPayload> CODEC=
        PacketCodec.of((p,b)->b.writeString(p.selection(),512),
            b->new CommandApprovalRequestPayload(b.readString(512)));
    @Override public CustomPayload.Id<? extends CustomPayload> getId(){return ID;}
}
