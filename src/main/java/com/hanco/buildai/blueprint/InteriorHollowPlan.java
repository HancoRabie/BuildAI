package com.hanco.buildai.blueprint;

import java.util.BitSet;

/**
 * Conservative, terrain-independent plan for excavate-INTERNAL-EMPTY-SPACE.
 * A set bit is a position whose FINAL BLUEPRINT STATE IS AIR, bounded in
 * opposite structural walls. An enclosed room has walls on both X and Z
 * axes; a hallway/tunnel has a continuous pair of parallel side walls.
 * We do NOT require blueprint floor or roof: many real structures have
 * open entrances, terrain floors or arched/open overhead sections.
 *
 * All blueprint non-AIR cells (walls, furniture, light, decorations) are
 * excluded by definition. This is conservative static geometry review,
 * not a flood fill that leaks outdoors through intentionally open doors.
 */
public final class InteriorHollowPlan {
    private InteriorHollowPlan(){}
    private static final int MAX_ROOM_SPAN=192;
    private static final int MIN_CORRIDOR_WALL_RUN=3;
    private static final int MAX_WALL_GAP=192;

    private static boolean structural(Blueprint b,int x,int y,int z){
        if(x<0||z<0||x>=b.width||z>=b.length||
           y<0||y>=b.height||b.isAir(x,y,z))return false;
        String id=Blueprint.id(b.get(x,y,z));
        return !id.contains("redstone")&&!id.contains("repeater")&&
            !id.contains("comparator")&&!id.contains("torch")&&
            !id.contains("lever")&&!id.contains("button")&&
            !id.contains("sign")&&!id.contains("rail")&&
            !id.contains("hopper")&&!id.contains("chest")&&
            !id.contains("carpet")&&!id.contains("flower")&&
            !id.contains("tall_grass")&&!id.contains("vine")&&
            !id.contains("fence")&&!id.contains("trapdoor")&&
            !id.contains("piston")&&!id.contains("observer")&&
            !id.contains("dispenser")&&!id.contains("dropper")&&
            !id.contains("scaffolding")&&!id.contains("ladder")&&
            !id.equals("minecraft:water")&&!id.equals("minecraft:lava");
    }

    private static boolean wallSegment(Blueprint b,int x,int y,int z){
        return structural(b,x,y,z)&&
            (structural(b,x,y-1,z)||structural(b,x,y+1,z));
    }
    private static boolean wall(Blueprint b,int x,int y,int z){
        // A two-high doorway/window breaks an otherwise continuous wall.
        // The floor below and ceiling above are NOT enough: otherwise a
        // mezzanine floor would incorrectly classify every room cell as a
        // wall. Require standing wall segments on BOTH SIDES of the gap.
        boolean at=structural(b,x,y,z);
        if(at)return wallSegment(b,x,y,z);
        boolean lintelAndSill=
            (structural(b,x,y-1,z)&&structural(b,x,y+2,z))||
            (structural(b,x,y-2,z)&&structural(b,x,y+1,z));
        if(!lintelAndSill)return false;
        return (wallSegment(b,x-1,y,z)&&wallSegment(b,x+1,y,z))||
            (wallSegment(b,x,y,z-1)&&wallSegment(b,x,y,z+1));
    }
    private static int volume(Blueprint b){
        return b.width*b.height*b.length;
    }

    private static boolean between(int near,int far,int point){
        return near>=0&&far>=0&&far-near>=3&&
            far-near<=MAX_WALL_GAP&&near<point&&point<far;
    }

