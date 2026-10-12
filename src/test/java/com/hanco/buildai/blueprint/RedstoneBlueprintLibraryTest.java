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

    @Test void everyDirectionResolvesTowardTargetInsteadOfHardcodedEastWest(){
        String[] dirs={"east","south","west","north"};
        String[] facing={"west","north","east","south"};
        String[] arrows={">","v","<","^"};
        for(int i=0;i<4;i++){
            String target=dirs[i],rear=facing[i],arrow=arrows[i];
            for(int delay=1;delay<=4;delay++){
                String state=RedstoneBlueprintLibrary.exampleState(
                    "R"+delay+arrow);
                assertTrue(state.contains("facing="+rear),state);
                assertTrue(state.contains("delay="+delay),state);
                assertEquals(state,
                    RedstoneBlueprintLibrary.orientedState("R"+delay+">",target));
            }
            for(int mode=0;mode<=1;mode++){
                String state=RedstoneBlueprintLibrary.exampleState(
                    "C"+mode+arrow);
                assertTrue(state.contains("facing="+rear),state);
                assertTrue(state.contains(
                    "mode="+(mode==0?"compare":"subtract")),state);
            }
            assertTrue(RedstoneBlueprintLibrary.exampleState("O"+arrow)
                .contains("facing="+rear));
            assertTrue(RedstoneBlueprintLibrary.exampleState("P"+arrow)
                .contains("facing="+target));
            assertTrue(RedstoneBlueprintLibrary.exampleState("SP"+arrow)
                .contains("facing="+target));
        }
    }

    @Test void targetCoordinatesChooseDirectionWithoutPlayerCamera(){
        assertTrue(RedstoneBlueprintLibrary.orientedStateToward(
            "R3>",8,2,8,8,2,7).contains("facing=south"));
        assertEquals("north",RedstoneBlueprintLibrary.directionToward(
            8,2,8,8,2,7));
        assertEquals("south",RedstoneBlueprintLibrary.directionToward(
            8,2,8,8,2,9));
        assertEquals("east",RedstoneBlueprintLibrary.directionToward(
            8,2,8,9,2,8));
        assertEquals("west",RedstoneBlueprintLibrary.directionToward(
            8,2,8,7,2,8));
        assertThrows(IllegalArgumentException.class,()->
            RedstoneBlueprintLibrary.directionToward(0,1,0,0,2,0));
        assertThrows(IllegalArgumentException.class,()->
            RedstoneBlueprintLibrary.directionToward(0,1,0,1,1,1));
    }

    @Test void fullCircuitAndPortsRotateCorrectlyInAllFourDirections(){
        String id="rbl.button_repeater_lamp.1";
        for(String target:new String[]{"east","south","west","north"}){
            Blueprint b=RedstoneBlueprintLibrary.templateBlueprint(id,target);
            var ports=RedstoneBlueprintLibrary.ports(id,target);
            var input=ports.stream().filter(p->p.name().equals("input")).findFirst().orElseThrow();
            var output=ports.stream().filter(p->p.name().equals("output")).findFirst().orElseThrow();
            assertEquals(6,b.solidCount());
            assertEquals("minecraft:redstone_lamp[lit=false]",
                b.get(output.x(),output.y(),output.z()));
            int mx=(input.x()+output.x())/2,
                mz=(input.z()+output.z())/2;
            String repeater=b.get(mx,1,mz);
            String expected=RedstoneBlueprintLibrary.orientedState("R2>",target);
            assertEquals(expected,repeater);
            assertTrue(b.get(input.x(),input.y(),input.z())
                .startsWith("minecraft:stone_button["));
            int dx=output.x()-input.x(),dz=output.z()-input.z();
            assertEquals(2,Math.abs(dx)+Math.abs(dz));
            assertEquals(target,
                dx>0?"east":dx<0?"west":dz>0?"south":"north");
            if(target.equals("north")||target.equals("south")){
                assertEquals(1,b.width);
                assertEquals(3,b.length);
            } else{
                assertEquals(3,b.width);
                assertEquals(1,b.length);
            }
        }
    }

    @Test void missingIdsCannotSilentlyInventModules(){
        assertThrows(IllegalArgumentException.class,()->
            RedstoneBlueprintLibrary.templateBlueprint("unknown imaginary machine"));
    }
}
