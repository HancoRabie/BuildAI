package com.hanco.buildai.blueprint;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RedstoneKnowledgeTest {
    @Test void redstoneWordingAlwaysLoadsTheFullPackagedTechnicalHandbook(){
        for(String request:new String[]{
            "Build a redstone machine","wire a Red Stone computer",
            "piston door with a timing circuit",
            "create an automatic item sorter with hoppers",
            "functional capture the flag arena using command blocks",
            "make a crafter auto production facility",
            "Build an observer clock", "Construct a vending machine with a comparator"}) {
            assertTrue(RedstoneKnowledge.isRedstoneRequest(request,null),request);
            String instructions=RedstoneKnowledge.instructions(request,null);
            assertTrue(instructions.contains("REDSTONE MASTER HANDBOOK"),request);
            assertTrue(instructions.contains("quasi-connectivity"),request);
            assertTrue(instructions.contains("copper_bulb"),request);
            assertTrue(instructions.contains("redstone_plan"),request);
            assertTrue(instructions.contains("CRAFTS one recipe")||
                instructions.contains("crafts and ejects"),request);
        }
    }
    @Test void revisionKeepsTechnicalManualEvenIfUserJustSaysChangeTheRoof(){
        String past="""
            {"operations":[{"block":"minecraft:redstone_wire","kind":"line"}],
              "redstone_plan":{"circuits":[{"name":"Door"}]}}
            """;
        assertTrue(RedstoneKnowledge.isRedstoneRequest("Move the doorway",past));
        assertTrue(RedstoneKnowledge.instructions("Move the doorway",past)
            .contains("REDSTONE MASTER HANDBOOK"));
    }
    @Test void ordinaryArchitectureKeepsFastPromptWithoutLargeUnnecessaryReference(){
        String plain=RedstoneKnowledge.instructions(
            "An elegant stone cottage with big windows and 2 floors",null);
        assertFalse(RedstoneKnowledge.isRedstoneRequest(
            "An elegant stone cottage with big windows and 2 floors",null));
        assertFalse(plain.contains("REDSTONE MASTER HANDBOOK"));
        assertTrue(plain.length()<600);
    }
    @Test void bundledManualCorrectsKnownOldReferenceError(){
        String guidance=RedstoneKnowledge.instructions("redstone copper bulb toggle",null);
        assertTrue(guidance.contains("NOT timing"));
        assertTrue(guidance.contains("crafter does NOT use this QC rule"));
        assertTrue(guidance.contains("Minecraft Java 1.21.1")||
            guidance.contains("MINECRAFT JAVA 1.21.1"));
        assertTrue(guidance.length()>10000);
    }
}
