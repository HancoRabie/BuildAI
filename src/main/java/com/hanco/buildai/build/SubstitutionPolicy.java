package com.hanco.buildai.build;

import com.hanco.buildai.blueprint.*;
import net.minecraft.block.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/** Geometry-aware material interchange, with narrow glass block -> vertical pane exception. */
public final class SubstitutionPolicy {
    private SubstitutionPolicy(){}
    private static boolean pane(String id){return id.equals("minecraft:glass_pane")||id.endsWith("_stained_glass_pane");}
    private static boolean glassBlock(String id){return id.equals("minecraft:glass")||id.endsWith("_stained_glass");}
    private static String category(String id) {
        if(pane(id))return "glass-pane";
        if(glassBlock(id))return "glass-block";
        if(id.endsWith("_slab"))return "slab";
        if(id.endsWith("_stairs"))return "stairs";
        if(id.endsWith("_wall"))return "wall";
        if(id.endsWith("_fence"))return "fence";
        if(id.endsWith("_door"))return "door";
        if(id.endsWith("_trapdoor"))return "trapdoor";
        if(id.equals("minecraft:water")||id.equals("minecraft:lava"))return "liquid";
        return "other";
    }
    public static void validateMapping(String from,String to,ServerWorld world) {
        BlockState a=BlockStates.parse(from),b=BlockStates.parse(to);
        String x=category(from),y=category(to);
        if(x.equals("liquid")||y.equals("liquid"))
            throw new IllegalArgumentException("Liquid substitutions disabled until Survival bucket accounting is verified");
        if(x.equals("glass-block")&&y.equals("glass-pane"))return;
        if(!x.equals(y))throw new IllegalArgumentException("Blocks have incompatible geometries: "+x+" -> "+y);
        if(x.equals("other")&&(!a.isFullCube(world,BlockPos.ORIGIN)||!b.isFullCube(world,BlockPos.ORIGIN)))
            throw new IllegalArgumentException("Substitutions of non-full-block shapes must be within an explicit shape family");
    }
    public static String apply(Blueprint blueprint,int x,int y,int z,Map<String,String> map){
        String old=blueprint.get(x,y,z),from=Blueprint.id(old),to=map.get(from);
        if(to==null)return old;
        String oldCategory=category(from),newCategory=category(to);
        if(oldCategory.equals("glass-block")&&newCategory.equals("glass-pane")){
            boolean vertical=(y>0&&glassBlock(Blueprint.id(blueprint.get(x,y-1,z)))) ||
                             (y+1<blueprint.height&&glassBlock(Blueprint.id(blueprint.get(x,y+1,z))));
            return vertical?to:old; // keep roof/floor glass as full glass blocks
        }
        int prop=old.indexOf('[');
        return to+(prop<0?"":old.substring(prop)); // transfer half/facing/waterlogged where valid
    }
}
