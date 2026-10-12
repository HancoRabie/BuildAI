package com.hanco.buildai.blueprint;

import net.minecraft.nbt.*;
import com.google.gson.JsonArray;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Converts four external formats into the same bounded blueprint; does not execute imported NBT. */
public final class SchematicReader {
    private SchematicReader() {}
    public static Blueprint read(Path path) throws IOException {
        if(Files.size(path)>16_000_000)throw new IOException("Input exceeds 16 MB");
        String lower=path.getFileName().toString().toLowerCase(Locale.ROOT);
        NbtCompound root;
        try(InputStream in=Files.newInputStream(path)){
            root=NbtIo.readCompressed(in,NbtSizeTracker.of(64L*1024*1024));
        }catch(RuntimeException ex){throw new IOException("NBT decoding failed: "+ex.getMessage(),ex);}
        try{
            if(lower.endsWith(".schem"))return sponge(root,lower);
            if(lower.endsWith(".schematic"))return legacy(root,lower);
            if(lower.endsWith(".litematic"))return litematic(root,lower);
            if(lower.endsWith(".nbt"))return structure(root,lower);
            throw new IOException("Supported extensions: .schem, .schematic, .litematic, .nbt");
        }catch(RuntimeException ex){throw new IOException("Bad schematic: "+ex.getMessage(),ex);}
    }
    private static Blueprint sponge(NbtCompound file,String name){
        NbtCompound c=file.contains("Schematic")?file.getCompound("Schematic"):file;
        int version=c.getInt("Version");
        if(version<1||version>3)throw new IllegalArgumentException("Unsupported Sponge version "+version);
        int w=c.getShort("Width")&65535,h=c.getShort("Height")&65535,d=c.getShort("Length")&65535;
        Blueprint b=new Blueprint(name,w,h,d);
        NbtCompound blocks=version==3?c.getCompound("Blocks"):c;
        NbtCompound palette=version==3?blocks.getCompound("Palette"):c.getCompound("Palette");
        if(palette.getKeys().isEmpty())throw new IllegalArgumentException("Missing Sponge palette");
        Map<Integer,String> states=new HashMap<>();
        for(String key:palette.getKeys())states.put(palette.getInt(key),key);
        byte[] data=blocks.getByteArray(version==3?"Data":"BlockData");
        int ptr=0;
        for(int y=0;y<h;y++)for(int z=0;z<d;z++)for(int x=0;x<w;x++){
            int v=0,shift=0,octet;
            do{
                if(ptr>=data.length||shift>28)throw new IllegalArgumentException("Varint overflow or truncated data");
                octet=data[ptr++]&255;
                v|=(octet&127)<<shift;shift+=7;
            }while((octet&128)!=0);
            if(!states.containsKey(v))throw new IllegalArgumentException("Unknown palette index "+v);
            b.set(x,y,z,states.get(v));
        }
        if(c.contains("BuildAICommandPlan")){
            JsonArray commands=com.google.gson.JsonParser.parseString(
                c.getString("BuildAICommandPlan")).getAsJsonArray();
            b.setCommands(CommandBlockPlan.decode(commands,b));
            b.warnings.add("Command-block scripts imported as INERT metadata; operator approval is required.");
        }
        b.warnings.add("Imported entity and block-entity payloads skipped for safety.");
        return b;
    }
    private static String stateFromNbt(NbtCompound c){
        String id=c.getString("Name");
        if(id.isBlank())throw new IllegalArgumentException("Palette entry has no Name");
        NbtCompound props=c.getCompound("Properties");
        List<String> entries=new ArrayList<>();
        for(String key:props.getKeys())entries.add(key+"="+props.getString(key));
        Collections.sort(entries);
        return id+(entries.isEmpty()?"":"["+String.join(",",entries)+"]");
    }
    private static int vector(NbtCompound c,String field){return c.getInt(field);}
    private static Blueprint litematic(NbtCompound root,String name) {
        NbtCompound regions=root.getCompound("Regions");
        if(regions.getKeys().isEmpty())throw new IllegalArgumentException("No regions");
        record Region(NbtCompound data,int x,int y,int z,int w,int h,int d){}
        List<Region> all=new ArrayList<>();
        int loX=Integer.MAX_VALUE,loY=Integer.MAX_VALUE,loZ=Integer.MAX_VALUE;
        int hiX=Integer.MIN_VALUE,hiY=Integer.MIN_VALUE,hiZ=Integer.MIN_VALUE;
        List<String> names=new ArrayList<>(regions.getKeys());Collections.sort(names);
        for(String key:names){
            NbtCompound c=regions.getCompound(key),size=c.getCompound("Size"),pos=c.getCompound("Position");
            int sx=vector(size,"x"),sy=vector(size,"y"),sz=vector(size,"z");
            int w=Math.abs(sx),h=Math.abs(sy),d=Math.abs(sz);
            if(w<1||h<1||d<1)throw new IllegalArgumentException("Invalid region");
            int ox=vector(pos,"x")+(sx<0?sx+1:0);
            int oy=vector(pos,"y")+(sy<0?sy+1:0);
            int oz=vector(pos,"z")+(sz<0?sz+1:0);
            all.add(new Region(c,ox,oy,oz,w,h,d));
            loX=Math.min(loX,ox);loY=Math.min(loY,oy);loZ=Math.min(loZ,oz);
            hiX=Math.max(hiX,ox+w);hiY=Math.max(hiY,oy+h);hiZ=Math.max(hiZ,oz+d);
        }
        Blueprint result=new Blueprint(name,hiX-loX,hiY-loY,hiZ-loZ);
        for(Region r:all){
            NbtList palette=r.data().getList("BlockStatePalette",NbtElement.COMPOUND_TYPE);
            List<String> states=new ArrayList<>();
            for(NbtElement elem:palette)states.add(stateFromNbt((NbtCompound)elem));
            if(states.isEmpty())throw new IllegalArgumentException("Empty region palette");
            long[] packed=r.data().getLongArray("BlockStates");
            int bits=Math.max(2,32-Integer.numberOfLeadingZeros(states.size()-1));
            long mask=(1L<<bits)-1;
            for(int y=0;y<r.h();y++)for(int z=0;z<r.d();z++)for(int x=0;x<r.w();x++){
                int i=x+r.w()*(z+r.d()*y);
                long start=(long)i*bits;
                int word=(int)(start>>>6),offset=(int)(start&63);
                if(word>=packed.length)throw new IllegalArgumentException("Truncated packed states");
                long value=packed[word]>>>offset;
                if(offset+bits>64){
                    if(word+1>=packed.length)throw new IllegalArgumentException("Truncated packed states");
                    value|=packed[word+1]<<(64-offset);
                }
                int p=(int)(value&mask);
                if(p>=states.size())throw new IllegalArgumentException("Invalid palette index");
                result.set(r.x()-loX+x,r.y()-loY+y,r.z()-loZ+z,states.get(p));
            }
        }
        result.warnings.add("Region metadata and block entities not restored.");
        return result;
    }
    private static Blueprint structure(NbtCompound root,String name){
        NbtList size=root.getList("size",NbtElement.INT_TYPE);
        if(size.size()!=3)throw new IllegalArgumentException("Missing structure dimensions");
        int w=((NbtInt)size.get(0)).intValue(),h=((NbtInt)size.get(1)).intValue(),d=((NbtInt)size.get(2)).intValue();
        Blueprint b=new Blueprint(name,w,h,d);
        NbtList palette=root.getList("palette",NbtElement.COMPOUND_TYPE);
        List<String> states=new ArrayList<>();
        for(NbtElement elem:palette)states.add(stateFromNbt((NbtCompound)elem));
        for(NbtElement elem:root.getList("blocks",NbtElement.COMPOUND_TYPE)){
            NbtCompound e=(NbtCompound)elem;
            NbtList pos=e.getList("pos",NbtElement.INT_TYPE);
            if(pos.size()!=3)throw new IllegalArgumentException("Bad structure block position");
            int p=e.getInt("state");
            if(p<0||p>=states.size())throw new IllegalArgumentException("Invalid palette index");
            b.set(((NbtInt)pos.get(0)).intValue(),((NbtInt)pos.get(1)).intValue(),((NbtInt)pos.get(2)).intValue(),states.get(p));
        }
        b.warnings.add("Imported block-entity NBT and entities stripped.");
        return b;
    }
    private static Blueprint legacy(NbtCompound root,String name){
        int w=root.getShort("Width")&65535,h=root.getShort("Height")&65535,d=root.getShort("Length")&65535;
        Blueprint b=new Blueprint(name,w,h,d);
        byte[] ids=root.getByteArray("Blocks"),meta=root.getByteArray("Data"),add=root.getByteArray("AddBlocks");
        if(ids.length<w*h*d||meta.length<w*h*d)throw new IllegalArgumentException("Incomplete legacy block arrays");
        for(int y=0;y<h;y++)for(int z=0;z<d;z++)for(int x=0;x<w;x++){
            int i=x+w*(z+d*y),block=ids[i]&255;
            if(add.length>i/2)block|=((i%2==0?add[i/2]&15:(add[i/2]>>>4)&15)<<8);
            String state=LegacyBlocks.convert(block,meta[i]&15);
            if(state==null)throw new IllegalArgumentException("Unsupported legacy block "+block+":"+(meta[i]&15)+" at "+x+","+y+","+z);
            b.set(x,y,z,state);
        }
        b.warnings.add("Legacy numeric mappings are partial. Unrecognized IDs are rejected.");
        return b;
    }
}
