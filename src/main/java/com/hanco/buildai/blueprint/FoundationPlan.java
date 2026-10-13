package com.hanco.buildai.blueprint;

import java.util.*;

/**
 * A foundation layer must contain MORE THAN 60% eligible material across
 * the building's connected bottom footprint, and must span that footprint.
 * Scan successive layers until that evidence fails, without an arbitrary
 * layer-count ceiling. Omit ONLY matching material blocks OUTSIDE validated
 * blueprint walls and their interiors, retaining intentional dirt gardens,
 * basement floors, decoration supports and architectural blocks.
 *
 * Custom material hints extend detection but NEVER bypass the wall/interior
 * protection mask. Omission skips future construction; it does not excavate
 * blocks already existing in the world.
 */
public final class FoundationPlan {
    private FoundationPlan(){}
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
    // More than 60%, not >= 60%. Count against the ANCHORED rectangular
    // footprint area rather than only against blocks that happen to exist.
    private static final double MIN_SHARE=0.60;
    private static final int MIN_AREA=16,MIN_SIDE=4;
    private static final int FOOTPRINT_RUN=2;

    private record Footprint(int minX,int maxX,int minZ,int maxZ,int count){
        int width(){return maxX-minX+1;}
        int length(){return maxZ-minZ+1;}
        int area(){return width()*length();}
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
        // Never omit functional blocks, even with explicit material hints.
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

    /** Anchor on the largest connected floor-like cluster at the FIRST
     * occupied blueprint Y. A blueprint can be much wider than the base,
     * e.g. a separate sign/advertisement, so never use full volume bounds.
     *
     * Importantly, the footprint can contain basement holes and other
     * architectural blocks. The connected cluster needs to occupy >60% of
     * its rectangular envelope, not 100%.
     */
    private static Footprint firstFootprint(Blueprint b,int y,Set<String> materials){
        int area=b.width*b.length;
        BitSet visited=new BitSet(area);
        int[] queue=new int[area];
        Footprint best=null;
        for(int z=0;z<b.length;z++)for(int x=0;x<b.width;x++){
            int p=z*b.width+x;
            if(visited.get(p)||b.isAir(x,y,z))continue;
            int head=0,tail=0,matched=0,minX=x,maxX=x,minZ=z,maxZ=z;
            visited.set(p);queue[tail++]=p;
            while(head<tail){
                int current=queue[head++],px=current%b.width,pz=current/b.width;
                minX=Math.min(minX,px);maxX=Math.max(maxX,px);
                minZ=Math.min(minZ,pz);maxZ=Math.max(maxZ,pz);
                if(eligible(b,px,y,pz,materials))matched++;
                int[] neighbors={pz>0?current-b.width:-1,
                    pz+1<b.length?current+b.width:-1,
                    px>0?current-1:-1,px+1<b.width?current+1:-1};
                for(int n:neighbors){
                    if(n<0||visited.get(n))continue;
                    if(!b.isAir(n%b.width,y,n/b.width)){
                        visited.set(n);queue[tail++]=n;
                    }
                }
            }
            Footprint f=new Footprint(minX,maxX,minZ,maxZ,matched);
            // The 60% is of ALL cells in the connected region's actual
            // rectangular footprint, including its walls/basement holes.
            // Otherwise a 60%-wide dirt patch would self-report 100%.
            if(f.width()<MIN_SIDE||f.length()<MIN_SIDE||f.area()<MIN_AREA||
               f.count<=MIN_SHARE*f.area())continue;
            if(best==null||f.area()>best.area()||
                (f.area()==best.area()&&f.count>best.count))best=f;
        }
        return best;
    }

