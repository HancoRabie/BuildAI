package com.hanco.buildai.blueprint;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

public class RedstoneBlueprintLibraryTest {
    @Test void everyBundledExampleHasCanonicalVerifiedSyntaxNotInventedStatus(){
        List<String> ids=RedstoneBlueprintLibrary.ids();
        assertTrue(ids.contains("rbl.button_repeater_lamp.1"));
        assertTrue(ids.contains("rbl.lever_repeater_lamp.1"));
        for(String id:ids){
            Blueprint b=RedstoneBlueprintLibrary.templateBlueprint(id);
            assertEquals(3,b.width,id);
            assertEquals(2,b.height,id);
            assertEquals(1,b.length,id);
            assertEquals(6,b.solidCount(),id);
            assertTrue(b.warnings.stream().anyMatch(x->x.contains("NOT VERIFIED")));
            assertEquals("minecraft:stone",b.get(1,0,0));
            assertEquals("minecraft:redstone_lamp[lit=false]",b.get(2,1,0));
        }
    }

    @Test void arrowDescribesPhysicalOutputAndBlockstateUsesJavaRearDirection(){
        Blueprint b=RedstoneBlueprintLibrary.templateBlueprint("rbl.button_repeater_lamp.1");
        assertEquals("minecraft:repeater[facing=west,delay=2,locked=false,powered=false]",
            b.get(1,1,0));
        assertEquals("minecraft:stone_button[face=floor,facing=north,powered=false]",
            b.get(0,1,0));
    }

    @Test void selectsOnlyRelevantRealTemplatesAndKeepsCanonBlockstates(){
        String lamp=RedstoneBlueprintLibrary.relevant(
            "Build a button lamp with a repeater",null);
        assertTrue(lamp.contains("rbl.button_repeater_lamp.1"));
        assertTrue(lamp.contains("minecraft:repeater[facing=west,delay=2"));
        assertTrue(lamp.contains("Y=1 B R2>"));
        assertTrue(lamp.contains("reference_unverified"));
        assertTrue(lamp.contains("🟥"));
        assertTrue(lamp.contains("🔥"));
        String ordinary=RedstoneBlueprintLibrary.relevant("Make a piston drawbridge",null);
        assertFalse(ordinary.contains("MODULE rbl.button_repeater_lamp.1"));
        assertTrue(ordinary.contains("No exact library circuit matches"));
    }

    @Test void redstoneKnowledgeUsesLibraryWithoutPullingItIntoUnrelatedBuilds(){
        String powered=RedstoneKnowledge.instructions("button lamp with redstone",null);
        assertTrue(powered.contains("BUILDAI RBL REUSABLE CIRCUIT LIBRARY"));
        assertTrue(powered.contains("MODULE rbl.button_repeater_lamp.1"));
        String house=RedstoneKnowledge.instructions("a large sandstone manor",null);
        assertFalse(house.contains("BUILDAI RBL"));
    }

    @Test void observerIsOppositeOfPhysicalOutputDirection(){
        assertEquals("minecraft:observer[facing=west,powered=false]",
            RedstoneBlueprintLibrary.exampleState("O>"));
        assertEquals("minecraft:repeater[facing=west,delay=2,locked=false,powered=false]",
            RedstoneBlueprintLibrary.exampleState("R2>"));
        assertEquals("minecraft:piston[facing=east,extended=false]",
            RedstoneBlueprintLibrary.exampleState("P>"));
        assertEquals("minecraft:comparator[facing=west,mode=subtract,powered=false]",
            RedstoneBlueprintLibrary.exampleState("C1>"));
    }

    @Test void missingIdsCannotSilentlyInventModules(){
        assertThrows(IllegalArgumentException.class,()->
            RedstoneBlueprintLibrary.templateBlueprint("unknown imaginary machine"));
    }
}
