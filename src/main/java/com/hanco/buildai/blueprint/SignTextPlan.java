package com.hanco.buildai.blueprint;

import com.google.gson.*;
import java.util.*;

/**
 * Passive, literal Minecraft sign text attached to an EXISTING sign cell.
 * Never accepts arbitrary block entity NBT, JSON text components, commands,
 * click/hover events, or player-entered executable content.
 */
public final class SignTextPlan {
    private SignTextPlan(){}
    public static final int MAX_SIGNS=512;
    public static final int MAX_LINE_CODEPOINTS=24;

    public record Entry(int x,int y,int z,List<String> front,List<String> back) {
        public Entry {
            front=validateLines(front);
            back=validateLines(back);
        }
    }

    private static List<String> validateLines(List<String> lines){
        if(lines==null||lines.size()>4)
            throw new IllegalArgumentException("Sign text must contain at most four lines");
        List<String> result=new ArrayList<>(4);
        for(String line:lines){
            if(line==null||line.codePointCount(0,line.length())>MAX_LINE_CODEPOINTS||
               line.chars().anyMatch(c->c<' '||c==127))
                throw new IllegalArgumentException("Sign line exceeds 24 characters or contains controls");
            result.add(line);
        }
        while(result.size()<4)result.add("");
        return List.copyOf(result);
    }
    public static boolean isSign(String state){
        String id=Blueprint.id(state);
        return id.startsWith("minecraft:") &&
            (id.endsWith("_sign")||id.endsWith("_wall_sign")||
             id.endsWith("_hanging_sign")||id.endsWith("_wall_hanging_sign"));
    }
    public static List<Entry> checked(Blueprint blueprint,List<Entry> entries){
        Objects.requireNonNull(blueprint);
        Objects.requireNonNull(entries);
        if(entries.size()>MAX_SIGNS)
            throw new IllegalArgumentException("Max 512 labeled signs per blueprint");
        List<Entry> safe=new ArrayList<>();
        Set<Integer> positions=new HashSet<>();
        for(Entry e:entries){
            if(e.x()<0||e.y()<0||e.z()<0||
                e.x()>=blueprint.width||e.y()>=blueprint.height||
                e.z()>=blueprint.length)
                throw new IllegalArgumentException("Sign position outside blueprint");
            if(!isSign(blueprint.get(e.x(),e.y(),e.z())))
                throw new IllegalArgumentException("Sign text assigned to non-sign at "+
                    e.x()+","+e.y()+","+e.z());
            if(!positions.add(blueprint.index(e.x(),e.y(),e.z())))
                throw new IllegalArgumentException("Duplicate sign coordinates");
            safe.add(e);
        }
        return List.copyOf(safe);
    }

    /** Rotate sign metadata without loading the Minecraft block registry.
     * Actual blockstates are rotated separately with the native engine. */
    public static List<Entry> rotated(Blueprint source,int turns){
        int t=BlueprintRotator.normalized(turns);
        if(t==0)return source.signs();
        List<Entry> out=new ArrayList<>();
        for(Entry entry:source.signs()){
            var newPos=BlueprintRotator.position(entry.x(),entry.y(),entry.z(),
                source.width,source.length,t);
            out.add(new Entry(newPos.getX(),newPos.getY(),newPos.getZ(),
                entry.front(),entry.back()));
        }
        return List.copyOf(out);
    }

    public static List<Entry> decode(JsonArray json,Blueprint blueprint){
        if(json==null)return List.of();
        if(json.size()>MAX_SIGNS)throw new IllegalArgumentException("Too many signs");
        List<Entry> list=new ArrayList<>();
        for(JsonElement value:json){
            if(!value.isJsonObject())throw new IllegalArgumentException("Invalid sign record");
            JsonObject o=value.getAsJsonObject();
            List<String> front=readLines(o,"lines");
            List<String> back=readLines(o,"back_lines");
            list.add(new Entry(o.get("x").getAsInt(),o.get("y").getAsInt(),
                o.get("z").getAsInt(),front,back));
        }
        return checked(blueprint,list);
    }
    private static List<String> readLines(JsonObject o,String key){
        if(!o.has(key))return List.of();
        JsonArray lines=o.getAsJsonArray(key);
        if(lines.size()>4)throw new IllegalArgumentException("Sign has more than four lines");
        List<String> result=new ArrayList<>();
        for(JsonElement e:lines){
            if(!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isString())
                throw new IllegalArgumentException("Sign text lines must be plain strings");
            result.add(e.getAsString());
        }
        return result;
    }

    public static JsonArray encode(List<Entry> entries){
        JsonArray out=new JsonArray();
        for(Entry e:entries){
            JsonObject o=new JsonObject();
            o.addProperty("x",e.x());o.addProperty("y",e.y());o.addProperty("z",e.z());
            JsonArray front=new JsonArray(),back=new JsonArray();
            for(String line:e.front())front.add(line);
            for(String line:e.back())back.add(line);
            o.add("lines",front);
            if(e.back().stream().anyMatch(line->!line.isEmpty()))o.add("back_lines",back);
            out.add(o);
        }
        return out;
    }
}
