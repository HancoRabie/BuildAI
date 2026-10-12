package com.hanco.buildai.blueprint;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure-Java regression tests. Tests that actually inspect Minecraft's block
 * registry must run inside Fabric runClient because JUnit lacks access wideners.
 */
public class AiBlockRepairTest {

    @Test void oneBadAIOperationDoesNotDiscardCompletedVoxelDesign() {
        String spec = """
            {"name":"Underwater Garden","width":3,"height":2,"length":2,"operations":[
              {"kind":"fill","block":"minecraft:stone_bricks","x1":0,"y1":0,"z1":0,"x2":2,"y2":0,"z2":1},
              {"kind":"fill","block":"minecraft:seagrass[waterlogged=true]","x1":1,"y1":1,"z1":0,"x2":1,"y2":1,"z2":0},
              {"kind":"fill","block":"minecraft:seagrass[waterlogged=true]","x1":1,"y1":1,"z1":1,"x2":1,"y2":1,"z2":1}
            ]}
            """;
        // Simulate a live-registry result to verify the generator's recovery
        // pipeline without initializing Minecraft internals in plain JUnit.
        ProceduralGenerator.Compilation result=ProceduralGenerator.compileWithRepair(spec,raw ->
            raw.equals("minecraft:seagrass[waterlogged=true]")
                ? new AiBlockRepair.Result("minecraft:seagrass",
                    "AI block auto-repaired: unsupported waterlogged=true")
                : new AiBlockRepair.Result(raw,null));

        Blueprint b=result.blueprint();
        assertEquals("minecraft:seagrass",b.get(1,1,0));
        assertEquals("minecraft:seagrass",b.get(1,1,1));
        assertEquals("minecraft:stone_bricks",b.get(0,0,0));
        assertEquals(1,b.warnings.size(),"Report once per distinct state");
        assertTrue(b.warnings.get(0).contains("waterlogged=true"));
        var corrected=JsonParser.parseString(result.correctedSpecification()).getAsJsonObject();
        assertEquals("minecraft:seagrass",corrected.getAsJsonArray("operations")
            .get(1).getAsJsonObject().get("block").getAsString());
        assertEquals("minecraft:seagrass",corrected.getAsJsonArray("operations")
            .get(2).getAsJsonObject().get("block").getAsString());
    }

    @Test void cutSandstoneAlternativesKeepStairShapeWhenAvailable() {
        Set<String> existing=Set.of("minecraft:sandstone_stairs",
            "minecraft:cut_sandstone","minecraft:stone_bricks");
        assertEquals("minecraft:sandstone_stairs",
            AiBlockRepair.suggestAlternative("minecraft:cut_sandstone_stairs",existing::contains));
        assertEquals("minecraft:cut_sandstone",
            AiBlockRepair.suggestAlternative("minecraft:cut_sandstone_column",existing::contains));
    }

    @Test void unrecognizedMaterialReceivesConcreteSafeFallback() {
        assertEquals("minecraft:stone_bricks",
            AiBlockRepair.suggestAlternative("minecraft:totally_imaginary_material",s -> false));
    }

    @Test void pureCompilerDoesNotModifyImportedOrPrevalidationStates() {
        String json="""
            {"name":"Unsafe Source","width":1,"height":1,"length":1,"operations":[
              {"kind":"fill","block":"minecraft:seagrass[waterlogged=true]",
               "x1":0,"y1":0,"z1":0,"x2":0,"y2":0,"z2":0}
            ]}
            """;
        assertEquals("minecraft:seagrass[waterlogged=true]",
            ProceduralGenerator.compile(json).get(0,0,0));
        // In-game block validation remains strict for untrusted imported data.
    }

    @org.junit.jupiter.api.Disabled("Requires Fabric runtime access wideners; smoke-test in runClient")
    @Test void liveRegistryMustRemoveUnsupportedSeagrassProperties() {
        assertEquals("minecraft:seagrass",
            AiBlockRepair.repair("minecraft:seagrass[waterlogged=true]").block());
        assertEquals("minecraft:sandstone_stairs",
            AiBlockRepair.repair("minecraft:cut_sandstone_stairs").block());
    }
}
