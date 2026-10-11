package com.hanco.buildai.blueprint;

import com.google.gson.*;
import java.util.*;

/**
 * Fast, non-destructive geometric QA of model-generated blueprints.
 * Checks facts we can actually infer from voxels and declared room boxes.
 * Cannot replace Minecraft movement simulation or real building inspection.
 *
 * v1 heuristics intentionally WARN rather than silently mutating a design.
 */
public final class ArchitectureAudit {
    private ArchitectureAudit(){}
    private static final int MAX_WARNINGS=24;

    public static List<String> inspect(Blueprint b,String requestedPrompt,String specification){
        List<String> results=new ArrayList<>();
        JsonObject root=null;
        try{if(specification!=null)root=JsonParser.parseString(specification).getAsJsonObject();}
        catch(RuntimeException e){results.add("Room manifest could not be read; validate navigation in-game.");}

        // Named landmark rooms should be provided explicitly, not assumed.
        String prompt=requestedPrompt==null?"":requestedPrompt.toLowerCase(Locale.ROOT);
        String building=b.name.toLowerCase(Locale.ROOT);
        boolean whiteHouse=prompt.contains("white house")||building.contains("white house");
        JsonArray rooms=root==null?null:root.getAsJsonArray("rooms");
        if(whiteHouse&&!declaresRoom(rooms,"oval office"))
            results.add("White House blueprint does not declare a verified Oval Office. Ask ChatGPT to add it to the West Wing with an accessible entrance.");

        int unsafeStairs=0,unflnishedStairs=0,flightSteps=0;
        // Access checks apply to stair flights with adjacent rising steps,
        // not decorative roof stairs and garden edging.
        for(int y=0;y<b.height;y++)for(int z=0;z<b.length;z++)for(int x=0;x<b.width;x++){
            if(!isStair(b.get(x,y,z)))continue;
            boolean partOfFlight=false;
            for(int[] v:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){
                int nx=x+v[0],nz=z+v[1];
                if(valid(b,nx,y+1,nz)&&isStair(b.get(nx,y+1,nz)) ||
                   valid(b,nx,y-1,nz)&&isStair(b.get(nx,y-1,nz))){
                    partOfFlight=true;break;
                }
            }
            if(!partOfFlight)continue;
            flightSteps++;
            if(y+2>=b.height || !b.isAir(x,y+1,z) || !b.isAir(x,y+2,z))
                unsafeStairs++;
            if(y>0){
                for(int[] v:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){
                    int nx=x+v[0],nz=z+v[1];
                    if(valid(b,nx,y,nz) && (Blueprint.id(b.get(nx,y,nz)).equals("minecraft:grass_block") ||
                        Blueprint.id(b.get(nx,y,nz)).equals("minecraft:dirt"))){
                        unflnishedStairs++;break;
                    }
                }
            }
        }
        if(unsafeStairs>0)results.add(unsafeStairs+
            " connected stair step(s) may lack 2-block headroom or a proper floor opening. Inspect stairwell slices.");
        if(unflnishedStairs>0)results.add(unflnishedStairs+
            " stair step(s) touch exposed soil/grass. Verify interior stairwell finish and landings.");
        if(flightSteps==0 && (prompt.contains("basement")||prompt.contains("upstairs")||
            prompt.contains("multi-story")||prompt.contains("multiple floors")))
            results.add("No identifiable connected stair flight found for a multi-level request. Confirm floor-to-floor access.");

        if(rooms==null||rooms.isEmpty()){
            if(whiteHouse||prompt.contains("fully furnished")||prompt.contains("floor plan"))
                results.add("No machine-readable room manifest. Room identity and circulation cannot be checked.");
        }else{
            Set<String> names=new HashSet<>();
            for(JsonElement room:rooms){
                if(results.size()>=MAX_WARNINGS)break;
                if(!room.isJsonObject())continue;
                JsonObject r=room.getAsJsonObject();
                String name=r.has("name")?r.get("name").getAsString():"Unnamed room";
                if(!names.add(name.toLowerCase(Locale.ROOT)))results.add("Repeated room label: "+name);
                try{
                    int x1=number(r,"x1"),y1=number(r,"y1"),z1=number(r,"z1");
                    int x2=number(r,"x2"),y2=number(r,"y2"),z2=number(r,"z2");
                    if(x1<0||y1<0||z1<0||x2>=b.width||y2>=b.height||z2>=b.length||
                       x2<x1||y2<y1||z2<z1){
                        results.add(name+": declared coordinates are outside the actual blueprint.");continue;
                    }
                    boolean traversable=false;
                    // Look for at least one two-block-high interior passage with a floor.
                    for(int y=y1+1;y<=y2-1&&!traversable;y++)
                        for(int z=z1;z<=z2&&!traversable;z++)
                            for(int x=x1;x<=x2&&!traversable;x++){
                                if(y+1<b.height && b.isAir(x,y,z)&&b.isAir(x,y+1,z)&&
                                    y>0&&!b.isAir(x,y-1,z))traversable=true;
                            }
                    if(!traversable)results.add(name+": no confirmed floor-supported two-block-high walkable space.");
                    // A border pass-through opening is evidence of a route to a corridor;
                    // NOT proof of end-to-end building connectivity.
                    boolean entry=false;
                    for(int y=y1+1;y<y2&&!entry;y++)
                        for(int z=z1;z<=z2&&!entry;z++)
                            for(int x=x1;x<=x2&&!entry;x++){
                                boolean boundary=x==x1||x==x2||z==z1||z==z2;
                                if(boundary&&y+1<b.height&&b.isAir(x,y,z)&&b.isAir(x,y+1,z))
                                    entry=true;
                            }
                    if(!entry)results.add(name+": no 2-block-high opening detected on its declared room boundary.");
                }catch(RuntimeException ex){results.add(name+": incomplete or invalid room coordinates.");}
            }
        }
        if(results.size()>MAX_WARNINGS)return List.copyOf(results.subList(0,MAX_WARNINGS));
        return List.copyOf(results);
    }
    private static int number(JsonObject r,String key){return r.get(key).getAsInt();}
    private static boolean declaresRoom(JsonArray rooms,String label){
        if(rooms==null)return false;
        for(JsonElement e:rooms){
            if(e.isJsonObject()&&e.getAsJsonObject().has("name") &&
                e.getAsJsonObject().get("name").getAsString().equalsIgnoreCase(label))
                return true;
        }
        return false;
    }
    private static boolean valid(Blueprint b,int x,int y,int z){
        return x>=0&&x<b.width&&y>=0&&y<b.height&&z>=0&&z<b.length;
    }
    private static boolean isStair(String raw){
        return Blueprint.id(raw).endsWith("_stairs");
    }
}
