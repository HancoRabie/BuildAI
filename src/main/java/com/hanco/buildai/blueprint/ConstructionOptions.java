package com.hanco.buildai.blueprint;

import java.util.*;

/**
 * Confirmation-only construction modes.
 *
 * foundation:no uses the connected bottom-platform footprint instead of
 * schematic AIR padding; each consecutive bottom layer must fill >=90% of
 * that footprint and contain ONE eligible block ID in >=51% of ALL its cells.
 * Actual underground basement geometry is protected from omission. This
 * flag sanitizes schematic placement ONLY. It never instructs excavation
 * of an existing world block.
 */
public record ConstructionOptions(boolean foundation,boolean excavate,boolean hollow,
                                  Set<String> foundationMaterials) {
    public static final ConstructionOptions DEFAULT=
        new ConstructionOptions(true,true,false,Set.of());

    public ConstructionOptions(boolean foundation,boolean excavate){
        this(foundation,excavate,false,Set.of());
    }
    public ConstructionOptions(boolean foundation,boolean excavate,boolean hollow){
        this(foundation,excavate,hollow,Set.of());
    }
    public ConstructionOptions {
        foundationMaterials=foundationMaterials==null?
            Set.of():Set.copyOf(foundationMaterials);
        if(hollow&&excavate)throw new IllegalArgumentException(
            "hollow:yes cannot be combined with full excavate:yes. "+
            "Use hollow:yes alone or excavate:no hollow:yes.");
        if(foundation&&!foundationMaterials.isEmpty())throw new IllegalArgumentException(
            "Foundation material hints require foundation:no.");
    }

    /** Syntax: foundation:no:grass_block,dirt,stonebrick; materials are
     * eligible candidates for the per-layer 51% majority gate over the actual connected footprint.
     * Namespaced IDs (minecraft:stone_bricks) are also permitted.
     */
    public static ConstructionOptions parse(String arguments){
        if(arguments==null||arguments.isBlank())return DEFAULT;
        Boolean base=null,clear=null,interior=null;
        Set<String> materials=new LinkedHashSet<>();
        // Tolerate a space after commas in user-provided material hints,
        // e.g. foundation:no:dirt, grass_block hollow:yes.
        for(String token:arguments.trim().replaceAll(",\\s+",",").split("\\s+")){
            String[] pair=token.split(":",3);
            if(pair.length<2||pair[0].isBlank())throw new IllegalArgumentException(
                "Expected foundation:yes|no[:block1,block2] excavate:yes|no "+
                "hollow:yes|no");
            String key=pair[0].toLowerCase(Locale.ROOT);
            if(pair.length==3&&!"foundation".equals(key))
                throw new IllegalArgumentException(
                    "Only foundation:no accepts a material list");
            boolean value=switch(pair[1].toLowerCase(Locale.ROOT)){
                case "yes"->true;
                case "no"->false;
                default->throw new IllegalArgumentException("Use yes or no for "+key);
            };
            switch(key){
                case "foundation"->{
                    if(base!=null)throw new IllegalArgumentException(
                        "Duplicate foundation flag");
                    base=value;
                    if(pair.length==3){
                        if(value)throw new IllegalArgumentException(
                            "Material hints require foundation:no");
                        for(String raw:pair[2].split(",",-1)){
                            if(raw.isBlank())throw new IllegalArgumentException(
                                "Empty foundation material ID");
                            if(materials.size()>=32)throw new IllegalArgumentException(
                                "At most 32 foundation material hints");
                            materials.add(normalizeMaterial(raw));
                        }
                    }
                }
                case "excavate"->{
                    if(clear!=null)throw new IllegalArgumentException(
                        "Duplicate excavate flag");
                    clear=value;
                }
                case "hollow"->{
                    if(interior!=null)throw new IllegalArgumentException(
                        "Duplicate hollow flag");
                    interior=value;
                }
                default->throw new IllegalArgumentException(
                    "Unknown confirmation flag: "+key);
            }
        }
        boolean onlyInterior=Boolean.TRUE.equals(interior);
        boolean includeFoundation=base==null||base;
        // Critical safety invariant: foundation:no alone MUST NOT trigger
        // full-site excavation merely because excavate was omitted. The
        // player can still explicitly select excavate:yes if desired.
        boolean fullExcavation=clear==null?
            includeFoundation&&!onlyInterior:clear;
        return new ConstructionOptions(includeFoundation,
            fullExcavation,onlyInterior,materials);
    }

    private static String normalizeMaterial(String input){
        String id=input.trim().toLowerCase(Locale.ROOT);
        if(id.equals("stonebrick")||id.equals("stonebricks"))
            id="stone_bricks";
        if(id.equals("grass"))id="grass_block";
        if(!id.contains(":"))id="minecraft:"+id;
        if(!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")||
            id.endsWith(":air")||id.equals("minecraft:water")||
            id.equals("minecraft:lava"))
            throw new IllegalArgumentException("Invalid foundation block ID: "+input);
        return id;
    }

    public static Set<Integer> identifiedFoundation(Blueprint b){
        return FoundationPlan.identifiedFoundation(b,Set.of());
    }
    public static Set<Integer> identifiedFoundation(Blueprint b,Set<String> hints){
        return FoundationPlan.identifiedFoundation(b,hints);
    }
}
