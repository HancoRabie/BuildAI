package com.hanco.buildai.blueprint;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.*;

/**
 * Read-only, bounded design QA for requested redstone systems. It inspects
 * real voxels and an optional AI-authored circuit manifest, never claims
 * to have executed a machine or silently changes the user's blueprint.
 */
public final class RedstoneAudit {
    private RedstoneAudit(){}

    private static final Set<String> CIRCUIT_COMPONENTS=Set.of(
        "minecraft:redstone_wire","minecraft:redstone_block","minecraft:redstone_torch",
        "minecraft:redstone_wall_torch","minecraft:repeater","minecraft:comparator",
        "minecraft:observer","minecraft:piston","minecraft:sticky_piston",
        "minecraft:dispenser","minecraft:dropper","minecraft:hopper",
        "minecraft:redstone_lamp","minecraft:target","minecraft:daylight_detector",
        "minecraft:powered_rail","minecraft:detector_rail","minecraft:activator_rail",
        "minecraft:sculk_sensor","minecraft:calibrated_sculk_sensor",
        "minecraft:tripwire_hook","minecraft:crafter",
        "minecraft:command_block","minecraft:repeating_command_block",
        "minecraft:chain_command_block");

    private static final Set<String> INPUTS=Set.of(
        "minecraft:lever","minecraft:daylight_detector","minecraft:observer",
        "minecraft:target","minecraft:tripwire_hook","minecraft:sculk_sensor",
        "minecraft:calibrated_sculk_sensor","minecraft:detector_rail",
        "minecraft:trapped_chest","minecraft:command_block",
        "minecraft:repeating_command_block");

    private static final Set<String> OUTPUTS=Set.of(
        "minecraft:redstone_lamp","minecraft:piston","minecraft:sticky_piston",
        "minecraft:dispenser","minecraft:dropper","minecraft:crafter",
        "minecraft:note_block","minecraft:powered_rail","minecraft:activator_rail",
        "minecraft:iron_door","minecraft:iron_trapdoor",
        "minecraft:command_block","minecraft:repeating_command_block",
        "minecraft:chain_command_block");

