package com.hanco.buildai.blueprint;

import com.google.gson.*;
import java.util.*;

/**
 * Static post-generation farm-function audit, not a Minecraft redstone or fluid simulator.
 * Looks for physical evidence of HARVEST -> TRANSPORT -> COLLECTION, instead of
 * accepting a water source used only for soil hydration or a decorative dispenser.
 */
public final class FarmFunctionAudit {
    private FarmFunctionAudit() {}

    public record Report(List<String> blockers, List<String> cautions,
                         Map<String,Integer> crops, int solidBlocks) {
        public boolean needsRepair(){return !blockers.isEmpty();}
        public List<String> warnings(){
            List<String> messages=new ArrayList<>();
            for(String s:blockers)messages.add("FARM FUNCTION BLOCKER: "+s);
            for(String s:cautions)messages.add("FARM FUNCTION QA: "+s);
            return List.copyOf(messages);
        }
    }

    private static boolean farmRequested(String prompt){
        if(prompt==null)return false;
        String lower=prompt.toLowerCase(Locale.ROOT);
        return (lower.contains("farm")||lower.contains("crop")||lower.contains("harvest")) &&
            (lower.contains("automat")||lower.contains("redstone")||lower.contains("harvest"));
    }
    private static boolean inside(Blueprint b,int x,int y,int z){
        return x>=0&&x<b.width&&y>=0&&y<b.height&&z>=0&&z<b.length;
    }
    private static boolean rootCrop(String id){
        return id.equals("minecraft:carrots")||id.equals("minecraft:potatoes")||
            id.equals("minecraft:wheat")||id.equals("minecraft:beetroots");
    }
    private static boolean chest(String id){
        return id.equals("minecraft:chest")||id.equals("minecraft:trapped_chest")||
            id.equals("minecraft:barrel");
    }
    private static boolean piston(String id){
        return id.equals("minecraft:piston")||id.equals("minecraft:sticky_piston");
    }
    private static boolean control(String id){
        return id.equals("minecraft:lever")||id.endsWith("_button")||
            id.equals("minecraft:observer")||id.equals("minecraft:daylight_detector")||
            id.equals("minecraft:tripwire_hook");
    }

