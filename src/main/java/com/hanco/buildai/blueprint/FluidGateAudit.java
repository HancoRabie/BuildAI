package com.hanco.buildai.blueprint;

import com.google.gson.*;
import java.util.*;

/**
 * Conservative geometry + specification audit for piston controlled fluids.
 * Redstone and water need a two-state plan. This is NOT a fluid simulator:
 * ambiguous designs get warnings, never arbitrary automatic block edits.
 */
public final class FluidGateAudit {
    private FluidGateAudit(){}
    public static boolean relevant(String request){
        if(request==null)return false;
        String s=request.toLowerCase(Locale.ROOT);
        return s.contains("flush")||s.contains("flood")||
            s.contains("irrigat")||s.contains("water gate")||
            s.contains("automatic farm")||s.contains("automated farm")||
            s.contains("crop farm")||s.contains("harvest")&&s.contains("water");
    }
    private static boolean piston(String id){
        return id.equals("minecraft:piston")||id.equals("minecraft:sticky_piston");
    }
    private static int[] facingVector(String state){
        if(state.contains("facing=north"))return new int[]{0,0,-1};
        if(state.contains("facing=south"))return new int[]{0,0,1};
        if(state.contains("facing=east"))return new int[]{1,0,0};
        if(state.contains("facing=west"))return new int[]{-1,0,0};
        if(state.contains("facing=up"))return new int[]{0,1,0};
        if(state.contains("facing=down"))return new int[]{0,-1,0};
        return null;
    }
    private static boolean inside(Blueprint b,int x,int y,int z){
        return x>=0&&x<b.width&&y>=0&&y<b.height&&z>=0&&z<b.length;
    }
    public static List<String> inspect(Blueprint b,String prompt,String specification){
        if(!relevant(prompt))return List.of();
        List<String> warnings=new ArrayList<>();
        int sources=0,pistons=0,frontSources=0;
        for(int y=0;y<b.height;y++)for(int z=0;z<b.length;z++)for(int x=0;x<b.width;x++){
            String state=b.get(x,y,z),id=Blueprint.id(state);
            if(BuildFluidRules.isWater(state))sources++;
            if(!piston(id))continue;
            pistons++;
            int[] dv=facingVector(state);
            if(dv==null)continue;
            int fx=x+dv[0],fy=y+dv[1],fz=z+dv[2];
            if(inside(b,fx,fy,fz) && BuildFluidRules.isWater(b.get(fx,fy,fz))){
                frontSources++;
                if(frontSources<=3)warnings.add(
                    "FLOODGATE QA: Source water occupies piston extension cell "+
                    "("+fx+","+fy+","+fz+") in the STATIC blueprint. "+
                    "Extending the piston replaces/blocks water; verify this "+
                    "is not a destroyed source. Use an upstream contained "+
                    "source and an open-air throat when retracted.");
            }
        }
        if(pistons>0 && sources>0){
            JsonObject plan=null;
            try{
                if(specification!=null){
                    JsonObject root=JsonParser.parseString(specification).getAsJsonObject();
                    if(root.has("fluid_gate_plan")&&root.get("fluid_gate_plan").isJsonObject())
                        plan=root.getAsJsonObject("fluid_gate_plan");
                }
            }catch(RuntimeException ignored){}
            if(plan==null){
                warnings.add("FLOODGATE QA: Missing explicit CLOSED/OPEN "+
                    "piston states and timed water flow plan. Water cannot pass "+
                    "through an extended piston head. Describe local XYZ source, "+
                    "gate throat, actual open-air path, time held open, reset "+
                    "and item collection in fluid_gate_plan.");
            }else{
                for(String key:List.of("source","gate","closedState","openState",
                    "path","holdOpenTicks","reset","collection","testing")){
                    if(!plan.has(key)||plan.get(key).isJsonNull()||
                        !plan.get(key).isJsonPrimitive()||
                        plan.get(key).getAsString().isBlank())
                        warnings.add("FLOODGATE QA: fluid_gate_plan is missing "+
                            key+"; cannot consider water piston layout engineered.");
                }
                if(plan.has("holdOpenTicks")&&plan.get("holdOpenTicks").isJsonPrimitive()){
                    try{
                        int ticks=Integer.parseInt(plan.get("holdOpenTicks").getAsString()
                            .replaceAll("[^0-9]",""));
                        if(ticks<20)warnings.add("FLOODGATE QA: "+ticks+
                            " game ticks is likely too short for a harvest "+
                            "water sweep. Estimate >=5 ticks per horizontal "+
                            "water cell plus extra downstream item travel.");
                    }catch(NumberFormatException ignored){
                        warnings.add("FLOODGATE QA: holdOpenTicks must be "+
                            "an estimated GAME tick count, not redstone ticks.");
                    }
                }
            }
            warnings.add("FLOODGATE QA: A STATIC schematic cannot verify "+
                "piston movement or water ticks. Test CLOSED -> RETRACT -> "+
                "FLOW -> COLLECTION -> EXTEND -> DRAIN in a Java 1.21.1 world.");
        } else if(sources>0)
            warnings.add("FLOODGATE QA: Flowing water is specified but "+
                "no piston gate found. Verify the intended harvest "+
                "trigger, containment and drain if automatic flushing was requested.");
        return List.copyOf(warnings);
    }
}