    public static List<String> inspect(Blueprint blueprint,String playerRequest,String specification) {
        boolean requested=RedstoneKnowledge.isRedstoneRequest(playerRequest,specification);
        if(!requested)return List.of(); // No costly 4M-cell scan for unrelated architecture.
        boolean hasCircuit=false,hasInput=false,hasOutput=false;
        int wires=0,unsupportedWires=0,unspecifiedFacing=0;
        int allRails=0,poweredRails=0,redstoneUnderRails=0;
        boolean railRequest=playerRequest!=null &&
            playerRequest.matches("(?is).*(roller[ -]?coaster|minecart|railway|railroad|rail track|train track).*");
        boolean explicitlyAllPowered=playerRequest!=null &&
            playerRequest.matches("(?is).*(all (?:rails|track) powered|every rail powered|fully powered rails).*");
        for(int y=0;y<blueprint.height;y++)
            for(int z=0;z<blueprint.length;z++)
                for(int x=0;x<blueprint.width;x++) {
                    String state=blueprint.get(x,y,z),id=Blueprint.id(state);
                    if(CIRCUIT_COMPONENTS.contains(id))hasCircuit=true;
                    if(INPUTS.contains(id)||id.endsWith("_button")||id.endsWith("_pressure_plate"))hasInput=true;
                    if(OUTPUTS.contains(id)||id.endsWith("_copper_bulb")||
                        id.endsWith("_fence_gate"))hasOutput=true;
                    if(id.equals("minecraft:rail")||id.equals("minecraft:powered_rail")){
                        allRails++;
                        if(id.equals("minecraft:powered_rail")){
                            poweredRails++;
                            if(y>0 && Blueprint.id(blueprint.get(x,y-1,z))
                                .equals("minecraft:redstone_block"))redstoneUnderRails++;
                        }
                        hasCircuit=true;
                    }
                    if(id.equals("minecraft:redstone_wire")){
                        wires++;
                        if(y>0&&blueprint.isAir(x,y-1,z))unsupportedWires++;
                    }
                    if((id.equals("minecraft:repeater")||id.equals("minecraft:comparator")||
                        id.equals("minecraft:observer")||id.equals("minecraft:piston")||
                        id.equals("minecraft:sticky_piston")||id.equals("minecraft:dispenser")||
                        id.equals("minecraft:dropper")||id.equals("minecraft:hopper")||
                        id.equals("minecraft:crafter"))&&!state.contains("facing="))
                        unspecifiedFacing++;
                }
        if(!requested&&!hasCircuit)return List.of();
        List<String> warnings=new ArrayList<>();
        if(railRequest && allRails>=8 && !explicitlyAllPowered &&
            poweredRails*5>=allRails*4)
            warnings.add("RAIL QA: "+poweredRails+"/"+allRails+
                " rails are powered. Most roller coasters should use mostly "+
                "normal rails with intermittent powered boosters tuned to "+
                "speed, gradient and cart load. Do NOT power every rail by default.");
        if(railRequest && redstoneUnderRails>=8 &&
            redstoneUnderRails*4>=poweredRails*3)
            warnings.add("RAIL QA: "+redstoneUnderRails+
                " powered rails sit on individual redstone blocks. "+
                "Use shared power sources or adjacent powered rail propagation "+
                "instead of a redstone block under each rail, unless requested.");
        if(requested&&!hasCircuit)
            warnings.add("REDSTONE QA: Requested a functional circuit but the blueprint contains "+
                "no detected redstone/command components. This is NOT a completed machine.");
        if(hasCircuit&&!hasInput&&requested&&!railRequest)
            warnings.add("REDSTONE QA: No obvious button, lever, plate, detector, observer or "+
                "other control input found; verify how the machine activates.");
        if(hasCircuit&&!hasOutput&&requested&&!railRequest)
            warnings.add("REDSTONE QA: No recognizable redstone output device found; "+
                "verify that the requested action is physically implemented.");
        if(unspecifiedFacing>0)
            warnings.add("REDSTONE QA: "+unspecifiedFacing+" directional components rely "+
                "on default facing. Check intended rear input/front output, support and orientation.");
        if(unsupportedWires>0)
            warnings.add("REDSTONE QA: "+unsupportedWires+" wire positions have AIR "+
                "beneath them inside the blueprint. They may lose support when the "+
                "build area is excavated; inspect Y-layer support.");
        if(requested){
            try{
                JsonObject root=specification==null?null:JsonParser.parseString(specification).getAsJsonObject();
                JsonArray circuits=null;
                if(root!=null&&root.has("redstone_plan")&&root.get("redstone_plan").isJsonObject()) {
                    JsonObject plan=root.getAsJsonObject("redstone_plan");
                    if(plan.has("circuits")&&plan.get("circuits").isJsonArray())
                        circuits=plan.getAsJsonArray("circuits");
                }
                if((circuits==null||circuits.isEmpty())&&!railRequest){
                    warnings.add("REDSTONE QA: Missing redstone_plan.circuits. Ask the AI "+
                        "to document INPUT, signalPath, OUTPUT, TIMING, RESET and TEST "+
                        "for each proposed circuit. Geometry alone is not proof.");
                }else if(circuits!=null){
                    int missing=0,reviewed=0;
                    for(JsonElement element:circuits){
                        if(reviewed++>=32)break;
                        if(!element.isJsonObject()){missing++;continue;}
                        JsonObject circuit=element.getAsJsonObject();
                        for(String key:List.of("name","input","signalPath","output",
                                             "timing","reset","test")){
                            if(!circuit.has(key)||!circuit.get(key).isJsonPrimitive()||
                                circuit.get(key).getAsString().isBlank())missing++;
                        }
                    }
                    if(missing>0)warnings.add("REDSTONE QA: Circuit manifest omits "+
                        missing+" required engineering fields across the first "+
                        Math.min(32,circuits.size())+" circuits.");
                    if(circuits.size()>32)warnings.add(
                        "REDSTONE QA: Circuit manifest contains more than 32 entries; "+
                        "inspect multi-phase control logic separately.");
                }
            }catch(RuntimeException parseError){
                warnings.add("REDSTONE QA: Unable to parse redstone circuit manifest; "+
                    "inspect the exact generated design specification.");
            }
        }
        if(hasCircuit)
            warnings.addAll(RedstoneLogicAudit.inspect(blueprint,playerRequest));
        if(hasCircuit)
            warnings.add("REDSTONE QA: Connection and geometry checks are NOT functional "+
                "simulation. Test idle, trigger, repeated pulses, reset and power interruption "+
                "in Minecraft Java 1.21.1 before public use.");
        return List.copyOf(warnings);
    }
}
