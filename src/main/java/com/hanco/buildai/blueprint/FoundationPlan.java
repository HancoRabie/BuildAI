package com.hanco.buildai.blueprint;

import java.util.*;

/**
 * Foundation detection based on the actual connected footprint of the
 * bottom platform, not the full schematic bounding rectangle (which often
 * has arbitrary AIR padding). Each candidate layer must retain at least
 * 90% of the anchored footprint and ONE eligible block type must occupy
 * at least 51% of the entire anchored footprint, not just its filled cells.
 *
 * A two-phase plan keeps architectural walls and enclosed basements:
 * (1) identify provisional foundation layers with >=90% footprint coverage;
 * (2) protect only recognized basement interiors and adjacent actual
 * underground walls/floors at their own Y-level; NEVER protect stone merely
 * because an aboveground wall occupies the same X/Z column.
 *
 * No inference from a schematic can prove a completely solid region is a
 * basement rather than disposable terrain. Such ambiguity must be inspected
 * before placement in a valuable world.
 */
public final class FoundationPlan {
    private FoundationPlan(){}
    private static final Set<String> NATURAL=Set.of(
        "dirt","grass_block","coarse_dirt","rooted_dirt","podzol",
        "mycelium","moss_block","mud","packed_mud","clay","gravel",
        "sand","red_sand","sandstone","red_sandstone",
        "stone","cobblestone","mossy_cobblestone","stone_bricks",
        "mossy_stone_bricks","cracked_stone_bricks","chiseled_stone_bricks",
        "deepslate","cobbled_deepslate","deepslate_bricks",
        "polished_deepslate","smooth_stone","polished_andesite",
        "andesite","diorite","granite","tuff","tuff_bricks",
        "calcite","dripstone_block","netherrack","soul_sand","soul_soil",
        "blackstone","polished_blackstone","polished_blackstone_bricks",
        "basalt","smooth_basalt","bedrock","obsidian",
        "quartz_block","smooth_quartz","iron_block");
    private static final int MIN_PERCENT=51;
    private static final int MIN_COVERAGE_PERCENT=90;
    private static final int MIN_SIDE=4;

    private static boolean foundationMaterial(String block,Set<String> extras){
        if(!block.startsWith("minecraft:"))return extras.contains(block);
        String name=block.substring("minecraft:".length());
        if(NATURAL.contains(name)||extras.contains(block))return true;
        return name.endsWith("_concrete")||name.endsWith("_terracotta")||
            name.endsWith("_planks")||name.endsWith("_bricks")||
            name.equals("smooth_stone_slab")||name.equals("stone_brick_slab");
    }
    private static boolean forbidden(String block){
        // Never omit functional blocks, even with explicit material hints.
        String s=block.substring(block.indexOf(':')+1);
        return s.equals("air")||s.endsWith("_air")||s.equals("water")||
            s.equals("lava")||s.contains("redstone")||
            s.contains("repeater")||s.contains("comparator")||
            s.contains("torch")||s.contains("sign")||s.contains("button")||
            s.contains("lever")||s.contains("piston")||
            s.contains("hopper")||s.contains("chest")||
            s.contains("door")||s.contains("rail")||
            s.contains("observer")||s.contains("dispenser")||
            s.contains("dropper")||s.contains("crafter")||
            s.contains("command_block")||s.contains("spawner")||
            s.contains("bed")||s.contains("scaffolding");
    }
    private static boolean eligible(Blueprint b,int x,int y,int z,Set<String> materials){
        if(b.isAir(x,y,z))return false;
        String id=Blueprint.id(b.get(x,y,z));
        return !forbidden(id)&&foundationMaterial(id,materials);
    }


    private record Footprint(BitSet mask,int area,int minX,int maxX,
                             int minZ,int maxZ){
        String size(){return (maxX-minX+1)+"x"+(maxZ-minZ+1);}
    }

