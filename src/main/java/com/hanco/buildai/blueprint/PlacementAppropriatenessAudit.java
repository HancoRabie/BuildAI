package com.hanco.buildai.blueprint;

import java.util.*;

/**
 * GENERAL voxel-placement appropriateness checks.
 *
 * Static geometry inspection, NOT game-tick simulation. In particular,
 * static geometry cannot prove dust power, quasi-connectivity, torches'
 * dynamic state, or piston sequence timing. Never move/delete blocks here:
 * repairing a gap in one circuit can short two intentionally isolated paths.
 */
public final class PlacementAppropriatenessAudit {
    private PlacementAppropriatenessAudit(){}
    public record Findings(List<String> blockers,List<String> suggestions){}
    private static final int[][] AXES={
        {1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}
    };
    private static final int MAX_ITEMS=12;

    private static boolean inside(Blueprint b,int x,int y,int z){
        return x>=0&&y>=0&&z>=0&&x<b.width&&y<b.height&&z<b.length;
    }
    private static String id(Blueprint b,int x,int y,int z){
        return inside(b,x,y,z)?Blueprint.id(b.get(x,y,z)):"minecraft:air";
    }
    private static String property(String state,String key){
        String tag=key+"=";
        int bracket=state.indexOf('[');
        if(bracket<0)return null;
        int at=state.indexOf(tag,bracket);
        if(at<0 || (at>bracket+1 && state.charAt(at-1)!=','))return null;
        int from=at+tag.length(),end=from;
        while(end<state.length() && state.charAt(end)!=',' && state.charAt(end)!=']')end++;
        return state.substring(from,end);
    }
    private static int[] vector(String name){
        if(name==null)return null;
        return switch(name){
            case "north"->new int[]{0,0,-1};
            case "south"->new int[]{0,0,1};
            case "east"->new int[]{1,0,0};
            case "west"->new int[]{-1,0,0};
            case "up"->new int[]{0,1,0};
            case "down"->new int[]{0,-1,0};
            default->null;
        };
    }
    private static boolean same(int[] a,int x,int y,int z){
        return a!=null&&a[0]==x&&a[1]==y&&a[2]==z;
    }
    private static boolean rail(String id){
        return id.equals("minecraft:rail")||id.equals("minecraft:powered_rail")||
            id.equals("minecraft:activator_rail")||id.equals("minecraft:detector_rail");
    }
    private static boolean redstoneInput(String id){
        return id.equals("minecraft:redstone_wire")||id.equals("minecraft:redstone_block")||
            id.equals("minecraft:redstone_torch")||
            id.equals("minecraft:redstone_wall_torch")||
            id.equals("minecraft:lever")||id.endsWith("_button")||
            id.endsWith("_pressure_plate")||
            id.equals("minecraft:repeater")||id.equals("minecraft:comparator")||
            id.equals("minecraft:observer")||id.equals("minecraft:daylight_detector")||
            id.equals("minecraft:target")||id.equals("minecraft:sculk_sensor")||
            id.equals("minecraft:calibrated_sculk_sensor")||
            id.equals("minecraft:detector_rail")||id.equals("minecraft:trapped_chest")||
            id.equals("minecraft:tripwire_hook");
    }

    /**
     * Is source block at (sx,sy,sz) PLAUSIBLY able to feed neighbor
     * (tx,ty,tz)? It is intentionally an overapproximation for dust and
     * lever sources; orientation of directional repeaters and observers
     * is checked exactly. Returning true is NOT a certificate of power.
     */
    private static boolean couldPower(Blueprint b,int sx,int sy,int sz,
                                      int tx,int ty,int tz){
        if(!inside(b,sx,sy,sz))return false;
        int dx=tx-sx,dy=ty-sy,dz=tz-sz;
        if(Math.abs(dx)+Math.abs(dy)+Math.abs(dz)!=1)return false;
        String state=b.get(sx,sy,sz),id=Blueprint.id(state);
        if(!redstoneInput(id))return false;
        if(id.equals("minecraft:repeater")||id.equals("minecraft:comparator")||
           id.equals("minecraft:observer")){
            // Java 1.21.1 'facing' for repeaters/comparators/observers
            // denotes their REAR/INPUT/SENSING side. Output is opposite.
            int[] facing=vector(property(state,"facing"));
            return facing==null || same(facing,-dx,-dy,-dz);
        }
        return true;
    }

