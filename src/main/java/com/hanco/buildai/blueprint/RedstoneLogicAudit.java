package com.hanco.buildai.blueprint;

import java.util.*;
import java.util.regex.*;

/**
 * Conservative STATIC redstone-logic topology review. It cannot compute
 * Minecraft tick behavior or certify truth tables: only flag geometries
 * that deserve targeted attention during in-world testing. No mutations.
 */
public final class RedstoneLogicAudit {
    private RedstoneLogicAudit(){}
    private static final Pattern FACING=Pattern.compile(
        "(?:^|\\[|,)facing=(north|south|east|west)");
    private static final Pattern BOOLEAN=Pattern.compile(
        "(?i)\\b(?:xor|xnor|and gate|nand|nor gate|majority|voting|selector|"+
        "logic gate|interlock|boolean)\\b");
    private static final int[][] NEIGHBORS={
        {1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}
    };
    private static boolean in(Blueprint b,int x,int y,int z){
        return x>=0&&y>=0&&z>=0&&x<b.width&&y<b.height&&z<b.length;
    }
    private static String id(Blueprint b,int x,int y,int z){
        return in(b,x,y,z)?Blueprint.id(b.get(x,y,z)):"minecraft:air";
    }
    private static boolean signalCandidate(String id){
        return id.equals("minecraft:redstone_wire")||
            id.equals("minecraft:repeater")||
            id.equals("minecraft:comparator")||
            id.equals("minecraft:redstone_block")||
            id.equals("minecraft:redstone_torch")||
            id.equals("minecraft:redstone_wall_torch")||
            id.equals("minecraft:observer")||
            id.equals("minecraft:lever")||
            id.endsWith("_button")||id.endsWith("_pressure_plate");
    }
    private static boolean nearLamp(Blueprint b,int x,int y,int z){
        for(int[] d:NEIGHBORS)
            if(id(b,x+d[0],y+d[1],z+d[2]).equals("minecraft:redstone_lamp"))
                return true;
        return false;
    }
    private static int[] attachment(Blueprint b,int x,int y,int z,String state){
        if(Blueprint.id(state).equals("minecraft:redstone_torch"))
            return new int[]{x,y-1,z};
        Matcher m=FACING.matcher(state);
        if(!m.find())return null;
        return switch(m.group(1)){
            case "east"->new int[]{x-1,y,z};
            case "west"->new int[]{x+1,y,z};
            case "north"->new int[]{x,y,z+1};
            case "south"->new int[]{x,y,z-1};
            default->null;
        };
    }

    /**
     * This is deliberately a WEAK possibility filter, not a Minecraft
     * strong/weak power simulation. It only warns when the torch's support
     * has no plausible input within direct neighboring voxels.
     */
    private static boolean hasPotentialControl(Blueprint b,int x,int y,int z,
                                               int tx,int ty,int tz){
        for(int[] d:NEIGHBORS){
            int nx=x+d[0],ny=y+d[1],nz=z+d[2];
            if(nx==tx&&ny==ty&&nz==tz)continue; // torch itself not its control
            if(signalCandidate(id(b,nx,ny,nz)))return true;
        }
        return false;
    }

    public static List<String> inspect(Blueprint b,String request){
        if(request==null||!BOOLEAN.matcher(request).find())return List.of();
        List<String> warnings=new ArrayList<>();
        int torchRisks=0,torches=0,comparators=0,repeaters=0;
        int lamps=0,wire=0;
        for(int y=0;y<b.height;y++)for(int z=0;z<b.length;z++)for(int x=0;x<b.width;x++){
            String state=b.get(x,y,z),block=Blueprint.id(state);
            if(block.equals("minecraft:redstone_wire"))wire++;
            if(block.equals("minecraft:repeater"))repeaters++;
            if(block.equals("minecraft:comparator"))comparators++;
            if(block.equals("minecraft:redstone_lamp"))lamps++;
            if(!block.equals("minecraft:redstone_torch")&&
               !block.equals("minecraft:redstone_wall_torch"))continue;
            torches++;
            if(!nearLamp(b,x,y,z))continue;
            int[] support=attachment(b,x,y,z,state);
            if(support==null){
                if(torchRisks++<5)warnings.add(
                    "LOGIC TOPOLOGY: Output-adjacent wall torch at "+
                    x+","+y+","+z+" has no explicit facing; verify its attachment block.");
            }else if(!hasPotentialControl(b,support[0],support[1],support[2],x,y,z)){
                if(torchRisks++<5)warnings.add(
                    "LOGIC TOPOLOGY: Torch adjacent to output lamp at "+
                    x+","+y+","+z+" may be ALWAYS ON: its attachment block at "+
                    support[0]+","+support[1]+","+support[2]+
                    " has no immediately adjacent potential control/power path. "+
                    "Trace how the planned inputs turn THIS SUPPORT BLOCK on/off "+
                    "for every required truth-table state.");
            }
        }
        if(lamps>0&&wire>0&&torches==0&&comparators==0&&repeaters>0){
            warnings.add("LOGIC TOPOLOGY: Binary circuit contains wire and repeaters "+
                "but no torch/comparator gate. Ordinary repeaters only transmit/delay "+
                "unless deliberately SIDE-LOCKED; verify the actual conditional "+
                "logic rather than assuming converging input wires implement XOR/AND/selector.");
        }
        if(!warnings.isEmpty())warnings.add(
            "LOGIC TOPOLOGY: These are static suspects, not confirmed failures. "+
            "Validate power to torch support, output idle OFF state, all truth-table "+
            "input combinations and branch isolation IN GAME.");
        return List.copyOf(warnings);
    }
}
