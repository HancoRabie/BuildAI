package com.hanco.buildai.blueprint;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Regression smoke tests for GUI actions that don't require a running game world. */
public class BlueprintPipelineTest {
    @TempDir Path temporary;

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

    @org.junit.jupiter.api.Disabled("Requires Fabric runtime access wideners; verify export/import in runClient")
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

    @Test void largeFootprintsUseCompressedRunsAndRetainExactDimensions() {
        Blueprint large=new Blueprint("Full-sized facade",160,24,90);
        large.set(0,0,0,"minecraft:stone_bricks");
        large.set(159,23,89,"minecraft:quartz_block");
        large.set(80,12,45,"minecraft:glass_pane[north=true,south=true,east=false,west=false,waterlogged=false]");
        JsonObject encoded=BlueprintJson.encode(large);
        assertTrue(encoded.has("runs"),"Large blueprints should use compact run-length encoding");
        assertFalse(encoded.has("cells"));
        Blueprint roundTrip=BlueprintJson.decode(encoded);
        assertEquals(160,roundTrip.width);
        assertEquals(24,roundTrip.height);
        assertEquals(90,roundTrip.length);
        assertEquals(3,roundTrip.solidCount());
        assertEquals(large.get(80,12,45),roundTrip.get(80,12,45));
        assertEquals(large.get(159,23,89),roundTrip.get(159,23,89));
    }

    @Test void oversizedArchitectureIsExplainedNotShrunk() {
        IllegalArgumentException error=assertThrows(IllegalArgumentException.class,
            ()->ProceduralGenerator.compile("{\"error\":\"Needs multiple full-scale sections\"}"));
        assertTrue(error.getMessage().contains("multiple full-scale sections"));
        assertThrows(IllegalArgumentException.class,()->new Blueprint("Huge",512,512,512));
    }

    @Test void architecturalChecklistFlagsMissingOvalOfficeWithoutChangingBlocks(){
        Blueprint b=new Blueprint("The White House",10,6,10);
        b.set(2,0,2,"minecraft:stone_bricks");
        var warnings=ArchitectureAudit.inspect(b,"Build a detailed White House with West Wing",null);
        assertTrue(warnings.stream().anyMatch(x->x.contains("Oval Office")));
        assertEquals("minecraft:stone_bricks",b.get(2,0,2));
        assertEquals("minecraft:air",b.get(5,1,5));
    }

    @Test void architecturalAuditFlagsStaircaseHeadroom(){
        Blueprint b=new Blueprint("Basement Stairs",8,6,8);
        b.set(2,1,2,"minecraft:oak_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]");
        b.set(2,2,3,"minecraft:oak_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]");
        b.set(2,3,3,"minecraft:stone_bricks"); // blocks headroom of the upper step
        var warnings=ArchitectureAudit.inspect(b,"Basement staircase",null);
        assertTrue(warnings.stream().anyMatch(x->x.contains("headroom")),warnings.toString());
    }

    @Test void declaredRoomWithoutExitIsFlagged(){
        Blueprint b=new Blueprint("Rooms",8,5,8);
        for(int x=1;x<=5;x++)for(int z=1;z<=5;z++)b.set(x,0,z,"minecraft:oak_planks");
        for(int y=1;y<=3;y++){
            for(int x=1;x<=5;x++){
                b.set(x,y,1,"minecraft:stone_bricks");
                b.set(x,y,5,"minecraft:stone_bricks");
            }
            for(int z=1;z<=5;z++){
                b.set(1,y,z,"minecraft:stone_bricks");
                b.set(5,y,z,"minecraft:stone_bricks");
            }
        }
        String json="{\"rooms\":[{\"name\":\"Private Study\",\"x1\":1,\"y1\":0,\"z1\":1,\"x2\":5,\"y2\":4,\"z2\":5}]}";
        var warnings=ArchitectureAudit.inspect(b,"Build a private study",json);
        assertTrue(warnings.stream().anyMatch(x->x.contains("opening")),warnings.toString());
    }

