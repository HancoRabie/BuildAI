package com.hanco.buildai.blueprint;

import java.util.*;
import java.util.regex.*;

/**
 * Deterministic, explicit stage coordinates for very large multi-floor farm
 * requests. This avoids inventing a tiny footprint or requiring the model to
 * hold the complete building plus every redstone subsystem in ONE answer.
 *
 * Stage geometry stays in a single shared global coordinate system. The
 * caller merges operation JSON, NOT flattened schematic block entities.
 */
public final class StagedFarmPlan {
    private static final Pattern FLOOR=Pattern.compile(
        "(?im)^\\s*[-*]?\\s*(?:\\*\\*)?Floor\\s+([1-9][0-9]?)"+
        "\\s*[:\\-]\\s*([^\\n]+)");
    private static final Pattern PATCH=Pattern.compile(
        "(?i)(\\d{1,3})\\s*[x×]\\s*(\\d{1,3})");
    public record Stage(String name,String purpose,int firstY,int lastY,
                        boolean fullBounds) {}
    private final int width,height,length;
    private final List<Stage> stages;

    private StagedFarmPlan(int width,int height,int length,List<Stage> stages){
        this.width=width;this.height=height;this.length=length;
        this.stages=List.copyOf(stages);
    }
    public int width(){return width;}
    public int height(){return height;}
    public int length(){return length;}
    public List<Stage> stages(){return stages;}

    public static boolean applies(String prompt){
        if(prompt==null||!prompt.matches("(?is).*(farm|harvest|crops).*"))
            return false;
        Matcher m=FLOOR.matcher(prompt);
        Set<Integer> floorNums=new TreeSet<>();
        while(m.find())floorNums.add(Integer.parseInt(m.group(1)));
        return floorNums.containsAll(Set.of(1,2,3)) &&
            prompt.matches("(?is).*(automatic|automated|redstone|piston).*");
    }
    public static StagedFarmPlan from(String prompt){
        if(!applies(prompt))
            throw new IllegalArgumentException("This isn't an explicitly "+
                "multistory automated farm; use the normal architect.");
        Matcher m=FLOOR.matcher(prompt);
        Map<Integer,String> floors=new TreeMap<>();
        while(m.find())floors.put(Integer.parseInt(m.group(1)),m.group(2)
            .replace("**","").trim());
        if(floors.size()>8||floors.lastKey()>8)
            throw new IllegalArgumentException("Too many floors for "+
                "the current staged farm engineering mode (max 8).");
        // User's 9x20 field is interior only; reserve 16+ blocks along
        // each direction for surrounding stairs, lanes and utilities.
        int patchW=9,patchL=20;
        Matcher dims=PATCH.matcher(prompt);
        while(dims.find()){
            int a=Integer.parseInt(dims.group(1)),b=Integer.parseInt(dims.group(2));
            if(a<=1||b<=1||a>450||b>450)continue;
            if(a<=20&&b>=a){patchW=Math.max(patchW,a);patchL=Math.max(patchL,b);}
            else if(b<=20&&a>=b){patchW=Math.max(patchW,b);patchL=Math.max(patchL,a);}
        }
        int width=Math.max(27,patchW+18);
        int length=Math.max(35,patchL+17);
        int height=9*(floors.lastKey()+1)+5;
        if((long)width*height*length>Blueprint.MAX_VOLUME)
            throw new IllegalArgumentException("Requested multistory footprint "+
                "exceeds single-project voxel limit. Sectioned placements "+
                "are needed; do not miniaturize the request.");
        List<Stage> stages=new ArrayList<>();
        stages.add(new Stage("Architectural shell",
            "Rustic building outer walls, windows, roof, actual floor decks, "+
            "storage-room architecture, stairs, lighting supports and reserved "+
            "utility shafts. Do NOT include crop planting or any machine "+
            "redstone in this stage; individual engineering stages follow.",
            0,height-1,true));
        // 0..8 for the ground room, 9..17 for floor 1, etc.
        for(var f:floors.entrySet()){
            int y=f.getKey()*9;
            stages.add(new Stage("Floor "+f.getKey()+" engineering",
                f.getValue()+". Generate THIS floor's complete, buildable "+
                "mechanism and crops only. Respect the user's exact required "+
                "crop counts, geometry, hydration, containment, collectors, "+
                "piston and source directions, and support states. Leave "+
                "physical connections for the shared control/transport bus "+
                "to final integration stage.",y,y+8,false));
        }
        stages.add(new Stage("Shared controls and item transport",
            "Complete ground-floor storage with three independent dedicated "+
            "crop inputs, real hopper/pipeline routing to ground floor, "+
            "master one-button trigger and properly oriented vertical "+
            "control bus between all stages. Connect every floor into "+
            "one coherent system without deleting entire previous floors. "+
            "Keep mechanical and fluid flow paths physical, not imaginary.",
            0,height-1,true));
        return new StagedFarmPlan(width,height,length,stages);
    }

    public String stagePrompt(String original,Stage stage,int index){
        String discipline="""
            BUILDAI STAGED GENERATION — NEW INDEPENDENT MODEL REQUEST:
            Your assigned engineering subsystem is ONE part of a multi-floor
            build. Do not attempt the entire project in one response. The
            mod merges geometry from separately generated stages. You MUST
            output normal blueprint JSON using GLOBAL coordinates and exact
            width=%d,height=%d,length=%d, no resize and no local offsets.
            STAGE %d of %d: %s
            SCOPE: %s
            The full player request below is authoritative. Return ONLY
            the geometry operations required by this stage, with actual
            component faces, states and engineering plans. Other stages
            handle the rest. Do not erase the shell or another crop floor.
            Floors have 9-block story bands, ground=Y0..8, floor1=Y9..17,
            floor2=Y18..26, floor3=Y27..35, etc. The shell adds stairs.
            %s
            ORIGINAL PLAYER REQUIREMENTS:
            %s
            """.formatted(width,height,length,index+1,stages.size(),
                stage.name(),stage.purpose(),
                stage.fullBounds()?"All global Y levels permitted; protect "+
                    "previous stage work and pre-existing corridors.":
                    "ONLY modify cells in global Y="+stage.firstY()+".."+
                    stage.lastY()+" inclusive. Do not spill into other floors.",
                original);
        if(discipline.length()>4000)
            throw new IllegalArgumentException("Player's request plus scoped "+
                "staged instructions exceed the 4000-character model limit. "+
                "Split the request into an initial description and revision; "+
                "BuildAI will not silently discard your constraints.");
        return discipline;
    }
}
