package com.hanco.buildai.client;

import com.hanco.buildai.blueprint.Blueprint;
import com.hanco.buildai.blueprint.SchematicReader;
import com.google.gson.*;
import net.minecraft.nbt.*;

import java.io.InputStream;
import java.nio.file.*;
import java.util.*;

/**
 * Read-only, opt-in decoration reference scanner. Only schematic sign text is
 * inspected; block-entity inventories, commands and entities are NEVER executed
 * or forwarded to the AI.
 *
 * Convention: first sign line "[BuildAI]" and second line "Chair", "Table",
 * "Fountain", etc; or one sign line with a known object name. Put the sign
 * immediately IN FRONT of the example. The sample is a compact local crop,
 * not a new executable structure.
 */
public final class StyleExampleScanner {
    public record Example(String label, String geometry) {}
    private StyleExampleScanner(){}
    private static final Set<String> SHORT_LABELS=Set.of(
        "chair","table","fountain","bench","lamp","sofa","bed","desk",
        "staircase","window","door","garden","tree","arch","balcony","shelf",
        "bookshelf","fireplace","cabinet","chandelier","statue","bridge");

    public static List<Example> scan(Path path) throws Exception {
        String name=path.getFileName().toString().toLowerCase(Locale.ROOT);
        if(!name.endsWith(".schem")&&!name.endsWith(".nbt"))
            throw new IllegalArgumentException(
                "Labeled sign extraction currently supports .schem and vanilla .nbt. Other files can still be reviewed.");
        Blueprint b=SchematicReader.read(path); // validates dimensions and import bounds
        NbtCompound root;
        try(InputStream in=Files.newInputStream(path)) {
            root=NbtIo.readCompressed(in,NbtSizeTracker.of(64L*1024*1024));
        }
        List<NbtCompound> signs=new ArrayList<>();
        if(name.endsWith(".schem")) {
            NbtCompound schematic=root.contains("Schematic")?root.getCompound("Schematic"):root;
            NbtCompound blocks=schematic.contains("Blocks")?schematic.getCompound("Blocks"):schematic;
            NbtList entities=blocks.getList("BlockEntities",NbtElement.COMPOUND_TYPE);
            for(NbtElement entry:entities)signs.add((NbtCompound)entry);
            // Sponge versions differ on location of block entity records.
            NbtList legacy=schematic.getList("BlockEntities",NbtElement.COMPOUND_TYPE);
            for(NbtElement entry:legacy)signs.add((NbtCompound)entry);
        } else {
            for(NbtElement entry:root.getList("blocks",NbtElement.COMPOUND_TYPE)){
                NbtCompound block=(NbtCompound)entry;
                if(block.contains("nbt")){
                    NbtCompound tile=block.getCompound("nbt").copy();
                    NbtList xyz=block.getList("pos",NbtElement.INT_TYPE);
                    if(xyz.size()==3)tile.putIntArray("Pos",new int[]{
                        ((NbtInt)xyz.get(0)).intValue(),
                        ((NbtInt)xyz.get(1)).intValue(),
                        ((NbtInt)xyz.get(2)).intValue()});
                    signs.add(tile);
                }
            }
        }
        List<Example> results=new ArrayList<>();
        Set<String> seen=new HashSet<>();
        for(NbtCompound n:signs){
            if(results.size()>=12)break;
            String id=n.getString("id");if(id.isBlank())id=n.getString("Id");
            if(!id.contains("sign"))continue;
            List<String> lines=signLines(n);
            String label=label(lines);
            if(label==null||label.length()>48)continue;
            int[] pos=n.getIntArray("Pos");
            if(pos.length!=3)pos=n.getIntArray("pos");
            if(pos.length!=3){
                if(n.contains("x")&&n.contains("y")&&n.contains("z"))
                    pos=new int[]{n.getInt("x"),n.getInt("y"),n.getInt("z")};
                else continue;
            }
            String key=Arrays.toString(pos)+":"+label;
            if(!seen.add(key))continue;
            int sx=pos[0],sy=pos[1],sz=pos[2];
            if(sx<0||sy<0||sz<0||sx>=b.width||sy>=b.height||sz>=b.length)continue;
            // Sample a bounded nearby object. The sign block itself is not a model.
            List<String> sample=new ArrayList<>();
            int minX=Math.max(0,sx-4),maxX=Math.min(b.width-1,sx+4);
            int minY=Math.max(0,sy-2),maxY=Math.min(b.height-1,sy+5);
            int minZ=Math.max(0,sz-4),maxZ=Math.min(b.length-1,sz+4);
            // Favor non-air blocks near the sign, sorted by distance so huge
            // decorative walls can't drown out the actual object geometry.
            record Cell(int x,int y,int z,String state,int dist){}
            List<Cell> occupied=new ArrayList<>();
            for(int y=minY;y<=maxY;y++)for(int z=minZ;z<=maxZ;z++)
                for(int x=minX;x<=maxX;x++){
                    if(b.isAir(x,y,z))continue;
                    String state=b.get(x,y,z);
                    if(Blueprint.id(state).contains("_sign"))continue;
                    int dist=Math.abs(x-sx)+Math.abs(y-sy)+Math.abs(z-sz);
                    occupied.add(new Cell(x-sx,y-sy,z-sz,state,dist));
                }
            occupied.sort(Comparator.comparingInt(Cell::dist));
            for(Cell cell:occupied){
                if(sample.size()>=120)break;
                sample.add(cell.x()+","+cell.y()+","+cell.z()+"="+cell.state());
            }
            if(sample.isEmpty())continue;
            results.add(new Example(label,"Relative to sign (dx,dy,dz), "+sample.size()+
                " nearby non-air blocks: "+String.join("; ",sample)));
        }
        return List.copyOf(results);
    }

    private static List<String> signLines(NbtCompound n){
        List<String> lines=new ArrayList<>();
        for(String side:new String[]{"front_text","back_text"}){
            NbtCompound text=n.getCompound(side);
            for(NbtElement element:text.getList("messages",NbtElement.STRING_TYPE))
                lines.add(plain(element.asString()));
        }
        for(int i=1;i<=4;i++)lines.add(plain(n.getString("Text"+i)));
        return lines;
    }
    private static String plain(String value){
        if(value==null||value.isBlank())return "";
        try{
            JsonElement obj=JsonParser.parseString(value);
            if(obj.isJsonObject()&&obj.getAsJsonObject().has("text"))
                return obj.getAsJsonObject().get("text").getAsString().trim();
            if(obj.isJsonPrimitive())return obj.getAsString().trim();
        }catch(RuntimeException ignored){}
        return value.trim();
    }
    private static String label(List<String> lines){
        for(int i=0;i<lines.size();i++){
            String line=lines.get(i).trim();
            if(line.equalsIgnoreCase("[BuildAI]")&&i+1<lines.size()){
                String after=lines.get(i+1).trim();
                if(!after.isBlank()&&after.length()<=48)return after;
            }
            if(SHORT_LABELS.contains(line.toLowerCase(Locale.ROOT)))return line;
        }
        return null;
    }
}
