package com.hanco.buildai.blueprint;

import java.util.*;

/**
 * Detects connected footprint-wide foundation PLATFORMS from the final
 * blueprint, independently of the whole blueprint bounding-box dimensions.
 * Only contiguous, densely rectangular near-base layers qualify; a matching
 * wall, statue, roof, room floor or solitary patch is not a foundation.
 *
 * Materials override ADDS to the built-in list. It never means "erase all
 * blocks of this material" -- that would destroy houses made of stone bricks.
 * Returned cells are only omitted from future construction, not excavated
 * from the player's world.
 */
public final class FoundationPlan {
    private FoundationPlan(){}
    private static final int MIN_AREA=16,MIN_SIDE=4;
    private static final double MIN_FILL=0.80,MIN_MATCH=0.68;
    private static final Set<String> NATURAL=Set.of(
        "dirt","grass_block","coarse_dirt","rooted_dirt","podzol",
        "mycelium","moss_block","mud","packed_mud","clay","gravel",
        "sand","red_sand","sandstone","red_sandstone",
        "stone","cobblestone","mossy_cobblestone","stone_bricks",
        "mossy_stone_bricks","cracked_stone_bricks","chiseled_stone_bricks",
        "deepslate","cobbled_deepslate","deepslate_bricks",
        "polished_deepslate","smooth_stone","polished_andesite",
        "andesite","diorite","granite","tuff","tuff_bricks",
        "calcite","dripstone_block","netherrack","soul_sand","soul_soil",
        "blackstone","polished_blackstone","polished_blackstone_bricks",
        "basalt","smooth_basalt","bedrock","obsidian",
        "quartz_block","smooth_quartz","iron_block");
    private record Layer(int y,int minX,int maxX,int minZ,int maxZ,
                         BitSet selected,int count) {
        int area(){return (maxX-minX+1)*(maxZ-minZ+1);}
        int width(){return maxX-minX+1;}
        int length(){return maxZ-minZ+1;}
    }
    private static boolean foundationMaterial(String block,Set<String> extras){
        if(!block.startsWith("minecraft:"))return extras.contains(block);
        String name=block.substring("minecraft:".length());
        if(NATURAL.contains(name)||extras.contains(block))return true;
        return name.endsWith("_concrete")||name.endsWith("_terracotta")||
            name.endsWith("_planks")||name.endsWith("_bricks")||
            name.equals("smooth_stone_slab")||name.equals("stone_brick_slab");
    }
    private static boolean forbidden(String block){
        // An override must never erase redstone, signs, inventories,
        // mechanical parts, fluids, or architectural devices.
        String s=block.substring(block.indexOf(':')+1);
        return s.equals("air")||s.endsWith("_air")||s.equals("water")||
            s.equals("lava")||s.contains("redstone")||
            s.contains("repeater")||s.contains("comparator")||
            s.contains("torch")||s.contains("sign")||s.contains("button")||
            s.contains("lever")||s.contains("piston")||
            s.contains("hopper")||s.contains("chest")||
            s.contains("door")||s.contains("rail")||
            s.contains("observer")||s.contains("dispenser")||
            s.contains("dropper")||s.contains("crafter")||
            s.contains("command_block")||s.contains("spawner")||
            s.contains("bed")||s.contains("scaffolding");
    }
    private static boolean eligible(Blueprint b,int x,int y,int z,Set<String> materials){
        if(b.isAir(x,y,z))return false;
        String id=Blueprint.id(b.get(x,y,z));
        return !forbidden(id)&&foundationMaterial(id,materials);
    }
    private static Layer largestConnectedLayer(Blueprint b,int y,Set<String> extras){
        int size=b.width*b.length;
        BitSet visited=new BitSet(size);
        int[] queue=new int[size];
        Layer best=null;
        for(int z=0;z<b.length;z++)for(int x=0;x<b.width;x++){
            int start=z*b.width+x;
            if(visited.get(start)||!eligible(b,x,y,z,extras))continue;
            visited.set(start);
            int begin=0,end=0;
            queue[end++]=start;
            BitSet cells=new BitSet(size);
            int minX=x,maxX=x,minZ=z,maxZ=z;
            while(begin<end){
                int p=queue[begin++],px=p%b.width,pz=p/b.width;
                cells.set(p);
                minX=Math.min(minX,px);maxX=Math.max(maxX,px);
                minZ=Math.min(minZ,pz);maxZ=Math.max(maxZ,pz);
                final int[] nx={px+1,px-1,px,px};
                final int[] nz={pz,pz,pz+1,pz-1};
                for(int d=0;d<4;d++){
                    int xx=nx[d],zz=nz[d];
                    if(xx<0||xx>=b.width||zz<0||zz>=b.length)continue;
                    int idx=zz*b.width+xx;
                    if(!visited.get(idx)&&eligible(b,xx,y,zz,extras)){
                        visited.set(idx);
                        queue[end++]=idx;
                    }
                }
            }
            Layer test=new Layer(y,minX,maxX,minZ,maxZ,cells,end);
            if(test.width()<MIN_SIDE||test.length()<MIN_SIDE||
               test.area()<MIN_AREA||test.count<MIN_FILL*test.area())continue;
            if(best==null||test.count>best.count)best=test;
        }
        return best;
    }
    private static boolean sameFootprint(Layer lower,Layer upper){
        int width=Math.max(0,Math.min(lower.maxX,upper.maxX)-
            Math.max(lower.minX,upper.minX)+1);
        int length=Math.max(0,Math.min(lower.maxZ,upper.maxZ)-
            Math.max(lower.minZ,upper.minZ)+1);
        int overlap=width*length;
        return overlap>=MIN_MATCH*Math.max(lower.area(),upper.area());
    }

