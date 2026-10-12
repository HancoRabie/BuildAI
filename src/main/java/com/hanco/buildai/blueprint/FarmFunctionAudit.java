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
        int containers=0,waters=0,rootWaterAtHarvestHeight=0,farmland=0;
        BitSet rootY=new BitSet(b.height);
        // The crop Y plane is the occupied plant block, not the farmland below.
        for(int y=0;y<b.height;y++)for(int z=0;z<b.length;z++)for(int x=0;x<b.width;x++){
            String id=Blueprint.id(b.get(x,y,z));
            if(rootCrop(id)){roots++;rootY.set(y);crops.merge(id,1,Integer::sum);}
            if(id.equals("minecraft:sugar_cane")){cane++;crops.merge(id,1,Integer::sum);}
            if(id.equals("minecraft:dispenser"))dispensers++;
            if(piston(id))pistons++;
            if(control(id))controls++;
            if(id.equals("minecraft:hopper"))hoppers++;
            if(chest(id))containers++;
            if(id.equals("minecraft:water"))waters++;
            if(id.equals("minecraft:farmland"))farmland++;
        }
        if(roots==0&&cane==0&&farmland==0)
            return new Report(List.of(),List.of(),Map.copyOf(crops),b.solidCount());
        // Separate irrigation at soil height from harvest water at plant height.
        for(int y=rootY.nextSetBit(0);y>=0;y=rootY.nextSetBit(y+1))
            for(int z=0;z<b.length;z++)for(int x=0;x<b.width;x++)
                if(BuildFluidRules.isWater(b.get(x,y,z)))rootWaterAtHarvestHeight++;

        List<String> blockers=new ArrayList<>(),cautions=new ArrayList<>();
        if(roots>0){
            boolean plausibleWaterRelease=false;
            if(waters>0&&pistons>0&&rootWaterAtHarvestHeight>0){
                // This is evidence of SOME water-control geometry, not a proof.
                plausibleWaterRelease=true;
            }
            if(!plausibleWaterRelease){
                blockers.add("Root crops ("+roots+" carrots/potatoes/wheat/beetroot plants) lack "+
                    "an identifiable water-release harvest route at plant height with a "+
                    "controlled piston gate. Soil hydration water alone does NOT harvest.");
            }else {
                cautions.add("Water and piston geometry is present near root-crop height, "+
                    "but flow from release to every crop and downstream collection is "+
                    "not physically simulated. Inspect containment, timer and reset.");
            }
            if(farmland==0)
                blockers.add("Root crops were placed without any farmland block beneath "+
                    "the field. Crop growth/retention may fail.");
            cautions.add("Carrots, potatoes, wheat and beetroot require replanting after "+
                "destructive water harvest. Vanilla redstone alone cannot replant them; "+
                "use manual replanting or a supported villager/worker system.");
        }
        if((roots>0||cane>0)&&hoppers==0){
            // Staged floor could lead to a later shared downward collector.
            if(json!=null&&json.contains("BUILDAI STAGED GENERATION")){
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
            if(roots>0 && ! (pistons>0 && waters>0 && rootWaterAtHarvestHeight>0))
                blockers.add("Dispensers cannot substitute for a working, supplied "+
                    "harvest-water release. A bonemeal dispenser grows crops but does "+
                    "not harvest them; an empty water-bucket dispenser does nothing.");
        }
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
