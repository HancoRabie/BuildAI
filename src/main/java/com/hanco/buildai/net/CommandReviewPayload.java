package com.hanco.buildai.net;

import com.hanco.buildai.blueprint.CommandBlockPlan;
import com.hanco.buildai.build.ConstructionProject;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/** Authoritative, bounded server snapshot: actual command plan, no client guesswork. */
public record CommandReviewPayload(String projectName,BlockPos origin,List<Item> entries)
    implements CustomPayload {
    public record Item(int number,BlockPos local,String blockState,String command,
                       boolean auto,boolean trackOutput){}
    public CommandReviewPayload{
        projectName=ProjectStatusPayload.bounded(projectName,100);
        entries=List.copyOf(entries);
        if(entries.size()>CommandBlockPlan.MAX_COMMANDS)
            throw new IllegalArgumentException("Too many command review entries");
        if(origin==null)throw new IllegalArgumentException("Missing locked origin");
        for(Item entry:entries){
            if(entry.number()<1||entry.number()>entries.size()||
                entry.command().length()>CommandBlockPlan.MAX_COMMAND_LENGTH||
                entry.blockState().length()>200)
                throw new IllegalArgumentException("Invalid command review entry");
        }
    }

    public static CommandReviewPayload of(ConstructionProject project){
        List<Item> items=new ArrayList<>();
        int index=0;
        for(CommandBlockPlan.Entry entry:project.blueprint.commands())
            items.add(new Item(++index,new BlockPos(entry.x(),entry.y(),entry.z()),
                project.blueprint.get(entry.x(),entry.y(),entry.z()),
                entry.command(),entry.auto(),entry.trackOutput()));
        return new CommandReviewPayload(project.blueprint.name,project.origin,items);
    }

    public static final CustomPayload.Id<CommandReviewPayload> ID=
        new CustomPayload.Id<>(Identifier.of("buildai","command_review"));
    public static final PacketCodec<RegistryByteBuf,CommandReviewPayload> CODEC=PacketCodec.of(
        (p,b)->{
            b.writeString(p.projectName(),120);
            b.writeBlockPos(p.origin());
            b.writeVarInt(p.entries().size());
            for(Item i:p.entries()){
                b.writeVarInt(i.number());b.writeBlockPos(i.local());
                b.writeString(i.blockState(),220);b.writeString(i.command(),800);
                b.writeBoolean(i.auto());b.writeBoolean(i.trackOutput());
            }
        },
        b->{
            String title=b.readString(120);
            BlockPos origin=b.readBlockPos();
            int size=b.readVarInt();
            if(size<0||size>CommandBlockPlan.MAX_COMMANDS)
                throw new IllegalArgumentException("Invalid command review size");
            List<Item> entries=new ArrayList<>();
            for(int i=0;i<size;i++)
                entries.add(new Item(b.readVarInt(),b.readBlockPos(),
                    b.readString(220),b.readString(800),
                    b.readBoolean(),b.readBoolean()));
            return new CommandReviewPayload(title,origin,entries);
        });
    @Override public CustomPayload.Id<? extends CustomPayload> getId(){return ID;}
}
