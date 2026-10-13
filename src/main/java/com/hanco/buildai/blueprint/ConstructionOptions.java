package com.hanco.buildai.blueprint;

import java.util.*;

/**
 * Confirmation-only construction modes. Default exactly preserves the
 * existing full-clear/full-blueprint behavior, for old worlds and F8 UI.
 */
public record ConstructionOptions(boolean foundation,boolean excavate,boolean hollow) {
    public static final ConstructionOptions DEFAULT=new ConstructionOptions(true,true,false);
    public ConstructionOptions(boolean foundation,boolean excavate){
        this(foundation,excavate,false);
    }
    public ConstructionOptions {
        if(hollow&&excavate)throw new IllegalArgumentException(
            "hollow:yes cannot be combined with full excavate:yes. "+
            "Use hollow:yes alone or excavate:no hollow:yes.");
    }

    public static ConstructionOptions parse(String arguments){
        if(arguments==null||arguments.isBlank())return DEFAULT;
        Boolean base=null,clear=null,interior=null;
        for(String token:arguments.trim().split("\\s+")){
            String[] pair=token.split(":",-1);
            if(pair.length!=2)throw new IllegalArgumentException(
                "Expected foundation:yes|no excavate:yes|no hollow:yes|no; e.g. "+
                "/buildai confirm foundation:no hollow:yes");
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
                case "hollow"->{
                    if(interior!=null)throw new IllegalArgumentException("Duplicate hollow flag");
                    interior=value;
                }
                default->throw new IllegalArgumentException("Unknown confirmation flag: "+key);
            }
        }
        boolean onlyInterior=Boolean.TRUE.equals(interior);
        return new ConstructionOptions(base==null||base,
            clear==null?!onlyInterior:clear,onlyInterior);
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
        if(area<16||b.width<4||b.length<4)return Set.of();

        // A blueprint may start with a thin padding-of-air layer, or have
        // 2-4 consecutive foundation layers. A y=0-only scan missed those.
        // Never remove layers once a real irregular structure begins.
        Set<Integer> omit=new HashSet<>();
        boolean started=false;
        for(int y=0;y<Math.min(4,b.height-1);y++){
            Map<String,Integer> materials=new HashMap<>();
            int occupied=0;
            boolean mechanical=false;
            for(int z=0;z<b.length;z++)for(int x=0;x<b.width;x++){
                if(b.isAir(x,y,z))continue;
                String id=Blueprint.id(b.get(x,y,z));
                if(!isBaseMaterial(id)){mechanical=true;break;}
                materials.merge(id,1,Integer::sum);
                occupied++;
            }
            // Allow limited initial blank padding, but don't look for an
            // accidental solid upper floor once the building has begun.
            if(occupied==0&&!started&&y<2)continue;
            if(mechanical||occupied<area*0.85)break;
            int dominant=materials.values().stream().mapToInt(Integer::intValue).max().orElse(0);
            if(dominant<occupied*0.85)break;
            for(int z=0;z<b.length;z++)for(int x=0;x<b.width;x++)
                if(!b.isAir(x,y,z))omit.add(b.index(x,y,z));
            started=true;
        }
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