    private static boolean solidConductor(String id){
        if(id.equals("minecraft:air")||id.equals("minecraft:cave_air")||
           id.equals("minecraft:void_air")||id.equals("minecraft:water")||
           id.equals("minecraft:lava"))return false;
        if(id.contains("glass")||id.contains("slab")||id.contains("stairs")||
           id.contains("leaves")||id.contains("fence")||id.contains("pane")||
           id.contains("torch")||id.contains("wire")||id.contains("rail")||
           id.contains("hopper")||id.contains("chest")||
           id.contains("repeater")||id.contains("comparator"))return false;
        return id.equals("minecraft:stone")||id.equals("minecraft:smooth_stone")||
            id.equals("minecraft:andesite")||id.equals("minecraft:diorite")||
            id.equals("minecraft:granite")||id.equals("minecraft:polished_granite")||
            id.equals("minecraft:polished_diorite")||
            id.equals("minecraft:cobblestone")||
            id.equals("minecraft:deepslate")||id.equals("minecraft:obsidian")||
            id.equals("minecraft:bedrock")||id.equals("minecraft:dirt")||
            id.equals("minecraft:grass_block")||
            id.endsWith("_bricks")||id.endsWith("_planks")||
            id.endsWith("_concrete")||id.endsWith("_terracotta")||
            id.equals("minecraft:iron_block")||id.equals("minecraft:gold_block")||
            id.equals("minecraft:quartz_block")||
            id.equals("minecraft:polished_andesite")||
            id.equals("minecraft:polished_deepslate");
    }
    private static boolean conductorHasPowerCandidate(
        Blueprint b,int cx,int cy,int cz,int lampX,int lampY,int lampZ){
        for(int[] v:AXES){
            int sx=cx+v[0],sy=cy+v[1],sz=cz+v[2];
            if(sx==lampX&&sy==lampY&&sz==lampZ)continue;
            if(couldPower(b,sx,sy,sz,cx,cy,cz))return true;
        }
        return false;
    }
    private static boolean canReceivePower(Blueprint b,int x,int y,int z){
        for(int[] v:AXES){
            int nx=x+v[0],ny=y+v[1],nz=z+v[2];
            if(couldPower(b,nx,ny,nz,x,y,z))return true;
            String neighbor=id(b,nx,ny,nz);
            if(solidConductor(neighbor)&&
                conductorHasPowerCandidate(b,nx,ny,nz,x,y,z))return true;
        }
        return false;
    }
    private static String nearbyUnconnectedSignal(Blueprint b,int x,int y,int z){
        for(int[] v:AXES){
            int mx=x+v[0],my=y+v[1],mz=z+v[2];
            int px=x+2*v[0],py=y+2*v[1],pz=z+2*v[2];
            if(!inside(b,px,py,pz)||!inside(b,mx,my,mz))continue;
            if(!b.isAir(mx,my,mz))continue;
            if(redstoneInput(id(b,px,py,pz)))
                return " Possible one-cell AIR gap at ("+mx+","+my+","+mz+
                    ") between endpoint and "+id(b,px,py,pz)+
                    " at ("+px+","+py+","+pz+").";
        }
        return "";
    }
    private static boolean border(Blueprint b,int x,int y,int z){
        return x==0||z==0||y==0||x==b.width-1||
            z==b.length-1||y==b.height-1;
    }
    private static boolean supportFromBlueprint(Blueprint b,int x,int y,int z){
        return inside(b,x,y,z)&&!b.isAir(x,y,z);
    }
    private static int[] attachmentOfWall(String facing,int x,int y,int z){
        int[] face=vector(facing);
        return face==null?null:new int[]{x-face[0],y-face[1],z-face[2]};
    }
    private static String xyz(int x,int y,int z){
        return "("+x+","+y+","+z+")";
    }

