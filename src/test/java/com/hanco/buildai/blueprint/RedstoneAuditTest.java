package com.hanco.buildai.blueprint;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class RedstoneAuditTest {
    @Test void detectsFunctionalPromptWithNoRealWiring() {
        Blueprint b=new Blueprint("Decorative console",8,4,8);
        b.set(2,1,2,"minecraft:stone");
        List<String> warnings=RedstoneAudit.inspect(b,
            "Make an automatic redstone sorting machine",null);
        assertTrue(warnings.stream().anyMatch(s->s.contains("no detected redstone")),warnings.toString());
        assertTrue(warnings.stream().anyMatch(s->s.contains("redstone_plan")),warnings.toString());
    }

    @Test void identifiesMissingSupportDirectionalDefaultsAndControl() {
        Blueprint b=new Blueprint("Test Signal",8,4,8);
        b.set(2,2,2,"minecraft:redstone_wire");
        b.set(4,1,2,"minecraft:repeater");
        b.set(6,1,2,"minecraft:redstone_lamp");
        var warnings=RedstoneAudit.inspect(b,"redstone door timer",null);
        assertTrue(warnings.stream().anyMatch(s->s.contains("AIR beneath")),warnings.toString());
        assertTrue(warnings.stream().anyMatch(s->s.contains("default facing")),warnings.toString());
        assertTrue(warnings.stream().anyMatch(s->s.contains("control input")),warnings.toString());
        assertTrue(warnings.stream().anyMatch(s->s.contains("functional")),warnings.toString());
    }

    @Test void manifestCanSupplyCompleteEngineeringFieldsWithoutSpuriousWarnings() {
        Blueprint b=new Blueprint("Workshop",8,4,8);
        b.set(1,1,1,"minecraft:oak_button[face=wall,facing=north,powered=false]");
        b.set(2,1,1,"minecraft:redstone_wire");
        b.set(3,1,1,"minecraft:repeater[delay=2,facing=east,locked=false,powered=false]");
        b.set(4,1,1,"minecraft:redstone_lamp");
        b.set(2,0,1,"minecraft:stone_bricks");
        String spec="""
            {"redstone_plan":{"circuits":[{
              "name":"Button Light",
              "input":"Oak wall button",
              "signalPath":"Dust to east-facing repeater",
              "output":"Lamp",
              "timing":"Two redstone ticks",
              "reset":"Automatically after release",
              "test":"Press twice and confirm lighting resets"
            }]}}
            """;
        var warnings=RedstoneAudit.inspect(b,"build a redstone lamp circuit",spec);
        assertTrue(warnings.stream().noneMatch(s->s.contains("Missing redstone_plan")),warnings.toString());
        assertTrue(warnings.stream().noneMatch(s->s.contains("default facing")),warnings.toString());
        assertTrue(warnings.stream().anyMatch(s->s.contains("NOT functional simulation")),warnings.toString());
    }

    @Test void decorativeNonRedstoneBuildingsAreNotWarned() {
        Blueprint b=new Blueprint("Stone Cottage",9,8,9);
        b.set(2,1,2,"minecraft:stone");
        assertTrue(RedstoneAudit.inspect(b,"build a decorative cottage",null).isEmpty());
    }
}
