package com.hanco.buildai.blueprint;

import java.util.BitSet;

/**
 * Conservative blueprint-space hollowing classification.
 *
 * The authoritative blueprint contains ALL block varieties belonging to
 * the design. Geometry is evaluated across the complete vertical columns
 * of connected structural masonry (stone, mossy, chiseled, etc.) instead
 * of requiring identical blocks or wall height at an individual AIR voxel.
 *
 * Only blueprint AIR voxels are ever removable. Non-AIR blueprint content
 * (including furniture, decoration, wiring and the walls themselves) cannot
 * appear in the resulting removal mask.
 *
 * Requires structurally recognizable rooms or sustained parallel corridor
 * boundaries. Isolated columns, horizontal platforms and open exteriors
 * never qualify merely because they lie inside the build bounding box.
 * Geometry inspection is not a substitute for inspecting the real world.
 */
public final class InteriorHollowPlan {
    private InteriorHollowPlan(){}
    private static final int MAX_WALL_SPAN=192;
    private static final int MIN_CORRIDOR_RUN=3;

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


    private static int columnIndex(Blueprint b,int x,int z){
        return z*b.width+x;
    }
    private static boolean in2d(Blueprint b,int x,int z){
        return x>=0&&z>=0&&x<b.width&&z<b.length;
    }
    private static boolean inY(Blueprint b,int y){
        return y>=0&&y<b.height;
    }
    private static final int EAST=1,WEST=2,SOUTH=4,NORTH=8;
    private static int openFaces(Blueprint b,int x,int y,int z){
        // Which faces of this vertical *standing pair* actually open to
        // blueprint AIR? Track direction. Otherwise a thick foundation
        // exposes its OUTER face and is accidentally interpreted as a
        // corridor wall facing IN toward the solid foundation.
        int faces=0;
        if(in2d(b,x+1,z)&&(b.isAir(x+1,y,z)||b.isAir(x+1,y+1,z)))faces|=EAST;
        if(in2d(b,x-1,z)&&(b.isAir(x-1,y,z)||b.isAir(x-1,y+1,z)))faces|=WEST;
        if(in2d(b,x,z+1)&&(b.isAir(x,y,z+1)||b.isAir(x,y+1,z+1)))faces|=SOUTH;
        if(in2d(b,x,z-1)&&(b.isAir(x,y,z-1)||b.isAir(x,y+1,z-1)))faces|=NORTH;
        return faces;
    }

    private static WallColumns analyze(Blueprint b){
        int len=b.width*b.length;
        boolean[] wall=new boolean[len];
        int[] faces=new int[len],runX=new int[len],runZ=new int[len];
        int[] lowest=new int[len],highest=new int[len];
        java.util.Arrays.fill(lowest,Integer.MAX_VALUE);
        java.util.Arrays.fill(highest,-1);
        // Determine actual connected STRUCTURAL WALL COLUMNS, not the
        // Minecraft block material name or one horizontal slice. Adjacent
        // y cells must form a standing face exposed to nearby blueprint
        // AIR. A 3-layer solid foundation under the whole building will
        // therefore not become a fake cross-wall through every room.
        for(int z=0;z<b.length;z++)for(int x=0;x<b.width;x++){
            int p=columnIndex(b,x,z);
            for(int y=0;y+1<b.height;y++){
                if(structural(b,x,y,z)&&structural(b,x,y+1,z)){
                    int open=openFaces(b,x,y,z);
                    if(open==0)continue;
                    wall[p]=true;
                    faces[p]|=open;
                    lowest[p]=Math.min(lowest[p],y);
                    highest[p]=Math.max(highest[p],y+1);
                }
            }
        }
        // Cache horizontal wall connectivity ONCE. Ray calculations on
        // a large blueprint must not rescan 100s of wall blocks for every
        // candidate voxel or every Y level.
        for(int z=0;z<b.length;z++){
            int x=0;
            while(x<b.width){
                if(!wall[columnIndex(b,x,z)]){x++;continue;}
                int start=x;
                while(x<b.width&&wall[columnIndex(b,x,z)])x++;
                for(int v=start;v<x;v++)runX[columnIndex(b,v,z)]=x-start;
            }
        }
        for(int x=0;x<b.width;x++){
            int z=0;
            while(z<b.length){
                if(!wall[columnIndex(b,x,z)]){z++;continue;}
                int start=z;
                while(z<b.length&&wall[columnIndex(b,x,z)])z++;
                for(int v=start;v<z;v++)runZ[columnIndex(b,x,v)]=z-start;
            }
        }
        return new WallColumns(wall,faces,runX,runZ,lowest,highest);
    }