    /**
     * Find the largest four-connected occupied island on the bottom layer.
     * Preserve its TRUE non-rectangular silhouette (an L-shaped platform
     * is not made rectangular). Holes fully enclosed by that island
     * count as part of the footprint for the 90% occupancy check.
     */
    private static Footprint footprint(Blueprint b,int y){
        int plane=b.width*b.length;
        BitSet seen=new BitSet(plane),largest=new BitSet(plane);
        int[] queue=new int[plane];
        int biggest=0;
        for(int p=0;p<plane;p++){
            int x=p%b.width,z=p/b.width;
            if(seen.get(p)||b.isAir(x,y,z))continue;
            seen.set(p);
            int head=0,tail=1;
            queue[0]=p;
            while(head<tail){
                int v=queue[head++],vx=v%b.width,vz=v/b.width;
                if(vx>0&&!seen.get(v-1)&&!b.isAir(vx-1,y,vz)){
                    seen.set(v-1);queue[tail++]=v-1;
                }
                if(vx+1<b.width&&!seen.get(v+1)&&!b.isAir(vx+1,y,vz)){
                    seen.set(v+1);queue[tail++]=v+1;
                }
                if(vz>0&&!seen.get(v-b.width)&&!b.isAir(vx,y,vz-1)){
                    seen.set(v-b.width);queue[tail++]=v-b.width;
                }
                if(vz+1<b.length&&!seen.get(v+b.width)&&!b.isAir(vx,y,vz+1)){
                    seen.set(v+b.width);queue[tail++]=v+b.width;
                }
            }
            if(tail>biggest){
                biggest=tail;largest.clear();
                for(int i=0;i<tail;i++)largest.set(queue[i]);
            }
        }
        if(biggest==0)return new Footprint(largest,0,0,-1,0,-1);
        int x0=b.width,x1=-1,z0=b.length,z1=-1;
        for(int p=largest.nextSetBit(0);p>=0;p=largest.nextSetBit(p+1)){
            int x=p%b.width,z=p/b.width;
            x0=Math.min(x0,x);x1=Math.max(x1,x);
            z0=Math.min(z0,z);z1=Math.max(z1,z);
        }
        // Flood from the rectangle's perimeter through unoccupied cells.
        // AIR unreachable from outside is a genuine hole IN the footprint.
        BitSet exterior=new BitSet(plane);
        int head=0,tail=0;
        for(int z=z0;z<=z1;z++)for(int x=x0;x<=x1;x++){
            if(x!=x0&&x!=x1&&z!=z0&&z!=z1)continue;
            int p=z*b.width+x;
            if(!largest.get(p)&&!exterior.get(p)){
                exterior.set(p);queue[tail++]=p;
            }
        }
        while(head<tail){
            int p=queue[head++],x=p%b.width,z=p/b.width;
            int[] neighbors={
                x>x0?p-1:-1,x<x1?p+1:-1,
                z>z0?p-b.width:-1,z<z1?p+b.width:-1};
            for(int n:neighbors){
                if(n<0||largest.get(n)||exterior.get(n))continue;
                exterior.set(n);queue[tail++]=n;
            }
        }
        for(int z=z0;z<=z1;z++)for(int x=x0;x<=x1;x++){
            int p=z*b.width+x;
            if(!largest.get(p)&&!exterior.get(p))largest.set(p);
        }
        return new Footprint(largest,largest.cardinality(),x0,x1,z0,z1);
    }

