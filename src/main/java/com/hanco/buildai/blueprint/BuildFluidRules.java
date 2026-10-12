package com.hanco.buildai.blueprint;

/** World-placed fluid SOURCE states are environmental positions, not inventory
 * block items or literal bucket blocks. Minecraft's world placement API can
 * install these exact source states; survival inventory buckets are not needed
 * for a BuildAI-administered block placement.
 */
public final class BuildFluidRules {
    private BuildFluidRules(){}
    public static boolean isFluidSource(String state){
        String id=Blueprint.id(state);
        return id.equals("minecraft:water")||id.equals("minecraft:lava");
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
