package com.hanco.buildai.blueprint;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ArchitecturalStyleAnalyzerTest {

    @Test void wholeSchematicReportsPaletteTrimAndExact3DMotifsWithoutLabels() {
        Blueprint b=new Blueprint("Detailed Roof",12,9,12);
        for(int x=0;x<12;x++)for(int z=0;z<12;z++){
            b.set(x,0,z,"minecraft:stone_bricks");
            b.set(x,8,z,"minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]");
        }
        for(int y=1;y<=5;y++)for(int x=0;x<12;x++){
            b.set(x,y,0,"minecraft:oak_planks");
            b.set(x,y,11,"minecraft:oak_planks");
        }
        b.set(4,3,0,"minecraft:glass_pane[north=true,south=false,east=false,west=false,waterlogged=false]");
        b.set(5,3,0,"minecraft:glass_pane[north=true,south=false,east=false,west=false,waterlogged=false]");
        b.set(4,2,0,"minecraft:oak_trapdoor[facing=north,half=top,open=false,powered=false,waterlogged=false]");
        var result=ArchitecturalStyleAnalyzer.analyze(b);
        assertEquals(b.solidCount(),result.blocks());
        assertEquals(4,result.uniqueMaterials());
        assertTrue(result.report().contains("minecraft:oak_stairs="),result.report());
        assertTrue(result.report().contains("minecraft:glass_pane="));
        assertTrue(result.report().contains("Detail vocabulary"));
        assertTrue(result.report().contains("EXACT DETAIL EXCERPTS"));
        assertTrue(result.report().contains("focal block"));
    }

    @Test void emptySchematicCannotBecomeAnAuthoritativeArchitectureReference() {
        Blueprint air=new Blueprint("Air",8,8,8);
        var error=assertThrows(IllegalArgumentException.class,()->
            ArchitecturalStyleAnalyzer.analyze(air));
        assertTrue(error.getMessage().contains("only air"));
    }

    @Test void analysisDoesNotMutateWorldBlocks() {
        Blueprint b=new Blueprint("Stone",3,3,3);
        b.set(0,0,0,"minecraft:chiseled_stone_bricks");
        var result=ArchitecturalStyleAnalyzer.analyze(b);
        assertEquals("minecraft:chiseled_stone_bricks",b.get(0,0,0));
        assertTrue(result.report().contains("non-air blocks: 1"));
    }
}