    /**
     * Protect actual BELOW-GROUND enclosed rooms, not a 2D projection of
     * ABOVE-ground architecture. The old projection retained foundation
     * stone in vertical columns below any wall/corridor, producing hanging
     * slabs and pillars. foundation:no should remove those lower layers.
     *
     * Only original schematic AIR INSIDE the qualifying foundation Y-range
     * establishes basement evidence. A two-block masonry collar plus one
     * floor layer is retained around that proven basement volume.
     * Decorative/room AIR above the foundation does not protect ANY stone
     * below, no matter how tall the temple is.
     */
    private static Set<Integer> protectedBasementBlocks(Blueprint b,
                    Set<Integer> provisional,int firstY,int lastY){
        BitSet enclosed=InteriorHollowPlan.enclosedAir(b);
        Set<Integer> protectedBlocks=new HashSet<>();
        int plane=b.width*b.length;
        for(int i=enclosed.nextSetBit(0);i>=0;i=enclosed.nextSetBit(i+1)){
            int y=i/plane;
            if(y<firstY||y>lastY)continue;
            int x=i%b.width,z=(i/b.width)%b.length;
            // Basement wall thickness and occupied basement floor.
            for(int dy=-1;dy<=0;dy++){
                int yy=y+dy;
                if(yy<firstY)continue;
                for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){
                    if(Math.abs(dx)+Math.abs(dz)>2)continue;
                    int xx=x+dx,zz=z+dz;
                    if(xx<0||zz<0||xx>=b.width||zz>=b.length)continue;
                    int candidate=b.index(xx,yy,zz);
                    if(provisional.contains(candidate))
                        protectedBlocks.add(candidate);
                }
            }
        }
        return protectedBlocks;
    }

    /**
     * An inspectable foundation decision. The SAME analysis provides the
     * omission set and a precise reason why layer scanning stopped.
     * Each layer needs >=90% of the bottom connected footprint occupied,
     * plus a single eligible block type >=51% of that full footprint.
     */
    public record Analysis(Set<Integer> omissions,int firstOccupiedY,
                           int qualifyingLayers,int stoppedAtY,String reason){
        public Analysis{
            omissions=Set.copyOf(omissions);
        }
        public String summary(){
            return "Foundation: "+omissions.size()+" block(s) omitted across "+
                qualifyingLayers+" qualifying layer(s); "+reason;
        }
    }

    private static String pct(int numerator,int denominator){
        return String.format(Locale.ROOT,"%.2f%%",(100.0*numerator)/denominator);
    }


    /** Lowest occupied slice can be a pair of dangling stone struts rather
     * than the main foundation. Select the broadest eligible PLATFORM band
     * among the first eight Y-levels, preferring repeated adjacent layers
     * to a one-off upper building floor. An eligible candidate must cover
     * >=90% of ITS true footprint with one foundation block ID >=51%.
     */
    private static boolean majorityOnFootprint(Blueprint b,int y,Footprint base,
                                               Set<String> materials){
        if(y<0||y>=b.height||base.area()<MIN_SIDE*MIN_SIDE)return false;
        int occupied=0;
        Map<String,Integer> types=new HashMap<>();
        for(int p=base.mask().nextSetBit(0);p>=0;p=base.mask().nextSetBit(p+1)){
            int x=p%b.width,z=p/b.width;
            if(b.isAir(x,y,z))continue;
            occupied++;
            String id=Blueprint.id(b.get(x,y,z));
            if(!forbidden(id)&&foundationMaterial(id,materials))
                types.merge(id,1,Integer::sum);
        }
        if((long)occupied*100<(long)MIN_COVERAGE_PERCENT*base.area())return false;
        return types.values().stream().anyMatch(count->
            (long)count*100>=(long)MIN_PERCENT*base.area());
    }

    private record FoundationAnchor(Footprint footprint,int y){}
    private static FoundationAnchor chooseAnchor(Blueprint b,int first,
                                                  Set<String> materials){
        Footprint fallback=footprint(b,first);
        FoundationAnchor best=null;
        int end=Math.min(b.height-1,first+8);
        for(int y=first;y<=end;y++){
            Footprint fp=footprint(b,y);
            if(fp.area()<MIN_SIDE*MIN_SIDE||
               fp.maxX()-fp.minX()+1<MIN_SIDE||
               fp.maxZ()-fp.minZ()+1<MIN_SIDE||
               !majorityOnFootprint(b,y,fp,materials))continue;
            boolean stable=majorityOnFootprint(b,y-1,fp,materials)||
                majorityOnFootprint(b,y+1,fp,materials);
            // Require an adjacent eligible Y-layer, so a single broad
            // architectural floor can't overrule a thinner true foundation.
            if(!stable)continue;
            if(best==null||fp.area()>best.footprint().area())
                best=new FoundationAnchor(fp,y);
        }
        return best==null?new FoundationAnchor(fallback,first):best;
    }

    public static Analysis analyze(Blueprint b,Set<String> materials){
        Objects.requireNonNull(b,"blueprint");
        Set<String> extras=materials==null?Set.of():materials;
        if(b.width<MIN_SIDE||b.length<MIN_SIDE)
            return new Analysis(Set.of(),-1,0,-1,
                "schematic width/length below four blocks");
        int first=-1;
        outer:for(int y=0;y<b.height;y++)
            for(int z=0;z<b.length;z++)for(int x=0;x<b.width;x++)
                if(!b.isAir(x,y,z)){first=y;break outer;}
        if(first<0)
            return new Analysis(Set.of(),-1,0,-1,"schematic has no blocks");
        FoundationAnchor anchor=chooseAnchor(b,first,extras);
        Footprint base=anchor.footprint();
        int coreStartY=anchor.y();
        if(base.area()<MIN_SIDE*MIN_SIDE||
           base.maxX()-base.minX()+1<MIN_SIDE||
           base.maxZ()-base.minZ()+1<MIN_SIDE)
            return new Analysis(Set.of(),first,0,first,
                "lowest connected platform is too small to classify as foundation");

        Set<Integer> provisional=new HashSet<>();
        int qualified=0,stop=b.height;
        String reason="all scanned layers qualified";
        for(int y=coreStartY;y<b.height;y++){
            int occupied=0;
            Map<String,Integer> counts=new HashMap<>(),allTypes=new HashMap<>();
            for(int p=base.mask().nextSetBit(0);p>=0;p=base.mask().nextSetBit(p+1)){
                int x=p%b.width,z=p/b.width;
                if(b.isAir(x,y,z))continue;
                occupied++;
                String id=Blueprint.id(b.get(x,y,z));
                allTypes.merge(id,1,Integer::sum);
                if(!forbidden(id)&&foundationMaterial(id,extras))
                    counts.merge(id,1,Integer::sum);
            }
            Map.Entry<String,Integer> actualDominant=allTypes.entrySet().stream()
                .max(Map.Entry.<String,Integer>comparingByValue()
                    .thenComparing(Map.Entry::getKey)).orElse(null);
            String actualTop=actualDominant==null?"none":
                actualDominant.getKey()+" "+
                    pct(actualDominant.getValue(),base.area());
            if((long)occupied*100<(long)MIN_COVERAGE_PERCENT*base.area()){
                stop=y;
                reason="stopped at schematic Y="+y+": "+occupied+"/"+base.area()+
                    " footprint positions occupied ("+pct(occupied,base.area())+
                    "); need at least 90% of the connected "+base.size()+
                    " platform footprint (basement voids allowed), NOT all "+b.width+"x"+b.length+
                    " schematic positions; largest actual block type: "+actualTop;
                break;
            }
            Map.Entry<String,Integer> dominant=counts.entrySet().stream()
                .max(Map.Entry.<String,Integer>comparingByValue()
                    .thenComparing(Map.Entry::getKey)).orElse(null);
            if(dominant==null||
                (long)dominant.getValue()*100<(long)MIN_PERCENT*base.area()){
                stop=y;
                reason="stopped at schematic Y="+y+
                    ": no single eligible block type reaches 51% over "+
                    base.area()+" connected-footprint cells. Largest eligible: "+
                    (dominant==null?"none":dominant.getKey()+" "+
                    pct(dominant.getValue(),base.area()))+
                    "; largest actual block type: "+actualTop;
                break;
            }
            String block=dominant.getKey();
            for(int p=base.mask().nextSetBit(0);p>=0;p=base.mask().nextSetBit(p+1)){
                int x=p%b.width,z=p/b.width;
                if(block.equals(Blueprint.id(b.get(x,y,z))))
                    provisional.add(b.index(x,y,z));
            }
            qualified++;
        }
        if(qualified==0)return new Analysis(Set.of(),first,0,stop,reason);
        // Scan BACK DOWN to the original lowest occupied Y to include
        // narrow foundation support tails. They need not satisfy 90%
        // themselves, but every removed cell must be an eligible material
        // and vertically attach to an already selected foundation cell.
        int tails=0;
        for(int yy=coreStartY-1;yy>=first;yy--){
            for(int p=base.mask().nextSetBit(0);p>=0;p=base.mask().nextSetBit(p+1)){
                int x0=p%b.width,z0=p/b.width;
                if(b.isAir(x0,yy,z0))continue;
                String id=Blueprint.id(b.get(x0,yy,z0));
                if(forbidden(id)||!foundationMaterial(id,extras))continue;
                if(provisional.contains(b.index(x0,yy+1,z0))){
                    if(provisional.add(b.index(x0,yy,z0)))tails++;
                }
            }
        }
        Set<Integer> protectedBlocks=protectedBasementBlocks(
            b,provisional,first,coreStartY+qualified-1);
        Set<Integer> omitted=new HashSet<>(provisional);
        omitted.removeAll(protectedBlocks);
        int retained=protectedBlocks.size();
        reason+="; anchored at schematic Y="+coreStartY+
            " (lowest occupied Y="+first+"), connected lower support-tail cells "+tails+
            "; >=90% core coverage and >=51% single-type majority; footprint "+base.area()+" cells ("+base.size()+
            ") inside schematic "+b.width+"x"+b.length+
            "; retained "+retained+" candidate blocks at ACTUAL underground "+
            "enclosed room walls/floors (no projected aboveground columns)";
        return new Analysis(omitted,first,qualified,stop,reason);
    }

    public static Set<Integer> identifiedFoundation(Blueprint b,Set<String> materials){
        return analyze(b,materials).omissions();
    }
}
