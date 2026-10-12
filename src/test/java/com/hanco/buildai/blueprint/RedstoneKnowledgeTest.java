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
    @Test void historicalAndBedrockSourcesAreNotTreatedAsJavaMechanics(){
        String handbook=RedstoneKnowledge.instructions("Redstone latch with repeater clock",null);
        assertTrue(handbook.contains("author's guide")||
            handbook.contains("AUTHOR'S GUIDE"),handbook.substring(0,300));
        assertTrue(handbook.contains("2011 Minecraft Forum"));
        assertTrue(handbook.contains("BEDROCK Edition"));
        assertTrue(handbook.contains("Minecraft Java 1.21.1")||
            handbook.contains("JAVA 1.21.1"));
        assertTrue(handbook.contains("1.21.2+"));
        assertTrue(handbook.contains("Crafters")||
            handbook.contains("Crafters do NOT use QC"));
    }
    @Test void logicPatternsAndRealisticTimersArePackagedInEveryMachineRequest(){
        String instructions=RedstoneKnowledge.instructions(
            "Build an automatic redstone item sorter with a latching switch",null);
        for(String fact:new String[]{
            "AND: 00->0; 01->0; 10->0; 11->1",
            "XOR: 00->0; 01->1; 10->1; 11->0",
            "RS (set-reset) latch",
            "Monostable / one-shot",
            "T flip-flop",
            "one item per 8 GAME ticks",
            "Quasi",
            "an actual successful pulse"}) {
            assertTrue(instructions.toLowerCase(java.util.Locale.ROOT)
                .contains(fact.toLowerCase(java.util.Locale.ROOT)),
                "Missing Java 1.21.1 reference: "+fact);
        }
    }
    @Test void boundedPulseConditioningIsNotConfusedWithArbitraryTimer(){
        String guidance=RedstoneKnowledge.instructions("a piston redstone timer",null);
        assertTrue(guidance.contains("widen input pulses shorter"));
        assertTrue(guidance.contains("NOT itself"));
        assertTrue(guidance.contains("20 GAME ticks"));
        assertTrue(guidance.contains("30 game"));
        assertTrue(guidance.contains("full TPS"));
    }
    @Test void nonMechanicalCottageStillDoesNotLoadHistoricalCircuitReference(){
        var plain=RedstoneKnowledge.instructions("A cozy medieval cottage",null);
        assertFalse(plain.contains("2011 Minecraft Forum"));
        assertTrue(plain.length()<600);
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
