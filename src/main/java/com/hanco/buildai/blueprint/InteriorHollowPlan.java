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
        // Geometry-only rule: every retained non-AIR block is part of the
        // design, regardless of registry ID. A chest, redstone component,
        // pot, flower, rail, slab or pointed dripstone is an architectural
        // boundary just as surely as a stone brick. Coherent standing-wall
        // checks (vertical pairs, inward AIR face and horizontal continuity)
        // provide spatial evidence; block TYPE never disqualifies a boundary.
        // Foundation omissions are first turned into AIR in a temporary
        // geometry-only view by the overloaded enclosedAir() method.
        return x>=0&&z>=0&&y>=0&&
            x<b.width&&z<b.length&&y<b.height&&!b.isAir(x,y,z);
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
     * Structural footprint for foundation decisions, intentionally separate
     * from the 3D AIR-only excavation mask.
     *
     * Walls may start ABOVE a foundation or surround a basement filled with
     * dirt in the blueprint: a 2D wall/interior plan protects these cells at
     * ALL candidate foundation Y levels. Requiring AIR at the same Y would
     * mistakenly erase intentional filled basements and garden dirt.
     *
     * The same inward-facing, continuous-wall checks as hollowing prevent
     * foundation slab edges or isolated posts from defining a fake room.
     */
    public static BitSet protectedFootprint(Blueprint b){
        int size=b.width*b.length;
        BitSet result=new BitSet(size);
        if(b.width<4||b.length<4||b.height<2)return result;
        WallColumns w=analyze(b);
        // Don't mark every tall block as a wall! A thick, disposable
        // dirt foundation has exposed OUTER vertical faces at its edges,
        // which can look like standing walls in isolation. First classify
        // ACTUAL wall-enclosed space; protect only wall columns bordering it.

        // At every X position, identify the nearest north/south walls
        // once. Avoid O(footprint * corridor length) repeated ray scans.
        int[] north=new int[size],south=new int[size];
        for(int x=0;x<b.width;x++){
            int previous=-1;
            for(int z=0;z<b.length;z++){
                if(w.at(b,x,z)&&face(b,w,x,z,SOUTH))previous=z;
                north[columnIndex(b,x,z)]=previous;
            }
            previous=-1;
            for(int z=b.length-1;z>=0;z--){
                if(w.at(b,x,z)&&face(b,w,x,z,NORTH))previous=z;
                south[columnIndex(b,x,z)]=previous;
            }
        }
        // Primary X-facing boundaries (both full rooms and long tunnels).
        for(int z=0;z<b.length;z++){
            int[] left=new int[b.width];
            int previous=-1;
            for(int x=0;x<b.width;x++){
                if(w.at(b,x,z)&&face(b,w,x,z,EAST))previous=x;
                left[x]=previous;
            }
            previous=-1;
            for(int x=b.width-1;x>=0;x--){
                if(w.at(b,x,z)&&face(b,w,x,z,WEST))previous=x;
                int l=left[x],r=previous;
                if(!between(l,r,x))continue;
                int p=columnIndex(b,x,z),f=north[p],back=south[p];
                boolean room=between(f,back,z)&&
                    runZ(b,w,l,z)>=2&&runZ(b,w,r,z)>=2&&
                    runX(b,w,x,f)>=2&&runX(b,w,x,back)>=2;
                boolean corridor=runZ(b,w,l,z)>=MIN_CORRIDOR_RUN&&
                    runZ(b,w,r,z)>=MIN_CORRIDOR_RUN&&
                    w.at(b,l,z-1)&&w.at(b,r,z-1)&&
                    w.at(b,l,z+1)&&w.at(b,r,z+1);
                if(room||corridor)result.set(p);
            }
        }
        // Orthogonal long east/west corridors (no roof/end walls needed).
        for(int x=0;x<b.width;x++)for(int z=0;z<b.length;z++){
            int p=columnIndex(b,x,z),f=north[p],back=south[p];
            if(!result.get(p)&&between(f,back,z)&&
               runX(b,w,x,f)>=MIN_CORRIDOR_RUN&&
               runX(b,w,x,back)>=MIN_CORRIDOR_RUN&&
               w.at(b,x-1,f)&&w.at(b,x-1,back)&&
               w.at(b,x+1,f)&&w.at(b,x+1,back))
                result.set(p);
        }
        // Add walls bordering validated interiors, INCLUDING matching
        // dirt/stone wall blocks on each scanned foundation layer.
        // Work from a snapshot so the expansion cannot spread across
        // unrelated podium edges or across distant parts of the blueprint.
        BitSet enclosed=(BitSet)result.clone();
        for(int p=enclosed.nextSetBit(0);p>=0;p=enclosed.nextSetBit(p+1)){
            int x=p%b.width,z=p/b.width;
            for(int dz=-2;dz<=2;dz++)for(int dx=-2;dx<=2;dx++){
                int nx=x+dx,nz=z+dz;
                if(w.at(b,nx,nz))result.set(columnIndex(b,nx,nz));
            }
        }
        return result;
    }

    /**
     * Build the interior AIR write-set for hollow:yes. This is an EXPLICIT
     * construction contract: the world must be AIR at each selected position,
     * regardless of whether it currently contains dirt, logs or leaves.
     *
     * At each standing-wall elevation:
     *   1. Identify room interiors by flood-filling the PLAN from outside.
     *      Seal only small, verified gaps between continuous wall segments
     *      (doors); never flood out of a doorway into the jungle.
     *   2. Separately accept sustained paired-wall corridors with open ends.
     *      Their longitudinal extent MUST stop where both walls stop.
     *   3. Only select actual blueprint AIR, never furniture/wiring/walls.
     *
     * An exposed roof/terrace is not a room merely because its border is tall:
     * walls need an inward-facing opening and a continuing wall run.
     */
    /**
     * Analyze only the geometry that will actually be built. Suppressed
     * foundation blocks are ignored as potential "walls" in every ray
     * (east/west, north/south and up/down); temporary construction supports
     * are never part of the schematic and are already absent.
     *
     * Omitted foundation cells themselves must NOT be excavated by
     * hollow:yes. The write-set contains original schematic AIR only.
     */
    public static BitSet enclosedAir(Blueprint original,java.util.Set<Integer> omitted){
        java.util.Objects.requireNonNull(original);
        if(omitted==null||omitted.isEmpty())return enclosedAir(original);
        Blueprint geometry=original.withoutBlocks(omitted);
        BitSet result=enclosedAir(geometry);
        for(int i:omitted)result.clear(i);
        for(int i=result.nextSetBit(0);i>=0;i=result.nextSetBit(i+1)){
            int x=i%original.width,z=(i/original.width)%original.length;
            int y=i/(original.width*original.length);
            if(!original.isAir(x,y,z))
                throw new IllegalStateException(
                    "Interior classification selected original design block "+
                    x+","+y+","+z);
        }
        return result;
    }

    public static BitSet enclosedAir(Blueprint b){
        BitSet selected=new BitSet(b.width*b.length*b.height);
        if(b.width<4||b.length<4||b.height<2)return selected;
        WallColumns w=analyze(b);
        int cells=b.width*b.length;
        int[] queue=new int[cells];
        BitSet boundary=new BitSet(cells);
        BitSet outside=new BitSet(cells);
        // AIR in wall doorway openings is intentionally NOT indoor volume.
        BitSet sealedOpenings=new BitSet(cells*b.height);
        for(int y=0;y<b.height;y++){
            boundary.clear();
            outside.clear();
            // Standing walls with an inward face form our room perimeter.
            // Never interpret the exposed *outer* face of a solid platform
            // as a boundary enclosing the platform itself.
            for(int z=0;z<b.length;z++)for(int x=0;x<b.width;x++){
                if(!w.spans(b,x,z,y))continue;
                int p=columnIndex(b,x,z);
                if((runX(b,w,x,z)>=2||runZ(b,w,x,z)>=2)&&
                   faceAtHeight(b,w,x,z,y))
                    boundary.set(p);
            }
            // Seal doorway-sized gaps only if they are within an actual
            // continuous wall run. This affects classification alone;
            // door AIR remains open in the constructed world.
            BitSet original=(BitSet)boundary.clone();
            for(int z=0;z<b.length;z++)for(int x=1;x<b.width-1;x++){
                if(original.get(columnIndex(b,x,z)))continue;
                for(int gap=1;gap<=2&&x+gap+1<b.width;gap++){
                    if(!original.get(columnIndex(b,x+gap,z)))continue;
                    boolean empty=true;
                    for(int k=0;k<gap;k++)
                        if(original.get(columnIndex(b,x+k,z)))empty=false;
                    if(!empty)continue;
                    if(!original.get(columnIndex(b,x-1,z))||
                       !original.get(columnIndex(b,x+gap+1,z)))continue;
                    for(int k=0;k<gap;k++){
                        boundary.set(columnIndex(b,x+k,z));
                        if(b.isAir(x+k,y,z))
                            sealedOpenings.set(b.index(x+k,y,z));
                    }
                    break;
                }
            }
            for(int x=0;x<b.width;x++)for(int z=1;z<b.length-1;z++){
                if(original.get(columnIndex(b,x,z)))continue;
                for(int gap=1;gap<=2&&z+gap+1<b.length;gap++){
                    if(!original.get(columnIndex(b,x,z+gap)))continue;
                    boolean empty=true;
                    for(int k=0;k<gap;k++)
                        if(original.get(columnIndex(b,x,z+k)))empty=false;
                    if(!empty)continue;
                    if(!original.get(columnIndex(b,x,z-1))||
                       !original.get(columnIndex(b,x,z+gap+1)))continue;
                    for(int k=0;k<gap;k++){
                        boundary.set(columnIndex(b,x,z+k));
                        if(b.isAir(x,y,z+k))
                            sealedOpenings.set(b.index(x,y,z+k));
                    }
                    break;
                }
            }
            // Iterative BFS, bounded to a single slice. No recursion and no
            // 4-million-cell queue; safe for the full blueprint volume cap.
            int head=0,tail=0;
            for(int z=0;z<b.length;z++)for(int x=0;x<b.width;x++){
                if(x!=0&&z!=0&&x!=b.width-1&&z!=b.length-1)continue;
                int p=columnIndex(b,x,z);
                if(!boundary.get(p)&&!outside.get(p)){
                    outside.set(p);queue[tail++]=p;
                }
            }
            while(head<tail){
                int p=queue[head++],x=p%b.width,z=p/b.width;
                if(x>0&& !boundary.get(p-1)&&!outside.get(p-1)){
                    outside.set(p-1);queue[tail++]=p-1;
                }
                if(x+1<b.width&&!boundary.get(p+1)&&!outside.get(p+1)){
                    outside.set(p+1);queue[tail++]=p+1;
                }
                if(z>0&&!boundary.get(p-b.width)&&!outside.get(p-b.width)){
                    outside.set(p-b.width);queue[tail++]=p-b.width;
                }
                if(z+1<b.length&&!boundary.get(p+b.width)&&!outside.get(p+b.width)){
                    outside.set(p+b.width);queue[tail++]=p+b.width;
                }
            }
            // True enclosed planar rooms (including interior turns/alcoves)
            // and roofless rooms are selected if blueprint specifies AIR.
            for(int z=0;z<b.length;z++)for(int x=0;x<b.width;x++){
                int p=columnIndex(b,x,z);
                if(!boundary.get(p)&&!outside.get(p)&&b.isAir(x,y,z))
                    selected.set(b.index(x,y,z));
            }
            // Open-ended tunnels cannot be found by exterior flood-fill.
            // Require continuously facing, paired standing sidewalls at this
            // elevation, and don't clear beyond either end of the walls.
            for(int z=1;z<b.length-1;z++){
                int left=-1;
                for(int x=1;x<b.width-1;x++){
                    if(!boundary.get(columnIndex(b,x,z))||
                       !w.spans(b,x,z,y))continue;
                    // A one-block-thick wall can face both in and out.
                    // Close an existing corridor BEFORE considering this
                    // column as the next potential start.
                    if(left>=0&&faceAtHeight(b,w,x,z,y,WEST)){
                        int right=x;
                        if(between(left,right,left+1)&&
                           runZ(b,w,left,z)>=MIN_CORRIDOR_RUN&&
                           runZ(b,w,right,z)>=MIN_CORRIDOR_RUN&&
                           w.spans(b,left,z-1,y)&&w.spans(b,right,z-1,y)&&
                           w.spans(b,left,z+1,y)&&w.spans(b,right,z+1,y))
                            for(int xx=left+1;xx<right;xx++)
                                if(b.isAir(xx,y,z))
                                    selected.set(b.index(xx,y,z));
                        left=-1;
                    }
                    if(faceAtHeight(b,w,x,z,y,EAST))left=x;
                }
            }
            for(int x=1;x<b.width-1;x++){
                int front=-1;
                for(int z=1;z<b.length-1;z++){
                    if(!boundary.get(columnIndex(b,x,z))||
                       !w.spans(b,x,z,y))continue;
                    if(front>=0&&faceAtHeight(b,w,x,z,y,NORTH)){
                        int back=z;
                        if(between(front,back,front+1)&&
                           runX(b,w,x,front)>=MIN_CORRIDOR_RUN&&
                           runX(b,w,x,back)>=MIN_CORRIDOR_RUN&&
                           w.spans(b,x-1,front,y)&&w.spans(b,x-1,back,y)&&
                           w.spans(b,x+1,front,y)&&w.spans(b,x+1,back,y))
                            for(int zz=front+1;zz<back;zz++)
                                if(b.isAir(x,y,zz))
                                    selected.set(b.index(x,y,zz));
                        front=-1;
                    }
                    if(faceAtHeight(b,w,x,z,y,SOUTH))front=z;
                }
            }
        }
        // Independently include AIR between sustained opposing masonry
        // walls, even when an irregular corridor shifts X/Z boundaries.
        selected.or(opposingWallAir(b,w));
        // A room/corridor may have shelves, stairwells, pots, glass,
        // decorative pillars and short recessed wall segments. Every
        // schematic block counts as an obstacle/anchor, never just stone.
        // Extend validated interiors through AIR bounded by the schematic
        // in two axes, without flooding into unconstrained outdoor air.
        extendThroughBoundedAir(b,selected,sealedOpenings);
        return selected;
    }


    /**
     * A second same-elevation interior classifier independent of the strict
     * exact-column corridor scan. Opposing inward-facing masonry boundaries
     * on three neighbouring rows form an interior band even when the walls
     * curve or shift sideways. Two isolated pillars never qualify.
     * Only SCHEMATIC AIR is selected, so painted vegetation is preserved.
     */
    private static BitSet opposingWallAir(Blueprint b,WallColumns w){
        int plane=b.width*b.length;
        BitSet selected=new BitSet(plane*b.height);
        BitSet betweenX=new BitSet(plane),betweenZ=new BitSet(plane);
        for(int y=0;y<b.height;y++){
            betweenX.clear();betweenZ.clear();
            for(int z=0;z<b.length;z++){
                int left=-1;
                for(int x=0;x<b.width;x++){
                    if(left>=0&&faceAtHeight(b,w,x,z,y,WEST)){
                        if(between(left,x,left+1))
                            for(int xx=left+1;xx<x;xx++)
                                if(b.isAir(xx,y,z))
                                    betweenX.set(columnIndex(b,xx,z));
                        left=-1;
                    }
                    if(faceAtHeight(b,w,x,z,y,EAST))left=x;
                }
            }
            for(int x=0;x<b.width;x++){
                int north=-1;
                for(int z=0;z<b.length;z++){
                    if(north>=0&&faceAtHeight(b,w,x,z,y,NORTH)){
                        if(between(north,z,north+1))
                            for(int zz=north+1;zz<z;zz++)
                                if(b.isAir(x,y,zz))
                                    betweenZ.set(columnIndex(b,x,zz));
                        north=-1;
                    }
                    if(faceAtHeight(b,w,x,z,y,SOUTH))north=z;
                }
            }
            for(int z=1;z<b.length-1;z++)for(int x=1;x<b.width-1;x++){
                if(!b.isAir(x,y,z))continue;
                int p=columnIndex(b,x,z);
                if((betweenX.get(p)&&betweenX.get(p-b.width)&&betweenX.get(p+b.width))||
                    (betweenZ.get(p)&&betweenZ.get(p-1)&&betweenZ.get(p+1)))
                    selected.set(b.index(x,y,z));
            }
        }
        return selected;
    }


    /**
     * Widen already-confirmed interiors into adjacent schematic AIR pockets
     * using ALL intentionally painted blocks as architectural evidence.
     *
     * A horizontal AIR span must be bounded at BOTH ends by schematic
     * non-AIR, and at least one other independent axis must also be bounded
     * (the other horizontal axis or floor+ceiling). This catches small
     * alcoves, piston compartments, pot corners and stone-lined stairwells.
     *
     * The candidate must be connected through other bounded AIR cells to
     * a previously confirmed room or corridor. Thus random vegetation,
     * a pair of posts, and air beyond a facade are not independently
     * reclassified as interior. No non-AIR schematic cell is ever selected.
     * Empty courtyards with a genuine 3D architectural envelope remain an
     * inherent geometric ambiguity, so a real schematic is needed to prove
     * correctness for those designs.
     */
    private static void extendThroughBoundedAir(Blueprint b,BitSet interior,BitSet sealedOpenings){
        int plane=b.width*b.length,volume=plane*b.height;
        BitSet betweenX=new BitSet(volume),betweenZ=new BitSet(volume);
        BitSet betweenY=new BitSet(volume);
        // Horizontal runs. Count all design blocks as endpoints: piston,
        // pot, glass, slab, pointed dripstone, lantern, moss, everything.
        for(int y=0;y<b.height;y++){
            for(int z=0;z<b.length;z++){
                int prev=-1;
                for(int x=0;x<b.width;x++){
                    if(b.isAir(x,y,z))continue;
                    if(prev>=0 && x-prev<=MAX_WALL_SPAN+1)
                        for(int xx=prev+1;xx<x;xx++)
                            betweenX.set(b.index(xx,y,z));
                    prev=x;
                }
            }
            for(int x=0;x<b.width;x++){
                int prev=-1;
                for(int z=0;z<b.length;z++){
                    if(b.isAir(x,y,z))continue;
                    if(prev>=0 && z-prev<=MAX_WALL_SPAN+1)
                        for(int zz=prev+1;zz<z;zz++)
                            betweenZ.set(b.index(x,y,zz));
                    prev=z;
                }
            }
        }
        // Floors and ceilings (not necessarily full cubes) bound the vertical
        // AIR interval. This includes top/bottom slabs; occupied is occupied.
        for(int z=0;z<b.length;z++)for(int x=0;x<b.width;x++){
            int prev=-1;
            for(int y=0;y<b.height;y++){
                if(b.isAir(x,y,z))continue;
                if(prev>=0 && y-prev<=65)
                    for(int yy=prev+1;yy<y;yy++)
                        betweenY.set(b.index(x,yy,z));
                prev=y;
            }
        }
        BitSet candidates=(BitSet)betweenX.clone();
        candidates.and(betweenZ);
        BitSet floorCeiling=(BitSet)betweenX.clone();
        floorCeiling.or(betweenZ);
        floorCeiling.and(betweenY);
        candidates.or(floorCeiling);
        candidates.andNot(interior);
        // The edge of a room may contain a real DOORWAY. Its schematic AIR
        // opens to the outside, so it must not be excavated by this bridge.
        candidates.andNot(sealedOpenings);

        // Six-face geometric cavity: no wall material classifier or
        // pre-existing room seed is needed to prove this voxel belongs to
        // a fully enclosed interior. Check east/west, north/south AND
        // floor/ceiling, all at the actual voxel coordinates. This includes
        // redstone, flower pots, fences, top slabs, dripstone and ANY other
        // retained schematic block as a valid enclosing face.
        BitSet allThree=(BitSet)betweenX.clone();
        allThree.and(betweenZ);
        allThree.and(betweenY);
        allThree.andNot(sealedOpenings);
        for(int i=allThree.nextSetBit(0);i>=0;i=allThree.nextSetBit(i+1)){
            int x=i%b.width,z=(i/b.width)%b.length,y=i/plane;
            if(x==0||x+1>=b.width||z==0||z+1>=b.length||
               y==0||y+1>=b.height)continue;
            if(!b.isAir(x-1,y,z)&&!b.isAir(x+1,y,z)&&
               !b.isAir(x,y,z-1)&&!b.isAir(x,y,z+1)&&
               !b.isAir(x,y-1,z)&&!b.isAir(x,y+1,z)){
                interior.set(i);
                candidates.clear(i);
            }
        }
        if(candidates.isEmpty())return;
        if(interior.isEmpty())return;
        // A 6-connected expansion through bounded cells, never through
        // exterior AIR. Each voxel enters the queue at most once.
        int[] queue=new int[volume];
        int head=0,tail=0;
        for(int i=interior.nextSetBit(0);i>=0;i=interior.nextSetBit(i+1))
            queue[tail++]=i;
        int[] adjacent=new int[6];
        while(head<tail){
            int i=queue[head++],x=i%b.width,z=(i/b.width)%b.length;
            adjacent[0]=x>0?i-1:-1;
            adjacent[1]=x+1<b.width?i+1:-1;
            adjacent[2]=z>0?i-b.width:-1;
            adjacent[3]=z+1<b.length?i+b.width:-1;
            adjacent[4]=i>=plane?i-plane:-1;
            adjacent[5]=i+plane<volume?i+plane:-1;
            for(int n:adjacent){
                if(n<0||!candidates.get(n))continue;
                candidates.clear(n);
                interior.set(n);
                queue[tail++]=n;
            }
        }
    }

    // A wall can have a one-layer decorative recess, but must exhibit a
    // real inward AIR-facing side at approximately this Y. The previous
    // algorithm ORed faces across ALL floors, misclassifying outside space.
    private static boolean faceAtHeight(Blueprint b,WallColumns w,int x,int z,int y){
        return faceAtHeight(b,w,x,z,y,EAST)||
            faceAtHeight(b,w,x,z,y,WEST)||
            faceAtHeight(b,w,x,z,y,SOUTH)||
            faceAtHeight(b,w,x,z,y,NORTH);
    }
    private static boolean faceAtHeight(Blueprint b,WallColumns w,int x,int z,int y,int direction){
        if(!w.spans(b,x,z,y)||!face(b,w,x,z,direction))return false;
        int dx=direction==EAST?1:direction==WEST?-1:0;
        int dz=direction==SOUTH?1:direction==NORTH?-1:0;
        if(!in2d(b,x+dx,z+dz))return false;
        for(int yy=Math.max(0,y-1);yy<=Math.min(b.height-1,y+1);yy++)
            if(structural(b,x,yy,z)&&b.isAir(x+dx,yy,z+dz))return true;
        return false;
    }
}
