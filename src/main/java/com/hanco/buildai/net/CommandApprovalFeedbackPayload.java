package com.hanco.buildai.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Full approval errors appear inside the Minecraft review screen, not behind it. */
public record CommandApprovalFeedbackPayload(boolean accepted,String message) implements CustomPayload {
    public CommandApprovalFeedbackPayload {
        if(message==null)message="";
        if(message.length()>900)message=message.substring(0,900);
    }
    public static final CustomPayload.Id<CommandApprovalFeedbackPayload> ID=
        new CustomPayload.Id<>(Identifier.of("buildai","command_approval_feedback"));
    public static final PacketCodec<RegistryByteBuf,CommandApprovalFeedbackPayload> CODEC=
        PacketCodec.of((p,b)->{b.writeBoolean(p.accepted());b.writeString(p.message(),900);},
            b->new CommandApprovalFeedbackPayload(b.readBoolean(),b.readString(900)));
    @Override public CustomPayload.Id<? extends CustomPayload> getId(){return ID;}
}
