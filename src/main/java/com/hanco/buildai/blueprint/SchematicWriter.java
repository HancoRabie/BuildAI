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
        // Sponge v3 sign block entities. Text content is strictly literal.
        NbtList blockEntities=new NbtList();
        for(SignTextPlan.Entry e:blueprint.signs()){
            NbtCompound sign=new NbtCompound();
            String id=Blueprint.id(blueprint.get(e.x(),e.y(),e.z()));
            sign.putString("Id",id.contains("hanging")?
                "minecraft:hanging_sign":"minecraft:sign");
            sign.putIntArray("Pos",new int[]{e.x(),e.y(),e.z()});
            sign.put("front_text",signFace(e.front()));
            sign.put("back_text",signFace(e.back()));
            sign.putBoolean("is_waxed",false);
            blockEntities.add(sign);
        }
        blocks.put("BlockEntities",blockEntities);
        schematic.put("Blocks",blocks);
        // Safe, non-executable BuildAI metadata. Do NOT export live Command
        // block-entity NBT to be automatically executed by unrelated importers.
        // Importing this tag still requires operator approval on our server.
        if(!blueprint.signs().isEmpty())
            schematic.putString("BuildAISignPlan",SignTextPlan.encode(blueprint.signs()).toString());
        if(!blueprint.commands().isEmpty())
            schematic.putString("BuildAICommandPlan",
                CommandBlockPlan.encode(blueprint.commands()).toString());
        // Safe metadata for exact-prompt regenerate and actual schematic-based
        // revisions. Byte arrays avoid NBT's modified-UTF 65 KB string cap
        // for complex 3D JSON while remaining passive when other tools import.
        if(blueprint.hasOriginalPrompt())
            schematic.putByteArray("BuildAIOriginalPromptUtf8",
                blueprint.originalPrompt().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if(blueprint.hasRevisionContext())
            schematic.putByteArray("BuildAIDesignSpecificationUtf8",
                blueprint.designSpecification().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        NbtCompound root=new NbtCompound();root.put("Schematic",schematic);
        if(output.getParent()!=null)Files.createDirectories(output.getParent());
        NbtIo.writeCompressed(root,output);
        return output;
    }
    private static NbtCompound signFace(List<String> lines){
        NbtCompound face=new NbtCompound();
        NbtList messages=new NbtList();
        for(String line:lines){
            com.google.gson.JsonObject literal=new com.google.gson.JsonObject();
            literal.addProperty("text",line);
            messages.add(NbtString.of(literal.toString()));
        }
        face.put("messages",messages);
        face.putString("color","black");
        face.putBoolean("has_glowing_text",false);
        return face;
    }
}