    public static Report inspect(Blueprint b,String prompt,String json){
        if(!farmRequested(prompt))return new Report(List.of(),List.of(),Map.of(),b.solidCount());
        Map<String,Integer> crops=new TreeMap<>();
        int roots=0,cane=0,dispensers=0,pistons=0,controls=0,hoppers=0;
        int containers=0,waters=0,farmland=0,missingFarmland=0;
        BitSet rootY=new BitSet(b.height);
        int[] minX=new int[b.height],minZ=new int[b.height];
        int[] maxX=new int[b.height],maxZ=new int[b.height];
        Arrays.fill(minX,Integer.MAX_VALUE);
        Arrays.fill(minZ,Integer.MAX_VALUE);
        Arrays.fill(maxX,-1);Arrays.fill(maxZ,-1);
        List<int[]> sourceCells=new ArrayList<>();
        List<int[]> pistonCells=new ArrayList<>();
        // The crop Y plane is the occupied plant block, not the farmland below.
        for(int y=0;y<b.height;y++)for(int z=0;z<b.length;z++)for(int x=0;x<b.width;x++){
            String id=Blueprint.id(b.get(x,y,z));
            if(rootCrop(id)){
                roots++;rootY.set(y);crops.merge(id,1,Integer::sum);
                minX[y]=Math.min(minX[y],x);maxX[y]=Math.max(maxX[y],x);
                minZ[y]=Math.min(minZ[y],z);maxZ[y]=Math.max(maxZ[y],z);
                if(y==0||!Blueprint.id(b.get(x,y-1,z)).equals("minecraft:farmland"))
                    missingFarmland++;
            }
            if(id.equals("minecraft:sugar_cane")){cane++;crops.merge(id,1,Integer::sum);}
            if(id.equals("minecraft:dispenser"))dispensers++;
            if(piston(id)){
                pistons++;
                if(pistonCells.size()<10_000)pistonCells.add(new int[]{x,y,z});
            }
            if(control(id))controls++;
            if(id.equals("minecraft:hopper"))hoppers++;
            if(chest(id))containers++;
            if(id.equals("minecraft:water")){
                waters++;
                if(sourceCells.size()<10_000)sourceCells.add(new int[]{x,y,z});
            }
            if(id.equals("minecraft:farmland"))farmland++;
        }
        // For a staged blueprint, the original multi-floor prompt mentions
        // every crop. Only enforce the species requested in THIS crop stage;
        // the shell and final item-bus stages intentionally have no plants.
        String expected=prompt==null?"":prompt.toLowerCase(Locale.ROOT);
        if(expected.startsWith("buildai staged generation")){
            boolean cropStage=expected.matches(
                "(?s).*stage \\d+ of \\d+: floor \\d+ engineering.*");
            if(!cropStage)expected="";
            else {
                int from=expected.indexOf("scope:");
                int to=from<0?-1:expected.indexOf(
                    "the full player request below",from);
                if(from>=0&&to>from)expected=expected.substring(from,to);
            }
        }
        List<String> blockers=new ArrayList<>(),cautions=new ArrayList<>();
        if(expected.contains("carrot")&&crops.getOrDefault("minecraft:carrots",0)==0)
            blockers.add("Requested carrot field has no actual planted carrot blocks.");
        if(expected.contains("potato")&&crops.getOrDefault("minecraft:potatoes",0)==0)
            blockers.add("Requested potato field has no actual planted potato blocks.");
        if(expected.contains("sugar cane")&&crops.getOrDefault("minecraft:sugar_cane",0)==0)
            blockers.add("Requested sugar cane field has no planted stalks.");
        if(roots==0&&cane==0&&farmland==0)
            return new Report(List.copyOf(blockers),List.of(),Map.copyOf(crops),b.solidCount());
        // Crop-level water must be NEAR the field, with an actual nearby piston.
        // A decoration on another floor cannot count as a harvest water gate.
        boolean plausibleWaterRelease=false;
        outer: for(int[] w:sourceCells){
            int x=w[0],y=w[1],z=w[2];
            if(!rootY.get(y)||x<minX[y]-3||x>maxX[y]+3||
                z<minZ[y]-3||z>maxZ[y]+3)continue;
            for(int[] p:pistonCells){
                if(Math.abs(p[0]-x)+Math.abs(p[1]-y)+Math.abs(p[2]-z)<=3){
                    plausibleWaterRelease=true;break outer;
                }
            }
        }

        if(roots>0){
            if(!plausibleWaterRelease){
                blockers.add("Root crops ("+roots+" carrots/potatoes/wheat/beetroot plants) lack "+
                    "an identifiable water-release harvest route at plant height with a "+
                    "controlled piston gate. Soil hydration water alone does NOT harvest.");
            }else {
                cautions.add("Water and piston geometry is present near root-crop height, "+
                    "but flow from release to every crop and downstream collection is "+
                    "not physically simulated. Inspect containment, timer and reset.");
            }
            if(missingFarmland>0)
                blockers.add(missingFarmland+" root crop plant(s) lack farmland "+
                    "directly beneath them; they may break or fail to grow.");
            cautions.add("Carrots, potatoes, wheat and beetroot require replanting after "+
                "destructive water harvest. Vanilla redstone alone cannot replant them; "+
                "use manual replanting or a supported villager/worker system.");
        }
        if((roots>0||cane>0)&&hoppers==0){
            // Staged floor could lead to a later shared downward collector.
            if(prompt!=null&&prompt.startsWith("BUILDAI STAGED GENERATION")){
                cautions.add("No local collection hopper yet; ensure the final shared "+
                    "integration stage installs a physical transport/collection path.");
            }else{
                blockers.add("No collection hoppers exist in this blueprint. "+
                    "Harvested items have no demonstrated hopper collection route.");
            }
        }
        if((roots>0||cane>0)&&containers==0)
            cautions.add("No storage chest or barrel exists in this blueprint; "+
                "verify that the output connects to the shared storage stage.");
        if(dispensers>0){
            cautions.add(dispensers+" dispenser(s) present. They are EMPTY in a block-only "+
                "BuildAI blueprint; bone meal and water buckets cannot function without "+
                "explicit inventory loading and power. Remove unnecessary dispensers "+
                "or document required player setup.");
            if(roots>0 && !plausibleWaterRelease)
                blockers.add("Dispensers cannot substitute for a working, supplied "+
                    "harvest-water release. A bonemeal dispenser grows crops but does "+
                    "not harvest them; an empty water-bucket dispenser does nothing.");
        }
        if((roots>0||cane>0)&&hoppers>0)
            cautions.add("Hoppers are present, but BuildAI has NOT simulated "+
                "whether every harvested item actually reaches them. Inspect "+
                "the collector trench, water limits, hopper direction and "+
                "vertical item-transfer connections.");
        if((roots>0||cane>0)&&controls==0)
            cautions.add("No identifiable observer, button, or lever; verify how "+
                "the farm is activated and resets.");
        if(cane>0&&pistons==0)
            blockers.add("Sugar cane is planted but no piston harvest mechanism "+
                "is visible. Static water is not sugar cane harvesting.");
        if(cane>0&&pistons>0&&hoppers>0)
            cautions.add("Pistons and collection hoppers are present, but "+
                "observer facing, timing and drop capture still need in-game testing.");
        return new Report(List.copyOf(blockers),List.copyOf(cautions),
            Map.copyOf(crops),b.solidCount());
    }

    /** Decline an unverified model rewrite unless it reduces detected defects
     * without stripping existing requested crops, dimensions, or most geometry.
     */
    public static boolean improves(Report before,Report after,Blueprint oldB,Blueprint newB){
        if(oldB.width!=newB.width||oldB.height!=newB.height||
            oldB.length!=newB.length)return false;
        if(after.blockers().size()>=before.blockers().size())return false;
        if(newB.solidCount()*10L<oldB.solidCount()*7L)return false;
        for(var entry:before.crops().entrySet())
            if(after.crops().getOrDefault(entry.getKey(),0)<entry.getValue())return false;
        return true;
    }
}
