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
     * An inspectable foundation decision. The SAME analysis provides the
     * omission set and a precise reason why layer scanning stopped. Keep
     * its rules identical to the player's full-schematic 100% footprint
     * and per-material >=51% majority contract.
     */
    public record Analysis(Set<Integer> omissions,int firstOccupiedY,
                           int qualifyingLayers,int stoppedAtY,String reason){
        public Analysis{
            omissions=Set.copyOf(omissions);
        }
        public String summary(){
            return "Foundation: "+omissions.size()+" block(s) omitted across "+
                qualifyingLayers+" qualifying layer(s); "+reason;
        }
    }

    private static String pct(int numerator,int denominator){
        return String.format(Locale.ROOT,"%.2f%%",(100.0*numerator)/denominator);
    }

    public static Analysis analyze(Blueprint b,Set<String> materials){
        Objects.requireNonNull(b,"blueprint");
        Set<String> extras=materials==null?Set.of():materials;
        if(b.width<MIN_SIDE||b.length<MIN_SIDE)
            return new Analysis(Set.of(),-1,0,-1,
                "schematic width/length is below the 4-block minimum");
        final int area=b.width*b.length;
        int first=-1;
        outer:for(int y=0;y<b.height;y++)
            for(int z=0;z<b.length;z++)for(int x=0;x<b.width;x++)
                if(!b.isAir(x,y,z)){first=y;break outer;}
        if(first<0)
            return new Analysis(Set.of(),-1,0,-1,"schematic has no occupied blocks");

        Set<Integer> omissions=new HashSet<>();
        int qualified=0;
        for(int y=first;y<b.height;y++){
            int occupied=0;
            Map<String,Integer> all=new HashMap<>(),eligible=new HashMap<>();
            for(int z=0;z<b.length;z++)for(int x=0;x<b.width;x++){
                if(b.isAir(x,y,z))continue;
                occupied++;
                String id=Blueprint.id(b.get(x,y,z));
                all.merge(id,1,Integer::sum);
                if(!forbidden(id)&&foundationMaterial(id,extras))
                    eligible.merge(id,1,Integer::sum);
            }
            Map.Entry<String,Integer> largest=all.entrySet().stream()
                .max(Map.Entry.<String,Integer>comparingByValue()
                    .thenComparing(Map.Entry::getKey)).orElse(null);
            String top=largest==null?"none":largest.getKey()+" "+
                pct(largest.getValue(),area);

            // Full occupancy is a HARD prerequisite. A single AIR cell
            // anywhere in this Y-layer invalidates the entire layer.
            if(occupied!=area)
                return new Analysis(omissions,first,qualified,y,
                    "stopped at schematic Y="+y+": "+occupied+"/"+area+
                    " cells occupied ("+pct(occupied,area)+"); need 100% "+
                    "full-footprint coverage. Largest block type: "+top);
            Map.Entry<String,Integer> majority=eligible.entrySet().stream()
                .max(Map.Entry.<String,Integer>comparingByValue()
                    .thenComparing(Map.Entry::getKey)).orElse(null);
            if(majority==null||(long)majority.getValue()*100<(long)MIN_PERCENT*area)
                return new Analysis(omissions,first,qualified,y,
                    "stopped at schematic Y="+y+": no SINGLE eligible "+
                    "foundation material reaches 51% of "+area+" cells. "+
                    "Largest eligible: "+(majority==null?"none":
                    majority.getKey()+" "+pct(majority.getValue(),area))+
                    "; largest actual block type: "+top+
                    ". Material hints add eligible IDs, not percentages.");

            String majorityId=majority.getKey();
            // No exception under walls, support columns or basements.
            // Other material IDs on this same layer remain intact.
            for(int z=0;z<b.length;z++)for(int x=0;x<b.width;x++)
                if(majorityId.equals(Blueprint.id(b.get(x,y,z))))
                    omissions.add(b.index(x,y,z));
            qualified++;
        }
        return new Analysis(omissions,first,qualified,b.height,
            "every occupied layer from Y="+first+" to "+
            (b.height-1)+" satisfied the full-footprint/51% rule");
    }

    public static Set<Integer> identifiedFoundation(Blueprint b,Set<String> materials){
        return analyze(b,materials).omissions();
    }
}