    /** Does the majority material actually SPAN the established base?
     * A 65%-area patch tucked into one corner is not an entire footprint.
     * Allow actual building walls to occupy edges in a mixed basement
     * layer, rather than insisting on dirt at a wall coordinate.
     */
    private static boolean reachesFootprint(Blueprint b,int y,Footprint f,
                                             Set<String> materials,BitSet walls){
        int xNear=0,xFar=0,zNear=0,zFar=0;
        for(int z=f.minZ;z<=f.maxZ;z++){
            if(eligible(b,f.minX,y,z,materials)||
               walls.get(z*b.width+f.minX))xNear++;
            if(eligible(b,f.maxX,y,z,materials)||
               walls.get(z*b.width+f.maxX))xFar++;
        }
        for(int x=f.minX;x<=f.maxX;x++){
            if(eligible(b,x,y,f.minZ,materials)||
               walls.get(f.minZ*b.width+x))zNear++;
            if(eligible(b,x,y,f.maxZ,materials)||
               walls.get(f.maxZ*b.width+x))zFar++;
        }
        return xNear>=FOOTPRINT_RUN&&xFar>=FOOTPRINT_RUN&&
            zNear>=FOOTPRINT_RUN&&zFar>=FOOTPRINT_RUN;
    }

    /** Never remove the specific block supporting an explicitly planned
     * functional/decorative item, even if it is on the outdoor part of a
     * foundation. Removing the whole layer would drop that decoration. */
    private static boolean protectsAbove(Blueprint b,int x,int y,int z){
        if(y+1>=b.height||b.isAir(x,y+1,z))return false;
        String above=Blueprint.id(b.get(x,y+1,z));
        return forbidden(above);
    }

    public static Set<Integer> identifiedFoundation(Blueprint b,Set<String> materials){
        Objects.requireNonNull(b);
        Set<String> extras=materials==null?Set.of():materials;
        if(b.width<MIN_SIDE||b.length<MIN_SIDE)return Set.of();

        int first=-1;
        outer:for(int y=0;y<b.height;y++)
            for(int z=0;z<b.length;z++)for(int x=0;x<b.width;x++)
                if(!b.isAir(x,y,z)){first=y;break outer;}
        if(first<0)return Set.of();
        Footprint footprint=firstFootprint(b,first,extras);
        if(footprint==null)return Set.of();

        // Protection is independent of material and Y. Actual standing
        // walls at higher Y protect built-in dirt/garden blocks and filled
        // basement floors in lower Y layers. Overrides use THIS SAME mask.
        BitSet protectedXZ=InteriorHollowPlan.protectedFootprint(b);
        BitSet skip=new BitSet(b.width*b.length*b.height);

        for(int y=first;y<b.height;y++){
            int matched=0;
            boolean functionalInLayer=false;
            for(int z=footprint.minZ;z<=footprint.maxZ;z++)
                for(int x=footprint.minX;x<=footprint.maxX;x++){
                    if(eligible(b,x,y,z,extras))matched++;
                    else if(!b.isAir(x,y,z)&&
                        forbidden(Blueprint.id(b.get(x,y,z))))
                        functionalInLayer=true;
                }

            // Enforce the player's >60%-per-layer threshold; don't count
            // the % of only the occupied blocks or delete a whole layer
            // just because 60% of it is rock.
            if(matched<=MIN_SHARE*footprint.area())break;
            if(!reachesFootprint(b,y,footprint,extras,protectedXZ))break;

            // A device already embedded in this layer means it is likely
            // an engineered floor, not disposable platform. Fail safe.
            if(functionalInLayer)break;

            for(int z=footprint.minZ;z<=footprint.maxZ;z++)
                for(int x=footprint.minX;x<=footprint.maxX;x++){
                    if(!eligible(b,x,y,z,extras))continue;
                    if(protectedXZ.get(z*b.width+x))continue;
                    if(protectsAbove(b,x,y,z))continue;
                    skip.set(b.index(x,y,z));
                }
        }

        HashSet<Integer> result=new HashSet<>(skip.cardinality());
        for(int i=skip.nextSetBit(0);i>=0;i=skip.nextSetBit(i+1))
            result.add(i);
        return Set.copyOf(result);
    }
}
