package com.hanco.buildai.blueprint;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public final class BlueprintRevisionSeedTest {
    @Test void oldSchematicGeometryCanBeReconstructedWithoutInventingPrompt() {
        Blueprint old=new Blueprint("Original older project",20,7,13);
        for(int y=0;y<7;y++)for(int z=0;z<13;z++)for(int x=0;x<20;x++){
            if(y==0)old.set(x,y,z,"minecraft:stone_bricks");
            if(y==2&&x==4)old.set(x,y,z,"minecraft:glass_pane[north=true,south=true,east=false,west=false,waterlogged=false]");
        }
        old.set(9,3,5,"minecraft:oak_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]");
        String spec=BlueprintRevisionSeed.from(old);
        assertTrue(spec.contains("\"operations\""));
        assertFalse(old.hasOriginalPrompt(),"legacy schematics have NO recoverable prompt");
        Blueprint reconstructed=ProceduralGenerator.compile(spec);
        assertEquals(old.name,reconstructed.name);
        assertEquals(old.width,reconstructed.width);
        assertEquals(old.height,reconstructed.height);
        assertEquals(old.length,reconstructed.length);
        for(int y=0;y<old.height;y++)for(int z=0;z<old.length;z++)
            for(int x=0;x<old.width;x++)
                assertEquals(old.get(x,y,z),reconstructed.get(x,y,z));
    }

    @Test void savedDesignHistoryTakesPrecedenceOverSynthesizedRunGeometry() {
        Blueprint old=new Blueprint("Ancient",5,5,5);
        String exact="{\"name\":\"Original AI design\",\"width\":5,\"height\":5,\"length\":5,\"operations\":[{\"kind\":\"fill\",\"block\":\"minecraft:stone\",\"x1\":0,\"x2\":0,\"y1\":0,\"y2\":0,\"z1\":0,\"z2\":0}]}";
        old.setGenerationContext("Build me a beautiful old fort.",exact);
        assertEquals("Build me a beautiful old fort.",old.originalPrompt());
        assertSame(exact,BlueprintRevisionSeed.from(old));
    }

    @Test void oldCommandBlocksKeepInertCoordinateMappedScriptsWhenUsedAsRevisionSeed() {
        Blueprint old=new Blueprint("Old command layout",9,4,9);
        old.set(2,1,2,"minecraft:command_block[facing=north]");
        old.setCommands(List.of(new CommandBlockPlan.Entry(
            2,1,2,"time set day",false,false)));
        String snapshot=BlueprintRevisionSeed.from(old);
        Blueprint restored=ProceduralGenerator.compile(snapshot);
        assertEquals(1,restored.commands().size());
        assertEquals("time set day",restored.commands().get(0).command());
        assertEquals(2,restored.commands().get(0).x());
    }

    @Test void metadataCannotBeOversized(){
        Blueprint b=new Blueprint("Prompt test",1,1,1);
        assertThrows(IllegalArgumentException.class,()->
            b.setGenerationContext("x".repeat(4001),""));
        assertThrows(IllegalArgumentException.class,()->
            b.setGenerationContext("Original","x".repeat(1_500_001)));
    }
}
