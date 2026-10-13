package com.hanco.buildai.blueprint;

import java.util.*;

/**
 * Extensible collection of specialist STATIC QA modules. The central
 * acceptance gate remains the player's original prompt, not one build type.
 * A module may use domain-specific Minecraft mechanics only when relevant.
 */
public final class SpecializedAuditRegistry {
    private SpecializedAuditRegistry(){}

    public record Findings(List<String> blockers,List<String> suggestions){}
    @FunctionalInterface public interface Rule {
        Findings inspect(Blueprint blueprint,String request,String specification);
    }
    private static final List<Rule> RULES=List.of(
        (blueprint,request,specification)->{
            // All generated designs pass the same static voxel placement
            // check before review. It flags unpowered endpoints and
            // unsupported/directionally misplaced components from ACTUAL
            // coordinates, not the AI's claimed success text.
            var placement=PlacementAppropriatenessAudit.inspect(blueprint,request);
            return new Findings(placement.blockers(),placement.suggestions());
        },
        (blueprint,request,specification)->{
            var report=FarmFunctionAudit.inspect(blueprint,request,specification);
            return new Findings(report.blockers(),report.cautions());
        },
        (blueprint,request,specification)->{
            List<String> warnings=FluidGateAudit.inspect(blueprint,request,specification);
            List<String> blockers=new ArrayList<>(),notes=new ArrayList<>();
            for(String issue:warnings){
                if(issue.contains("piston extension cell")||
                   issue.contains("too short for a harvest"))
                    blockers.add(issue);
                else notes.add(issue);
            }
            return new Findings(List.copyOf(blockers),List.copyOf(notes));
        },
        (blueprint,request,specification)->{
            // Existing structural checks are advisory unless there is a
            // verified, unavoidable breakage. They are not game simulation.
            List<String> warnings=ArchitectureAudit.inspect(
                blueprint,request,specification);
            return new Findings(List.of(),warnings);
        }
    );

    public static Findings inspect(Blueprint blueprint,String request,
                                    String specification){
        List<String> blockers=new ArrayList<>(),suggestions=new ArrayList<>();
        for(Rule rule:RULES){
            try{
                Findings result=rule.inspect(blueprint,request,specification);
                blockers.addAll(result.blockers());
                suggestions.addAll(result.suggestions());
            }catch(RuntimeException problem){
                suggestions.add("One static inspection module could not finish; "+
                    "review the structure in Minecraft before claiming it works.");
            }
        }
        return new Findings(List.copyOf(blockers),List.copyOf(suggestions));
    }
}
