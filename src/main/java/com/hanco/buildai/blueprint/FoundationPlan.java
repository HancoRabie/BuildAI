package com.hanco.buildai.blueprint;

import java.util.*;

/**
 * Bottom-up, strict whole-schematic foundation detection. A layer qualifies
 * ONLY when each X/Z position in the full schematic rectangle is occupied
 * AND a single eligible foundation block ID occupies at least 51% of that
 * entire area. Omit every matching block in a qualifying layer, with no
 * room, basement, wall-column or support exceptions.
 *
 * Unlike hollow:yes, this does not excavate existing world terrain.
 * A majority block may ALSO be part of deliberate architecture. Imported
 * schematic geometry alone cannot distinguish those intentions.
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
    private static final int MIN_PERCENT=51;
    private static final int MIN_SIDE=4;

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

    /**
     * Apply the same explicit material-majority rule at every consecutive
     * fully populated bottom layer. No subsection may define its own
     * denominator and no columns are exempt AFTER a layer has qualified.
     */
    public static Set<Integer> identifiedFoundation(Blueprint b,Set<String> materials){
        Objects.requireNonNull(b,"blueprint");
        Set<String> extras=materials==null?Set.of():materials;
        if(b.width<MIN_SIDE||b.length<MIN_SIDE)return Set.of();
        final int layerArea=b.width*b.length;
        int first=-1;
        outer:for(int y=0;y<b.height;y++)
            for(int z=0;z<b.length;z++)for(int x=0;x<b.width;x++)
                if(!b.isAir(x,y,z)){first=y;break outer;}
        if(first<0)return Set.of();

        Set<Integer> omissions=new HashSet<>();
        for(int y=first;y<b.height;y++){
            int occupied=0;
            Map<String,Integer> frequencies=new HashMap<>();
            for(int z=0;z<b.length;z++)for(int x=0;x<b.width;x++){
                if(b.isAir(x,y,z))continue;
                occupied++;
                String id=Blueprint.id(b.get(x,y,z));
                if(!forbidden(id)&&foundationMaterial(id,extras))
                    frequencies.merge(id,1,Integer::sum);
            }
            // ALL horizontal positions, including the schematic's edges.
            // Missing even one position stops bottom-up foundation scanning.
            if(occupied!=layerArea)break;

            String majorityBlock=null;
            int majorityCount=0;
            for(var entry:frequencies.entrySet())
                if(entry.getValue()>majorityCount){
                    majorityBlock=entry.getKey();
                    majorityCount=entry.getValue();
                }
            if(majorityBlock==null||
                (long)majorityCount*100<(long)MIN_PERCENT*layerArea)break;

            // Once a layer qualifies, delete this TYPE at every position.
            // Do not silently preserve areas under walls, support columns,
            // interiors or basements. Other block types remain untouched.
            for(int z=0;z<b.length;z++)for(int x=0;x<b.width;x++)
                if(majorityBlock.equals(Blueprint.id(b.get(x,y,z))))
                    omissions.add(b.index(x,y,z));
        }
        return Set.copyOf(omissions);
    }
}
