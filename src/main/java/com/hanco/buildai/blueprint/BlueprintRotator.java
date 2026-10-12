package com.hanco.buildai.blueprint;

import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/**
 * Strict Y-axis 90-degree blueprint rotation. Client wireframe and server
 * construction use the SAME coordinate transform. Minecraft's native block
 * rotation handles stairs, doors, wall connections, rails and directional devices.
 */
public final class BlueprintRotator {
    private BlueprintRotator(){}
    public static int normalized(int turns){return Math.floorMod(turns,4);}
    public static int width(Blueprint b,int turns){return normalized(turns)%2==0?b.width:b.length;}
    public static int length(Blueprint b,int turns){return normalized(turns)%2==0?b.length:b.width;}
    public static BlockPos position(int x,int y,int z,int w,int l,int turns){
        return switch(normalized(turns)){
            case 1 -> new BlockPos(l-1-z,y,x);
            case 2 -> new BlockPos(w-1-x,y,l-1-z);
            case 3 -> new BlockPos(z,y,w-1-x);
            default -> new BlockPos(x,y,z);
        };
    }
    public static Blueprint rotate(Blueprint source,int turns){
        int t=normalized(turns);
        if(t==0)return source;
        Blueprint result=new Blueprint(source.name,width(source,t),source.height,length(source,t));
        result.warnings.addAll(source.warnings);
        Map<String,String> rotatedStates=new HashMap<>();
        for(int y=0;y<source.height;y++)
            for(int z=0;z<source.length;z++)
                for(int x=0;x<source.width;x++){
                    String state=source.get(x,y,z);
                    if(source.isAir(x,y,z))continue;
                    String corrected=rotatedStates.computeIfAbsent(state,k->rotatedState(k,t));
                    BlockPos dest=position(x,y,z,source.width,source.length,t);
                    result.set(dest.getX(),dest.getY(),dest.getZ(),corrected);
                }
        return result;
    }
    private static String rotatedState(String raw,int turns){
        BlockState block=BlockStates.parse(raw);
        BlockRotation rotation=switch(turns){
            case 1 -> BlockRotation.CLOCKWISE_90;
            case 2 -> BlockRotation.CLOCKWISE_180;
            case 3 -> BlockRotation.COUNTERCLOCKWISE_90;
            default -> BlockRotation.NONE;
        };
        BlockState rotated=block.rotate(rotation);
        StringBuilder result=new StringBuilder(Registries.BLOCK.getId(rotated.getBlock()).toString());
        if(!rotated.getEntries().isEmpty()){
            StringJoiner states=new StringJoiner(",");
            for(var entry:rotated.getEntries().entrySet())
                states.add(property(entry.getKey(),entry.getValue()));
            result.append('[').append(states).append(']');
        }
        return result.toString();
    }
    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> String property(Property<?> key,Comparable<?> value){
        Property<T> property=(Property<T>)key;
        return property.getName()+"="+property.name((T)value);
    }
}
