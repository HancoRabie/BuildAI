package com.hanco.buildai.blueprint;
import com.google.gson.*;
import java.util.*;

/** Compiles model-authored geometry operations into deterministic, bounded voxel data. */
public final class ProceduralGenerator {
    private ProceduralGenerator() {}
    public static Blueprint compile(String json) {
        JsonObject root=JsonParser.parseString(json).getAsJsonObject();
        if(root.has("error"))
            throw new IllegalArgumentException("AI cannot produce this blueprint at the requested scale: "+
                root.get("error").getAsString());
        int w=number(root,"width",1,512),h=number(root,"height",1,512),d=number(root,"length",1,512);
        Blueprint b=new Blueprint(root.has("name")?root.get("name").getAsString():"AI Design",w,h,d);
        JsonArray ops=root.getAsJsonArray("operations");
        if(ops==null||ops.size()<1||ops.size()>16000)throw new IllegalArgumentException("Expected 1-16000 design operations");
        for(JsonElement e:ops) {
            JsonObject op=e.getAsJsonObject();
            String kind=op.get("kind").getAsString().toLowerCase(Locale.ROOT);
            String block=op.get("block").getAsString();
            if(block.length()>180||!block.matches("[a-z0-9_.-]+:[a-z0-9_./-]+(\\[[a-z0-9_,=.-]+\\])?"))
                throw new IllegalArgumentException("Invalid block state "+block);
            int x1=number(op,"x1",0,w-1),y1=number(op,"y1",0,h-1),z1=number(op,"z1",0,d-1);
            int x2=number(op,"x2",0,w-1),y2=number(op,"y2",0,h-1),z2=number(op,"z2",0,d-1);
            int ax=Math.min(x1,x2),ay=Math.min(y1,y2),az=Math.min(z1,z2);
            int bx=Math.max(x1,x2),by=Math.max(y1,y2),bz=Math.max(z1,z2);
            switch(kind) {
                case "fill","box" -> {
                    for(int y=ay;y<=by;y++)for(int z=az;z<=bz;z++)for(int x=ax;x<=bx;x++)b.set(x,y,z,block);
                }
                case "shell" -> {
                    for(int y=ay;y<=by;y++)for(int z=az;z<=bz;z++)for(int x=ax;x<=bx;x++)
                        if(x==ax||x==bx||y==ay||y==by||z==az||z==bz)b.set(x,y,z,block);
                }
                case "walls" -> {
                    for(int y=ay;y<=by;y++)for(int z=az;z<=bz;z++)for(int x=ax;x<=bx;x++)
                        if(x==ax||x==bx||z==az||z==bz)b.set(x,y,z,block);
                }
                case "ellipsoid","organic_canopy" -> {
                    // Curved domes and natural tree crowns; deterministic and
                    // clipped to the authored box. Leaves never erase structural
                    // timbers, windows, lanterns, or existing trunk blocks.
                    boolean crown=kind.equals("organic_canopy");
                    int seed=op.has("seed")?number(op,"seed",0,1_000_000):37;
                    double cx=(ax+bx+1)/2.0,cy=(ay+by+1)/2.0,cz=(az+bz+1)/2.0;
                    double rx=(bx-ax+1)/2.0,ry=(by-ay+1)/2.0,rz=(bz-az+1)/2.0;
                    for(int y=ay;y<=by;y++)for(int z=az;z<=bz;z++)for(int x=ax;x<=bx;x++) {
                        double nx=(x+0.5-cx)/rx,ny=(y+0.5-cy)/ry,nz=(z+0.5-cz)/rz;
                        double dist=nx*nx+ny*ny+nz*nz;
                        // Tiny per-voxel edge variation avoids rectangular crowns,
                        // while interior blocks remain connected and reproducible.
                        double edge=1.0;
                        if(crown){
                            int hash=seed ^ (x*73428767) ^ (y*912931) ^ (z*19349663);
                            hash^=hash>>>13;hash*=1274126177;hash^=hash>>>16;
                            edge+=(((hash&1023)/1023.0)-0.5)*0.24;
                        }
                        if(dist>edge)continue;
                        String current=b.get(x,y,z);
                        if(!crown || b.isAir(x,y,z) || Blueprint.id(current).endsWith("_leaves"))
                            b.set(x,y,z,block);
                    }
                }
                case "line" -> {
                    int steps=Math.max(Math.abs(x2-x1),Math.max(Math.abs(y2-y1),Math.abs(z2-z1)));
                    for(int i=0;i<=steps;i++) {
                        double f=steps==0?0:(double)i/steps;
                        b.set((int)Math.round(x1+(x2-x1)*f),(int)Math.round(y1+(y2-y1)*f),(int)Math.round(z1+(z2-z1)*f),block);
                    }
                }
                default -> throw new IllegalArgumentException("Unknown primitive "+kind);
            }
        }
        b.validateSlices();
        return b;
    }
    private static int number(JsonObject o,String key,int min,int max) {
        int n=o.get(key).getAsInt();
        if(n<min||n>max)throw new IllegalArgumentException(key+" outside bounds "+min+".."+max);
        return n;
    }
}
