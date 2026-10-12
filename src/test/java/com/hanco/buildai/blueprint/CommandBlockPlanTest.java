package com.hanco.buildai.blueprint;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class CommandBlockPlanTest {
    private static Blueprint sample() {
        Blueprint blueprint=new Blueprint("Capture the Flag test",9,5,9);
        blueprint.set(2,1,1,"minecraft:command_block[facing=north,conditional=false]");
        blueprint.set(2,1,2,"minecraft:chain_command_block[facing=south,conditional=false]");
        blueprint.set(2,1,3,"minecraft:repeating_command_block[facing=south,conditional=false]");
        return blueprint;
    }

    @Test void compilerRetainsExactCommandsAndTransportPreservesThem(){
        String spec="""
            {"name":"CTF Arena","width":9,"height":5,"length":9,
              "operations":[
                {"kind":"fill","block":"minecraft:command_block","x1":2,"y1":1,"z1":1,"x2":2,"y2":1,"z2":1},
                {"kind":"fill","block":"minecraft:repeating_command_block","x1":2,"y1":1,"z1":3,"x2":2,"y2":1,"z2":3}
              ],"commands":[
                {"x":2,"y":1,"z":1,"command":"team add red","auto":false},
                {"x":2,"y":1,"z":3,"command":"execute as @a[team=red] run effect give @s speed 1 0 true","auto":true}
              ]}
            """;
        Blueprint b=ProceduralGenerator.compile(spec);
        assertEquals(2,b.commands().size());
        assertEquals("team add red",b.commands().get(0).command());
        assertTrue(b.commands().get(1).auto());
        Blueprint roundTrip=BlueprintJson.decode(BlueprintJson.encode(b));
        assertEquals(b.commands(),roundTrip.commands());
    }

    @Test void rejectCommandMismatchedToSolidBlock(){
        Blueprint b=sample();
        assertThrows(IllegalArgumentException.class,()->
            b.setCommands(List.of(new CommandBlockPlan.Entry(
                0,0,0,"scoreboard objectives add ctfWins dummy",false,false))));
        assertThrows(IllegalArgumentException.class,()->
            b.setCommands(List.of(new CommandBlockPlan.Entry(
                99,0,0,"team add blue",false,false))));
    }

    @Test void dangerousRootsAndNestedExecuteTargetsStayForbidden(){
        for(String command:List.of(
            "op Steve","stop","setblock ~ ~ ~ minecraft:bedrock",
            "execute as @a run function namespace:destructive",
            "execute as @s run execute at @s run data modify entity @s ...",
            "/scoreboard objectives add round dummy",
            "team add red\nop Steve"))
            assertThrows(IllegalArgumentException.class,()->
                CommandBlockPlan.validateCommand(command),command);
        assertDoesNotThrow(()->CommandBlockPlan.validateCommand(
            "execute as @a[team=blue] run scoreboard players add @s flags 1"));
    }

    @Test void duplicateCommandPositionAndOverLimitRejected(){
        Blueprint b=sample();
        var entry=new CommandBlockPlan.Entry(2,1,1,"team add red",false,false);
        var twice=CommandBlockPlan.encode(List.of(entry,entry));
        assertThrows(IllegalArgumentException.class,()->CommandBlockPlan.decode(twice,b));
        assertThrows(IllegalArgumentException.class,()->
            CommandBlockPlan.validateCommand("give @p "+ "x".repeat(800)));
    }

    @Test void rotationWithoutCommandCoordinateRewritingIsExplicitlyRejected(){
        Blueprint b=sample();
        b.setCommands(List.of(new CommandBlockPlan.Entry(2,1,1,"team add red",false,false)));
        assertThrows(IllegalArgumentException.class,()->BlueprintRotator.rotate(b,1));
        assertSame(b,BlueprintRotator.rotate(b,0));
    }

    @Test void ctfWarningsDoNotClaimFunctionalityFromVisualGeometry(){
        Blueprint b=sample();
        b.setCommands(List.of(new CommandBlockPlan.Entry(2,1,1,"team add blue",false,false)));
        var warnings=CommandGameAudit.inspect(b,"Build a capture the flag arena");
        assertTrue(warnings.stream().anyMatch(w->w.contains("scoreboard")),warnings.toString());
        assertTrue(warnings.stream().anyMatch(w->w.contains("loadout")),warnings.toString());
        assertTrue(warnings.stream().anyMatch(w->w.contains("integration testing")));
    }
}
