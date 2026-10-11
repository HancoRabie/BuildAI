package com.hanco.buildai.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/**
 * A bounded client-only cinematic cue. Never sends dropped items or entities.
 * PREPARE masks batched server excavation; PULL plays AFTER every cell is AIR.
 */
public record CinematicExcavationPayload(int stage, BlockPos origin, int width, int height,
                                         int length, List<BlockPos> samples, int durationTicks)
    implements CustomPayload {
    public static final int PREPARE=0, PULL=1, STOP=2;
    public static final Id<CinematicExcavationPayload> ID=
        new Id<>(Identifier.of("buildai","cinematic_excavation"));
    public static final PacketCodec<RegistryByteBuf,CinematicExcavationPayload> CODEC=
        PacketCodec.of((p,b)->{
            b.writeVarInt(p.stage());b.writeBlockPos(p.origin());
            b.writeVarInt(p.width());b.writeVarInt(p.height());b.writeVarInt(p.length());
            b.writeVarInt(p.durationTicks());
            int count=Math.min(256,p.samples().size());
            b.writeVarInt(count);
            for(int i=0;i<count;i++)b.writeBlockPos(p.samples().get(i));
        },b->{
            int stage=b.readVarInt();BlockPos pos=b.readBlockPos();
            int width=b.readVarInt(),height=b.readVarInt(),length=b.readVarInt();
            int duration=b.readVarInt(),count=b.readVarInt();
            if(stage<0||stage>2||width<0||width>512||height<0||height>512||
                length<0||length>512||duration<0||duration>200||count<0||count>256)
                throw new IllegalArgumentException("Invalid excavation FX packet");
            List<BlockPos> samples=new ArrayList<>(count);
            for(int i=0;i<count;i++)samples.add(b.readBlockPos());
            return new CinematicExcavationPayload(stage,pos,width,height,length,
                List.copyOf(samples),duration);
        });
    @Override public Id<? extends CustomPayload> getId(){return ID;}
}
