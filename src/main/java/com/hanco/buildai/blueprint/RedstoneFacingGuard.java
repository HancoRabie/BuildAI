package com.hanco.buildai.blueprint;

import java.util.*;
import java.util.regex.*;

/**
 * Minecraft JAVA 1.21.1 repeater/comparator facing semantics:
 * facing indicates the REAR / INPUT side (opposite the front/OUTPUT).
 * E.g. flow WEST -> EAST requires minecraft:repeater[facing=west].
 *
 * Only confidently fix reversed repeaters with an immediate identifiable
 * SOURCE on one side and a terminal OUTPUT on the other. Redstone dust,
 * logic networks and comparator feedback are ambiguous: warn, NEVER flip.
 */
public final class RedstoneFacingGuard {
    private RedstoneFacingGuard(){}
    private static final Pattern FACING=Pattern.compile("(?:^|\\[|,)facing=(north|south|east|west)");
    private static final String[] DIRS={"north","east","south","west"};
    private static final int[] DX={0,1,0,-1},DZ={-1,0,1,0};
    public record Fix(int x,int y,int z,String before,String after){}
    private static String facing(String state){
        var matcher=FACING.matcher(state);
        return matcher.find()?matcher.group(1):null;
    }
    public static String opposite(String direction){
        return switch(direction){
            case "north"->"south";case "south"->"north";
            case "east"->"west";case "west"->"east";
            default->throw new IllegalArgumentException("Invalid cardinal direction "+direction);
        };
    }
    private static boolean source(String id){
        return id.equals("minecraft:redstone_block")||
            id.equals("minecraft:lever")||
            id.equals("minecraft:redstone_torch")||
            id.equals("minecraft:redstone_wall_torch")||
            id.equals("minecraft:daylight_detector")||
            id.equals("minecraft:target")||
            id.endsWith("_button")||id.endsWith("_pressure_plate");
    }
    private static boolean sink(String id){
        return id.equals("minecraft:redstone_lamp")||
            id.equals("minecraft:note_block")||
            id.equals("minecraft:piston")||
            id.equals("minecraft:sticky_piston")||
            id.equals("minecraft:dispenser")||
            id.equals("minecraft:dropper")||
            id.equals("minecraft:crafter")||
            id.equals("minecraft:command_block")||
            id.equals("minecraft:iron_door")||
            id.equals("minecraft:iron_trapdoor");
    }
    private static String neighbor(Blueprint b,int x,int y,int z,int dx,int dz){
        int nx=x+dx,nz=z+dz;
        if(nx<0||nx>=b.width||nz<0||nz>=b.length)return "minecraft:air";
        return Blueprint.id(b.get(nx,y,nz));
    }
    public static List<Fix> unambiguousReversals(Blueprint b){
        List<Fix> fix=new ArrayList<>();
        for(int y=0;y<b.height;y++)for(int z=0;z<b.length;z++)for(int x=0;x<b.width;x++){
            String state=b.get(x,y,z);
            if(!Blueprint.id(state).equals("minecraft:repeater"))continue;
            String actual=facing(state);
            if(actual==null)continue;
            String expected=null;
            for(int d=0;d<4;d++){
                String input=neighbor(b,x,y,z,DX[d],DZ[d]);
                int opposite=(d+2)%4;
                String output=neighbor(b,x,y,z,DX[opposite],DZ[opposite]);
                if(source(input)&&sink(output)){
                    if(expected!=null){expected=null;break;}
                    expected=DIRS[d];
                }
            }
            if(expected==null||expected.equals(actual))continue;
            // Side locking is legitimate; don't change a complex circuit
            // where a comparator/repeater may be intentionally set up.
            boolean ambiguous=false;
            for(int d=0;d<4;d++){
                if(DIRS[d].equals(expected)||DIRS[d].equals(opposite(expected)))continue;
                String side=neighbor(b,x,y,z,DX[d],DZ[d]);
                if(side.equals("minecraft:repeater")||side.equals("minecraft:comparator")){
                    ambiguous=true;break;
                }
            }
            if(ambiguous)continue;
            String adjusted=state.replace("facing="+actual,"facing="+expected);
            fix.add(new Fix(x,y,z,state,adjusted));
        }
        return List.copyOf(fix);
    }
}
