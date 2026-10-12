package com.hanco.buildai.blueprint;

/**
 * Java 1.21.1 fluid economy. The blueprint stores real WATER/LAVA block states,
 * never bucket items as geometry.
 *
 * Creative: no material charge. Survival: TWO water buckets PER PROJECT (the
 * renewable-water minimum), plus ONE lava bucket PER LAVA SOURCE. Empty buckets
 * are returned to the same BuildAI supply chest after charging. The server
 * creates sources late in NORMALIZE, not while fragile wiring is being built.
 */
public final class BuildFluidRules {
    private BuildFluidRules(){}
    public static boolean isWater(String state){
        return Blueprint.id(state).equals("minecraft:water");
    }
    public static boolean isLava(String state){
        return Blueprint.id(state).equals("minecraft:lava");
    }
    public static boolean isFluidSource(String state){
        return isWater(state)||isLava(state);
    }
    public static int requiredWaterBuckets(int waterSourceCells){
        if(waterSourceCells<0)throw new IllegalArgumentException("Negative water source count");
        return waterSourceCells==0?0:2;
    }
    public static int requiredLavaBuckets(int lavaSourceCells){
        if(lavaSourceCells<0)throw new IllegalArgumentException("Negative lava source count");
        return lavaSourceCells;
    }
    public static boolean isFluidBucket(String name){
        return name.equals("minecraft:water_bucket")||name.equals("minecraft:lava_bucket");
    }
    public static String sourceForBucket(String requested){
        return switch(requested){
            case "minecraft:water_bucket"->"minecraft:water";
            case "minecraft:lava_bucket"->"minecraft:lava";
            default->requested;
        };
    }
}
