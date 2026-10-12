package com.hanco.buildai.blueprint;

import com.google.gson.JsonParser;
import net.minecraft.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** A typo in one AI-authored block must not discard the completed design. */
public class AiBlockRepairTest {
    @BeforeAll static void initMinecraftRegistries() {
        Bootstrap.initialize();
    }

    @Test void unsupportedSeagrassPropertyIsRemovedWithoutLosingGeometry() {
        String spec = """
            {"name":"Underwater Garden","width":3,"height":2,"length":2,"operations":[
              {"kind":"fill","block":"minecraft:stone_bricks","x1":0,"y1":0,"z1":0,"x2":2,"y2":0,"z2":1},
              {"kind":"fill","block":"minecraft:seagrass[waterlogged=true]","x1":1,"y1":1,"z1":0,"x2":1,"y2":1,"z2":1}
            ]}
            """;
        ProceduralGenerator.Compilation result = ProceduralGenerator.compileDesign(spec);
        Blueprint b = result.blueprint();
        assertEquals("minecraft:seagrass", b.get(1,1,0));
        assertEquals("minecraft:seagrass", b.get(1,1,1));
        assertEquals("minecraft:stone_bricks", b.get(0,0,0));
        assertEquals(1, b.warnings.size(), "Report once per unique invalid state");
        assertTrue(b.warnings.get(0).contains("waterlogged=true"));
        assertEquals("minecraft:seagrass",JsonParser.parseString(result.correctedSpecification())
            .getAsJsonObject().getAsJsonArray("operations").get(1)
            .getAsJsonObject().get("block").getAsString());
        BlockStates.validate(b);
    }

    @Test void inventedCutSandstoneStairsBecomeValidSandstoneStairs() {
        String raw="minecraft:cut_sandstone_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]";
        var repaired=AiBlockRepair.repair(raw);
        assertEquals("minecraft:sandstone_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]",
            repaired.block());
        assertTrue(repaired.warning().contains("nonexistent block replaced"));
        BlockStates.parse(repaired.block());
    }

    @Test void malformedAIBooleanFallsBackToBlockDefault() {
        var repaired=AiBlockRepair.repair("minecraft:oak_stairs[facing=north,waterlogged=maybe]");
        assertEquals("minecraft:oak_stairs[facing=north]",repaired.block());
        assertTrue(repaired.warning().contains("invalid value"));
    }

    @Test void importedStatesStayStrictInsteadOfBeingSilentlyEdited() {
        assertThrows(IllegalArgumentException.class,
            ()->BlockStates.parse("minecraft:seagrass[waterlogged=true]"));
        assertThrows(IllegalArgumentException.class,
            ()->BlockStates.parse("minecraft:cut_sandstone_stairs"));
    }
}
