package com.hanco.buildai.blueprint;

import net.minecraft.nbt.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Export AI-generated geometry to a genuine GZip-compressed Sponge v3 .schem file. */
public final class SchematicWriter {
    private SchematicWriter() {}
    public static Path write(Blueprint blueprint,Path output) throws IOException {
        BlockStates.validate(blueprint);
        if(!output.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".schem"))
            throw new IOException("Export filename must end in .schem");
        NbtCompound schematic=new NbtCompound();
        schematic.putInt("Version",3);
        schematic.putInt("DataVersion",3955); // Minecraft Java 1.21.1
        schematic.putShort("Width",(short)blueprint.width);
        schematic.putShort("Height",(short)blueprint.height);
        schematic.putShort("Length",(short)blueprint.length);
        schematic.putIntArray("Offset",new int[]{0,0,0});
        NbtCompound blocks=new NbtCompound();
        NbtCompound palette=new NbtCompound();
        Map<String,Integer> indices=new LinkedHashMap<>();
        ByteArrayOutputStream encoded=new ByteArrayOutputStream();
        for(int y=0;y<blueprint.height;y++)for(int z=0;z<blueprint.length;z++)for(int x=0;x<blueprint.width;x++) {
            String state=blueprint.get(x,y,z);
            Integer i=indices.get(state);
            if(i==null) {
                i=indices.size();indices.put(state,i);palette.putInt(state,i);
            }
            int value=i;
            while((value&~127)!=0) {
                encoded.write((value&127)|128);
                value>>>=7;
            }
            encoded.write(value);
        }
        blocks.putInt("PaletteMax",indices.size());
        blocks.put("Palette",palette);
        blocks.putByteArray("Data",encoded.toByteArray());
        blocks.put("BlockEntities",new NbtList());
        schematic.put("Blocks",blocks);
        NbtCompound root=new NbtCompound();root.put("Schematic",schematic);
        if(output.getParent()!=null)Files.createDirectories(output.getParent());
        NbtIo.writeCompressed(root,output);
        return output;
    }
}