    public static Set<Integer> identifiedFoundation(Blueprint b,Set<String> materials){
        Objects.requireNonNull(b);
        Set<String> extras=materials==null?Set.of():materials;
        if(b.width<MIN_SIDE||b.length<MIN_SIDE)return Set.of();

        // Anchor the strip to the FIRST occupied Y layer anywhere in the
        // design, not y=0: imported schematics may contain lower AIR padding.
        int first=-1;
        outer:for(int y=0;y<b.height;y++)
            for(int z=0;z<b.length;z++)for(int x=0;x<b.width;x++)
                if(!b.isAir(x,y,z)){first=y;break outer;}
        if(first<0)return Set.of();
        Layer firstLayer=largestConnectedLayer(b,first,extras);
        if(firstLayer==null)return Set.of();

        BitSet omitted=new BitSet(b.width*b.length*b.height);
        Layer previous=firstLayer;
        int last=first;
        for(int y=first;y<b.height;y++){
            Layer layer=y==first?firstLayer:largestConnectedLayer(b,y,extras);
            if(layer==null||!sameFootprint(previous,layer))break;

            // A true footprint platform is occupied mostly by BASE blocks.
            // Never omit a layer if there is significant non-base
            // architecture in the same rectangular area.
            int other=0;
            for(int z=layer.minZ;z<=layer.maxZ;z++)
                for(int x=layer.minX;x<=layer.maxX;x++)
                    if(!b.isAir(x,y,z)&&!layer.selected.get(z*b.width+x))
                        other++;
            if(other>layer.area()*0.05)break;

            for(int p=layer.selected.nextSetBit(0);p>=0;
                    p=layer.selected.nextSetBit(p+1)){
                int x=p%b.width,z=p/b.width;
                omitted.set(b.index(x,y,z));
            }
            previous=layer;last=y;
        }
        if(last<first)return Set.of();
        HashSet<Integer> result=new HashSet<>(omitted.cardinality());
        for(int i=omitted.nextSetBit(0);i>=0;i=omitted.nextSetBit(i+1))
            result.add(i);
        return Set.copyOf(result);
    }
}
