package com.hanco.buildai.blueprint;

import com.google.gson.*;
import java.util.*;

/**
 * Common palette-based blueprint transport.
 * Small files use legacy cells; larger files use run-length encoded palette
 * indexes to avoid enormous JSON arrays of repeated Minecraft air/stone.
 * Both representations remain supported for imported client versions.
 */
public final class BlueprintJson {
    private BlueprintJson() {}
    public static JsonObject encode(Blueprint b) {
        JsonObject root=new JsonObject();
        root.addProperty("name",b.name);
        root.addProperty("width",b.width);
        root.addProperty("height",b.height);
        root.addProperty("length",b.length);
        JsonArray palette=new JsonArray(),cells=new JsonArray(),runs=new JsonArray();
        Map<String,Integer> indices=new LinkedHashMap<>();
        long volume=(long)b.width*b.height*b.length;
        boolean useRuns=volume>100_000;
        int previous=-1,repeat=0;
        for(int y=0;y<b.height;y++)for(int z=0;z<b.length;z++)for(int x=0;x<b.width;x++){
            String block=b.get(x,y,z);
            int index=indices.computeIfAbsent(block,s->{palette.add(s);return indices.size();});
            if(!useRuns)cells.add(index);
            else {
                if(previous==index)repeat++;
                else {
                    if(repeat!=0)addRun(runs,previous,repeat);
                    previous=index;repeat=1;
                }
            }
        }
        root.add("palette",palette);
        if(!b.commands().isEmpty())root.add("commands",CommandBlockPlan.encode(b.commands()));
        if(useRuns){
            if(repeat!=0)addRun(runs,previous,repeat);
            root.add("runs",runs);
        }else root.add("cells",cells);
        return root;
    }
    private static void addRun(JsonArray runs,int state,int length){
        JsonArray r=new JsonArray();r.add(state);r.add(length);runs.add(r);
    }
    public static Blueprint decode(JsonObject root) {
        int w=root.get("width").getAsInt(),h=root.get("height").getAsInt(),d=root.get("length").getAsInt();
        Blueprint b=new Blueprint(root.has("name")?root.get("name").getAsString():"Untitled",w,h,d);
        JsonArray palette=root.getAsJsonArray("palette");
        if(palette==null||palette.size()<1||palette.size()>8192)
            throw new IllegalArgumentException("Invalid block palette size");
        List<String> names=new ArrayList<>();
        for(JsonElement v:palette)names.add(v.getAsString());
        long volume=(long)w*h*d;
        JsonArray runs=root.getAsJsonArray("runs");
        if(runs!=null){
            long i=0;
            for(JsonElement item:runs){
                JsonArray r=item.getAsJsonArray();
                if(r.size()!=2)throw new IllegalArgumentException("Malformed palette run");
                int p=r.get(0).getAsInt(),count=r.get(1).getAsInt();
                if(p<0||p>=names.size()||count<1||i+count>volume)
                    throw new IllegalArgumentException("Invalid blueprint run or cell count");
                for(int n=0;n<count;n++,i++) {
                    int x=(int)(i%w),z=(int)((i/w)%d),y=(int)(i/(w*d));
                    b.set(x,y,z,names.get(p));
                }
            }
            if(i!=volume)throw new IllegalArgumentException("Incomplete palette runs");
        }else {
            JsonArray cells=root.getAsJsonArray("cells");
            if(cells==null||cells.size()!=volume)
                throw new IllegalArgumentException("Incorrect cell count");
            int i=0;
            for(int y=0;y<h;y++)for(int z=0;z<d;z++)for(int x=0;x<w;x++){
                int p=cells.get(i++).getAsInt();
                if(p<0||p>=names.size())throw new IllegalArgumentException("Bad palette index");
                b.set(x,y,z,names.get(p));
            }
        }
        if(root.has("commands"))b.setCommands(
            CommandBlockPlan.decode(root.getAsJsonArray("commands"),b));
        return b;
    }
}
