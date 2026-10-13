package com.hanco.buildai.blueprint;

import java.util.BitSet;

/**
 * Conservative, terrain-independent plan for excavate-INTERNAL-EMPTY-SPACE.
 * A set bit is a position whose FINAL BLUEPRINT STATE IS AIR, bounded in
 * both horizontal axes by structural walls AND vertically by a floor and a
 * ceiling. All built blocks, including furniture, lights and decoration,
 * are excluded by definition.
 *
 * This is geometry classification, not 3D flood-fill: intentional door
 * and window openings should not make the entire interior "exterior".
 * A hollow operation MUST fail closed when no convincing room is found.
 */
public final class InteriorHollowPlan {
    private InteriorHollowPlan(){}
    private static final int MAX_ROOM_SPAN=192;
    private static final int MAX_FLOOR_TO_CEILING=96;

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

    public static BitSet enclosedAir(Blueprint b){
        BitSet insideX=new BitSet(volume(b));
        BitSet insideBoth=new BitSet(volume(b));
        if(b.width<4||b.length<4||b.height<4)return insideBoth;

        for(int y=1;y<b.height-1;y++){
            // Horizontal ray crossing in X (walls LEFT and RIGHT).
            for(int z=1;z<b.length-1;z++){
                int left=-1;
                int[] nearestLeft=new int[b.width];
                for(int x=0;x<b.width;x++){
                    if(wall(b,x,y,z))left=x;
                    nearestLeft[x]=left;
                }
                int right=-1;
                for(int x=b.width-1;x>=0;x--){
                    if(wall(b,x,y,z))right=x;
                    if(x==0||x==b.width-1||b.isAir(x,y,z)==false)continue;
                    int l=nearestLeft[x];
                    if(l>=0&&right>=0&&right-l>=3&&
                       right-l<=MAX_ROOM_SPAN&&l<x&&x<right)
                        insideX.set(b.index(x,y,z));
                }
            }
            // Orthogonal Z crossing. Both axes MUST be enclosed, so roofed
            // overhangs, balconies and open platforms cannot be excavated.
            for(int x=1;x<b.width-1;x++){
                int front=-1;
                int[] nearestFront=new int[b.length];
                for(int z=0;z<b.length;z++){
                    if(wall(b,x,y,z))front=z;
                    nearestFront[z]=front;
                }
                int back=-1;
                for(int z=b.length-1;z>=0;z--){
                    if(wall(b,x,y,z))back=z;
                    if(z==0||z==b.length-1)continue;
                    int i=b.index(x,y,z),f=nearestFront[z];
                    if(insideX.get(i)&&f>=0&&back>=0&&
                       back-f>=3&&back-f<=MAX_ROOM_SPAN&&f<z&&z<back)
                        insideBoth.set(i);
                }
            }
        }

        // For EACH interior-air column require floor and ceiling in the
        // blueprint. Runs in O(volume) without allocating huge 3D arrays.
        for(int z=1;z<b.length-1;z++)for(int x=1;x<b.width-1;x++){
            int lastFloor=-1;
            for(int y=0;y<b.height;y++){
                if(structural(b,x,y,z))lastFloor=y;
                int i=b.index(x,y,z);
                if(insideBoth.get(i)&&
                    (lastFloor<0||y-lastFloor>MAX_FLOOR_TO_CEILING))
                    insideBoth.clear(i);
            }
            int nextCeiling=-1;
            for(int y=b.height-1;y>=0;y--){
                if(structural(b,x,y,z))nextCeiling=y;
                int i=b.index(x,y,z);
                if(insideBoth.get(i)&&
                    (nextCeiling<0||nextCeiling-y>MAX_FLOOR_TO_CEILING))
                    insideBoth.clear(i);
            }
        }
        return insideBoth;
    }
}