    /** Require continuing VERTICAL standing walls on both sides of a
     * hallway. A one-off facade, column pair, statue legs or scattered
     * support columns are not a legitimate enclosed corridor. */
    private static boolean corridorWallsX(Blueprint b,int left,int right,
                                          int y,int z){
        return standingRunZ(b,left,y,z)>=MIN_CORRIDOR_WALL_RUN&&
               standingRunZ(b,right,y,z)>=MIN_CORRIDOR_WALL_RUN;
    }
    private static boolean corridorWallsZ(Blueprint b,int front,int back,
                                          int y,int x){
        return standingRunX(b,x,y,front)>=MIN_CORRIDOR_WALL_RUN&&
               standingRunX(b,x,y,back)>=MIN_CORRIDOR_WALL_RUN;
    }
    private static int standingRunZ(Blueprint b,int x,int y,int z){
        if(!wallSegment(b,x,y,z))return 0;
        int count=1;
        for(int z0=z-1;z0>=0&&z0>=z-4&&wallSegment(b,x,y,z0);z0--)count++;
        for(int z0=z+1;z0<b.length&&z0<=z+4&&wallSegment(b,x,y,z0);z0++)count++;
        return count;
    }
    private static int standingRunX(Blueprint b,int x,int y,int z){
        if(!wallSegment(b,x,y,z))return 0;
        int count=1;
        for(int x0=x-1;x0>=0&&x0>=x-4&&wallSegment(b,x0,y,z);x0--)count++;
        for(int x0=x+1;x0<b.width&&x0<=x+4&&wallSegment(b,x0,y,z);x0++)count++;
        return count;
    }

    /**
     * Three alternatives for a genuinely interior cell:
     * 1. Enclosed between standing walls in BOTH horizontal directions;
     * 2. Between continuous, parallel X-boundary corridor walls;
     * 3. Between continuous, parallel Z-boundary corridor walls.
     *
     * The strong both-axis case detects rooms with door apertures;
     * cases 2/3 detect roofless/terrain-floor corridors even with open
     * entrances at both ends. There is deliberately NO requirement for a
     * solid floor/roof above/below that exact coordinate.
     *
     * Only final blueprint AIR cells can appear in the mask. Any original
     * world blocks at those locations are removed before construction;
     * generated wall/room content cannot be removed by the hollow pass.
     */
    public static BitSet enclosedAir(Blueprint b){
        int volume=volume(b);
        BitSet xSpan=new BitSet(volume);
        BitSet xCorridor=new BitSet(volume);
        BitSet result=new BitSet(volume);
        if(b.width<4||b.length<4||b.height<2)return result;

        for(int y=0;y<b.height;y++){
            // Pair closest standing walls along X for each row.
            for(int z=0;z<b.length;z++){
                int[] nearestLeft=new int[b.width];
                int left=-1;
                for(int x=0;x<b.width;x++){
                    if(wall(b,x,y,z))left=x;
                    nearestLeft[x]=left;
                }
                int right=-1;
                for(int x=b.width-1;x>=0;x--){
                    if(wall(b,x,y,z))right=x;
                    if(!b.isAir(x,y,z))continue;
                    int l=nearestLeft[x];
                    if(between(l,right,x)){
                        int index=b.index(x,y,z);
                        xSpan.set(index);
                        if(corridorWallsX(b,l,right,y,z))xCorridor.set(index);
                    }
                }
            }
            // Pair closest standing walls along Z for each column.
            // Both-axis rooms require xSpan AND zSpan.
            // Narrow open-ended corridors may use either parallel-wall pair.
            for(int x=0;x<b.width;x++){
                int[] nearestFront=new int[b.length];
                int front=-1;
                for(int z=0;z<b.length;z++){
                    if(wall(b,x,y,z))front=z;
                    nearestFront[z]=front;
                }
                int back=-1;
                for(int z=b.length-1;z>=0;z--){
                    if(wall(b,x,y,z))back=z;
                    if(!b.isAir(x,y,z))continue;
                    int f=nearestFront[z],index=b.index(x,y,z);
                    boolean withinZ=between(f,back,z);
                    if((xSpan.get(index)&&withinZ)||xCorridor.get(index)||
                        (withinZ&&corridorWallsZ(b,f,back,y,x))){
                        result.set(index);
                    }
                }
            }
        }
        return result;
    }
}