    private record WallColumns(boolean[] wall,int[] faces,int[] runX,int[] runZ,
                               int[] bottom,int[] top){
        boolean at(Blueprint b,int x,int z){
            return in2d(b,x,z)&&wall[columnIndex(b,x,z)];
        }
        boolean spans(Blueprint b,int x,int z,int y){
            if(!at(b,x,z))return false;
            int p=columnIndex(b,x,z);
            // Hollow ONLY within the standing-wall vertical extent.
            // An open-top building must never clear the jungle canopy
            // ABOVE its last wall row, even if the plan footprint is inside.
            return y>=bottom[p]&&y<=top[p];
        }
    }
    private static int runZ(Blueprint b,WallColumns w,int x,int z){
        return w.at(b,x,z)?w.runZ[columnIndex(b,x,z)]:0;
    }
    private static int runX(Blueprint b,WallColumns w,int x,int z){
        return w.at(b,x,z)?w.runX[columnIndex(b,x,z)]:0;
    }
    private static boolean face(Blueprint b,WallColumns w,int x,int z,int direction){
        return w.at(b,x,z)&&(w.faces[columnIndex(b,x,z)]&direction)!=0;
    }
    private static boolean between(int left,int right,int pos){
        return left>=0&&right>=0&&left<pos&&pos<right&&
            right-left>=3&&right-left<=MAX_WALL_SPAN;
    }
    private static boolean corridorAlongZ(Blueprint b,WallColumns w,
                                          int l,int r,int z,int y){
        // Both parallel walls must extend on BOTH sides of the cell.
        // This excludes the open approach just before the entrance, and
        // avoids clearing the door itself on a short building facade.
        return w.spans(b,l,z,y)&&w.spans(b,r,z,y)&&
            face(b,w,l,z,EAST)&&face(b,w,r,z,WEST)&&
            w.at(b,l,z-1)&&w.at(b,r,z-1)&&
            w.at(b,l,z+1)&&w.at(b,r,z+1)&&
            runZ(b,w,l,z)>=MIN_CORRIDOR_RUN&&
            runZ(b,w,r,z)>=MIN_CORRIDOR_RUN;
    }
    private static boolean corridorAlongX(Blueprint b,WallColumns w,
                                          int f,int back,int x,int y){
        return w.spans(b,x,f,y)&&w.spans(b,x,back,y)&&
            face(b,w,x,f,SOUTH)&&face(b,w,x,back,NORTH)&&
            w.at(b,x-1,f)&&w.at(b,x-1,back)&&
            w.at(b,x+1,f)&&w.at(b,x+1,back)&&
            runX(b,w,x,f)>=MIN_CORRIDOR_RUN&&
            runX(b,w,x,back)>=MIN_CORRIDOR_RUN;
    }

    /**
     * Pair nearest opposing wall COLUMNS in the plan view. For a cell to
     * clear, it must be inside a bounded room (X and Z wall pairs), or in
     * a corridor between two CONTINUOUS parallel walls.
     *
     * Wall pairs must span that cell's height (using the standing wall
     * column, not an exact same-Y material lookup). Final blueprint
     * non-air positions are never cleared.
     */
    public static BitSet enclosedAir(Blueprint b){
        BitSet selected=new BitSet(b.width*b.length*b.height);
        if(b.width<4||b.length<4||b.height<2)return selected;
        WallColumns w=analyze(b);
        int columns=b.width*b.length;
        boolean[] xBound=new boolean[columns],xCorridor=new boolean[columns];
        int[] xLeft=new int[columns],xRight=new int[columns];
        for(int y=0;y<b.height;y++){
            java.util.Arrays.fill(xBound,false);
            java.util.Arrays.fill(xCorridor,false);
            java.util.Arrays.fill(xLeft,-1);
            java.util.Arrays.fill(xRight,-1);
            for(int z=0;z<b.length;z++){
                int left=-1;
                int[] last=new int[b.width];
                for(int x=0;x<b.width;x++){
                    if(w.spans(b,x,z,y))left=x;
                    last[x]=left;
                }
                int right=-1;
                for(int x=b.width-1;x>=0;x--){
                    if(w.spans(b,x,z,y))right=x;
                    if(!b.isAir(x,y,z))continue;
                    int l=last[x],p=columnIndex(b,x,z);
                    if(between(l,right,x)&&face(b,w,l,z,EAST)&&
                       face(b,w,right,z,WEST)){
                        xBound[p]=true;
                        xLeft[p]=l;xRight[p]=right;
                        xCorridor[p]=corridorAlongZ(b,w,l,right,z,y);
                    }
                }
            }
            for(int x=0;x<b.width;x++){
                int front=-1;
                int[] last=new int[b.length];
                for(int z=0;z<b.length;z++){
                    if(w.spans(b,x,z,y))front=z;
                    last[z]=front;
                }
                int back=-1;
                for(int z=b.length-1;z>=0;z--){
                    if(w.spans(b,x,z,y))back=z;
                    if(!b.isAir(x,y,z))continue;
                    int f=last[z],p=columnIndex(b,x,z);
                    boolean zBound=between(f,back,z)&&
                        face(b,w,x,f,SOUTH)&&face(b,w,x,back,NORTH);
                    // The two-axis room rule requires its boundaries to
                    // have LATERAL wall continuity, avoiding isolated posts.
                    boolean room=xBound[p]&&zBound&&
                        runZ(b,w,xLeft[p],z)>=2&&
                        runZ(b,w,xRight[p],z)>=2&&
                        runX(b,w,x,f)>=2&&runX(b,w,x,back)>=2;
                    boolean corridor=xCorridor[p]||
                        (zBound&&corridorAlongX(b,w,f,back,x,y));
                    if(room||corridor)selected.set(b.index(x,y,z));
                }
            }
        }
        return selected;
    }
}
