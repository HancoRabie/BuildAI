package com.hanco.buildai.blueprint;

import com.google.gson.*;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/** Portable, compact schema for client upload, persistence, and AI generation. */
public final class BlueprintJson {
    private BlueprintJson() {}
    public static JsonObject encode(Blueprint b) {
        JsonObject root=new JsonObject();
        root.addProperty("name",b.name);
        root.addProperty("width",b.width);root.addProperty("height",b.height);root.addProperty("length",b.length);
        JsonArray palette=new JsonArray(),cells=new JsonArray();
        Map<String,Integer> indices=new LinkedHashMap<>();
        for(int y=0;y<b.height;y++)for(int z=0;z<b.length;z++)for(int x=0;x<b.width;x++) {
            String block=b.get(x,y,z);
            int index=indices.computeIfAbsent(block,s->{palette.add(s);return indices.size();});
            cells.add(index);
        }
        root.add("palette",palette);root.add("cells",cells);
        return root;
    }
    public static Blueprint decode(JsonObject root) {
        int w=root.get("width").getAsInt(),h=root.get("height").getAsInt(),d=root.get("length").getAsInt();
        Blueprint b=new Blueprint(root.has("name")?root.get("name").getAsString():"Untitled",w,h,d);
        JsonArray palette=root.getAsJsonArray("palette"), cells=root.getAsJsonArray("cells");
        if(cells.size()!=w*h*d||palette.size()>8192)throw new IllegalArgumentException("Incorrect cell count or palette too large");
        List<String> names=new ArrayList<>();
        for(JsonElement v:palette)names.add(v.getAsString());
        int i=0;
        for(int y=0;y<h;y++)for(int z=0;z<d;z++)for(int x=0;x<w;x++) {
            int p=cells.get(i++).getAsInt();
            if(p<0||p>=names.size())throw new IllegalArgumentException("Bad palette index");
            b.set(x,y,z,names.get(p));
        }
        return b;
    }
}
