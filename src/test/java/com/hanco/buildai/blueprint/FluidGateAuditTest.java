package com.hanco.buildai.blueprint;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public final class FluidGateAuditTest {
    @Test void neverTreatPistonHeadSpaceAsWaterPipe(){
        Blueprint b=new Blueprint("Failed floodgate",12,6,12);
        b.set(4,2,6,"minecraft:piston[facing=north,extended=false]");
        b.set(4,2,5,"minecraft:water[level=0]"); // head extension cell
        var notes=FluidGateAudit.inspect(b,
            "Automated farm with water flushing and piston floodgate",null);
        assertTrue(notes.stream().anyMatch(x->x.contains("piston extension cell")),
            notes.toString());
        assertTrue(notes.stream().anyMatch(x->x.contains("CLOSED/OPEN")),
            notes.toString());
    }
    @Test void shortHoldOpenTimesAreWarned(){
        Blueprint b=new Blueprint("Floodgate",12,6,12);
        b.set(4,2,6,"minecraft:piston[facing=north,extended=false]");
        b.set(4,3,5,"minecraft:water[level=0]");
        String spec="""
          {"fluid_gate_plan":{
            "source":"(4,3,5)","gate":"(4,2,5)",
            "closedState":"head blocks throat",
            "openState":"head retracted, air at throat",
            "path":"into crop row","holdOpenTicks":"4",
            "reset":"re-power piston","collection":"hopper channel",
            "testing":"run test in Minecraft"
          }}
          """;
        var warnings=FluidGateAudit.inspect(b,"automatic water flushing farm",spec);
        assertTrue(warnings.stream().anyMatch(x->x.contains("too short")),warnings.toString());
        assertFalse(warnings.stream().anyMatch(x->x.contains("Missing explicit")),warnings.toString());
    }
    @Test void unrelatedBuildingHasNoFloodgateAdvice(){
        Blueprint b=new Blueprint("Stone manor",5,4,5);
        b.set(2,1,2,"minecraft:stone_bricks");
        assertTrue(FluidGateAudit.inspect(b,"Build a decorative stone manor",null).isEmpty());
    }
}
