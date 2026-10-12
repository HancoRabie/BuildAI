package com.hanco.buildai.blueprint;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public final class BlueprintDeliveryPolicyTest {
    private static final String REFUSAL="""
        {"error":"I cannot truthfully deliver this as a fully functional, preloaded, validated farm using the available block-only blueprint format. Essential inventory and entity configuration and actual Minecraft testing are not available."}
        """;

    @Test void validationIsNotGeometryFailure() {
        assertTrue(BlueprintDeliveryPolicy.retryableLimitation(REFUSAL));
        assertTrue(BlueprintDeliveryPolicy.correction().contains("COMPLETE JSON blueprint"));
        assertTrue(BlueprintDeliveryPolicy.correction().contains("same scale")
            || BlueprintDeliveryPolicy.correction().contains("requested scale"));
        assertTrue(BlueprintDeliveryPolicy.correction().contains("limitations"));
    }

    @Test void neverAutomaticallyRetriesRealEngineLimits() {
        for(String response:new String[]{
            "{\"error\":\"This design exceeds the 4,000,000 voxel limit\"}",
            "{\"error\":\"Requires section-based construction\"}",
            "{\"error\":\"Cannot fit inside 512 block axis\"}",
            "{\"error\":\"Too many cells to represent within engine dimensions\"}"
        })assertFalse(BlueprintDeliveryPolicy.retryableLimitation(response),response);
    }

    @Test void usableGeometryWinsOverApologeticMetadata() {
        String validWithCaveat="""
            {"name":"Farm","width":3,"height":2,"length":3,
             "operations":[{"kind":"fill","block":"minecraft:stone",
               "x1":0,"y1":0,"z1":0,"x2":2,"y2":0,"z2":2}],
             "error":"I cannot validate this without Minecraft",
             "limitations":["Dispenser requires two buckets of water in its own inventory",
                            "Test piston floodgate with a button"]}
            """;
        assertFalse(BlueprintDeliveryPolicy.retryableLimitation(validWithCaveat));
        Blueprint b=ProceduralGenerator.compile(validWithCaveat);
        assertEquals(9,b.solidCount());
        assertTrue(b.warnings.stream().anyMatch(s->s.contains("MODEL DESIGN CAVEAT")));
        assertEquals(2,b.warnings.stream().filter(s->s.contains("SETUP / IN-GAME TEST")).count());
    }

    @Test void refusalWithoutGeometryStillFailsRatherThanInventingAWorkingFarm() {
        assertThrows(IllegalArgumentException.class,
            ()->ProceduralGenerator.compile(REFUSAL));
        assertFalse(BlueprintDeliveryPolicy.retryableLimitation(
            "{\"error\":\"The requested design exceeds a hard engine limit; "+
            "use section-based construction\"}"));
    }

    @Test void noFalseRetriesOnNormalBlueprintsOrUnrelatedErrors() {
        assertFalse(BlueprintDeliveryPolicy.retryableLimitation(
            "{\"name\":\"Farm\",\"operations\":[{\"block\":\"minecraft:stone\"}]}"));
        assertFalse(BlueprintDeliveryPolicy.retryableLimitation(
            "{\"error\":\"Minecraft block IDs invalid\"}"));
    }
}
