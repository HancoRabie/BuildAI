package com.hanco.buildai.blueprint;

import com.google.gson.JsonObject;
import net.minecraft.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Regression smoke tests for GUI actions that don't require a running game world. */
public class BlueprintPipelineTest {
    @TempDir Path temporary;

    @BeforeAll static void initializeVanilla() {
        Bootstrap.initialize();
    }

    @Test void demoCottageIsCompleteHollowArchitecture() {
        Blueprint demo=DemoBlueprint.cottage();
        assertEquals(15,demo.width);
        assertEquals(11,demo.height);
        assertEquals(13,demo.length);
        assertTrue(demo.solidCount()>300);
        assertTrue(demo.solidCount()<demo.width*demo.height*demo.length);
        assertTrue(demo.isAir(6,3,1),"Doorway should remain open");
        assertEquals("minecraft:glass_pane",demo.get(3,3,1));
        assertFalse(demo.isAir(0,0,0),"Foundation present");
        BlockStates.validate(demo);
    }

    @Test void networkBlueprintJsonPreservesEveryBlockState() {
        Blueprint demo=DemoBlueprint.cottage();
        JsonObject encoded=BlueprintJson.encode(demo);
        Blueprint decoded=BlueprintJson.decode(encoded);
        assertEquals(demo.solidCount(),decoded.solidCount());
        for(int y=0;y<demo.height;y++)for(int z=0;z<demo.length;z++)for(int x=0;x<demo.width;x++)
            assertEquals(demo.get(x,y,z),decoded.get(x,y,z),"Cell "+x+","+y+","+z);
        assertEquals(demo.materials(),decoded.materials());
    }

    @Test void saveAndImportGeneratedSchematic() throws Exception {
        Blueprint sample=DemoBlueprint.cottage();
        Path output=temporary.resolve("demo.schem");
        assertEquals(output,SchematicWriter.write(sample,output));
        Blueprint recovered=SchematicReader.read(output);
        assertEquals(sample.width,recovered.width);
        assertEquals(sample.height,recovered.height);
        assertEquals(sample.length,recovered.length);
        assertEquals(sample.solidCount(),recovered.solidCount());
        for(int y=0;y<sample.height;y++)for(int z=0;z<sample.length;z++)for(int x=0;x<sample.width;x++)
            assertEquals(sample.get(x,y,z),recovered.get(x,y,z));
    }

    @Test void aiOperationCompilerCarvesDoorsAndRejectsUnsafeActions() {
        String json="""
            {"name":"Practice House","width":5,"height":4,"length":5,"operations":[
              {"kind":"shell","block":"minecraft:stone_bricks","x1":0,"y1":0,"z1":0,"x2":4,"y2":3,"z2":4},
              {"kind":"fill","block":"minecraft:air","x1":2,"y1":1,"z1":0,"x2":2,"y2":2,"z2":0}
            ]}
            """;
        Blueprint b=ProceduralGenerator.compile(json);
        assertEquals("minecraft:stone_bricks",b.get(0,0,0));
        assertTrue(b.isAir(2,1,0));
        assertTrue(b.isAir(2,2,0));
        assertTrue(b.isAir(2,1,2));
        assertEquals(5,b.width);
        assertThrows(IllegalArgumentException.class,()->
            ProceduralGenerator.compile(json.replace("\"kind\":\"shell\"","\"kind\":\"command\"")));
        assertThrows(IllegalArgumentException.class,()->
            ProceduralGenerator.compile(json.replace("\"x2\":4","\"x2\":500")));
    }

    @Test void invalidBlueprintDataIsRejected() {
        assertThrows(IllegalArgumentException.class,()->new Blueprint("bad",100,100,100));
        Blueprint a=DemoBlueprint.cottage();
        JsonObject packet=BlueprintJson.encode(a);
        packet.getAsJsonArray("cells").remove(0);
        assertThrows(IllegalArgumentException.class,()->BlueprintJson.decode(packet));
    }
}
