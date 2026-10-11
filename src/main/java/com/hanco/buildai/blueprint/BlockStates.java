package com.hanco.buildai.blueprint;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import java.util.*;

/** Refuses unknown blocks/properties instead of destroying a schematic's meaning. */
public final class BlockStates {
    private BlockStates() {}
    public static BlockState parse(String raw) {
        String[] parts=raw.split("\\[",2);
        Identifier id=Identifier.tryParse(parts[0]);
        if(id==null||!Registries.BLOCK.containsId(id))throw new IllegalArgumentException("Unknown block: "+parts[0]);
        BlockState state=Registries.BLOCK.get(id).getDefaultState();
        if(parts.length==1)return state;
        String properties=parts[1];
        if(!properties.endsWith("]"))throw new IllegalArgumentException("Malformed state: "+raw);
        properties=properties.substring(0,properties.length()-1);
        for(String field:properties.split(",")) {
            if(field.isBlank())continue;
            String[] kv=field.split("=",2);
            if(kv.length!=2)throw new IllegalArgumentException("Malformed property "+field);
            Property<?> property=state.getBlock().getStateManager().getProperty(kv[0]);
            if(property==null)throw new IllegalArgumentException("Unknown property "+field+" for "+id);
            state=apply(state,property,kv[1]);
        }
        return state;
    }
    private static <T extends Comparable<T>> BlockState apply(BlockState state,Property<T> property,String value) {
        T parsed=property.parse(value).orElseThrow(()->new IllegalArgumentException("Invalid "+property.getName()+"="+value));
        return state.with(property,parsed);
    }
    public static void validate(Blueprint b) {
        // Parsing registry properties is expensive: repeated blocks across millions
        // of cells are validated once per distinct full block state.
        Set<String> seen=new HashSet<>();
        for(int y=0;y<b.height;y++)for(int z=0;z<b.length;z++)for(int x=0;x<b.width;x++){
            String state=b.get(x,y,z);
            if(seen.add(state))parse(state);
        }
        b.validateSlices();
    }
}