    @Test void completedAIDoorsHavePairedHalvesWithoutOverwritingObstructions(){
        Blueprint b=new Blueprint("Door Test",8,6,8);
        b.set(2,1,2,"minecraft:dark_oak_door[facing=east,half=lower,hinge=left,open=false,powered=false]");
        b.set(4,1,2,"minecraft:oak_door");
        b.set(4,2,2,"minecraft:stone_bricks");
        var warnings=DoorPlanner.completeSafePairs(b);
        assertTrue(b.get(2,2,2).contains("half=upper"));
        assertTrue(b.get(2,1,2).contains("half=lower"));
        assertEquals("minecraft:stone_bricks",b.get(4,2,2),
            "Never overwrite a wall to force a missing door half");
        assertTrue(warnings.stream().anyMatch(s->s.contains("collides")));
    }

    @Test void organicCanopyIsNotARectangularSlabAndPreservesTrunk(){
        String json="""
            {"name":"Jungle Tree","width":17,"height":13,"length":17,"operations":[
                {"kind":"line","block":"minecraft:jungle_log","x1":8,"y1":0,"z1":8,"x2":8,"y2":9,"z2":8},
                {"kind":"organic_canopy","block":"minecraft:jungle_leaves","seed":19,
                 "x1":2,"y1":5,"z1":2,"x2":14,"y2":12,"z2":14}
            ]}
            """;
        Blueprint b=ProceduralGenerator.compile(json);
        assertTrue(b.get(8,8,8).contains("jungle_log"),"Tree trunk must remain intact");
        assertEquals("minecraft:air",b.get(2,5,2),"Rounded crowns should not fill box corners");
        long leaves=0;
        for(int y=5;y<13;y++)for(int z=2;z<=14;z++)for(int x=2;x<=14;x++)
            if(Blueprint.id(b.get(x,y,z)).equals("minecraft:jungle_leaves"))leaves++;
        assertTrue(leaves>100 && leaves<1000,"Organic canopy should be full but not a solid cube");
    }

    @Test void quarterTurnCoordinatesAgreeWithRotatedBoundingBoxes(){
        Blueprint original=new Blueprint("Asymmetric Front",7,5,13);
        int[][] expected={
            {0,0,7,13},{12,0,13,7},{6,12,7,13},{0,6,13,7}
        };
        for(int t=0;t<4;t++){
            assertEquals(expected[t][2],BlueprintRotator.width(original,t));
            assertEquals(expected[t][3],BlueprintRotator.length(original,t));
            var front=BlueprintRotator.position(0,0,0,original.width,original.length,t);
            assertEquals(expected[t][0],front.getX());
            assertEquals(expected[t][1],front.getZ());
            for(int y=0;y<original.height;y++)for(int z=0;z<original.length;z++)
                for(int x=0;x<original.width;x++){
                    var p=BlueprintRotator.position(x,y,z,original.width,original.length,t);
                    assertTrue(p.getX()>=0&&p.getX()<BlueprintRotator.width(original,t));
                    assertTrue(p.getZ()>=0&&p.getZ()<BlueprintRotator.length(original,t));
                    assertEquals(y,p.getY());
                }
        }
    }

    @Test void rotationFourTimesReturnsSameCoordinate(){
        int w=9,l=14;
        for(int x=0;x<w;x++)for(int z=0;z<l;z++){
            var a=BlueprintRotator.position(x,1,z,w,l,1);
            var b=BlueprintRotator.position(a.getX(),1,a.getZ(),l,w,1);
            var cc=BlueprintRotator.position(b.getX(),1,b.getZ(),w,l,1);
            var d=BlueprintRotator.position(cc.getX(),1,cc.getZ(),l,w,1);
            assertEquals(x,d.getX());
            assertEquals(z,d.getZ());
        }
    }

    @Test void invalidBlueprintDataIsRejected() {
        assertThrows(IllegalArgumentException.class,()->new Blueprint("bad",500,500,500));
        Blueprint a=DemoBlueprint.cottage();
        JsonObject packet=BlueprintJson.encode(a);
        packet.getAsJsonArray("cells").remove(0);
        assertThrows(IllegalArgumentException.class,()->BlueprintJson.decode(packet));
    }
}