    public static Findings inspect(Blueprint blueprint,String request){
        List<String> blockers=new ArrayList<>(),suggestions=new ArrayList<>();
        int unpoweredLampCount=0,unsupportedCount=0,repeaterCount=0;
        // A deliberately unpowered lamp in a decorative sculpture is
        // valid. Escalate endpoint disconnection to a repair blocker only
        // when the requested structure actually relies on redstone.
        boolean functionalPower=RedstoneKnowledge.isRedstoneRequest(request,null);
        int unpoweredActuators=0,blockedPistons=0;
        int yMax=blueprint.height,zMax=blueprint.length,xMax=blueprint.width;
        for(int y=0;y<yMax;y++)for(int z=0;z<zMax;z++)for(int x=0;x<xMax;x++){
            String state=blueprint.get(x,y,z),block=Blueprint.id(state);
            if(block.equals("minecraft:redstone_lamp") && !canReceivePower(blueprint,x,y,z)){
                unpoweredLampCount++;
                if(unpoweredLampCount<=MAX_ITEMS){
                    String message="PLACEMENT: Lamp "+xyz(x,y,z)+
                        " has no plausible adjacent redstone power source or "+
                        "powered conducting block in the blueprint."+
                        nearbyUnconnectedSignal(blueprint,x,y,z)+
                        " A visually nearby but nonadjacent wire/repeater "+
                        "cannot activate the lamp.";
                    if(border(blueprint,x,y,z))
                        suggestions.add(message+" It touches the blueprint "+
                            "boundary, so externally supplied power is possible.");
                    else if(functionalPower)blockers.add(message);
                    else suggestions.add(message+
                        " This may be intentional decorative placement.");
                }
            }
            // Broader mechanical endpoints can use Java quasi-connectivity,
            // indirect blocks, or pulses; a missing DIRECT feed is therefore
            // only a review suggestion, never proof of a broken actuator.
            boolean actuator=block.equals("minecraft:piston")||
                block.equals("minecraft:sticky_piston")||
                block.equals("minecraft:dispenser")||
                block.equals("minecraft:dropper")||
                block.equals("minecraft:crafter")||
                block.equals("minecraft:note_block")||
                block.equals("minecraft:iron_door")||
                block.equals("minecraft:iron_trapdoor");
            if(actuator && functionalPower && !canReceivePower(blueprint,x,y,z) &&
               !border(blueprint,x,y,z) && unpoweredActuators++<MAX_ITEMS)
                suggestions.add("PLACEMENT: "+block+" at "+xyz(x,y,z)+
                    " has no direct plausible redstone neighbor. Check "+
                    "intended activation, including indirect block power, "+
                    "block updates and quasi-connectivity where relevant.");
            if((block.equals("minecraft:piston")||
                block.equals("minecraft:sticky_piston"))){
                int[] face=vector(property(state,"facing"));
                if(face!=null){
                    int nx=x+face[0],ny=y+face[1],nz=z+face[2];
                    String ahead=id(blueprint,nx,ny,nz);
                    if((ahead.equals("minecraft:obsidian")||
                        ahead.equals("minecraft:bedrock")||
                        ahead.equals("minecraft:crying_obsidian")||
                        ahead.equals("minecraft:reinforced_deepslate")||
                        ahead.equals("minecraft:end_portal_frame")) &&
                        blockedPistons++<MAX_ITEMS)
                        suggestions.add("PLACEMENT: Piston "+xyz(x,y,z)+
                            " extends toward an immovable "+ahead+
                            " at "+xyz(nx,ny,nz)+
                            "; it will not push this obstruction.");
                }
            }
            // A support-less part would be destroyed or impossible after
            // clearing the design volume. Bottom-face terrain is unknown,
            // and non-excavation overlay may deliberately use local terrain.
            boolean floorSupported=block.equals("minecraft:redstone_wire")||
                block.equals("minecraft:repeater")||
                block.equals("minecraft:comparator")||rail(block)||
                block.equals("minecraft:redstone_torch");
            if(floorSupported && y>0 && blueprint.isAir(x,y-1,z)){
                unsupportedCount++;
                if(unsupportedCount<=MAX_ITEMS)
                    suggestions.add("PLACEMENT: "+block+" at "+xyz(x,y,z)+
                        " has AIR beneath it in the blueprint. "+
                        "Add a valid supporting block; excavation may "+
                        "cause the component to drop.");
            }
            if(block.equals("minecraft:redstone_wall_torch")||
               block.endsWith("_wall_sign")||
               block.endsWith("_wall_hanging_sign")||
               ((block.endsWith("_button")||block.equals("minecraft:lever"))&&
                   "wall".equals(property(state,"face")))){
                int[] support=attachmentOfWall(property(state,"facing"),x,y,z);
                if(support!=null && inside(blueprint,support[0],support[1],support[2])&&
                    blueprint.isAir(support[0],support[1],support[2])&&
                    unsupportedCount++<MAX_ITEMS)
                    suggestions.add("PLACEMENT: Wall-mounted "+block+" at "+
                        xyz(x,y,z)+" has no support at "+
                        xyz(support[0],support[1],support[2])+".");
            }
            if(block.equals("minecraft:repeater")||
               block.equals("minecraft:comparator")){
                // Catch obviously misplaced repeaters powering no input:
                // do not assume every repeater is a one-way signal path,
                // as lock-gates use side inputs deliberately.
                String facing=property(state,"facing");
                int[] rear=vector(facing);
                if(rear!=null){
                    int rx=x+rear[0],ry=y+rear[1],rz=z+rear[2];
                    if(inside(blueprint,rx,ry,rz)&&blueprint.isAir(rx,ry,rz)&&
                       repeaterCount++<MAX_ITEMS)
                        suggestions.add("PLACEMENT: "+block+" at "+
                            xyz(x,y,z)+" faces an AIR rear/input cell "+
                            xyz(rx,ry,rz)+
                            ". Confirm intentional configuration and signal path.");
                }
            }
        }
        if(unpoweredLampCount>MAX_ITEMS)
            blockers.add("PLACEMENT: "+(unpoweredLampCount-MAX_ITEMS)+
                " more lamp(s) have no plausible adjacent power feed; examples above.");
        if(unsupportedCount>MAX_ITEMS)
            suggestions.add("PLACEMENT: Further unsupported blocks found: "+
                (unsupportedCount-MAX_ITEMS)+"; inspect support layers.");
        if(!blockers.isEmpty() || !suggestions.isEmpty())
            suggestions.add("PLACEMENT QA: Static endpoint/support checks are "+
                "not a power-level or tick simulation. Fix only verified "+
                "connections; do not bridge deliberate circuit isolation. "+
                "Test every machine in Java 1.21.1 after placement.");
        return new Findings(List.copyOf(blockers),List.copyOf(suggestions));
    }
}
