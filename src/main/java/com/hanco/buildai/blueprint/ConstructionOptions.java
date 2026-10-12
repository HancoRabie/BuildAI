package com.hanco.buildai.blueprint;

import java.util.*;

/**
 * Confirmation-only construction modes. Default exactly preserves the
 * existing full-clear/full-blueprint behavior, for old worlds and F8 UI.
 */
public record ConstructionOptions(boolean foundation,boolean excavate) {
    public static final ConstructionOptions DEFAULT=new ConstructionOptions(true,true);

    public static ConstructionOptions parse(String arguments){
        if(arguments==null||arguments.isBlank())return DEFAULT;
        Boolean base=null,clear=null;
        for(String token:arguments.trim().split("\\s+")){
            String[] pair=token.split(":",-1);
            if(pair.length!=2)throw new IllegalArgumentException(
                "Expected foundation:yes|no and/or excavate:yes|no; e.g. "+
                "/buildai confirm foundation:no excavate:no");
            String key=pair[0].toLowerCase(Locale.ROOT);
            boolean value=switch(pair[1].toLowerCase(Locale.ROOT)){
                case "yes"->true;
                case "no"->false;
                default->throw new IllegalArgumentException(
                    "Use yes or no for "+key);
            };
            switch(key){
                case "foundation"->{
                    if(base!=null)throw new IllegalArgumentException("Duplicate foundation flag");
                    base=value;
                }
                case "excavate"->{
                    if(clear!=null)throw new IllegalArgumentException("Duplicate excavate flag");
                    clear=value;
                }
                default->throw new IllegalArgumentException("Unknown confirmation flag: "+key);
            }
        }
        return new ConstructionOptions(base==null||base,clear==null||clear);
    }

    /**
     * Only mark a very obvious, flat, monochrome, non-mechanical y=0
     * PLATFORM. No generic first-layer stripping: statue legs, redstone,
     * signs, fluids and natural-scene silhouettes must survive.
     *
     * Returns positions to OMIT from placement, not positions to excavate.
     * For non-obvious foundations return empty; fail safe rather than damage.
     */
    public static Set<Integer> identifiedFoundation(Blueprint b){
        long area=(long)b.width*b.length;
        if(area<16)return Set.of();
        Map<String,Integer> materials=new HashMap<>();
        int occupied=0;
        for(int z=0;z<b.length;z++)for(int x=0;x<b.width;x++){
            if(b.isAir(x,0,z))continue;
            String id=Blueprint.id(b.get(x,0,z));
            if(!isBaseMaterial(id))return Set.of();
            materials.merge(id,1,Integer::sum);
            occupied++;
        }
        if(occupied<area*0.85)return Set.of();
        int dominant=materials.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        if(dominant<occupied*0.85)return Set.of();
        // A very thin pixel-art sheet, 1xN fence, or full vertical panel
        // is not a foundation even when its first row happens to be flat.
        if(b.width<4||b.length<4)return Set.of();
        Set<Integer> omit=new HashSet<>();
        for(int z=0;z<b.length;z++)for(int x=0;x<b.width;x++)
            if(!b.isAir(x,0,z))omit.add(b.index(x,0,z));
        return Set.copyOf(omit);
    }

    private static boolean isBaseMaterial(String id){
        if(!id.startsWith("minecraft:"))return false;
        return id.matches("minecraft:(?:stone|stone_bricks|deepslate_bricks|"+
            "polished_deepslate|cobblestone|smooth_stone|smooth_stone_slab|"+
            "stone_brick_slab|polished_andesite|andesite|diorite|granite|"+
            "quartz_block|smooth_quartz|iron_block|concrete|"+
            "(?:[a-z_]+_)?concrete|(?:[a-z_]+_)?terracotta|"+
            "(?:oak|spruce|birch|dark_oak|jungle|acacia|mangrove|cherry|"+
            "bamboo|crimson|warped)_planks)");
    }
}
