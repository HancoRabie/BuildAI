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
    private static boolean exposedPair(Blueprint b,int x,int y,int z){
        // A 2+-block floor/foundation sheet is NOT a vertical wall:
        // interior cells of a thick platform have no horizontally adjacent
        // AIR at either of the two stacked heights. A side wall has at
        // least one exposed side into its proposed open room/corridor.
        final int[][] dirs={{1,0},{-1,0},{0,1},{0,-1}};
        for(int[] dir:dirs){
            int nx=x+dir[0],nz=z+dir[1];
            if(in2d(b,nx,nz)&&
                (b.isAir(nx,y,nz)||b.isAir(nx,y+1,nz)))
                return true;
        }
        return false;
    }

    private static WallColumns analyze(Blueprint b){
        int len=b.width*b.length;
        boolean[] wall=new boolean[len];
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
                if(structural(b,x,y,z)&&structural(b,x,y+1,z)&&
                   exposedPair(b,x,y,z)){
                    wall[p]=true;
                    lowest[p]=Math.min(lowest[p],y);
                    highest[p]=Math.max(highest[p],y+1);
                }
            }
        }
        return new WallColumns(wall,lowest,highest);
    }

    private record WallColumns(boolean[] wall,int[] bottom,int[] top){
        boolean at(Blueprint b,int x,int z){
            return in2d(b,x,z)&&wall[columnIndex(b,x,z)];
        }
        boolean spans(Blueprint b,int x,int z,int y){
            if(!at(b,x,z))return false;
            int p=columnIndex(b,x,z);
            // Allows 1-block decorative ledge at upper/lower ends.
            // Never projects a ground-level wall all the way up through
            // empty open sky or across a multi-story structure.
            return y>=bottom[p]&&y<=top[p]+1;
        }
    }
    private static int runZ(Blueprint b,WallColumns w,int x,int z){
        if(!w.at(b,x,z))return 0;
        int count=1;
        for(int v=z-1;v>=0&&w.at(b,x,v);v--)count++;
        for(int v=z+1;v<b.length&&w.at(b,x,v);v++)count++;
        return count;
    }
    private static int runX(Blueprint b,WallColumns w,int x,int z){
        if(!w.at(b,x,z))return 0;
        int count=1;
        for(int v=x-1;v>=0&&w.at(b,v,z);v--)count++;
        for(int v=x+1;v<b.width&&w.at(b,v,z);v++)count++;
        return count;
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
            w.at(b,l,z-1)&&w.at(b,r,z-1)&&
            w.at(b,l,z+1)&&w.at(b,r,z+1)&&
            runZ(b,w,l,z)>=MIN_CORRIDOR_RUN&&
            runZ(b,w,r,z)>=MIN_CORRIDOR_RUN;
    }
    private static boolean corridorAlongX(Blueprint b,WallColumns w,
                                          int f,int back,int x,int y){
        return w.spans(b,x,f,y)&&w.spans(b,x,back,y)&&
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
                    if(between(l,right,x)){
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
                    boolean zBound=between(f,back,z);
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
