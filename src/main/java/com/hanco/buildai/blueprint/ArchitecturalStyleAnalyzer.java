package com.hanco.buildai.blueprint;

import java.nio.file.Path;
import java.util.*;

/**
 * Whole-blueprint, READ-ONLY architectural reference analysis.
 * No NBT entities/inventories/sign instructions and no model calls at import.
 * The compact, inspectable geometry report may later be shared with the model
 * only when the player explicitly saves the reference.
 */
public final class ArchitecturalStyleAnalyzer {
    private ArchitecturalStyleAnalyzer() {}
    public record Analysis(String report, int blocks, int uniqueMaterials) {}
    private record Feature(int x,int y,int z,String state,int score) {}

    public static Analysis inspect(Path path) throws Exception {
        return analyze(SchematicReader.read(path));
    }

    public static Analysis analyze(Blueprint b) {
        Map<String,Integer> palette=new HashMap<>();
        int solid=0,stairs=0,slabs=0,panes=0,walls=0,fences=0,trapdoors=0;
        int roofStairs=0,roofSlabs=0;
        int[] perLayer=new int[b.height];
        Feature[] examples=new Feature[16];

        for(int y=0;y<b.height;y++)for(int z=0;z<b.length;z++)for(int x=0;x<b.width;x++) {
            if(b.isAir(x,y,z))continue;
            String state=b.get(x,y,z),id=Blueprint.id(state);
            palette.merge(id,1,Integer::sum);
            solid++;perLayer[y]++;
            if(id.endsWith("_stairs")){stairs++;if(y*5>=b.height*4)roofStairs++;}
            if(id.endsWith("_slab")){slabs++;if(y*5>=b.height*4)roofSlabs++;}
            if(id.endsWith("_pane")||id.equals("minecraft:iron_bars"))panes++;
            if(id.endsWith("_wall"))walls++;
            if(id.endsWith("_fence"))fences++;
            if(id.endsWith("_trapdoor"))trapdoors++;

            int score=interest(id);
            if(score<2)continue;
            // Extract detail examples near exposed surfaces, not an arbitrary
            // interior volume; maintain coverage across XY height and XZ site.
            boolean exposed=x==0||z==0||x==b.width-1||z==b.length-1||y==b.height-1||
                b.isAir(Math.max(0,x-1),y,z)||b.isAir(Math.min(b.width-1,x+1),y,z)||
                b.isAir(x,y,Math.max(0,z-1))||b.isAir(x,y,Math.min(b.length-1,z+1));
            if(!exposed)continue;
            int bucket=(x>=b.width/2?1:0)+2*(z>=b.length/2?1:0)+
                4*Math.min(3,y*4/b.height);
            Feature candidate=new Feature(x,y,z,state,score);
            if(examples[bucket]==null||score>examples[bucket].score())
                examples[bucket]=candidate;
        }

        if(solid==0)throw new IllegalArgumentException("The schematic contains only air; there is no building style to analyze");
        List<Map.Entry<String,Integer>> ranked=new ArrayList<>(palette.entrySet());
        ranked.sort(Comparator.<Map.Entry<String,Integer>>comparingInt(Map.Entry::getValue)
            .reversed().thenComparing(Map.Entry::getKey));
        StringBuilder result=new StringBuilder(4500);
        result.append("ARCHITECTURAL REFERENCE (derived from voxel geometry, not a screenshot):\n");
        result.append("Dimensions W x H x L: ").append(b.width).append('x').append(b.height)
            .append('x').append(b.length).append("; non-air blocks: ").append(solid)
            .append("; distinct block types: ").append(palette.size()).append(".\n");
        result.append("Main material palette (actual counts): ");
        for(int i=0;i<Math.min(12,ranked.size());i++){
            if(i>0)result.append("; ");
            var p=ranked.get(i);
            result.append(p.getKey()).append('=').append(p.getValue());
        }
        result.append(".\n");
        result.append("Detail vocabulary (actual counts): stairs=").append(stairs)
            .append(", slabs=").append(slabs).append(", glass panes/iron bars=").append(panes)
            .append(", walls=").append(walls).append(", fences=").append(fences)
            .append(", trapdoors=").append(trapdoors).append(".\n");
        result.append("Upper 20% of schematic: ").append(roofStairs)
            .append(" stairs and ").append(roofSlabs)
            .append(" slabs; these MAY indicate roof/trim detailing, not confirmed roof shape.\n");

        int fullest=0,highest=0;
        for(int y=0;y<b.height;y++){
            if(perLayer[y]>perLayer[fullest])fullest=y;
            if(perLayer[y]>0)highest=y;
        }
        result.append("Most occupied Y layer: ").append(fullest)
            .append(" (").append(perLayer[fullest]).append(" blocks); top occupied Y: ")
            .append(highest).append(".\n");
        result.append("EXACT DETAIL EXCERPTS. Coordinates are relative to each sample center; preserve the");
        result.append(" block states and arrangement when adapting an applicable motif,");
        result.append(" but do not assume each crop is a standalone object.\n");
        List<Feature> candidates=new ArrayList<>();
        for(Feature f:examples)if(f!=null)candidates.add(f);
        candidates.sort(Comparator.comparingInt(Feature::score).reversed());
        List<Feature> chosen=new ArrayList<>();
        for(Feature f:candidates){
            if(chosen.size()>=4)break;
            boolean tooNear=false;
            for(Feature old:chosen)if(Math.abs(old.x()-f.x())+
                Math.abs(old.y()-f.y())+Math.abs(old.z()-f.z())<7){tooNear=true;break;}
            if(!tooNear)chosen.add(f);
        }
        int n=0;
        for(Feature f:chosen){
            result.append("Example ").append(++n).append(": center=(")
                .append(f.x()).append(',').append(f.y()).append(',').append(f.z())
                .append("), focal block=").append(f.state()).append("; neighbors: ");
            int written=0;
            outer:for(int dy=-2;dy<=2;dy++)for(int dz=-1;dz<=1;dz++)
                for(int dx=-2;dx<=2;dx++) {
                    int x=f.x()+dx,y=f.y()+dy,z=f.z()+dz;
                    if(x<0||y<0||z<0||x>=b.width||y>=b.height||z>=b.length||
                        b.isAir(x,y,z))continue;
                    if(written++>=32)break outer;
                    result.append(dx).append(',').append(dy).append(',').append(dz)
                        .append('=').append(b.get(x,y,z)).append(';');
                }
            result.append('\n');
        }
        result.append("Use these observations as inspiration for proportions, material contrast,");
        result.append(" depth, edge trimming and local detail density; do not copy an entire reference");
        result.append(" or infer inaccessible rooms, symmetry, structural integrity or redstone function.\n");
        return new Analysis(result.toString(),solid,palette.size());
    }

    private static int interest(String id) {
        if(id.contains("lantern")||id.contains("chandelier")||id.endsWith("_trapdoor"))return 10;
        if(id.endsWith("_pane")||id.contains("glass")||id.contains("iron_bars"))return 9;
        if(id.endsWith("_stairs")||id.endsWith("_wall")||id.endsWith("_fence"))return 8;
        if(id.endsWith("_slab")||id.contains("polished")||id.contains("chiseled"))return 6;
        if(id.endsWith("_door")||id.endsWith("_sign")||id.contains("light"))return 5;
        if(id.endsWith("_log")||id.endsWith("_leaves"))return 3;
        return 0;
    }
}
